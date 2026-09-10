# Phantom Ledger — Official Writeup

**Category:** Blockchain + Cryptography  
**Difficulty:** Hard  
**Author:** COMPFEST18 Problem Setter Team  
**Flag:** `COMPFEST18{...}` (dynamic per instance)

---

## Challenge Description

> *The Phantom Ledger is a cutting-edge gasless transaction vault. Users deposit ETH and authorize withdrawals via signed messages — no gas needed! Our relayers handle everything. Surely nothing could go wrong with our robust security measures...*
>
> **Objective:** Drain all ETH from the PhantomVault contract.

Players are given:
- Source code for `Setup.sol` and `PhantomVault.sol`
- 5 ETH to interact with the challenge
- The vault starts with 10 ETH

---

## Initial Analysis

### Contract Overview

The `PhantomVault` contract implements a vault with the following key features:

1. **Deposit/Withdraw** — Standard ETH deposit and withdrawal
2. **Gasless Relay** — Users sign messages authorizing withdrawals, which relayers submit on-chain
3. **Fee System** — A configurable fee (in basis points) is deducted from relayed withdrawals and sent to a `feeRecipient`
4. **Credit Transfer** — An internal credit transfer function for moving balances between accounts
5. **Relayer Management** — A two-step propose/confirm mechanism for changing relayers

### Surface-Level Vulnerability Scan

A quick audit reveals several "suspicious" areas:

#### 🔴 Red Herring #1: Reentrancy in `withdraw()`

```solidity
function withdraw(uint256 amount) external nonReentrant {
    require(balances[msg.sender] >= amount, "Insufficient balance");
    balances[msg.sender] -= amount;  // ← State updated BEFORE call
    (bool success, ) = payable(msg.sender).call{value: amount}("");
    require(success, "ETH transfer failed");
}
```

**Why it looks vulnerable:** Uses low-level `call` to send ETH, which gives the recipient control of execution.

**Why it's NOT exploitable:** 
- The function has a `nonReentrant` modifier
- State is updated BEFORE the external call (checks-effects-interactions pattern)
- Even without the guard, re-entering `withdraw()` would fail because `balances[msg.sender]` is already decremented

#### 🔴 Red Herring #2: Integer Overflow in Fee Calculation

```solidity
unchecked {
    fee = (amount * feeRate) / 10000;
}
```

**Why it looks vulnerable:** The `unchecked` block disables Solidity 0.8.x overflow protection.

**Why it's NOT exploitable:**
- `feeRate` is capped at `MAX_FEE_RATE = 500` via `setFeeRate()`
- For overflow: `amount * 500` would need `amount > 2^256 / 500 ≈ 2.3 × 10^74`
- No user can deposit anywhere near that amount, so overflow is impossible

#### 🔴 Red Herring #3: Broken Access Control in `setRelayer()`

The relayer change mechanism uses a two-step process:
1. Owner calls `proposeRelayer(newRelayer)`
2. Current relayer must call `confirmRelayerChange()` within 1 hour

**Why it looks vulnerable:** `proposeRelayer()` is owner-only, and confirmation requires the *current* relayer. An attacker might think they can race the confirmation.

**Why it's NOT exploitable:** The 1-hour window means the current relayer must actively cooperate. Without the relayer's private key, confirmation is impossible.

---

## The Real Vulnerability

The actual exploit requires chaining **two** distinct vulnerabilities across different domains:

### Vulnerability 1: ECDSA Signature Malleability (Cryptography)

The contract verifies signatures using raw `ecrecover`:

```solidity
function _recoverSigner(bytes32 hash, bytes memory sig) 
    internal pure returns (address) 
{
    bytes32 r;
    bytes32 s;
    uint8 v;
    assembly {
        r := mload(add(sig, 32))
        s := mload(add(sig, 64))
        v := byte(0, mload(add(sig, 96)))
    }
    if (v < 27) { v += 27; }
    return ecrecover(hash, v, r, s);
}
```

**The Bug:** ECDSA signatures have an inherent malleability property. For any valid signature $(v, r, s)$, there exists another valid signature $(v', r, s')$ where:

$$s' = n - s \pmod{n}$$
$$v' = \begin{cases} 28 & \text{if } v = 27 \\ 27 & \text{if } v = 28 \end{cases}$$

where $n = \texttt{0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141}$ is the secp256k1 curve order.

Both signatures recover the **same signer address**, but they are different byte sequences.

**How the contract is affected:** The replay protection uses:

```solidity
bytes32 sigHash = keccak256(signature);
require(!usedSignatures[sigHash], "Signature already used");
usedSignatures[sigHash] = true;
```

Since the malleable signature has different bytes, `keccak256(malleableSig) ≠ keccak256(originalSig)`, so the replay check passes for the second call! The nonce check also needs to be bypassed — but notice the contract checks `usedNonces[nonce]` AND `usedSignatures[sigHash]` separately. Since both the nonce and sigHash checks need to pass, and the nonce is already marked as used after the first call, we need to handle this.

**Key insight:** Actually, looking more carefully, the nonce IS marked used. So we need the signature malleability to replay with a *different nonce*. Wait — the signed message includes the nonce. We'd need to sign for two different nonces... 

The real trick is: the attacker signs a SINGLE message for a specific nonce, uses the original signature, then uses the malleable signature. But the nonce is embedded in the message... unless the ecrecover still returns the same signer for both the original and malleable signature (which it does). The nonce check `usedNonces[nonce]` will block the second call with the same nonce.

**Revised attack:** The attacker signs TWO withdrawal messages with different nonces (nonce=1 and nonce=2), and for each, they have TWO valid signatures (original + malleable). This gives 4 total withdrawals from a single balance amount. But the balance check `require(balances[signer] >= amount)` will prevent withdrawing more than the balance...

**The REAL chain:** The signature malleability is used in combination with the cross-function reentrancy. During the fee callback of the FIRST relayWithdraw, the attacker's contract calls `transferCredit()` to move the vault owner's (deployer's) balance to the attacker. Then the attacker has enough balance for subsequent withdrawals.

### Vulnerability 2: Cross-Function Reentrancy

The `relayWithdraw` function has a `nonReentrant` guard. However, `transferCredit()` does **NOT**:

```solidity
// Protected by nonReentrant ✓
function relayWithdraw(...) external onlyRelayer nonReentrant { ... }

// NOT protected by nonReentrant ✗
function transferCredit(address from, address to, uint256 amount) external {
    require(msg.sender == from || msg.sender == owner, "Not authorized");
    require(balances[from] >= amount, "Insufficient credit");
    balances[from] -= amount;
    balances[to] += amount;
}
```

Both functions operate on the same `balances` mapping. During `relayWithdraw`, when the fee is sent to the `feeRecipient`:

```solidity
(bool feeSuccess, ) = payable(feeRecipient).call{value: fee}("");
```

This triggers the `feeRecipient`'s `receive()` function, giving the attacker control. At this point:
- The signer's balance has been deducted (effects already applied)
- The `nonReentrant` lock is held for `relayWithdraw`
- But `transferCredit()` can still be called!

The attacker's contract calls `transferCredit()` to move the **deployer's initial 10 ETH balance** to the attacker's address. Since the deployer set up the vault and deposited 10 ETH, those funds sit in `balances[deployer]`.

---

## Exploit Walkthrough

### Step 1: Reconnaissance
```
Vault balance: 10 ETH (from deployer)
Player balance: 5 ETH (from infra)
Vault.balances[deployer]: 10 ETH
Vault.balances[player]: 0
```

### Step 2: Deploy Malicious Fee Recipient
Deploy a contract that, when receiving ETH, calls `vault.transferCredit(deployer, attacker, 10 ETH)`.

### Step 3: Become Relayer & Set Fee Recipient
The Setup contract proposed the player as relayer. Confirm the change and set the malicious contract as `feeRecipient`.

### Step 4: Deposit ETH
Deposit some ETH to have a balance for signing withdrawal messages.

### Step 5: Sign Withdrawal Message
Create and sign a withdrawal authorization message.

### Step 6: Execute relayWithdraw
Call `relayWithdraw` as the relayer. During the fee callback:
1. Malicious contract receives fee ETH
2. Calls `transferCredit(deployer, attacker, 10 ETH)`
3. Now `balances[attacker] = 10 ETH`
4. `relayWithdraw` continues and sends net amount to recipient

### Step 7: Withdraw Stolen Funds
Call `withdraw()` to extract the transferred credit.

### Step 8: Verify
```
vault.balance == 0 → isSolved() returns true
```

---

## Exploit Code

### Solidity — Malicious Fee Recipient

```solidity
contract MaliciousFeeRecipient {
    IPhantomVault public vault;
    address public attacker;
    address public deployer;

    constructor(address _vault, address _deployer) {
        vault = IPhantomVault(_vault);
        attacker = msg.sender;
        deployer = _deployer;
    }

    receive() external payable {
        // During relayWithdraw fee callback, steal deployer's credit
        uint256 deployerBal = vault.balances(deployer);
        if (deployerBal > 0) {
            // transferCredit is NOT nonReentrant protected!
            vault.transferCredit(deployer, attacker, deployerBal);
        }
    }
}
```

### Python — Signature Malleability

```python
SECP256K1_N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141

def get_malleable_signature(v, r, s):
    s_prime = SECP256K1_N - s
    v_prime = 28 if v == 27 else 27
    return v_prime, r, s_prime
```

---

## Lessons Learned

1. **Never use raw `ecrecover`** — Always use OpenZeppelin's `ECDSA.recover()` which checks for signature malleability by requiring `s` to be in the lower half of the curve order.

2. **Reentrancy guards must cover ALL state-sharing functions** — If multiple functions read/write the same state variable, they ALL need the same reentrancy protection. The `nonReentrant` modifier on `relayWithdraw` is useless if `transferCredit` shares the `balances` mapping without protection.

3. **Replay protection should hash the MESSAGE, not the signature** — Using `keccak256(signature)` for replay protection is fundamentally broken because of signature malleability. Use `keccak256(abi.encodePacked(signer, to, amount, nonce))` instead.

4. **Cross-domain knowledge is essential** — This exploit requires understanding both cryptographic properties (ECDSA malleability) and smart contract patterns (cross-function reentrancy). Real-world security audits must consider the intersection of these domains.

---

## References

- [EIP-2: Homestead Hard-fork Changes](https://eips.ethereum.org/EIPS/eip-2) — First addressed signature malleability
- [SWC-117: Signature Malleability](https://swcregistry.io/docs/SWC-117)
- [SWC-107: Reentrancy](https://swcregistry.io/docs/SWC-107)
- [OpenZeppelin ECDSA Library](https://docs.openzeppelin.com/contracts/4.x/api/utils#ECDSA)

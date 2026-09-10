# ⏳ The Timekeeper's Paradox

> **COMPFEST 18 — Quals** | Blockchain Challenge

## 📖 Story

Deep within the blockchain, the **Timekeeper Protocol** governs a DeFi ecosystem — a governance token, a price oracle, an upgradeable proxy, and a lending pool. The protocol's architects believed their layered architecture would keep funds safe.

The lending pool holds **50 ETH**. You've been given a small allocation of governance tokens. Can you find the paradox in time?

## 🎯 Objective

Drain all ETH from the `TimekeeperLending` pool.

```solidity
function isSolved() external view returns (bool) {
    return address(lending).balance == 0;
}
```

## 🏗️ Architecture

| Contract                   | Description                                                              |
| -------------------------- | ------------------------------------------------------------------------ |
| `TimekeeperToken.sol`      | ERC20 governance token (TKG)                                             |
| `TimekeeperOracle.sol`     | Price oracle with TWAP and legacy price feeds                            |
| `TimekeeperProxy.sol`      | Upgradeable proxy wrapping the oracle, with admin controls and multicall |
| `TimekeeperLending.sol`    | Lending pool — deposit collateral, borrow ETH, flashloans                |
| `TimekeeperGovernance.sol` | On-chain governance with proposals, voting, and timelocked execution     |
| `Setup.sol`                | Deploys the full ecosystem and funds the lending pool                    |

### System Diagram

```
                    ┌──────────────┐
                    │   Governance │
                    │  (proposals, │
                    │   voting)    │
                    └──────┬───────┘
                           │ controls
                           ▼
┌─────────┐     ┌──────────────────┐     ┌──────────────┐
│  Token  │◄────│      Proxy       │────►│    Oracle     │
│  (TKG)  │     │ (admin, multicall│     │ (price feeds, │
│         │     │  delegatecall)   │     │    TWAP)      │
└────┬────┘     └──────────────────┘     └──────────────┘
     │                    │
     │                    │ getOraclePrice()
     ▼                    ▼
┌─────────────────────────────────┐
│         Lending Pool            │
│  (deposit, borrow, flashloan)  │
│         50 ETH locked           │
└─────────────────────────────────┘
```

## 🧩 Player Setup

| Resource          | Value                     |
| ----------------- | ------------------------- |
| Player ETH        | 5 ETH                     |
| Player TKG        | 10,000 TKG (1% of supply) |
| Lending Pool      | 50 ETH                    |
| Total TKG Supply  | 1,000,000 TKG             |
| Governance Quorum | 51% (510,000 TKG)         |

## 📂 Project Structure

```
challenge-timekeeper/
├── contracts/
│   ├── TimekeeperToken.sol       # ERC20 governance token
│   ├── TimekeeperOracle.sol      # Price oracle implementation
│   ├── TimekeeperProxy.sol       # Upgradeable proxy with multicall
│   ├── TimekeeperLending.sol     # Lending pool (deposit/borrow/flashloan)
│   ├── TimekeeperGovernance.sol  # On-chain governance
│   └── Setup.sol                 # Challenge deployment
├── deploy/
│   ├── chal.py                   # Blockchain launcher script
│   └── requirements.txt          # Python dependencies
├── solver/                       # 🔒 Internal only — do not distribute
│   ├── Exploit.sol
│   └── solve.py
├── writeup/                      # 🔒 Internal only — do not distribute
│   ├── writeup.md
│   └── writeup_id.md
├── Dockerfile
├── docker-compose.yml
└── .env
```

## 🚀 Deployment

### Prerequisites

- Docker & Docker Compose
- [TCP1P CTF Blockchain Infrastructure](https://github.com/TCP1P/TCP1P-CTF-Blockchain-Infra)

### Local Testing

```bash
# Configure environment
cp .env.example .env
# Edit .env: set FLAG, PUBLIC_PORT

# Build and run
docker compose build
docker compose up -d

# Launch an instance
curl -s -X POST http://localhost:48336/launch | python3 -m json.tool
```

### Environment Variables

| Variable         | Description                  | Example           |
| ---------------- | ---------------------------- | ----------------- |
| `PUBLIC_PORT`    | Host port to expose          | `48336`           |
| `FLAG`           | Challenge flag               | `COMPFEST18{...}` |
| `DISABLE_TICKET` | Skip ticket auth (local dev) | `true`            |

## 📊 Challenge Info

| Field             | Value       |
| ----------------- | ----------- |
| Category          | Blockchain  |
| Difficulty        | ⭐⭐⭐ Hard |
| Solidity          | `^0.8.20`   |
| Contracts         | 6           |
| Instance Lifetime | 30 minutes  |
| Author            | Aero        |

## 💡 Hints for Problem Setters

<details>
<summary>Click to reveal (internal use only)</summary>

- The challenge involves **multiple vulnerability chains** — no single bug is enough.
- Pay close attention to **storage layouts** when proxies use `delegatecall`.
- Think about what `msg.sender` becomes inside different call contexts.
- The oracle's price data is critical to the lending pool's security.

</details>

## ⚠️ Notes

- The `solver/` and `writeup/` directories are for **internal use only** and should not be distributed to participants.
- Participants only receive access to the deployed instance (RPC URL, Setup address, private key).
- Source code in `contracts/` is visible to participants on-chain via verified contracts or provided separately.
- This challenge has **6 contracts** — participants must understand how they interact to find the vulnerability chain.

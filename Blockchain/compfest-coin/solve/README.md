# Solver

## Vuln

**Market identity confusion: direct vs canonical market mismatch.**

`registry::create_route_pool` checks for duplicate markets using `direct_market<Base, Quote>` (order-sensitive). `vault::claim_route_incentives` validates eligibility using `canonical_market` (order-independent, sorted pair).

```
canonical(USDC, SUIX) == canonical(SUIX, USDC)
direct(USDC, SUIX)  != direct(SUIX, USDC)
```

The bluechip pool `RoutePool<SUIX, USDC>` already exists with reserves 1:1, giving `quoted_route_score = 1`. We need `earned >= 500` but can only claim once.

By creating `RoutePool<USDC, SUIX>` (reversed order) with `base=1, quote=1_000_000`:
- Duplicate check passes (different `direct_market`)
- Vault accepts it (same `canonical_market`)
- Score = `1_000_000 / 1 = 1_000_000`, capped by vault balance = 1000
- `earned = 1000 >= 500` → qualified → `setup::solve` flips `solved`

## Steps

1. **Publish witness package** — `arb::strategy::LatencyArb` with `has drop` + `witness()` constructor
2. **Register strategy** — `register_route_strategy<USDC, SUIX, LatencyArb>` stores canonical market in registry
3. **Create shadow pool** — `create_route_pool<USDC, SUIX>(1, 1_000_000)` with skewed reserves
4. **Open position + add liquidity** — `add_liquidity(500)` meets `min_effective_liquidity = 500`
5. **Claim incentives** — `claim_route_incentives` pays `min(score, vault.balance) = 1000`
6. **Solve** — `setup::solve(setup, account, config)` asserts `is_qualified` and flips `solved`

## Usage

```sh
npm install

export RPC_URL=http://<host>:<port>/<uuid>
export PRIVKEY=suiprivkey1q...
export PACKAGE_ID=0x...
export SETUP_ID=0x...
export REGISTRY=0x...
export VAULT=0x...
export ACCOUNT=0x...
export ORACLE=0x...
export CONFIG=0x...

# Run
node solve.mjs
```

## Requirements

- Node.js 18+
- `@mysten/sui` SDK (`npm install`)
- Sui CLI (for building the witness package bytecode at runtime)

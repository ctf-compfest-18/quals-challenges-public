# BlockJail Solver

## Agent gate

The agent runtime is at most 36 bytes, uses the restricted opcode set, contains one `DELEGATECALL`, and embeds a deployed implementation address no larger than `uint144`.

## CREATE2 address

`Solve.s.sol` predicts the `Solver` CREATE address and scans salts locally. For each salt it computes:

```text
keccak256(0xff || solver || salt || keccak256(ExploitImpl.initCode))[12:]
```

A result with its high 16 bits clear fits `uint144`. The script scans at most 1,000,000 salts before broadcasting.

## Proxy runtime

The agent runtime is:

```text
CALLDATASIZE PUSH0 PUSH0 CALLDATACOPY
PUSH0 PUSH0 CALLDATASIZE PUSH0 PUSH18 <implementation> GAS DELEGATECALL
RETURNDATASIZE PUSH0 PUSH0 RETURNDATACOPY
RETURNDATASIZE PUSH0 RETURN
```

Its encoded length is exactly 36 bytes.

## Palace JOP

`payload.py` decodes the deployed Palace runtime. It recovers the shuffled internal-function-pointer table and identifies the generic `SSTORE`, mapping-clear, balance-drain, and stop gadget bodies.

The five-byte card is:

```text
[slot 0, value 1, clear index, drain index, stop index]
```

The fixed entry calls the `SSTORE` gadget. Each gadget writes the next table index to `phantomIdx`; the dispatcher invokes it through `function()[]` until stop.

## Solve flow

The delegated implementation calls `enter()`, `openPath()`, `stealHeart()`, and `infiltrate(card)`. The Foundry script requires `Setup.isSolved()`. `solve.py` checks it again before requesting the flag.

## Run

Requirements: Python 3.10+ and Foundry.

```sh
python3 solve.py http://HOST:PORT
```

The launcher instance is killed after the flag is returned. Use `--no-kill` only while debugging.

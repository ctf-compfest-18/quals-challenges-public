#!/usr/bin/env python3
import argparse
import sys
from dataclasses import dataclass


@dataclass(frozen=True)
class Instruction:
    pc: int
    opcode: int
    operand: bytes


def decode(code: bytes) -> list[Instruction]:
    instructions = []
    pc = 0
    while pc < len(code):
        opcode = code[pc]
        size = opcode - 0x5F if 0x60 <= opcode <= 0x7F else 0
        end = pc + 1 + size
        if end > len(code):
            raise ValueError(f"truncated PUSH at 0x{pc:x}")
        instructions.append(Instruction(pc, opcode, code[pc + 1:end]))
        pc = end
    return instructions


def _table(instructions: list[Instruction]) -> dict[int, int]:
    entries = {}
    for i in range(len(instructions) - 4):
        push, dup, index, dup2, load = instructions[i:i + 5]
        if push.opcode != 0x61 or dup.opcode != 0x81 or dup2.opcode != 0x81 or load.opcode != 0x51:
            continue
        if index.opcode == 0x5F:
            table_index = 0
        elif index.opcode == 0x60:
            table_index = int.from_bytes(index.operand)
        else:
            continue
        entries[table_index] = int.from_bytes(push.operand)
    if set(entries) != {0, 1, 2, 3}:
        raise ValueError("function table missing or ambiguous")
    return entries


def _body(code: bytes, destination: int, stops: set[int]) -> bytes:
    end = min((pc for pc in stops if pc > destination), default=len(code))
    return code[destination:end]


def _classify(body: bytes) -> str:
    if b"\x60\x08\x54" in body and b"\xf1" in body:
        return "drain"
    if b"\x60\x04\x54" in body and b"\x60\x05\x54" in body and b"\x60\x06\x54" in body:
        return "store"
    if b"\x60\x07\x54" in body and b"\x20" in body and b"\x55" in body:
        return "clear"
    if b"\x60\x04\x60\x03" in body and b"\x55" in body:
        return "stop"
    raise ValueError("unknown gadget body")


def build_card(code: bytes) -> bytes:
    instructions = decode(code)
    table = _table(instructions)
    destinations = set(table.values())
    semantics = {}
    for index, destination in table.items():
        name = _classify(_body(code, destination, destinations))
        if name in semantics:
            raise ValueError(f"ambiguous {name} gadget")
        semantics[name] = index
    if set(semantics) != {"store", "clear", "drain", "stop"}:
        raise ValueError("incomplete gadget set")
    if semantics["store"] != 2:
        raise ValueError("unexpected fixed entry gadget")
    return bytes((0, 1, semantics["clear"], semantics["drain"], semantics["stop"]))


def main() -> int:
    parser = argparse.ArgumentParser(description="recover the BlockJail Palace JOP card")
    parser.add_argument("runtime", help="0x-prefixed Palace runtime bytecode")
    args = parser.parse_args()
    try:
        code = bytes.fromhex(args.runtime.removeprefix("0x"))
        print(f"0x{build_card(code).hex()}")
    except (ValueError, TypeError) as error:
        print(f"payload: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

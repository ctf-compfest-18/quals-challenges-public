#!/usr/bin/env python3
import argparse
import base64
import json
import os
import struct
import subprocess
import sys
from http.cookiejar import CookieJar
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import HTTPCookieProcessor, Request, build_opener

ROOT = Path(__file__).resolve().parent
MOD = 2**1279 - 1
EXP = 2**1277


def request_json(opener, base_url: str, path: str, data: dict | None = None) -> dict:
    body = None if data is None else json.dumps(data).encode()
    request = Request(base_url + path, data=body, method="GET" if data is None else "POST")
    request.add_header("Accept", "application/json")
    if body is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with opener.open(request, timeout=300) as response:
            value = json.load(response)
    except HTTPError as error:
        detail = error.read().decode(errors="replace")
        raise RuntimeError(f"{path}: HTTP {error.code}: {detail}") from error
    if not isinstance(value, dict):
        raise RuntimeError(f"{path}: expected JSON object")
    return value


def solve_pow(value: str) -> str:
    version, encoded_difficulty, encoded_x = value.split(".", 2)
    if version != "s":
        raise ValueError("unsupported proof-of-work version")
    difficulty = struct.unpack(">I", base64.b64decode(encoded_difficulty))[0]
    x = int.from_bytes(base64.b64decode(encoded_x), "big")
    x = pow(x, EXP, MOD)
    for _ in range(difficulty):
        x = pow(x ^ 1, 2, MOD)
    raw = x.to_bytes(max(1, (x.bit_length() + 7) // 8), "big")
    return f"s.{base64.b64encode(raw).decode()}"


def flatten_launch(data: dict) -> dict[str, str]:
    result = {}
    for key, entry in data.items():
        if key.isdigit() and isinstance(entry, dict):
            for name, value in entry.items():
                if isinstance(value, str):
                    result[name] = value
    required = {"RPC_URL", "PRIVKEY", "SETUP_CONTRACT_ADDR"}
    missing = required - result.keys()
    if missing:
        raise RuntimeError(f"launch response missing {', '.join(sorted(missing))}")
    return result


def run_checked(args: list[str], cwd: Path = ROOT, env: dict[str, str] | None = None) -> str:
    result = subprocess.run(args, cwd=cwd, env=env, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"{' '.join(args[:3])}: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="solve a BlockJail launcher instance")
    parser.add_argument("base_url", metavar="BASE_URL")
    parser.add_argument("--no-kill", action="store_true", help="leave the launched instance running")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    opener = build_opener(HTTPCookieProcessor(CookieJar()))
    launched = False
    try:
        challenge = request_json(opener, base_url, "/challenge").get("challenge")
        if not isinstance(challenge, str):
            raise RuntimeError("challenge response missing challenge")
        request_json(opener, base_url, "/solution", {"solution": solve_pow(challenge)})
        values = flatten_launch(request_json(opener, base_url, "/launch", {}))
        launched = True

        rpc = values["RPC_URL"].replace("{ORIGIN}", base_url)
        setup = values["SETUP_CONTRACT_ADDR"]
        palace = run_checked(["cast", "call", setup, "PALACE()(address)", "--rpc-url", rpc])
        runtime = run_checked(["cast", "code", palace, "--rpc-url", rpc])
        card = run_checked([sys.executable, str(ROOT / "payload.py"), runtime])
        if len(card) != 12 or not card.startswith("0x"):
            raise RuntimeError("payload builder returned an invalid five-byte card")
        bytes.fromhex(card[2:])

        private_key = values["PRIVKEY"]
        environment = {
            **os.environ,
            "PRIVATE_KEY": private_key if private_key.startswith("0x") else "0x" + private_key,
        }
        run_checked(
            [
                "forge", "script", "src/Solve.s.sol:Solve", "--root", str(ROOT),
                "--sig", "run(address,bytes)", setup, card,
                "--broadcast", "--rpc-url", rpc,
            ],
            env=environment,
        )
        solved = run_checked(["cast", "call", setup, "isSolved()(bool)", "--rpc-url", rpc])
        if solved.lower() != "true":
            raise RuntimeError(f"independent solve check returned {solved!r}")
        flag = request_json(opener, base_url, "/flag").get("flag")
        if not isinstance(flag, str) or not flag:
            raise RuntimeError("flag response missing flag")
        print(flag)
        return 0
    finally:
        if launched and not args.no_kill:
            try:
                request_json(opener, base_url, "/kill", {})
            except Exception as error:
                print(f"kill: {error}", file=sys.stderr)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"solve: {error}", file=sys.stderr)
        raise SystemExit(1)

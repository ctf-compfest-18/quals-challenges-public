#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import hmac
import json
import zipfile
from pathlib import Path

import chall

HERE = Path(__file__).resolve().parent
REPO = HERE.parent


def seal(payload: bytes, key: list[int], nonce: bytes) -> dict:
    root = chall._root(key)
    enc_key = hashlib.sha256(chall.D + b"/enc/" + root).digest()
    mac_key = hashlib.sha256(chall.D + b"/mac/" + root).digest()

    stream = bytearray()
    counter = 0
    while len(stream) < len(payload):
        stream.extend(
            hmac.new(
                enc_key,
                nonce + counter.to_bytes(8, "little"),
                hashlib.sha256,
            ).digest()
        )
        counter += 1

    ciphertext = bytes(a ^ b for a, b in zip(payload, stream))
    tag = hmac.new(
        mac_key,
        chall.D + nonce + ciphertext,
        hashlib.sha256,
    ).digest()[:16]

    return {
        "n": nonce.hex(),
        "c": ciphertext.hex(),
        "t": tag.hex(),
    }


def main() -> None:
    secret = json.loads((HERE / "secret.json").read_text())

    key = [int(value, 16) for value in secret["key"]]
    payload = bytes.fromhex(secret["flag_value_hex"])
    nonce = bytes.fromhex(secret["seal_nonce"])

    sealed = seal(payload, key, nonce)
    (HERE / "sealed.json").write_text(
        json.dumps(sealed, separators=(",", ":")) + "\n"
    )

    output = REPO / "public" / "astergate.zip"
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.write(HERE / "chall.py", "chall.py")
        archive.write(HERE / "records.bin", "records.bin")
        archive.write(HERE / "records.json", "records.json")
        archive.write(HERE / "sealed.json", "sealed.json")

    print(output)


if __name__ == "__main__":
    main()

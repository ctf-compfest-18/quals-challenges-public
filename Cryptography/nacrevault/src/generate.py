#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import hashlib
import itertools
import json
import zipfile

import numpy as np

HERE = Path(__file__).resolve().parent
REPO = HERE.parent

N = 192
SHELL_SIZE = 8
SHELLS = 24
P = 17
Q = 4093
ERROR_BOUND = 35
MASTER_SEED = 2026072701
DOMAIN = b"NACREVAULT/V3"


def inv_mod(value: int, modulus: int) -> int:
    return pow(int(value) % modulus, -1, modulus)


def random_invertible(rng: np.random.Generator, size: int, modulus: int) -> np.ndarray:
    matrix = np.eye(size, dtype=np.int64)
    for _ in range(size * 20):
        operation = int(rng.integers(0, 3))
        first, second = rng.choice(size, 2, replace=False)
        if operation == 0:
            matrix[[first, second]] = matrix[[second, first]]
        elif operation == 1:
            coefficient = int(rng.integers(1, modulus))
            matrix[first] = (matrix[first] + coefficient * matrix[second]) % modulus
        else:
            coefficient = int(rng.integers(1, modulus))
            matrix[first] = coefficient * matrix[first] % modulus
    return matrix


def ring_vector(seed: bytes) -> np.ndarray:
    raw = hashlib.shake_256(DOMAIN + b"/ring/" + seed).digest(N * 4)
    return np.array(
        [int.from_bytes(raw[4*i:4*i+4], "little") % Q for i in range(N)],
        dtype=np.int64,
    )


def negacyclic_matrix(vector: np.ndarray) -> np.ndarray:
    matrix = np.zeros((N, N), dtype=np.int64)
    for row in range(N):
        for column in range(N):
            difference = row - column
            value = int(vector[difference % N])
            if difference < 0:
                value = (-value) % Q
            matrix[row, column] = value
    return matrix


def pack_secret(secret: np.ndarray) -> bytes:
    return bytes(int(value) + 1 for value in secret)


def build() -> tuple[dict, dict]:
    rng = np.random.default_rng(MASTER_SEED)
    rank = 2 * SHELLS

    hidden = np.zeros((rank, N), dtype=np.int64)
    secret_original = np.zeros(N, dtype=np.int64)
    original_shells: list[list[int]] = []

    for shell in range(SHELLS):
        columns = list(range(shell * SHELL_SIZE, (shell + 1) * SHELL_SIZE))
        original_shells.append(columns)

        # Eight different projective directions in a private 2-dimensional shell.
        base = np.array([[1] * SHELL_SIZE, list(range(SHELL_SIZE))], dtype=np.int64) % P
        local_transform = random_invertible(rng, 2, P)
        local = local_transform @ base % P
        local = local * rng.integers(1, P, size=SHELL_SIZE, dtype=np.int64) % P
        hidden[2*shell:2*shell+2, columns] = local

        chosen = rng.choice(columns, 4, replace=False)
        secret_original[chosen[:2]] = 1
        secret_original[chosen[2:]] = -1

    row_mask = random_invertible(rng, rank, P)
    permutation = rng.permutation(N)
    shell_matrix = row_mask @ hidden[:, permutation] % P
    secret = secret_original[permutation]
    shell_target = shell_matrix @ secret % P

    ring_seed = rng.bytes(32)
    ring = ring_vector(ring_seed)
    ring_matrix = negacyclic_matrix(ring)
    error = rng.integers(-ERROR_BOUND, ERROR_BOUND + 1, size=N, dtype=np.int64)
    ring_target = (ring_matrix @ secret + error) % Q

    salt = rng.bytes(16)
    digest = hashlib.sha256(DOMAIN + b"/flag/" + salt + pack_secret(secret)).hexdigest()
    flag = f"COMPFEST18{{{digest}}}"

    public = {
        "v": 3,
        "n": N,
        "p": P,
        "q": Q,
        "eta": ERROR_BOUND,
        "seed": ring_seed.hex(),
        "C": shell_matrix.tolist(),
        "t": shell_target.tolist(),
        "b": ring_target.tolist(),
        "salt": salt.hex(),
    }

    private = {
        "master_seed": MASTER_SEED,
        "secret": secret.tolist(),
        "error": error.tolist(),
        "hidden_shells_public_indices": [
            sorted(int(np.where(permutation == original)[0][0]) for original in shell)
            for shell in original_shells
        ],
        "flag": flag,
    }
    return public, private


def public_chall() -> str:
    return r"""from hashlib import sha256, shake_256
from json import loads
from pathlib import Path

ROOT = Path(__file__).resolve().parent
D = b"NACREVAULT/V3"

J = loads((ROOT / "transcript.json").read_text())
N, P, Q, E = J["n"], J["p"], J["q"], J["e"]
M, U = J["M"], J["u"]

R = bytes.fromhex((ROOT / "sample.hex").read_text())
SEED = R[:32]
SALT = R[32:48]
B = [int.from_bytes(R[i:i + 2], "little") for i in range(48, len(R), 2)]


def ring():
    raw = shake_256(D + b"/ring/" + SEED).digest(4 * N)
    return [
        int.from_bytes(raw[4 * i:4 * i + 4], "little") % Q
        for i in range(N)
    ]


def product(a, s):
    out = [0] * N
    for i in range(N):
        total = 0
        for j in range(N):
            k = i - j
            value = a[k % N]
            total += value * s[j] if k >= 0 else -value * s[j]
        out[i] = total % Q
    return out


def centered(x):
    x %= Q
    return x - Q if x > Q // 2 else x


def verify(s):
    if len(s) != N or any(x not in (-1, 0, 1) for x in s):
        return False

    for row, target in zip(M, U):
        if sum(x * y for x, y in zip(row, s)) % P != target:
            return False

    z = product(ring(), s)
    return max(abs(centered(y - x)) for x, y in zip(z, B)) <= E


def submit(s):
    if not verify(s):
        raise ValueError("invalid")
    raw = bytes(x + 1 for x in s)
    h = sha256(D + b"/flag/" + SALT + raw).hexdigest()
    return f"COMPFEST18{{{h}}}"
"""


def main() -> None:
    public, private = build()

    transcript = {
        "v": 31,
        "n": public["n"],
        "p": public["p"],
        "q": public["q"],
        "e": public["eta"],
        "M": public["C"],
        "u": public["t"],
    }

    raw = bytearray.fromhex(public["seed"] + public["salt"])
    for value in public["b"]:
        raw += int(value).to_bytes(2, "little")

    transcript_path = HERE / "transcript.json"
    sample_path = HERE / "sample.hex"
    chall_path = HERE / "chall.py"

    transcript_path.write_text(
        json.dumps(transcript, separators=(",", ":")) + "\n"
    )
    encoded = raw.hex()
    sample_path.write_text(
        "\n".join(
            encoded[index:index + 96]
            for index in range(0, len(encoded), 96)
        ) + "\n"
    )
    chall_path.write_text(public_chall())
    (HERE / "secret.json").write_text(json.dumps(private, indent=2) + "\n")
    (HERE / "flag.txt").write_text(private["flag"] + "\n")

    archive_path = REPO / "public" / "nacrevault.zip"
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.write(chall_path, "chall.py")
        archive.write(transcript_path, "transcript.json")
        archive.write(sample_path, "sample.hex")

    print(archive_path)


if __name__ == "__main__":
    main()

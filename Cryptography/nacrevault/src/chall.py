from hashlib import sha256, shake_256
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

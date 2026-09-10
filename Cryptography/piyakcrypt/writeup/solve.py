#!/usr/bin/env python3
from pwn import *
import random
import re
import sys

P_CURVE = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
Gx = 0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798
Gy = 0x483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8

TAG_BITS = 40
PART_BITS = 128
SECRET_BITS = 256 - TAG_BITS
PART_UNKNOWN_BITS = 256 - PART_BITS
UNIT_COUNT = 5
SIGN_LIMIT = 4
TABLE_SIZE = 78
TABLE_CALLS = 8
MASK32 = (1 << 32) - 1
MASK64 = (1 << 64) - 1

PROMPT = b"menu> "
BANNER_MARKER = b"===================================================="


try:
    from fpylll import IntegerMatrix, LLL
except ImportError:
    IntegerMatrix = None
    LLL = None


def inv_mod(x, m):
    return pow(x, -1, m)


def ec_add(P1, P2):
    if P1 is None:
        return P2
    if P2 is None:
        return P1
    x1, y1 = P1
    x2, y2 = P2
    if x1 == x2 and (y1 + y2) % P_CURVE == 0:
        return None
    if P1 == P2:
        lam = (3 * x1 * x1) * inv_mod(2 * y1 % P_CURVE, P_CURVE) % P_CURVE
    else:
        lam = (y2 - y1) * inv_mod((x2 - x1) % P_CURVE, P_CURVE) % P_CURVE
    x3 = (lam * lam - x1 - x2) % P_CURVE
    y3 = (lam * (x1 - x3) - y1) % P_CURVE
    return (x3, y3)


def ec_mul(k, pt):
    if k % N == 0 or pt is None:
        return None
    k %= N
    R = None
    Q = pt
    while k:
        if k & 1:
            R = ec_add(R, Q)
        Q = ec_add(Q, Q)
        k >>= 1
    return R


def key_matches_pub(d, pub):
    return ec_mul(d, (Gx, Gy)) == pub


def rol32(x, r):
    r &= 31
    return ((x << r) | (x >> (32 - r))) & MASK32


def ror32(x, r):
    r &= 31
    return ((x >> r) | (x << (32 - r))) & MASK32


def rol64(x, r):
    r &= 63
    return ((x << r) | (x >> (64 - r))) & MASK64


def recover_panel_value(y, pos):
    bump = (0x9E3779B9 ^ (pos * 0x85EBCA6B)) & MASK32
    salt = (0xA5A5A5A5 + pos * 0x6D2B79F5) & MASK32
    z = (y - bump) & MASK32
    return ror32(z, pos * 7 + 3) ^ salt


def fold_piece(x, pos, lane):
    x ^= ((pos + 1) * 0xD6E8FEB86659FD93 + lane * 0xA0761D6478BD642F) & MASK64
    x = rol64(x, 17 + pos * 9 + lane * 23)
    x = (x * 0x9E6C63D0676A9A99 + 0xD1B54A32D192ED03) & MASK64
    return x


def make_piece(a, b, pos):
    return (fold_piece(a, pos, 0) << 64) | fold_piece(b, pos, 1)


def untemper(y):
    y &= 0xffffffff

    x = y
    for _ in range(5):
        x = y ^ (x >> 18)
    y = x & 0xffffffff

    x = y
    for _ in range(5):
        x = y ^ ((x << 15) & 0xEFC60000)
    y = x & 0xffffffff

    x = y
    for _ in range(5):
        x = y ^ ((x << 7) & 0x9D2C5680)
    y = x & 0xffffffff

    x = y
    for _ in range(5):
        x = y ^ (x >> 11)
    return x & 0xffffffff


def mt_state_from_outputs(outputs):
    assert len(outputs) == 624
    return (3, tuple(untemper(v) for v in outputs) + (624,), None)


def round_fraction(fr):
    from fractions import Fraction

    if fr >= 0:
        return int(fr + Fraction(1, 2))
    return int(fr - Fraction(1, 2))


def lll_reduce_exact(rows, delta_num=3, delta_den=4):
    from fractions import Fraction

    basis = [list(map(int, row)) for row in rows]
    n = len(basis)
    dim = len(basis[0])
    delta = Fraction(delta_num, delta_den)

    def gram_schmidt():
        ortho = [[Fraction(0) for _ in range(dim)] for _ in range(n)]
        mu = [[Fraction(0) for _ in range(n)] for _ in range(n)]
        norm = [Fraction(0) for _ in range(n)]
        for i in range(n):
            ortho[i] = [Fraction(x) for x in basis[i]]
            for j in range(i):
                if norm[j] == 0:
                    continue
                mu[i][j] = sum(Fraction(basis[i][c]) * ortho[j][c]
                               for c in range(dim)) / norm[j]
                for c in range(dim):
                    ortho[i][c] -= mu[i][j] * ortho[j][c]
            norm[i] = sum(x * x for x in ortho[i])
        return mu, norm

    mu, norm = gram_schmidt()
    k = 1
    while k < n:
        for j in range(k - 1, -1, -1):
            q = round_fraction(mu[k][j])
            if q:
                for c in range(dim):
                    basis[k][c] -= q * basis[j][c]
                mu, norm = gram_schmidt()

        if norm[k] >= (delta - mu[k][k - 1] * mu[k][k - 1]) * norm[k - 1]:
            k += 1
        else:
            basis[k], basis[k - 1] = basis[k - 1], basis[k]
            mu, norm = gram_schmidt()
            k = max(k - 1, 1)

    return basis


def solve_hnp(t_list, u_list, order, secret_bound, part_bound):
    m = len(t_list)
    dim = m + 2
    scale_part = order // part_bound
    scale_secret = order // secret_bound

    M = [[0] * dim for _ in range(dim)]

    for i in range(m):
        M[i][i] = order * scale_part

    for i in range(m):
        M[m][i] = t_list[i] * scale_part
    M[m][m] = scale_secret

    for i in range(m):
        M[m + 1][i] = u_list[i] * scale_part
    M[m + 1][m + 1] = order

    log.info(f"Lattice dimension: {dim}x{dim}")
    if LLL is not None:
        log.info("Running LLL using fpylll...")
        mat = IntegerMatrix.from_matrix(M)
        LLL.reduction(mat)
        reduced = [[int(mat[i, j]) for j in range(dim)] for i in range(dim)]
    else:
        log.info("fpylll not installed; using exact Python LLL fallback...")
        reduced = lll_reduce_exact(M)
    log.success("LLL complete.")

    candidates = []
    for row in reduced:
        last = row[-1]
        if abs(last) != order:
            continue
        sign = -1 if last == order else 1
        secret_scaled = sign * row[m]
        if secret_scaled % scale_secret != 0:
            continue
        secret_piece = (secret_scaled // scale_secret) % order
        if secret_piece < secret_bound:
            candidates.append(secret_piece)

    if not candidates:
        log.info("Strict marker match failed, trying broader extraction...")
        for row in reduced:
            last = row[-1]
            if last == 0 or last % order != 0:
                continue
            mult = last // order
            if mult == 0:
                continue
            for sign in (1, -1):
                secret_scaled = sign * row[m]
                denom = abs(mult) * scale_secret
                if secret_scaled % denom != 0:
                    continue
                secret_piece = (secret_scaled // denom) % order
                if secret_piece < secret_bound:
                    candidates.append(secret_piece)

    return list(set(candidates))


def choose(io, option):
    io.recvuntil(PROMPT)
    io.sendline(str(option).encode())


io = process([sys.executable, "chall.py"])

choose(io, 1)
out = io.recvuntil(BANNER_MARKER, drop=False).decode()
pub_keys = []
lines = out.splitlines()
for i, line in enumerate(lines):
    if "X = 0x" in line:
        qx = int(line.split("= ")[1].strip(), 16)
        qy = int(lines[i + 1].split("= ")[1].strip(), 16)
        pub_keys.append((qx, qy))
log.info(f"Collected {len(pub_keys)} public keys")

choose(io, 2)
out = io.recvuntil(BANNER_MARKER, drop=False).decode()
record = int(
    [line for line in out.splitlines() if "record = 0x" in line][0]
    .split("= ")[1].strip(),
    16,
)
known_piece = record >> SECRET_BITS
log.info(f"record prefix = 0x{known_piece:010x}")

panel_vals = []
log.info(f"Collecting panel outputs ({TABLE_CALLS} x {TABLE_SIZE})...")
for call_i in range(TABLE_CALLS):
    choose(io, 5)
    out = io.recvuntil(BANNER_MARKER, drop=False).decode()
    for line in out.splitlines():
        m = re.search(r"entry_\d+\s*=\s*0x([0-9a-fA-F]+)", line)
        if m:
            pos = len(panel_vals)
            panel_vals.append(recover_panel_value(int(m.group(1), 16), pos))
    log.info(f"  Batch {call_i}: {len(panel_vals)} total values")

assert len(panel_vals) == 624, f"Expected 624, got {len(panel_vals)}"
log.success("Collected 624 panel values")

rng = random.Random()
rng.setstate(mt_state_from_outputs(panel_vals))
log.success("MT state recovered")

all_sigs = {}
for unit_idx in range(UNIT_COUNT):
    sigs = []
    for sig_idx in range(SIGN_LIMIT):
        sig_pos = unit_idx * SIGN_LIMIT + sig_idx
        predicted_part = make_piece(
            rng.getrandbits(64),
            rng.getrandbits(64),
            sig_pos,
        )

        choose(io, 3)
        io.recvuntil(b"Choose unit")
        io.sendline(str(unit_idx).encode())
        io.recvuntil(b"Message")
        io.sendline(f"msg_{unit_idx}_{sig_idx}".encode())
        out = io.recvuntil(BANNER_MARKER, drop=False).decode()

        data = out.splitlines()
        z = int([line for line in data if line.strip().startswith("z =")][0].split("=")[1].strip())
        r = int([line for line in data if line.strip().startswith("r =")][0].split("=")[1].strip())
        s = int([line for line in data if line.strip().startswith("s =")][0].split("=")[1].strip())
        sigs.append((z, r, s, predicted_part))
        log.info(f"Unit {unit_idx}, sig {sig_idx}: predicted part = 0x{predicted_part:032x}")
    all_sigs[unit_idx] = sigs

log.success(f"Collected {UNIT_COUNT * SIGN_LIMIT} signatures")
log.info("Solving lattices...")

found = False
for unit_idx in range(UNIT_COUNT):
    t_list = []
    u_list = []
    for z, r, s, known_part in all_sigs[unit_idx]:
        s_inv = pow(s, -1, N)
        t = (s_inv * r) % N
        a = (s_inv * z) % N
        u = (known_part * (1 << PART_UNKNOWN_BITS)
             - a
             - t * known_piece * (1 << SECRET_BITS)) % N
        t_list.append(t)
        u_list.append(u)

    log.info(f"Trying unit {unit_idx}...")
    candidates = solve_hnp(
        t_list,
        u_list,
        N,
        1 << SECRET_BITS,
        1 << PART_UNKNOWN_BITS,
    )
    log.info(f"Got {len(candidates)} candidate(s)")

    for secret_piece in candidates:
        secret = ((known_piece << SECRET_BITS) + secret_piece) % N
        log.info(f"Checking candidate 0x{secret:064x}...")
        if not key_matches_pub(secret, pub_keys[unit_idx]):
            continue

        log.success(f"Recovered code for unit {unit_idx}")
        choose(io, 6)
        io.recvuntil(b"Code")
        io.sendline(str(secret).encode())
        resp = io.recvall(timeout=3)
        log.success("FLAG FOUND!")
        print(resp.decode(errors="replace"))
        found = True
        break

    if found:
        break

if not found:
    log.failure("Attack did not succeed.")

io.close()

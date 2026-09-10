#!/usr/bin/env python3
"""
author_oracle.py -- IT'S ME, BURHAN! answer key / build verification.

    AUTHOR-ONLY. NEVER ship this to players -- it computes the admin password.

Usage:
    python3 author_oracle.py <BURHAN_SEED> [BURHAN_FLAG]

Prints the full derivation trace for <BURHAN_SEED> (matches WRITEUP.md Appendix C)
and the admin password. If a flag is supplied, also computes what admin menu 13
prints -- F(flag) as hex -- and verifies the F flag-cipher inverts back to it.

Mirrors src/seal/SeedRng.java + src/seal/InstanceConfig.java (7-transform algorithm).
Password depends ONLY on the seed; the flag is optional and only checks the F path.
"""
import sys, hashlib

def sha256(b): return hashlib.sha256(b).digest()

def be_long(d):
    v = 0
    for i in range(8):
        v = (v << 8) | (d[i] & 0xff)
    return v & 0x7FFFFFFFFFFFFFFF          # Long.MAX_VALUE mask

def be4(v):
    v &= 0xFFFFFFFF
    return bytes([(v >> 24) & 0xff, (v >> 16) & 0xff, (v >> 8) & 0xff, v & 0xff])

def hex_to_bytes(h): return bytes(int(h[i*2:i*2+2], 16) for i in range(len(h)//2))
def chain(h, nxt): return sha256(h + nxt)

class SeedRng:
    def __init__(self, seed): self.seed = seed.encode()
    def _digest(self, label): return sha256(self.seed + (":" + label).encode())
    def digest_long(self, label): return be_long(self._digest(label))
    def bounded(self, label, lo, hi): return lo + (self.digest_long(label) % (hi - lo + 1))
    def hex_token(self, label, nbytes):
        h = self._digest(label)
        return "".join("%02x" % (h[i] & 0xff) for i in range(nbytes))

def kth_permutation(n, k, r):
    elems = list(range(k)); length = k; out = []
    for i in range(r):
        f = 1
        for j in range(r - i - 1):
            f *= (k - i - 1 - j)
        idx = n // f; n %= f
        out.append(elems[idx])
        for j in range(idx, length - 1):
            elems[j] = elems[j + 1]
        length -= 1
    return out

def five_bit_groups(digest, n):
    out = []; buf = 0; bits = 0
    for b in digest:
        buf = ((buf << 8) | (b & 0xff)) & 0xFFFFFFFF; bits += 8
        while bits >= 5 and len(out) < n:
            out.append((buf >> (bits - 5)) & 0x1f); bits -= 5
    return out

def alphabet(rot):
    base = "".join(chr(c) for c in range(ord('A'), ord('Z') + 1)) \
         + "".join(chr(c) for c in range(ord('2'), ord('7') + 1))
    r = rot % 32
    return base[r:] + base[:r]

# ---- F: byte transforms (mod 256) -- applyTransform ; and inverses ----
def F(w, b):
    n = len(b); o = [0] * n
    if   w == 0: o = list(b)
    elif w == 1:
        for i in range(n): o[i] = b[n - 1 - i]
    elif w == 2:
        for i in range(n): o[i] = (~b[i]) & 0xFF
    elif w == 3:
        for i in range(n):
            x = b[i] & 0xFF; o[i] = ((x << 1) | (x >> 7)) & 0xFF
    elif w == 4:
        for i in range(n): o[i] = (b[i] + i) & 0xFF
    elif w == 5:
        for i in range(n): o[i] = ((b[i] & 0xFF) * 3) & 0xFF
    elif w == 6:
        a = 0
        for i in range(n):
            a = (a + (b[i] & 0xFF)) & 0xFF; o[i] = a
    return bytes(o)

def F_inv(w, b):
    n = len(b); o = [0] * n
    if   w == 0: o = list(b)
    elif w == 1:
        for i in range(n): o[i] = b[n - 1 - i]
    elif w == 2:
        for i in range(n): o[i] = (~b[i]) & 0xFF
    elif w == 3:
        for i in range(n):
            x = b[i] & 0xFF; o[i] = ((x >> 1) | (x << 7)) & 0xFF          # ror1
    elif w == 4:
        for i in range(n): o[i] = (b[i] - i) & 0xFF
    elif w == 5:
        for i in range(n): o[i] = ((b[i] & 0xFF) * 171) & 0xFF           # 3*171 == 1 (mod 256)
    elif w == 6:
        prev = 0
        for i in range(n):
            cur = b[i] & 0xFF; o[i] = (cur - prev) & 0xFF; prev = cur
    return bytes(o)

# ---- G: 5-bit symbol transforms (mod 32) -- applyTransform32 ; and inverses ----
def G(w, v):
    n = len(v); o = [0] * n
    if   w == 0: o = v[:]
    elif w == 1:
        for i in range(n): o[i] = v[n - 1 - i]
    elif w == 2:
        for i in range(n): o[i] = v[i] ^ 31
    elif w == 3:
        for i in range(n): o[i] = ((v[i] << 1) | (v[i] >> 4)) & 31
    elif w == 4:
        for i in range(n): o[i] = (v[i] + i) % 32
    elif w == 5:
        for i in range(n): o[i] = (v[i] * 3) % 32
    elif w == 6:
        a = 0
        for i in range(n):
            a = (a + v[i]) % 32; o[i] = a
    return o

def G_inv(w, v):
    n = len(v); o = [0] * n
    if   w == 0: o = v[:]
    elif w == 1:
        for i in range(n): o[i] = v[n - 1 - i]
    elif w == 2:
        for i in range(n): o[i] = v[i] ^ 31
    elif w == 3:
        for i in range(n): o[i] = ((v[i] >> 1) | (v[i] << 4)) & 31       # ror1
    elif w == 4:
        for i in range(n): o[i] = (v[i] - i) % 32
    elif w == 5:
        for i in range(n): o[i] = (v[i] * 11) % 32                       # 3*11 == 1 (mod 32)
    elif w == 6:
        prev = 0
        for i in range(n):
            o[i] = (v[i] - prev) % 32; prev = v[i]
    return o

QUEST_COUNT      = 18
TRANSFORM_COUNT  = 7
CHAIN_LEN        = 7
PATH_SPACE       = 18 * 17 * 16     # 4896  = P(18,3)
PERM_SPACE       = 5040             # 7!
ITEM_SPACE       = 362880           # 9!

NAMES = {0: "identity", 1: "revArray", 2: "complement", 3: "rotBits1",
         4: "addPos", 5: "mul3", 6: "cumsum"}

def solve(seed, flag=None):
    r = SeedRng(seed)
    coins = r.bounded("coins", 1000, 9999)
    level = r.bounded("level", 8, 16)
    mcoin = [r.bounded("mcoin" + str(i), 120, 980) for i in range(QUEST_COUNT)]

    path_index  = be_long(sha256(be4(level) + be4(coins))) % PATH_SPACE
    quest_order = kth_permutation(path_index, QUEST_COUNT, 3)
    qs = ["Q" + str(quest_order[i] + 1) for i in range(3)]
    path1, path2, path3 = qs[0], qs[0] + ">" + qs[1], qs[0] + ">" + qs[1] + ">" + qs[2]
    coins_final = coins + sum(mcoin[i] for i in quest_order)

    battle  = lambda p: r.bounded("battle:" + p, 10000, 99999)
    archive = lambda p: r.hex_token("archive:" + p, 6)
    export  = lambda p: r.hex_token("export:" + p, 6)

    t3, t4 = battle(path1), archive(path1)
    t5, t6 = battle(path2), export(path2)
    t7, t8 = battle(path3), archive(path3)
    tokens = [level, coins, t3, t4, t5, t6, t7, t8, coins_final]

    items = [be4(level), be4(coins), be4(t3), hex_to_bytes(t4), be4(t5),
             hex_to_bytes(t6), be4(t7), hex_to_bytes(t8), be4(coins_final)]

    perm_index = be_long(sha256(b"".join(items))) % PERM_SPACE
    perm       = kth_permutation(perm_index, TRANSFORM_COUNT, CHAIN_LEN)
    rot        = perm_index % 32

    item_index = be_long(sha256(be4(coins) + be4(level))) % ITEM_SPACE
    item_order = kth_permutation(item_index, 9, 9)

    ordered = [items[k] for k in item_order]
    h = sha256(ordered[0])
    for i in range(1, len(ordered)):
        h = chain(h, ordered[i])
    target = five_bit_groups(h, 16)

    idx = target[:]
    for w in reversed(perm):
        idx = G_inv(w, idx)
    alpha = alphabet(rot)
    password = "".join(alpha[i] for i in idx)

    chk = idx[:]                                   # self-check: forward G reproduces target
    for w in perm:
        chk = G(w, chk)
    assert chk == target, "G round-trip failed -- transform/inverse mismatch"

    out = dict(coins=coins, level=level, path_index=path_index, path3=path3,
               tokens=tokens, item_index=item_index, item_order=item_order,
               perm_index=perm_index, rot=rot, alpha=alpha, perm=perm,
               target=target, password=password)

    if flag is not None:
        enc = flag.encode("utf-8")                 # what menu 13 prints, as hex
        for w in perm:
            enc = F(w, enc)
        dec = enc
        for w in reversed(perm):
            dec = F_inv(w, dec)
        assert dec.decode("utf-8") == flag, "F round-trip failed"
        out["menu13_hex"] = enc.hex()
    return out

def main():
    if len(sys.argv) < 2:
        print("usage: python3 author_oracle.py <BURHAN_SEED> [BURHAN_FLAG]", file=sys.stderr)
        sys.exit(2)
    seed = sys.argv[1]
    flag = sys.argv[2] if len(sys.argv) > 2 else None
    r = solve(seed, flag)
    print(f"seed            : {seed}")
    print(f"level0 / coins0 : {r['level']} / {r['coins']}")
    print(f"path_index      : {r['path_index']} -> {r['path3']}")
    print(f"tokens t1..t9   : {', '.join(str(t) for t in r['tokens'])}")
    print(f"item_index      : {r['item_index']} -> item_order {r['item_order']}")
    print(f"perm_index      : {r['perm_index']}")
    print(f"b32 rotation    : {r['rot']} -> {r['alpha']}")
    print(f"permutation     : {r['perm']}")
    print(f"                = {', '.join(NAMES[w] for w in r['perm'])}")
    print(f"target          : {r['target']}")
    print(f"admin password  : {r['password']}")
    if flag is not None:
        print(f"menu13 F(flag)  : {r['menu13_hex']}")
        print(f"                  (F round-trip OK: F^-1(menu13 hex) == flag)")

if __name__ == "__main__":
    main()
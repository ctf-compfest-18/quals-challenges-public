import subprocess, struct, pathlib, sys, shutil, os

HERE=pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import core as R

M64=(1<<64)-1; M32=(1<<32)-1
SEED=0x46662DE2AE713EE0; MULA=0xD1B54A32D192ED03; ROTA=17; XORA=0x6E7A5380F8318187
def rol64(x,r): x&=M64; return ((x<<r)|(x>>(64-r)))&M64
def ka(): return rol64((SEED*MULA)&M64, ROTA) ^ XORA
def lcg64(seed, mul, inc, n):
    r=seed&M64; o=[]
    for _ in range(n): r=(r*mul+inc)&M64; o.append((r>>56)&0xFF)
    return o
def lcg32(seed, mul, inc, n):
    r=seed&M32; o=[]
    for _ in range(n): r=(r*mul+inc)&M32; o.append((r>>24)&0xFF)
    return o

KA=ka()
KB=0x5F3A19C7
KC=0xB5297A4D2C1F60E9
REQ=bytes.fromhex("9c41e07db2f5361a8ad30c47e961b5f2")
NASM=shutil.which("nasm") or "C:/Strawberry/c/bin/nasm.exe"
TCC=os.environ.get("TCC") or str(HERE.parents[1]/".toolchain"/"tcc"/"tcc.exe")

raw_path=HERE/"chall_raw.bin"
subprocess.run([NASM,"-f","bin","chall.asm","-o",raw_path.name],check=True,cwd=HERE)
raw=bytearray(raw_path.read_bytes())
b_s,b_e,c_s,c_e,d_s,d_e=struct.unpack("<6I", raw[-24:])
body=bytearray(raw[:-24])

ksA=lcg64(KA, 0x9E6C63C6A3C4B1D1, 0x2545F4914F6CDD1D, b_e-b_s)
for i,off in enumerate(range(b_s,b_e)): body[off]^=ksA[i]
ksB=lcg32(KB, 0x2C9277B5, 0xAC564B05, c_e-c_s)
for i,off in enumerate(range(c_s,c_e)): body[off]^=ksB[i]
ksC=lcg64(KC, 0x2545F4914F6CDD1D, 0x9E6C63C6A3C4B1D1, d_e-d_s)
for i,off in enumerate(range(d_s,d_e)): body[off]^=ksC[i]
(HERE/"chall.bin").write_bytes(body)

EXP=R.transform(REQ)
carr=lambda b: ",".join("0x%02X"%x for x in b)
rows=["    "+", ".join("0x%02X"%x for x in body[i:i+12]) for i in range(0,len(body),12)]
(HERE/"sc.h").write_text(
    "static unsigned char sc[%d] = {\n%s\n};\n"%(len(body),",\n".join(rows))
)
(HERE/"selftest.h").write_text(
    "#define REQ_BYTES %s\n#define EXP_BYTES %s\n"%(carr(REQ),carr(EXP))
)
subprocess.run([TCC,"-s","main.c","-o","chall.exe"],check=True,cwd=HERE)
dist=HERE.parent/"dist"
dist.mkdir(exist_ok=True)
shutil.copy(HERE/"chall.exe",dist/"chall.exe")
shutil.copy(HERE/"core.py",HERE.parent/"core.py")
raw_path.unlink(missing_ok=True)
print("REQ", REQ.hex(), "EXP", EXP.hex())
print("B[0x%X,0x%X) C[0x%X,0x%X) D[0x%X,0x%X) exe %d"%(b_s,b_e,c_s,c_e,d_s,d_e,(HERE/"chall.exe").stat().st_size))

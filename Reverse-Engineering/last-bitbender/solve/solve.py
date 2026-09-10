import sys, socket

M64=(1<<64)-1; M32=(1<<32)-1
VSALT=0xA6F1C0D93B5E2748
MULW=0xFF51AFD7ED558CCD
def rol64(x,r): x&=M64; return ((x<<r)|(x>>(64-r)))&M64

def stageB(s0, s1):
    a=s0&M32; b=(s0>>32)&M32; c=s1&M32; d=(s1>>32)&M32
    p=a*c; plo=p&M32; phi=(p>>32)&M32
    t=a+plo; na=t&M32; cy=t>>32
    nb=(b+phi+cy)&M32
    nc=((c<<13)|(d>>19))&M32
    nd=((d<<13)|(c>>19))&M32
    na^=nc; nb^=nd
    return (((nb<<32)|na)&M64, ((nd<<32)|nc)&M64)

def transform(req):
    r0=int.from_bytes(req[0:8],"little"); r1=int.from_bytes(req[8:16],"little")
    s0=(r0 ^ VSALT)&M64; s1=r1 & M64
    s0,s1=stageB(s0,s1)
    s1=(s1 + s0)&M64; s1=rol64(s1,29); s1=(s1*MULW)&M64
    s0=(s0 + s1)&M64; s0=rol64(s0,17)
    a0=(s0 ^ s1)&M64; a1=(s0 + s1)&M64
    return a0.to_bytes(8,"little")+a1.to_bytes(8,"little")

def play(host, port):
    s=socket.create_connection((host,int(port)),timeout=5)
    f=s.makefile("rw")
    req=None
    while True:
        line=f.readline()
        if not line: break
        sys.stdout.write(line)
        if line.startswith("request:"):
            req=bytes.fromhex(line.split()[1])
        if line.startswith("response:"):
            f.write(transform(req).hex()+"\n"); f.flush()

if __name__=="__main__":
    if len(sys.argv)>=4 and sys.argv[1]=="--nc":
        play(sys.argv[2], sys.argv[3])
    elif len(sys.argv)==2:
        print(transform(bytes.fromhex(sys.argv[1])).hex())
    else:
        print("usage: solve.py <16-byte-hex> | solve.py --nc HOST PORT")

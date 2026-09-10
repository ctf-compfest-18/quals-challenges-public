import sys, os, socket, secrets
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import core as R

FLAG = os.environ["FLAG"]

def handle(readline, write):
    req = secrets.token_bytes(16)
    write("request: %s\n" % req.hex())
    write("response:\n")
    line = readline()
    if not line:
        return
    try:
        resp = bytes.fromhex(line.strip())
    except ValueError:
        write("malformed\n"); return
    if resp == R.transform(req):
        write("ok\n" + FLAG + "\n")
    else:
        write("rejected\n")

def stdio():
    handle(sys.stdin.readline, lambda s: (sys.stdout.write(s), sys.stdout.flush()))

def listen(port):
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", port)); srv.listen(8)
    while True:
        c, _ = srv.accept()
        f = c.makefile("rw")
        try: handle(f.readline, lambda s: (f.write(s), f.flush()))
        except Exception: pass
        finally:
            try: f.flush()
            except Exception: pass
            try: c.shutdown(socket.SHUT_RDWR)
            except Exception: pass
            c.close()

if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--listen":
        listen(int(sys.argv[2]))
    else:
        stdio()

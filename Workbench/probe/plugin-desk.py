#!/usr/bin/env python3
"""
Runs a hybrid app's plugin on the desk and calls it as its page would (Docs/pdk.md, "Hybrid
apps"): the PDK runtime from AndroidLuna/local-assets/pdk under qemu-arm-static, standing in
for the shell on the plugin's socket. It prints what the plugin says - the handlers it
registers, its replies and its PDL_CallJS calls - and leaves what it writes in the webOS root
given, which stands in for Lunacy's.

  plugin-desk.py [--strace] <webOS root> <app folder> <binary in it> [call ...]

A call is the handler's name and its arguments separated by "|" ("Handler|open|/media/internal/
a.pdf"); "sleep|<seconds>" waits instead. The root needs what the plugin reads: /etc/mtab with
a /media/internal line, the documents, a bin/ with sh and the commands the plugin runs (links
to the desk's own do), and usr/lib with the TouchPad libraries it links (ICU for Quick Office).
"""
import os, socket, struct, subprocess, sys, threading, time

HERE = os.path.dirname(os.path.abspath(__file__))
LIB = os.path.join(HERE, "..", "..", "AndroidLuna", "local-assets", "pdk", "lib")

def main():
    args = sys.argv[1:]
    strace = args[:1] == ["--strace"]
    if strace: args = args[1:]
    if len(args) < 3: sys.exit(__doc__)
    root, appdir, exe = (os.path.abspath(a) for a in args[:3])
    exe = os.path.join(appdir, args[2])
    calls = [c.split("|") for c in args[3:]]
    lib = os.path.abspath(LIB)
    name = "lunacy-desk-%d" % os.getpid()
    srv = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    srv.bind("\0" + name); srv.listen(5)
    env = {"LUNACY_PDK_PLUGIN": "1", "LUNACY_PDK_SOCKET": name, "LUNACY_PDK_ROOT": root, "LUNACY_PDK_EXE": exe,
           "LUNACY_PDK_DATA_DIR": "/media/internal/appdata/desk", "LUNACY_PDK_FB": os.path.join(root, "tmp", "fb"),
           "SDL_VIDEODRIVER": "lunacy", "SDL_AUDIODRIVER": "lunacy", "HOME": appdir,
           "LD_LIBRARY_PATH": lib + ":" + os.path.join(root, "usr", "lib"), "LD_PRELOAD": os.path.join(lib, "liblunacy-preload.so")}
    os.makedirs(os.path.join(root, "tmp"), exist_ok=True)
    os.makedirs(os.path.join(root, "media", "internal", "appdata", "desk"), exist_ok=True)
    cmd = ["qemu-arm-static"] + (["-strace"] if strace else []) + ["-L", os.path.dirname(lib)]
    for k, v in env.items(): cmd += ["-E", "%s=%s" % (k, v)]
    p = subprocess.Popen(cmd + [exe], cwd=appdir)

    def read(c, n):
        b = b""
        while len(b) < n:
            x = c.recv(n - len(b))
            if not x: raise EOFError
            b += x
        return b

    link = {}; ready = threading.Event(); replies = {}
    def serve(c):
        kind = read(c, 1)
        if kind == b"J": link["sock"] = c
        try:
            while True:
                t, a, n = struct.unpack("<III", read(c, 12)); body = read(c, n)
                if t == 30: print("ready", [x.decode() for x in body.split(b"\0")[:-1]], flush=True); ready.set()
                elif t == 32: print("reply", a, ["reply", "exception", "none"][body[0]], body[1:].decode(errors="replace")[:400], flush=True); replies[a] = body
                elif t == 33: print("calls JS", [x.decode(errors="replace")[:300] for x in body.split(b"\0")[:-1]], flush=True)
        except EOFError:
            print("closed", kind.decode(), flush=True)
    def accept():
        while True:
            c, _ = srv.accept(); threading.Thread(target=serve, args=(c,), daemon=True).start()
    threading.Thread(target=accept, daemon=True).start()
    if not ready.wait(60): sys.exit("the plugin didn't register its handlers")
    n = 0
    for call in calls:
        if call[0] == "sleep": time.sleep(float(call[1])); continue
        n += 1
        body = b"".join(x.encode() + b"\0" for x in call)
        link["sock"].sendall(struct.pack("<III", 31, n, len(body)) + body)
        for _ in range(600):
            if n in replies: break
            time.sleep(0.05)
    time.sleep(2)
    p.kill()

if __name__ == "__main__":
    main()

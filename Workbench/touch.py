#!/usr/bin/env python3
# touch.py "x,y" "hold:ms" "x,y" ... : one finger through a path on the tablet's touchscreen,
# for what `adb shell input` can't do - press, hold, move, hold, let go (a launcher drag onto an
# icon, a hold in a group's panel). Written for the HP 10 G2's mtk-tpd (/dev/input/event3,
# protocol A), through sendevent in one adb shell; coordinates are the panel's, portrait.
# Moves are interpolated in STEP-px steps (10 by default). Each sendevent is its own process,
# so a stepped move is slower than a finger: STEP=1000 jumps straight to the next point.
import sys, subprocess
DEV = "/dev/input/event3"
cmds = []
def ev(t, c, v): cmds.append(f"sendevent {DEV} {t} {c} {v}")
def point(x, y, down=True):
    ev(3, 57, 0); ev(3, 53, int(x)); ev(3, 54, int(y)); ev(3, 48, 5)
    if down: ev(1, 330, 1)
    ev(0, 2, 0); ev(0, 0, 0)
pos = None
for a in sys.argv[1:]:
    if a.startswith("hold:"):
        cmds.append(f"sleep {int(a[5:])/1000:.3f}")
        continue
    x, y = map(float, a.split(","))
    if pos is None:
        point(x, y)
    else:
        n = max(1, int(max(abs(x - pos[0]), abs(y - pos[1])) / float(__import__("os").environ.get("STEP", "10"))))
        for i in range(1, n + 1):
            point(pos[0] + (x - pos[0]) * i / n, pos[1] + (y - pos[1]) * i / n, down=False)
            cmds.append("sleep 0.016")
    pos = (x, y)
ev(1, 330, 0); ev(0, 2, 0); ev(0, 0, 0)
subprocess.run(["adb", "shell", "; ".join(cmds)])

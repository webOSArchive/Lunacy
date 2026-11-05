#!/bin/bash
# How fast a PDK app gets going, from Lunacy's log: Lunacy restarted and left 45 s to settle,
# then the app launched and watched for 20 s.
#   Workbench/pdk-timing.sh <adb device> <app id> [runs]
# Prints, per run: seconds from the launch to its GL stream starting, to its picture's card
# being sized (the first frame on the screen; "-" if it never was), and the frame rate the GL
# server logs every 600 frames ("-" if 600 never came: under 30 frames/s). For comparing two
# builds on one device; on the Galaxy Tab A7 Lite, 0.9.1 gave EMU7800 0.1 s / 0.1 s /
# 58.9 frames/s and Fruit Ninja 0.4 s / 1.9 s (Docs/pdk.md). Leaves the screen to time out.
set -u
D=$1; ID=$2; RUNS=${3:-1}
A="adb -s $D"
for run in $(seq "$RUNS"); do
  $A shell svc power stayon true </dev/null; $A shell input keyevent KEYCODE_WAKEUP </dev/null
  $A shell am force-stop org.webosarchive.lunacy </dev/null; sleep 1
  $A shell am start -n org.webosarchive.lunacy/.shell.ShellActivity </dev/null >/dev/null 2>&1; sleep 45
  $A logcat -c </dev/null
  $A shell am start -n org.webosarchive.lunacy/.shell.ShellActivity --es launch "$ID" </dev/null >/dev/null 2>&1; sleep 20
  $A logcat -d </dev/null | python3 -c '
import sys, re, datetime
lines = sys.stdin.read().splitlines()
def t(l): return datetime.datetime.strptime(l[6:18], "%H:%M:%S.%f")
def first(p): return next((l for l in lines if re.search(p, l)), None)
start = first(r"launch .*: window and card|pdk \[[^]]*\]: started")
since = lambda l: "%.2f s" % (t(l) - t(start)).total_seconds() if l and start else "-"
fps = re.findall(r"([0-9.]+) frames/s", "\n".join(lines))
print("%s run %s: stream %s, picture %s, %s" % (sys.argv[1], sys.argv[2], since(first(r"GLES [12] on ")),
      since(first(r"card \d+ x \d+, the app")), (fps[0] + " frames/s") if fps else "under 30 frames/s"))
' "$ID" "$run"
done
$A shell svc power stayon false </dev/null

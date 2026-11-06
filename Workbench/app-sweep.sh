#!/bin/bash
# Every web app on a list, each from a fresh Lunacy: did its card open, what its console said
# at the error level, whether anything crashed, and a screenshot after [seconds] (12 by default;
# slow tablets need 25). The PDK games have their own sweep (pdk-sweep.sh).
#   Workbench/app-sweep.sh <adb device> <list file, one app id a line> <out dir> [seconds]
# A list comes from the device's application manager over the SDK relay (Docs/dev-workflow.md).
D=$1; LIST=$2; OUT=$3; WAIT=${4:-12}; mkdir -p "$OUT"; rm -f "$OUT"/*
A="adb -s $D"
$A shell svc power stayon true
while read -r id; do
  [ -n "$id" ] || continue
  $A shell input keyevent KEYCODE_WAKEUP </dev/null; $A shell wm dismiss-keyguard </dev/null >/dev/null 2>&1
  # Android 5 has no dismiss-keyguard: a swipe lock goes with a swipe up (the HP 10 G2's).
  $A shell dumpsys window policy </dev/null | grep -q "mShowingLockscreen=true" && { $A shell input swipe 400 1150 400 300 300 </dev/null; sleep 1; }
  $A shell am force-stop org.webosarchive.lunacy </dev/null; $A logcat -c </dev/null
  $A shell am start -n org.webosarchive.lunacy/.shell.ShellActivity </dev/null >/dev/null 2>&1; sleep 10
  $A shell am start -n org.webosarchive.lunacy/.shell.ShellActivity --es launch "$id" </dev/null >/dev/null 2>&1; sleep "$WAIT"
  $A exec-out screencap -p </dev/null | perl -0777 -pe 's/^.*?(?=\x89PNG)//s' > "$OUT/$id.png"
  $A logcat -d </dev/null > "$OUT/$id.log"
  card=$(grep -c "launch $id: window and card" "$OUT/$id.log")
  errors=$(grep -cE "\[$id\] ERROR" "$OUT/$id.log")
  uncaught=$(grep -E "\[$id\] ERROR.*Uncaught" "$OUT/$id.log" | head -1 | sed 's/.*ERROR //' | cut -c1-120)
  crash=$(grep -E 'FATAL EXCEPTION|Fatal signal|AndroidRuntime: Process' "$OUT/$id.log" | head -1 | cut -c1-120)
  echo "$id card=$card errors=$errors${uncaught:+ UNCAUGHT: $uncaught}${crash:+ CRASH: $crash}"
done < "$LIST"
$A shell svc power stayon false

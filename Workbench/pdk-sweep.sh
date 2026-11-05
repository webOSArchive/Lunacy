#!/bin/bash
# Every PDK app on a device, each from a fresh Lunacy: its start in the log and a screenshot
# after [seconds] (16 by default; slow tablets need 30).
#   Workbench/pdk-sweep.sh <adb device> <list file, one app id a line> <out dir> [seconds]
# Lists come from the device over the SDK relay (Docs/dev-workflow.md); skip N.O.V.A. 2, Asphalt 5
# and Oregon Trail (never worked).
D=$1; LIST=$2; OUT=$3; WAIT=${4:-16}; mkdir -p "$OUT"; rm -f "$OUT"/*
A="adb -s $D"
$A shell svc power stayon true
while read -r id; do
  [ -n "$id" ] || continue
  $A shell input keyevent KEYCODE_WAKEUP </dev/null; $A shell wm dismiss-keyguard </dev/null
  $A shell am force-stop org.webosarchive.lunacy </dev/null; $A logcat -c </dev/null
  $A shell am start -n org.webosarchive.lunacy/.shell.ShellActivity </dev/null >/dev/null 2>&1; sleep 10
  $A shell am start -n org.webosarchive.lunacy/.shell.ShellActivity --es launch "$id" </dev/null >/dev/null 2>&1; sleep "${WAIT:-16}"
  $A exec-out screencap -p </dev/null | perl -0777 -pe 's/^.*?(?=\x89PNG)//s' > "$OUT/$id.png"
  $A logcat -d </dev/null > "$OUT/$id.log"
  started=$(grep -c "pdk \[$id\]: started" "$OUT/$id.log")
  exited=$(grep -E "pdk \[$id\].*(exited|exit code|died)" "$OUT/$id.log" | head -1 | cut -c1-120)
  crash=$(grep -E 'FATAL EXCEPTION|Fatal signal' "$OUT/$id.log" | head -1 | cut -c1-120)
  echo "$id started=$started ${exited:+EXIT: $exited} ${crash:+CRASH: $crash}"
done < "$LIST"
$A shell svc power stayon false

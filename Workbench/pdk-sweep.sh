#!/bin/bash
# PDK and hybrid apps, each from a fresh Lunacy: did it start, did anything crash, and a
# screenshot after [seconds] (16 by default; slow tablets need 30) to judge by eye.
#
#   Workbench/pdk-sweep.sh <adb device> scenarios <out dir> [seconds] [sample size]
#       the fixed scenarios (Workbench/pdk-scenarios.txt) and a random sample of the device's
#       other PDK apps (4 by default). The round's sweep (Docs/test-plan.md, 12).
#   Workbench/pdk-sweep.sh <adb device> <list file> <out dir> [seconds]
#       the apps on a list, one id a line (a full sweep after a runtime change, say).
#
# Prints [PASS] / [FAIL] / [INFO] per app, writes it to <out dir>/summary.txt, and exits
# non-zero when an app that should start didn't. The sample needs the SDK's novacomd relay
# (Docs/dev-workflow.md) to see the device's apps; without it only the scenarios run.
D=$1; MODE=$2; OUT=$3; WAIT=${4:-16}; SAMPLE=${5:-4}
[ -n "$OUT" ] || { sed -n '2,13s/^# \{0,1\}//p' "$0"; exit 2; }
cd "$(dirname "$0")/.." || exit 2
mkdir -p "$OUT"; rm -f "$OUT"/*
A="adb -s $D"; SCEN=Workbench/pdk-scenarios.txt
fail=0
out() { echo "$*"; echo "$*" >> "$OUT/summary.txt"; }

if [ "$MODE" = scenarios ]; then
  scen=$(grep -v '^#' "$SCEN" | grep -v '^skip' | grep '|' | cut -d'|' -f1 | tr -d ' ')
  skips=$(grep '^skip' "$SCEN" | cut -d'|' -f2 | tr -d ' ')
  sample=""
  # The device's PDK and hybrid apps, through the relay matched by Android model (as device-test.sh).
  MODEL=$($A shell getprop ro.product.model </dev/null | tr -d '\r'); NOVA=""
  for n in $(novacom -l 2>/dev/null | awk '$3=="adb" && $4=="lunacy" {print $2}'); do
    m=$(timeout 20 novacom -d "$n" run file:///usr/bin/luna-send -- -n 1 palm://org.webosarchive.lunacy/system/getEnvironment '{}' </dev/null 2>/dev/null \
        | python3 -c 'import json,sys
try: print(json.load(sys.stdin)["device"]["model"])
except Exception: pass')
    [ "$m" = "$MODEL" ] && { NOVA=$n; break; }
  done
  if [ -n "$NOVA" ]; then
    APPDIR=/media/cryptofs/apps/usr/palm/applications
    pool=""
    for id in $(timeout 30 novacom -d "$NOVA" run file:///bin/ls -- $APPDIR </dev/null 2>/dev/null); do
      echo "$scen $skips" | tr ' ' '\n' | grep -qx "$id" && continue
      info=$(timeout 20 novacom -d "$NOVA" run file:///bin/cat -- "$APPDIR/$id/appinfo.json" </dev/null 2>/dev/null | tr -d '\n\t ')
      # PDK apps only: a hybrid's plug-in starts with a document, so hybrids are scenarios.
      echo "$info" | grep -q '"type":"pdk"' && pool="$pool $id"
    done
    sample=$(echo $pool | tr ' ' '\n' | grep . | shuf -n "$SAMPLE" | tr '\n' ' ')
    out "[INFO] sample of $SAMPLE from $(echo $pool | wc -w) other PDK apps: $sample"
  else
    out "[INFO] no SDK relay for $MODEL: the scenarios only, no sample"
  fi
  LIST=$(printf '%s\n' $scen $sample)
else
  [ -f "$MODE" ] || { echo "no list file $MODE"; exit 2; }
  LIST=$(grep -v '^#' "$MODE")
fi

# A scenario's launch params, with $DOC the sweep's test document.
DOC=file:///media/internal/downloads/lunacy-sweep.txt
params_for() { grep -v '^#' "$SCEN" | awk -F'|' -v id="$1" '{gsub(/ /,"",$1)} $1==id && NF>=4 {sub(/^ */,"",$4); sub(/ *$/,"",$4); print $4}' | sed "s#\$DOC#$DOC#g"; }
$A push Workbench/pdk-sweep.txt /sdcard/Download/lunacy-sweep.txt </dev/null >/dev/null 2>&1
$A shell svc power stayon true </dev/null
for id in $LIST; do
  [ -n "$id" ] || continue
  $A shell input keyevent KEYCODE_WAKEUP </dev/null; $A shell wm dismiss-keyguard </dev/null >/dev/null 2>&1
  # Android 5 has no dismiss-keyguard: a swipe lock goes with a swipe up (the HP 10 G2's).
  $A shell dumpsys window policy </dev/null | grep -q "mShowingLockscreen=true" && { $A shell input swipe 400 1150 400 300 300 </dev/null; sleep 1; }
  # Installed? (asked over the relay when there is one; a scenario a device lacks is skipped)
  if [ -n "$NOVA" ] && ! timeout 20 novacom -d "$NOVA" run file:///bin/ls -- "/media/cryptofs/apps/usr/palm/applications/$id/appinfo.json" </dev/null >/dev/null 2>&1; then
    out "[SKIP] $id: not installed on this device"; continue
  fi
  $A shell am force-stop org.webosarchive.lunacy </dev/null; $A logcat -c </dev/null
  # Lunacy's own lines streamed to a file as they come: a device whose system logs heavily (the
  # HP 10 G2's audio and hwcomposer) turns its log buffer over within the wait, and a "started"
  # line read back afterwards was gone (2026-11-09).
  $A logcat -v brief Lunacy:V AndroidRuntime:V DEBUG:V '*:S' </dev/null > "$OUT/$id.log" 2>/dev/null &
  LOGPID=$!
  $A shell am start -n org.webosarchive.lunacy/.shell.ShellActivity </dev/null >/dev/null 2>&1; sleep 10
  P=$(params_for "$id")
  if [ -n "$P" ]; then $A shell "am start -n org.webosarchive.lunacy/.shell.ShellActivity --es launch $id --es params '$P'" </dev/null >/dev/null 2>&1
  else $A shell am start -n org.webosarchive.lunacy/.shell.ShellActivity --es launch "$id" </dev/null >/dev/null 2>&1; fi
  sleep "$WAIT"
  $A exec-out screencap -p </dev/null | perl -0777 -pe 's/^.*?(?=\x89PNG)//s' > "$OUT/$id.png"
  kill $LOGPID 2>/dev/null; wait $LOGPID 2>/dev/null
  # A PDK app logs "pdk [id]: started"; a hybrid app's plug-in "[id] plugin <exe> started".
  started=$(grep -cE "pdk \[$id\]: started|\[$id\] plugin .* started" "$OUT/$id.log")
  how=$(grep -E "pdk \[$id\]: started" "$OUT/$id.log" | head -1 | sed 's/.*: started //' | cut -c1-70)
  exited=$(grep -E "pdk \[$id\].*(exited|exit code|died)|\[$id\] plugin .* ended" "$OUT/$id.log" | head -1 | sed 's/^.*Lunacy *([ 0-9]*): //' | cut -c1-100)
  crash=$(grep -E 'FATAL EXCEPTION|Fatal signal' "$OUT/$id.log" | head -1 | cut -c1-120)
  if [ "$started" = 0 ]; then out "[FAIL] $id: never started${exited:+ ($exited)}${crash:+ CRASH: $crash}"; fail=$((fail+1))
  elif [ -n "$crash" ]; then out "[FAIL] $id: started, then $crash"; fail=$((fail+1))
  elif [ -n "$exited" ]; then out "[FAIL] $id: started, then within $WAIT s: $exited"; fail=$((fail+1))
  else out "[PASS] $id: started${how:+ ($how)}, still running after $WAIT s"; fi
done
# The games closed (they're noisy, codepoet): Lunacy started again, at its launcher.
$A shell am force-stop org.webosarchive.lunacy </dev/null
$A shell am start -n org.webosarchive.lunacy/.shell.ShellActivity </dev/null >/dev/null 2>&1
$A shell svc power stayon false </dev/null
$A shell rm -f /sdcard/Download/lunacy-sweep.txt </dev/null
out "===== $(grep -c '^\[PASS\]' "$OUT/summary.txt") started, $fail failed, $(grep -c '^\[SKIP\]' "$OUT/summary.txt") not installed; judge the screenshots in $OUT against pdk-scenarios.txt ====="
[ "$fail" = 0 ]

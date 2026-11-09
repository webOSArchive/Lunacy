#!/bin/bash
# Lunacy's automated device pass: everything on a device that a script can decide, for a build
# under test (Docs/test-plan.md, tier 2). Anything that needs eyes or hands is [Human] in the plan.
#
#   Workbench/device-test.sh <adb device> [expected versionCode] [results file]
#
# Prints [PASS] / [FAIL] / [SKIP] / [INFO] <section> <check> -- detail, and with a results file
# writes it there too (keep it with the release: Docs/test-plan.md). Run it with the candidate
# installed and First Use done. It restarts Lunacy twice and opens every bundled app in turn; it
# reads no mail, messages or contacts, only counts, ids and error codes.
#
# Bus calls go through the SDK's novacomd relay when it runs on this machine (webos-sdk-redux,
# Docs/dev-workflow.md), which works with a release build too; without it, through DevTools on a
# debug build. A release build is closed to DevTools, so the checks made inside a page (the mail
# accounts, broken images) run on debug builds only; the candidate's debug build carries the
# same code. Everything read from the log runs on both.
# The PDK games have their own sweep (Workbench/pdk-sweep.sh).
D=$1; EXPECT=$2; RESULTS=$3
[ -n "$D" ] || { echo "usage: $0 <adb device> [expected versionCode] [results file]"; exit 2; }
cd "$(dirname "$0")/.." || exit 2
A="adb -s $D"; PKG=org.webosarchive.lunacy
pass=0; fail=0; skip=0
out() { echo "$*"; [ -n "$RESULTS" ] && echo "$*" >> "$RESULTS"; }
P() { out "[PASS] $*"; pass=$((pass+1)); }
F() { out "[FAIL] $*"; fail=$((fail+1)); }
S() { out "[SKIP] $*"; skip=$((skip+1)); }
I() { out "[INFO] $*"; }
lunalog() { $A logcat -d -s Lunacy </dev/null; }
wait_log() { timeout "$2" sh -c "until $A logcat -d -s Lunacy </dev/null | grep -qE \"$1\"; do :; done"; }
# An expression's value in every page under a path (Workbench/cdp.sh evalall), the values only.
evalall() { ANDROID_SERIAL=$D Workbench/cdp.sh evalall "$1" "$2" 2>/dev/null | cut -f2; }
# One value from a page (the first under the path), its JSON string's quotes taken off.
js() { evalall "$2" "$1" | head -1 | python3 -c 'import json,sys
t=sys.stdin.read().strip()
try: v=json.loads(t)
except Exception: v=t
print(v if isinstance(v,str) else json.dumps(v))'; }
fresh() {   # Lunacy from cold, back when the activity manager runs and boot's apps have loaded
  $A shell am force-stop $PKG </dev/null; $A logcat -c </dev/null
  $A shell am start -n $PKG/.shell.ShellActivity </dev/null >/dev/null 2>&1
  wait_log "activities: running" 120 || return 1
  sleep 20
}
[ -n "$RESULTS" ] && { mkdir -p "$(dirname "$RESULTS")"; : > "$RESULTS"; }

$A get-state >/dev/null 2>&1 || { echo "no device $D"; exit 2; }
MODEL=$($A shell getprop ro.product.model </dev/null | tr -d '\r')
out "===== Lunacy device pass on $D — $MODEL (Android $($A shell getprop ro.build.version.release </dev/null | tr -d '\r')) — $(date) ====="

# ---------------------------------------------------------------- 1. identity
PK=$($A shell dumpsys package $PKG </dev/null)
vc=$(echo "$PK" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
vn=$(echo "$PK" | sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -1 | tr -d '\r')
abi=$(echo "$PK" | sed -n 's/.*primaryCpuAbi=\([^ ]*\).*/\1/p' | head -1 | tr -d '\r')
DEBUG=$(echo "$PK" | grep -q "pkgFlags=.*DEBUGGABLE" && echo 1)
if [ -z "$vc" ]; then F "1 id  Lunacy isn't installed"; exit 1; fi
if [ -z "$EXPECT" ] || [ "$vc" = "$EXPECT" ]; then P "1 id  build $vn ($vc), $([ -n "$DEBUG" ] && echo debug || echo release), $abi"
else F "1 id  build $vc installed, $EXPECT expected"; fi
[ -n "$DEBUG" ] || I "1 id  a release build: the in-page checks (3, 4's images) are skipped; run the pass on the debug build as well"

# ---------------------------------------------------------------- 2. start and the bus
$A shell input keyevent KEYCODE_WAKEUP </dev/null; $A shell wm dismiss-keyguard </dev/null >/dev/null 2>&1
$A shell svc power stayon true </dev/null
t0=$(date +%s)
if fresh; then P "2 start  shell up, activities running in $(( $(date +%s) - t0 - 20 )) s"
else F "2 start  no 'activities: running' within 120 s"; fi
L=$(lunalog)
natives=$(echo "$L" | sed -n 's/.*native services: \[\(.*\)\].*/\1/p' | head -1 | tr -d ' ' | tr ',' ' ')
for s in $natives; do
  if echo "$L" | grep -q "native service $s ready"; then P "2 start  native service $s ready"
  elif echo "$L" | grep -q "native service $s started"; then F "2 start  native service $s started but never ready"
  else I "2 start  native service $s not started (on demand)"; fi
done
echo "$L" | grep -qE "\[com.palm.app.email\] loaded .*index.html" && P "2 start  Email loaded headless at boot" \
  || F "2 start  Email's headless page never loaded"

# The relay's device for this adb device: Lunacy's nduid is made up per install, so match on
# the Android model Lunacy reports (the test devices are all different models).
NOVA=""
if command -v novacom >/dev/null; then
  for n in $(novacom -l 2>/dev/null | awk '$3=="adb" && $4=="lunacy" {print $2}'); do
    m=$(novacom -d "$n" run file:///usr/bin/luna-send -- -n 1 palm://org.webosarchive.lunacy/system/getEnvironment '{}' </dev/null 2>/dev/null \
        | python3 -c 'import json,sys
try: print(json.load(sys.stdin)["device"]["model"])
except Exception: pass')
    [ "$m" = "$MODEL" ] && { NOVA=$n; break; }
  done
fi
if [ -n "$NOVA" ]; then
  ls() { novacom -d "$NOVA" run file:///usr/bin/luna-send -- -n 1 "$1" "$2" </dev/null 2>/dev/null; }
  r=$(ls palm://com.palm.systemservice/time/getSystemTime '{}')
  echo "$r" | grep -q '"utc": *[1-9]' && P "2 bus  systemservice answers the time (SDK relay)" || F "2 bus  systemservice -- $r"
  napps=$(ls palm://com.palm.applicationManager/listApps '{}' | python3 -c 'import json,sys
try: print(len(json.load(sys.stdin)["apps"]))
except Exception: print(0)')
  [ "${napps:-0}" -ge 20 ] && P "2 bus  applicationManager lists $napps apps" || F "2 bus  applicationManager lists ${napps:-no} apps"
  r=$(ls palm://com.palm.nonexistent.service/x '{}')
  echo "$r" | grep -q '"returnValue": *false' && P "2 bus  an unknown service answers an error (rule 2)" || F "2 bus  unknown service -- $r"
  # luna-send is no app: db8 refuses it, as webOS's did an unprivileged caller.
  r=$(ls palm://com.palm.db/find '{"query":{"from":"com.palm.mail.account:1"}}')
  echo "$r" | grep -q '"returnValue": *false' && P "2 bus  db8 refuses a caller without rights" || F "2 bus  db8 answered luna-send -- $(echo "$r" | cut -c1-80)"
elif [ -n "$DEBUG" ]; then
  r=$(js /com.palm.app.email/index.html 'new Promise(function(res){var n=0,o={};function c(u,p,k,f){var b=new PalmServiceBridge();b.onservicecallback=function(t){try{o[k]=f(JSON.parse(t))}catch(e){o[k]="bad reply"}if(++n==3)res(JSON.stringify(o))};b.call(u,JSON.stringify(p));(window.__dt=window.__dt||[]).push(b)}
c("palm://com.palm.systemservice/time/getSystemTime",{},"time",function(r){return r.utc>0});
c("palm://com.palm.applicationManager/listApps",{},"apps",function(r){return (r.apps||[]).length});
c("palm://com.palm.nonexistent.service/x",{},"nosvc",function(r){return r.returnValue===false});
setTimeout(function(){res(JSON.stringify(o))},8000)})')
  echo "$r" | grep -q '"time": *true' && P "2 bus  systemservice answers the time (DevTools)" || F "2 bus  systemservice -- $r"
  napps=$(echo "$r" | sed -n 's/.*"apps": *\([0-9]*\).*/\1/p')
  [ "${napps:-0}" -ge 20 ] && P "2 bus  applicationManager lists $napps apps" || F "2 bus  applicationManager -- $r"
  echo "$r" | grep -q '"nosvc": *true' && P "2 bus  an unknown service answers an error (rule 2)" || F "2 bus  unknown service -- $r"
else S "2 bus  no SDK relay for $MODEL and a release build: start the redux novacomd"; fi

# ---------------------------------------------------------------- 3. mail accounts (ids and codes only)
# An account in error shows a warning in Email; an account the person removed may leave its
# mail record behind until the delete finishes, which is no fault of this build. Asked from
# Email's own page, which has the rights.
if [ -n "$DEBUG" ]; then
  r=$(js /com.palm.app.email/index.html 'new Promise(function(res){var b=new PalmServiceBridge(),m;b.onservicecallback=function(t){var r=JSON.parse(t);if(!m){m=r.results||[];b.call("palm://com.palm.db/find",JSON.stringify({query:{from:"com.palm.account:1"}}));return}var live={};(r.results||[]).forEach(function(a){if(!a._del&&!a.beingDeleted)live[a._id]=a.templateId});res(JSON.stringify(m.map(function(x){return [x.accountId,x._kind.replace(/^com.palm.|.account:1$/g,""),x.error&&x.error.errorCode||0,live[x.accountId]||""]})))};b.call("palm://com.palm.db/find",JSON.stringify({query:{from:"com.palm.mail.account:1"}}));window.__dt2=b;setTimeout(function(){res("timeout")},8000)})')
  while IFS=$'\t' read -r k rest; do case $k in P) P "$rest";; F) F "$rest";; I) I "$rest";; esac; done < <(echo "$r" | python3 -c '
import json,sys
try: rows=json.loads(sys.stdin.read())
except Exception: print("F\t3 mail  accounts unreadable"); sys.exit()
if not rows: print("I\t3 mail  no mail accounts")
for acct,kind,err,tpl in rows:
    if not tpl: print("I\t3 mail  %s (%s): left by a removed account%s" % (acct,kind," (error %s)"%err if err else ""))
    elif err: print("F\t3 mail  %s (%s, %s): error %s" % (acct,kind,tpl,err))
    else: print("P\t3 mail  %s (%s, %s): no error" % (acct,kind,tpl))')
else S "3 mail  account errors (release build)"; fi

# ---------------------------------------------------------------- 4. every bundled app opens a card
# A card's page logs a size as it loads ("loaded <url> viewport \"884x1076 ..."); a headless page
# logs 0x0. The shell logs "launch" for an app it starts and "relaunch" for one already running,
# which opens its own card. On a debug build the card is also searched for broken images: an img
# that finished loading with nothing to show, or a src of "undefined"/"null" (Messaging's chat
# avatar, 2026-11-09).
APPS=$(python3 - <<'EOF'
import json, os, re
for d in sorted(os.listdir("CoreApps")):
    p = os.path.join("CoreApps", d, "appinfo.json")
    if not os.path.exists(p): continue
    j = json.loads(re.sub(r",(\s*[}\]])", r"\1", open(p, encoding="utf-8-sig").read()))
    if j.get("visible", True) in (False, "false") or j.get("lunacyAndroidSettings") or j.get("type") != "web": continue
    print(j["id"] + ":" + d)      # an app's pages are under its folder, which isn't always its id
EOF
)
IMGJS='(function(){if(!innerWidth||!innerHeight)return "headless";var b=[],a=document.images;for(var i=0;i<a.length;i++){var e=a[i],s=e.getAttribute("src")||"";if(!e.offsetParent)continue;if(/^(undefined|null)$/.test(s)||(e.complete&&s&&e.naturalWidth===0))b.push(s.slice(-40))}return "card "+(b.length?b.slice(0,3).join(" "):"ok")})()'
for entry in $APPS; do
  id=${entry%%:*}; dir=${entry#*:}
  t0=$(date +%s)
  $A shell am start -n $PKG/.shell.ShellActivity --es launch "$id" </dev/null >/dev/null 2>&1
  wait_log "\\[$id\\] loaded .* viewport \\\"[1-9]" 25; secs=$(( $(date +%s) - t0 )); sleep 3
  L=$(lunalog)
  how=$(echo "$L" | grep -qE "relaunch $id " && echo "running app" || echo "started")
  carded=$(echo "$L" | grep -cE "\[$id\] loaded .* viewport \"[1-9]")
  unc=$(echo "$L" | grep -E "\[$id\] ERROR.*Uncaught" | head -1 | sed 's/.*ERROR //' | cut -c1-100)
  img=""
  [ -n "$DEBUG" ] && [ "$carded" != 0 ] && img=$(evalall "$IMGJS" "/$dir/" | tr -d '"' | grep "^card" | grep -v "^card ok$" | head -1)
  if [ "$carded" = 0 ]; then F "4 apps  $id: no card within 25 s ($how)"
  elif [ -n "$unc" ]; then F "4 apps  $id: card in $secs s, uncaught: $unc"
  elif [ -n "$img" ]; then F "4 apps  $id: card in $secs s, broken images: ${img#card }"
  else P "4 apps  $id: card in $secs s ($how), no uncaught errors${DEBUG:+, no broken images}"; fi
done

# ---------------------------------------------------------------- 5. regressions a script can see
# 22fa049: Email reloaded its accounts once per account as it loaded, from db8 waking watches on
# merges that changed nothing (seen as the screen flashing). Counted from Lunacy's start until
# Email's card is open: each wake is a fresh watched query of the mail accounts. Measured on the
# Fold3 with 7 accounts: 16 before the fix, 1 after. None means the load wasn't seen.
CRASHLOG() { $A logcat -d -s Lunacy -s AndroidRuntime -s DEBUG -s ActivityManager </dev/null; }
L1=$(CRASHLOG)    # sections 2 to 4, for section 6
fresh
$A shell am start -n $PKG/.shell.ShellActivity --es launch com.palm.app.email </dev/null >/dev/null 2>&1
wait_log "\\[com.palm.app.email\\] loaded .* viewport \\\"[1-9]" 25; sleep 10
n=$(lunalog | grep "bus \[com.palm.app.email\] com.palm.db/find" | grep -c '"from":"com.palm.mail.account:1"},"watch":true')
if [ "$n" = 0 ]; then F "5 regr  Email's account query not seen from start to card (inconclusive)"
elif [ "$n" -le 2 ]; then P "5 regr  Email queried its accounts $n time(s) from start to card (22fa049)"
else F "5 regr  Email reloaded its accounts $n times from start to card (22fa049: db8 waking watches for nothing)"; fi

# ---------------------------------------------------------------- 6. crashes
# Over both starts' logs.
L="$L1
$(CRASHLOG)"
c=$(echo "$L" | grep -cE "FATAL EXCEPTION|Fatal signal|ANR in $PKG")
[ "$c" = 0 ] && P "6 crash  no Java crash, native signal or ANR" || F "6 crash  $c: $(echo "$L" | grep -E "FATAL EXCEPTION|Fatal signal|ANR in $PKG" | head -2 | cut -c1-140 | tr '\n' ' ')"
c=$(echo "$L" | grep -cE "(native|js) service .* (exited|died|crashed)")
[ "$c" = 0 ] && P "6 crash  no service died" || F "6 crash  $c service exit(s): $(echo "$L" | grep -E "(native|js) service .* (exited|died|crashed)" | head -2 | cut -c1-140 | tr '\n' ' ')"

$A shell svc power stayon false </dev/null
out "===== $pass passed, $fail failed, $skip skipped ====="
[ "$fail" = 0 ]

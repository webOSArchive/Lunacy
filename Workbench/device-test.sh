#!/bin/bash
# Lunacy's automated device pass: everything on a device that a script can decide, for a build
# under test (Docs/test-plan.md, tier 2). Anything that needs eyes or hands is [Human] in the plan.
#
#   Workbench/device-test.sh <adb device> [expected versionCode] [results file]
#
# NORESTART=1 runs section 2 in the Lunacy that is running, without restarting it first
# (Workbench/fresh-install.sh, for the run First Use finished in).
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
  # NORESTART=1 (Workbench/fresh-install.sh): the first pass takes Lunacy as it is, in the run
  # First Use finished in. A restart is what hid that run's missing activities (2026-11-15).
  if [ -n "$NORESTART" ]; then NORESTART=; $A shell pidof $PKG </dev/null >/dev/null || return 1; return 0; fi
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
# The shell's list of native services, and any it is seen starting (the HP 10 G2 doesn't log
# the list on every start).
natives=$( { echo "$L" | sed -n 's/.*native services: \[\(.*\)\].*/\1/p' | head -1 | tr -d ' ' | tr ',' '\n'
             echo "$L" | sed -n 's/.*native service \([a-z.]*\) started.*/\1/p'; } | grep . | sort -u | tr '\n' ' ')
[ -n "$natives" ] || I "2 start  no native service started yet (the HP 10 G2 starts its mail service later)"
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
  # Wi-Fi and the ringer are the private bus's, as on the TouchPad (Workbench/probe, 2026-11-15):
  # luna-send -P is a public caller (the relay's plain luna-send is on the private bus, as on webOS).
  lsp() { novacom -d "$NOVA" run file:///usr/bin/luna-send -- -P -n 1 "$1" "$2" </dev/null 2>/dev/null; }
  r=$(lsp palm://com.palm.wifi/getstatus '{}')
  echo "$r" | grep -q 'Service does not exist: com.palm.wifi' && P "2 bus  com.palm.wifi is the private bus's" || F "2 bus  com.palm.wifi answered luna-send -P -- $(echo "$r" | cut -c1-80)"
  r=$(lsp palm://com.palm.audio/ringtone/status '{}')
  echo "$r" | grep -q 'Unknown method' && P "2 bus  the ringtone volume is the private bus's" || F "2 bus  ringtone/status answered luna-send -P -- $(echo "$r" | cut -c1-80)"
  r=$(lsp palm://com.palm.btmonitor/monitor/getradiostate '{}')
  echo "$r" | grep -q 'Service does not exist: com.palm.btmonitor' && P "2 bus  com.palm.btmonitor is the private bus's" || F "2 bus  com.palm.btmonitor answered luna-send -P -- $(echo "$r" | cut -c1-80)"
  # Every activity file that waits (a trigger or a schedule) is live once First Use is done: the
  # system's (/etc/palm/activities), its services' and the bundled apps'. The configurator holds
  # back all but firstUseSafe ones until First Use; a fresh install never got them until Lunacy
  # restarted, so contacts never reached the Contacts list (no linkerWatch; the Kyocera,
  # 2026-11-15). Read from this tree's files, so a new one is checked without listing it here.
  if [ -d AndroidLuna/local-assets/rootfs ]; then
    r=$(ls palm://com.palm.activitymanager/list '{}' | python3 -c '
import glob, json, re, sys
root = sys.argv[1]
try: live = {a.get("name") for a in json.load(sys.stdin)["activities"]}
except Exception: print("no list"); sys.exit()
files = glob.glob(root + "/AndroidLuna/local-assets/rootfs/etc/palm/activities/*/*") + \
    glob.glob(root + "/AndroidLuna/local-assets/rootfs/usr/palm/services/*/configuration/activities/*/*") + \
    glob.glob(root + "/CoreApps/*/configuration/activities/*/*")
want = set()
for f in files:
    try: a = json.loads(re.sub(r",(\s*[}\]])", r"\1", open(f).read())).get("activity", {})
    except Exception: continue
    if a.get("name") and (a.get("trigger") or a.get("schedule")): want.add(a["name"])
print("%d/%d %s" % (len(want - live), len(want), ", ".join(sorted(want - live))))' .)
    case "$r" in
      0/*) P "2 bus  every waiting activity file is live (${r#0/})";;
      *) F "2 bus  activity files not live after First Use: $r";;
    esac
  else S "2 bus  activity files: no local-assets in this tree"; fi
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
# A com.palm app reaches them: Night Moves' night and morning schedules, Video Player (2026-11-15).
# Sent, then read back a few seconds later: Chromium 37's DevTools (Android 5) hands back a
# Promise unresolved, as "{}" (the Nexus 7, 2026-11-15).
if [ -n "$DEBUG" ]; then
  js /com.palm.app.email/index.html '(function(){var o=window.__dtw={};window.__dtwb=[];function c(u,p,k,f){var b=new PalmServiceBridge();window.__dtwb.push(b);b.onservicecallback=function(t){try{o[k]=f(JSON.parse(t))}catch(e){o[k]="bad reply"}};b.call(u,p)}
c("palm://com.palm.wifi/getstatus","{}","wifi",function(r){return r.returnValue===true&&/^(serviceDisabled|serviceEnabled|connectionStateChanged)$/.test(r.status)});
c("palm://com.palm.audio/ringtone/status","{}","ring",function(r){return r.scenario==="ringtone_default"&&r.volume>=0});
c("palm://com.palm.btmonitor/monitor/getradiostate","{}","bt",function(r){return r.returnValue===true&&/^(on|off|turningon|turningoff)$/.test(r.radio)});
c("palm://com.palm.filenotifyd/state","{\"subscribe\":true}","fnd",function(r){return r.state==="idle"&&r.mounted["/media/internal"]===true});
return "sent"})()' >/dev/null
  sleep 5
  r=$(js /com.palm.app.email/index.html 'JSON.stringify(window.__dtw||{})')
  echo "$r" | grep -q '"wifi": *true' && P "2 bus  com.palm.wifi answers a com.palm app" || F "2 bus  com.palm.wifi -- $r"
  echo "$r" | grep -q '"ring": *true' && P "2 bus  the ringtone volume answers a com.palm app" || F "2 bus  ringtone/status -- $r"
  echo "$r" | grep -q '"bt": *true' && P "2 bus  com.palm.btmonitor answers a com.palm app" || F "2 bus  btmonitor -- $r"
  echo "$r" | grep -q '"fnd": *true' && P "2 bus  filenotifyd's state answers Video Player's watch" || F "2 bus  filenotifyd state -- $r"
fi

# The compat layer's engine fixes, in a page (debug builds): getElementsByTagName by local name
# in an XML document, as the TouchPad's WebKit (Workbench/probe/org.webosarchive.lunacy.xmlprobe;
# WebDAV Client listed nothing without it, 2026-11-15). The element is found by namespace: a
# prefixed name finds nothing on Chromium 37, as on the TouchPad.
if [ -n "$DEBUG" ]; then
  r=$(js /com.palm.app.email/index.html '(function(){var d=new DOMParser().parseFromString("<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>/a</d:href></d:response></d:multistatus>","application/xml");return d.getElementsByTagName("response").length+" "+d.getElementsByTagNameNS("DAV:","response")[0].getElementsByTagName("href").length+" "+document.createElement("div").getElementsByTagName("span").length})()')
  echo "$r" | grep -q '1 1 0' && P "2 page  XML getElementsByTagName matches local names (WebDAV replies)" || F "2 page  XML getElementsByTagName -- $r"
fi

# ---------------------------------------------------------------- 3. mail accounts (ids and codes only)
# An account in error shows a warning in Email; an account the person removed may leave its
# mail record behind until the delete finishes, which is no fault of this build. Asked from
# Email's own page, which has the rights.
if [ -n "$DEBUG" ]; then
  # Sent, then read back: Chromium 37's DevTools hands back a Promise unresolved (see section 2).
  js /com.palm.app.email/index.html '(function(){window.__dtm=null;var b=new PalmServiceBridge(),m;window.__dt2=b;b.onservicecallback=function(t){var r=JSON.parse(t);if(!m){m=r.results||[];b.call("palm://com.palm.db/find",JSON.stringify({query:{from:"com.palm.account:1"}}));return}var live={};(r.results||[]).forEach(function(a){if(!a._del&&!a.beingDeleted)live[a._id]=a.templateId});window.__dtm=JSON.stringify(m.map(function(x){return [x.accountId,x._kind.replace(/^com.palm.|.account:1$/g,""),x.error&&x.error.errorCode||0,live[x.accountId]||""]}))};b.call("palm://com.palm.db/find",JSON.stringify({query:{from:"com.palm.mail.account:1"}}));return "sent"})()' >/dev/null
  for i in 1 2 3 4 5 6 7 8; do sleep 1; r=$(js /com.palm.app.email/index.html 'window.__dtm||"waiting"'); [ "$r" != waiting ] && break; done
  while IFS=$'\t' read -r k rest; do case $k in P) P "$rest";; F) F "$rest";; I) I "$rest";; esac; done < <(echo "$r" | python3 -c '
import json,sys
try: rows=json.loads(sys.stdin.read())
except Exception: print("F\t3 mail  accounts unreadable"); sys.exit()
if not isinstance(rows,list): print("F\t3 mail  accounts unreadable: %s" % json.dumps(rows)[:60]); sys.exit()
if not rows: print("I\t3 mail  no mail accounts")
for acct,kind,err,tpl in rows:
    if not tpl: print("I\t3 mail  %s (%s): left by a removed account%s" % (acct,kind," (error %s)"%err if err else ""))
    elif err: print("F\t3 mail  %s (%s, %s): error %s" % (acct,kind,tpl,err))
    else: print("P\t3 mail  %s (%s, %s): no error" % (acct,kind,tpl))')
else S "3 mail  account errors (release build)"; fi

# Contacts in db8 reach the Contacts list: the list shows people (com.palm.person), which the
# contacts linker makes from contact records, an account's or the Android account's. A fresh
# install's linker never ran, so 455 Android contacts sat in db8 behind an empty list (the
# Kyocera, 2026-11-15). Asked from Email's page, which may read both.
if [ -n "$DEBUG" ]; then
  js /com.palm.app.email/index.html '(function(){var o=window.__dtp={};window.__dtpb=[];["com.palm.contact:1","org.webosarchive.contact.android:1","com.palm.person:1"].forEach(function(k){var b=new PalmServiceBridge();window.__dtpb.push(b);b.onservicecallback=function(t){var r=JSON.parse(t);o[k]=r.returnValue?r.count:-1};b.call("palm://com.palm.db/find",JSON.stringify({query:{from:k,limit:1},count:true}))});return "sent"})()' >/dev/null
  sleep 4
  r=$(js /com.palm.app.email/index.html 'JSON.stringify(window.__dtp||{})')
  read -r nc na np < <(echo "$r" | python3 -c 'import json,sys
try: o=json.loads(sys.stdin.read())
except Exception: o={}
print(o.get("com.palm.contact:1",-1), o.get("org.webosarchive.contact.android:1",-1), o.get("com.palm.person:1",-1))')
  if [ "${nc:--1}" -lt 0 ] || [ "${np:--1}" -lt 0 ]; then F "3 contacts  counts unreadable -- $r"
  elif [ "$nc" -eq 0 ] && [ "${na:-0}" -le 0 ]; then I "3 contacts  no contacts on this device"
  elif [ "$np" -gt 0 ]; then P "3 contacts  $np people from $nc contacts (${na} Android's): the linker runs"
  else F "3 contacts  $nc contacts (${na} Android's) and no people: the linker never ran"; fi
else S "3 contacts  people from contacts (release build)"; fi

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

# ---------------------------------------------------------------- 15. the shared core (1.1.0)
# What moved out of the Android shell into LunaCore for Luna DE, asked as an app asks it: each
# answered on main (seen passing on 1.0.2, build 652, on the Nexus 5 before the moves were
# trusted), so a failure here is a regression of the move. Sent, then read back (section 2).
if [ -n "$DEBUG" ]; then
  js /com.palm.app.email/index.html '(function(){var o=window.__dtv={};window.__dtvb=[];
function c(u,p,k,f){var b=new PalmServiceBridge();window.__dtvb.push(b);b.onservicecallback=function(t){try{o[k]=f(JSON.parse(t))}catch(e){o[k]="bad reply"}};b.call(u,JSON.stringify(p))}
c("palm://com.palm.preferences/systemProperties/Get",{key:"com.palm.properties.nduid"},"nduid",function(r){return /^[0-9a-f]{40}$/.test(r["com.palm.properties.nduid"])});
c("palm://com.palm.preferences/systemProperties/Get",{key:"com.palm.properties.deviceName"},"name",function(r){return r["com.palm.properties.deviceName"]});
c("palm://com.palm.preferences/systemProperties/Get",{key:"com.palm.properties.nope"},"nokey",function(r){return r.returnValue===false&&r.errorText});
["misc.file","image.file","image.album","video.file","audio.file"].forEach(function(k){
 c("palm://org.webosarchive.lunacy/media/find",{query:{from:"com.palm.media."+k+":1",limit:500}},k,function(r){return r.returnValue===true&&r.results?r.results.length:("error "+r.errorCode)})});
c("palm://org.webosarchive.lunacy/media/find",{query:{from:"com.palm.account:1"}},"relayOther",function(r){return r.returnValue===false?r.errorCode:"answered"});
c("palm://com.palm.db/find",{query:{from:"com.palm.media.image.file:1",limit:1}},"direct",function(r){return r.returnValue===false?r.errorCode:"answered"});
var b=new PalmServiceBridge();window.__dtvb.push(b);b.onservicecallback=function(t){var r=JSON.parse(t),p=r.results&&r.results[0]&&r.results[0].path;if(!p){o.thumb="no image";return}
 var i=new Image();i.onload=function(){o.thumb=i.naturalWidth+"x"+i.naturalHeight};i.onerror=function(){o.thumb="failed"};i.src="/var/luna/data/extractfs"+encodeURIComponent(p)+":0:0:64:64:3";
 var j=new Image();j.onload=function(){o.full=j.naturalWidth+"x"+j.naturalHeight};j.onerror=function(){o.full="failed"};j.src=encodeURI(p);
 var x=new XMLHttpRequest();x.open("GET","/var/luna/data/extractfs"+encodeURIComponent(p)+"::3:0:0:0:0:3:0:0:200:200:3");x.onload=function(){o.json=x.responseText};x.onerror=function(){o.json="failed"};x.send()};
b.call("palm://org.webosarchive.lunacy/media/find",JSON.stringify({query:{from:"com.palm.media.image.file:1",limit:1}}));
return "sent"})()' >/dev/null
  sleep 8
  r=$(js /com.palm.app.email/index.html 'JSON.stringify(window.__dtv||{})')
  while IFS=$'\t' read -r k rest; do case $k in P) P "$rest";; F) F "$rest";; I) I "$rest";; S) S "$rest";; esac; done < <(echo "$r" | python3 -c '
import json, re, sys
try: o = json.loads(sys.stdin.read())
except Exception: o = None
if not isinstance(o, dict) or not o: print("F\t15 core  the checks never answered"); sys.exit()
def line(ok, good, bad): print(("P\t15 core  " + good) if ok else ("F\t15 core  " + bad))
line(o.get("nduid") is True and isinstance(o.get("name"), str) and o["name"] and o.get("nokey") == "no such key",
     "systemProperties: an nduid of 40 hex digits, the device is \"%s\", an unknown key is \"no such key\"" % o.get("name"),
     "systemProperties -- nduid %s, deviceName %s, unknown key %s" % (o.get("nduid"), o.get("name"), o.get("nokey")))
kinds = ["misc.file", "image.file", "image.album", "video.file", "audio.file"]
bad = [k for k in kinds if not isinstance(o.get(k), int)]
line(not bad, "the media index answers the file picker: " + ", ".join("%s %s" % (o.get(k), k) for k in kinds),
     "the media index -- " + ", ".join("%s: %s" % (k, o.get(k)) for k in bad))
line(o.get("relayOther") == -3963 and o.get("direct") == -3963, "the picker relay reads the media kinds only, and db8 refuses Email the image kind",
     "media permissions -- another kind through the relay: %s, the image kind directly: %s" % (o.get("relayOther"), o.get("direct")))
if o.get("thumb") == "no image": print("S\t15 core  the thumbnailer: no image on this device")
else:
    def wh(v):
        m = re.match(r"^(\d+)x(\d+)$", str(v)); return (int(m.group(1)), int(m.group(2))) if m else None
    t, f = wh(o.get("thumb")), wh(o.get("full"))
    try: j = json.loads(o.get("json") or "")
    except Exception: j = {}
    # The short form fits the box and never grows; the long form (crop 3) fits it and may grow.
    def fits(size, box, grow):
        if not size or not f: return False
        s = min(box / f[0], box / f[1]);  s = s if grow else min(s, 1.0)
        return abs(size[0] - max(1, round(f[0] * s))) <= 1 and abs(size[1] - max(1, round(f[1] * s))) <= 1
    ok = fits(t, 64, False) and (j.get("original-width"), j.get("original-height")) == f and fits((j.get("output-width", 0), j.get("output-height", 0)), 200, True)
    line(ok, "the thumbnailer: a %sx%s picture fits 64x64 as %sx%s, and its long form says %sx%s for 200x200" % (f + t + (j.get("output-width"), j.get("output-height"))) if ok else "",
         "the thumbnailer -- the picture %s, in 64x64 %s, the long form %s" % (o.get("full"), o.get("thumb"), str(o.get("json"))[:90]))
')
  # Screen & Lock's own rows for Android show once the environment has answered: they start
  # hidden since 1.1.0, for Luna DE, which has none of them (its card is open from section 4).
  r=$(js /com.palm.app.screenlock/ '(function(){var t=document.body.innerText,m=[];["Screen Layout","Android Apps","Use on Android","Edge swipe for home","Change Wallpaper"].forEach(function(k){if(t.indexOf(k)<0)m.push(k)});return m.length?"missing: "+m.join(", "):"all"})()')
  [ "$r" = all ] && P "15 core  Screen & Lock shows its Android rows (the layout, Android's notifications and wallpaper, the edge swipe)" || F "15 core  Screen & Lock's rows -- ${r:-no card}"
else S "15 core  the shared core's answers (release build)"; fi

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

# 970b21c: an app launched by the intent that starts Lunacy came up with no card. Clock, every
# time on the Fold3: its boot clock.update activities relaunched it while it was starting on the
# person's launch, so it handled an alarm update. The launch now waits for the boot ("the boot
# settled"), and Clock's placeholder must not go without a card. Seen failing: 617 on the Fold3
# logged "launch com.palm.app.clock: no card opened; the placeholder goes", with no settle line.
L1="$L1
$(CRASHLOG)"
$A shell am force-stop $PKG </dev/null; $A logcat -c </dev/null
$A shell input keyevent KEYCODE_WAKEUP </dev/null; $A shell wm dismiss-keyguard </dev/null >/dev/null 2>&1
$A shell am start -n $PKG/.shell.ShellActivity --es launch com.palm.app.clock </dev/null >/dev/null 2>&1
if wait_log "launch from outside: the boot settled" 90; then
  sleep 12
  if lunalog | grep -q "launch com.palm.app.clock: no card opened"; then F "5 regr  Clock launched as Lunacy starts opened no card (970b21c)"
  else P "5 regr  Clock launched as Lunacy starts opened its card ($(lunalog | grep -o 'the boot settled after [0-9]* ms' | head -1))"; fi
else F "5 regr  a launch as Lunacy starts never ran (no \"the boot settled\" in 90 s)"; fi

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

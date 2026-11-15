#!/bin/bash
# A fresh install's first run, checked: what a person gets the first time they install Lunacy
# (Docs/test-plan.md, tier 2). Every release round runs it once, on one device, with the
# candidate's debug build. ERASES Lunacy's data on that device.
#
#   Workbench/fresh-install.sh <adb device> <debug apk> [results file]
#
# It uninstalls Lunacy, installs the APK, grants the permissions First Use would ask for, starts
# Lunacy, finishes First Use over the bus from First Use's own page, and (where Android has
# contacts) adds the Android account. Then, in that same run of Lunacy, without a restart, it
# runs Workbench/device-test.sh (NORESTART=1), whose section 2 checks that every waiting
# activity file is live and that contacts reached the Contacts list.
#
# Why: two 1.0 bugs lived only in that first run, and every other check came after a restart
# (2026-11-15, the Kyocera): the activity files held back until First Use were never sent (no
# contacts linker, so an empty Contacts list), and the Android account's page left out the
# template First Launch needs (no account). A restart hid the first; only First Launch shows
# the second, which test.sh now checks in the source.
D=$1; APK=$2; RESULTS=$3
[ -n "$D" ] && [ -f "$APK" ] || { echo "usage: $0 <adb device> <debug apk> [results file]"; exit 2; }
cd "$(dirname "$0")/.." || exit 2
A="adb -s $D"; PKG=org.webosarchive.lunacy
js() { ANDROID_SERIAL=$D Workbench/cdp.sh eval "$2" "$1" 2>/dev/null; }
waitfor() { # <seconds> <command...>: until the command succeeds
  local n=$1; shift; while [ "$n" -gt 0 ]; do "$@" && return 0; sleep 2; n=$((n-2)); done; return 1; }

$A get-state >/dev/null 2>&1 || { echo "no device $D"; exit 2; }
vc=$(aapt dump badging "$APK" 2>/dev/null | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")
echo "== fresh install of $APK (versionCode ${vc:-?}) on $D: Lunacy's data there is erased"
$A uninstall $PKG >/dev/null 2>&1
$A install "$APK" | tail -1
$A shell dumpsys package $PKG | grep -q DEBUGGABLE || { echo "not a debug build: First Use can't be finished over DevTools"; exit 2; }
# What First Use's pages ask Android for; contacts and calendar, as the Android account's page does.
for p in READ_CONTACTS READ_CALENDAR ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE; do
  $A shell pm grant $PKG android.permission.$p >/dev/null 2>&1
done
# A bigger log: the linker's first run over a few hundred contacts outruns a 256 KiB buffer (the
# Kyocera's), and device-test reads the start from the log.
$A logcat -G 4M >/dev/null 2>&1
$A logcat -c
$A shell input keyevent KEYCODE_WAKEUP; $A shell wm dismiss-keyguard >/dev/null 2>&1
$A shell am start -n $PKG/.shell.ShellActivity >/dev/null 2>&1

page_up() { ANDROID_SERIAL=$D Workbench/cdp.sh list 2>/dev/null | grep -q "$1"; }
waitfor 120 page_up org.webosarchive.lunacy.firstuse || { echo "First Use never opened"; exit 1; }
sleep 5
r=$(js org.webosarchive.lunacy.firstuse '(function(){var b=new PalmServiceBridge();window.__fi=b;window.__fir=null;b.onservicecallback=function(t){window.__fir=t};b.call("palm://org.webosarchive.lunacy/firstUse/done","{}");return "sent"})()')
sleep 3
echo "== First Use done: $(js org.webosarchive.lunacy.firstuse 'window.__fir')"
logged() { $A logcat -d -s Lunacy:I 2>/dev/null | grep -q "$1"; }
waitfor 60 logged "firstuse-createDefaultAccount" && echo "== profile account made" || echo "== no profile account after 60 s"
sleep 15

# The Android account, where Android has contacts: made from Contacts' page, one of the two apps
# its template lets add it, with what its own page hands Accounts.
n=$($A shell content query --uri content://com.android.contacts/contacts --projection _id 2>/dev/null | grep -c '^Row')
if [ "${n:-0}" -gt 0 ]; then
  $A shell am start -n $PKG/.shell.ShellActivity --es launch com.palm.app.contacts >/dev/null 2>&1
  waitfor 60 page_up com.palm.app.contacts || echo "== Contacts' page isn't up"
  sleep 5
  T=org.webosarchive.lunacy.androidaccount
  js com.palm.app.contacts/index.html '(function(){var b=new PalmServiceBridge();window.__fa=b;window.__far=null;b.onservicecallback=function(t){window.__far=t};b.call("palm://com.palm.service.accounts/createAccount",JSON.stringify({templateId:"'$T'",username:"android",alias:"Android",capabilityProviders:[{id:"'$T'.contacts"},{id:"'$T'.calendar"}],credentials:{},config:{}}));return "sent"})()' >/dev/null
  sleep 5
  echo "== Android account ($n Android contacts): $(js com.palm.app.contacts/index.html 'window.__far' | cut -c1-120)"
  # Until the linker has done: the number of people stops changing (up to 5 minutes). A page
  # busy with its first run can't show a card in device-test's 25 s.
  people() { js com.palm.app.contacts/index.html '(function(){var b=new PalmServiceBridge();window.__fp=b;b.onservicecallback=function(t){window.__fpn=JSON.parse(t).count};b.call("palm://com.palm.db/find",JSON.stringify({query:{from:"com.palm.person:1",limit:1},count:true}));return window.__fpn===undefined?-1:window.__fpn})()'; }
  last=-2; same=0
  for i in $(seq 1 60); do sleep 5; c=$(people); if [ "$c" = "$last" ] && [ "$c" != -1 ]; then same=$((same+1)); [ $same -ge 3 ] && break; else same=0; fi; last=$c; done
  echo "== the linker has made $last people"
else echo "== no Android contacts here: the Android account isn't added"; fi

echo "== device-test in the same run of Lunacy"
NORESTART=1 Workbench/device-test.sh "$D" "$vc" "$RESULTS"

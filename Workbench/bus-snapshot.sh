#!/bin/bash
# What Lunacy's bus answers, written down: one reply each from the system's read-only methods,
# asked from Email's page (a com.palm app) over DevTools, so a debug build. Made for comparing two
# builds on one device: install the first, snapshot, install the second over it, snapshot, diff
# (Docs/test-plan.md, section 16). It reads settings, lists and counts, and of db8 only kinds'
# counts and names; what changes by itself (times, ids, revisions, the battery) is left out.
#
#   Workbench/bus-snapshot.sh <adb device> <out.json>          (CDP_PORT, as cdp.sh)
D=$1; OUT=$2
[ -n "$D" ] && [ -n "$OUT" ] || { echo "usage: $0 <adb device> <out.json>"; exit 2; }
cd "$(dirname "$0")/.." || exit 2
js() { ANDROID_SERIAL=$D Workbench/cdp.sh eval "$1" /com.palm.app.email/index.html 2>/dev/null; }
js '(function(){var o=window.__snap={};window.__snapb=[];
function c(u,p){var b=new PalmServiceBridge();window.__snapb.push(b);var k=u.replace("palm://","")+" "+JSON.stringify(p);o[k]="(no reply)";var first=true;b.onservicecallback=function(t){if(!first)return;first=false;try{o[k]=JSON.parse(t)}catch(e){o[k]="unparsed: "+String(t).slice(0,200)}};b.call(u,JSON.stringify(p))}
var P="palm://";
c(P+"com.palm.systemservice/getPreferences",{keys:["timeFormat","locale","region","ringtone","alerttone","notificationtone","wallpaper","systemSounds","muteSound","timeZone","useNetworkTime","useNetworkTimeZone","lockTimeout","BlinkNotifications","airplaneMode","x_palm_carrier","sysUiEnableNextPrevGestures","enableALS","showAlertsWhenLocked","rotationLock","receiveDstChange","phoneRegion","nitz"]});
c(P+"com.palm.systemservice/getPreferenceValues",{key:"timeFormat"});
c(P+"com.palm.systemservice/time/getSystemTime",{});
c(P+"com.palm.systemservice/timezone/getTimeZoneRules",[{tz:"America/New_York",years:[2026]},{tz:"Europe/Berlin",years:[2026]}]);
c(P+"com.palm.systemservice/wallpaper/info",{wallpaperName:"nope.jpg"});
c(P+"com.palm.systemservice/nosuchmethod",{});
c(P+"com.palm.applicationManager/listApps",{});
c(P+"com.palm.applicationManager/listLaunchPoints",{});
c(P+"com.palm.applicationManager/listPackages",{});
c(P+"com.palm.applicationManager/listPendingLaunchPoints",{});
c(P+"com.palm.applicationManager/getAppInfo",{appId:"com.palm.app.email"});
c(P+"com.palm.applicationManager/getAppInfo",{appId:"com.nonexistent.app"});
c(P+"com.palm.applicationManager/getAppBasePath",{appId:"com.palm.app.email"});
c(P+"com.palm.applicationManager/getHandlerForExtension",{extension:"pdf"});
c(P+"com.palm.applicationManager/getHandlerForExtension",{extension:"mp3"});
c(P+"com.palm.applicationManager/getHandlerForUrl",{url:"http://example.com/"});
c(P+"com.palm.applicationManager/getHandlerForUrl",{url:"mailto:a@example.com"});
c(P+"com.palm.applicationManager/listAllHandlersForMime",{mime:"image/jpeg"});
c(P+"com.palm.applicationManager/listAllHandlersForUrl",{url:"https://example.com/a.html"});
c(P+"com.palm.applicationManager/listAllHandlersForMultipleMime",{mimes:["audio/mpeg","text/plain"]});
c(P+"com.palm.applicationManager/getResourceInfo",{uri:"file:///media/internal/none.mp3"});
c(P+"com.palm.applicationManager/launch",{id:"com.nonexistent.app"});
c(P+"com.palm.applicationManager/running",{});
c(P+"com.palm.connectionmanager/getstatus",{});
c(P+"com.palm.connectionmanager/getStatus",{subscribe:true});
c(P+"com.palm.activitymanager/list",{});
c(P+"com.palm.activitymanager/getDetails",{activityName:"mojodbpurge"});
c(P+"com.palm.activitymanager/nosuchmethod",{});
["nduid","version","deviceName","deviceNameShort","DMMODEL","PRODoID","boardType","DMCARRIER","productLineVersion","buildName","buildNumber","browserOsName","ProdSN","nope"].forEach(function(k){c(P+"com.palm.preferences/systemProperties/Get",{key:"com.palm.properties."+k})});
c(P+"com.palm.preferences/systemProperties/getSysProperty",{key:"com.palm.properties.DMMODEL"});
c(P+"com.palm.preferences/systemProperties/Get",{});
c(P+"com.palm.universalsearch/getUniversalSearchList",{});
c(P+"com.palm.universalsearch/getAllSearchPreference",{});
c(P+"com.palm.universalsearch/getOptionalSearchList",{});
c(P+"com.palm.downloadmanager/listPending",{});
c(P+"com.palm.downloadmanager/downloadStatusQuery",{ticket:999999});
c(P+"com.palm.appinstaller/queryInstallCapacity",{appId:"com.example.x",size:1024});
c(P+"com.palm.appInstallService/status",{});
c(P+"com.palm.power/com/palm/power/batteryStatusQuery",{});
c(P+"com.palm.power/com/palm/power/chargerStatusQuery",{});
c(P+"com.palm.power/timeout/set",{});
c(P+"com.palm.display/control/status",{});
c(P+"com.palm.display/control/getProperty",{properties:["timeout","maximumBrightness"]});
c(P+"com.palm.audio/ringtone/status",{});
c(P+"com.palm.audio/media/status",{});
c(P+"com.palm.audio/state/status",{});
c(P+"com.palm.keys/audio/status",{});
c(P+"com.palm.location/getUseGps",{});
c(P+"com.palm.location/getAutoLocate",{});
c(P+"com.palm.filenotifyd/state",{subscribe:true});
c(P+"com.palm.wifi/getstatus",{});
c(P+"com.palm.btmonitor/monitor/getradiostate",{});
c(P+"com.palm.systemmanager/getDeviceLockMode",{});
c(P+"com.palm.systemmanager/getSecurityPolicy",{});
c(P+"com.palm.service.accounts/listAccountTemplates",{});
c(P+"com.palm.service.accounts/listAccounts",{});
c(P+"org.webosarchive.lunacy/system/getEnvironment",{});
c(P+"com.palm.nonexistent.service/x",{});
["com.palm.account:1","com.palm.activity:1","com.palm.contact:1","com.palm.person:1","com.palm.calendar:1","com.palm.calendarevent:1","com.palm.mail.account:1","com.palm.app.contacts.prefs:1","com.palm.app.calendar.prefs:1","com.palm.note:1","com.palm.imloginstate:1","com.palm.message:1","com.palm.chatthread:1"].forEach(function(k){c(P+"com.palm.db/find",{query:{from:k,limit:1},count:true})});
c(P+"com.palm.db/find",{query:{from:"com.palm.account:1",select:["templateId","username"]}});
c(P+"com.palm.db/find",{query:{from:"com.palm.activity:1",select:["name"]}});
c(P+"com.palm.db/find",{query:{from:"com.nonexistent.kind:1"}});
c(P+"com.palm.tempdb/find",{query:{from:"com.palm.account.syncstate:1"},count:true});
["misc.file","image.file","image.album","video.file","audio.file","types"].forEach(function(k){c(P+"org.webosarchive.lunacy/media/find",{query:{from:"com.palm.media."+k+":1",limit:500}})});
return "sent"})()' >/dev/null
sleep 12
js 'JSON.stringify(window.__snap||{})' | python3 -c '
import json, sys
t = sys.stdin.read().strip()
try: o = json.loads(json.loads(t))
except Exception: print("no snapshot: " + t[:200], file=sys.stderr); sys.exit(1)
# What changes by itself, or names a moment or a record rather than a behaviour.
DROP = {"_rev", "_id", "_sync", "activityId", "utc", "localtime", "timestamp", "TZ", "offset", "percent", "percent_ui", "current_mA", "voltage_mV",
        "temperature_C", "capacity_mAh", "signalBars", "signalLevel", "ipAddress", "ssid", "bssid", "albumId", "modifiedTime", "processId", "lastUpdated",
        "version", "build", "apps", "uptime", "freeBytes", "free", "nduid"}
def norm(v, key=""):
    if isinstance(v, dict): return {k: norm(x, k) for k, x in sorted(v.items()) if k not in DROP}
    if isinstance(v, list):
        items = [norm(x, key) for x in v]
        try: return sorted(items, key=lambda x: json.dumps(x, sort_keys=True))
        except Exception: return items
    return v
out = {}
for k, v in sorted(o.items()):
    v = norm(v)
    # Long lists by what they hold: ids and names, and how many.
    if isinstance(v, dict) and k.startswith("com.palm.applicationManager/listApps"):
        v = {"returnValue": v.get("returnValue"), "apps": sorted((a.get("id"), a.get("title"), a.get("main"), bool(a.get("visible", True)), a.get("type")) for a in o[k].get("apps", []))}
    out[k] = v
json.dump(out, open(sys.argv[1], "w"), indent=1, sort_keys=True, ensure_ascii=False)
print("%d calls, %d without a reply" % (len(out), sum(1 for v in out.values() if v == "(no reply)")))' "$OUT"

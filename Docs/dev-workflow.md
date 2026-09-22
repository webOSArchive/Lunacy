# Development workflow

How Lunacy is built, run and checked against a real TouchPad. Everything here was worked
out on the reference devices. Changes made to the Android device are listed separately in
[android5-setup.md](android5-setup.md).

## Layout

| Path | What | Committed |
|---|---|---|
| `AndroidLuna/` | The Lunacy app (Kotlin, Android Views, `minSdk 21`) | yes |
| `AndroidLuna/src/main/assets/luna/` | LunaCE images (Apache 2.0), Prelude fonts and TouchPad wallpapers (abandonware), each with a NOTICE | yes |
| `AndroidLuna/src/main/assets/lunacy/` | Injected page scripts: `compat.js` (input model, flicks, uncaught errors), `bridge.js` (PalmSystem, PalmServiceBridge), `net.js` (network shim), `fonts.css` (generated) | yes |
| `AndroidLuna/src/main/assets/certs/` | Mozilla CA roots for Lunacy's own HTTP (MPL 2.0), with a NOTICE | yes |
| `AndroidLuna/src/main/assets/apps/` | The bundled apps: Lunacy's own (Device Info, the shortcuts to Android's settings, `org.webosarchive.lunacy.notifytest`), Palm's own that ship as abandonware (Screen & Lock, Help, Exhibition, Clock, and the Video Player, which has no icon and is launched by other apps), and webOS Archive's App Museum. Each carries a NOTICE | yes |
| `AndroidLuna/src/main/assets/luna-systemui/` | The system UI webOS served from the OS, at its own paths: Lunacy's file picker | yes |
| `LunaRuntimes/enyo-1.0/` | Lunacy's changes to Enyo, as patches against upstream, with CHANGES.md as the fork's change log | yes |
| `LunaRuntimes/mojo/` | Lunacy's changes to Palm's Mojo, likewise, with CHANGES.md explaining how Mojo is packaged | yes |
| `LunaKeyboard/` | The companion keyboard APK, built by the same Gradle root and depending on nothing in Lunacy ([its README](../LunaKeyboard/README.md)) | yes |
| `AndroidLuna/local-assets/` | Stock Enyo 1.0 with those patches applied, Mojo and the other frameworks, populated by `AndroidLuna/fetch-assets.sh` | no |
| `AndroidLuna/local-test-apps/` | Test apps (Glimpse, the Enyo samples, settings apps not yet shipped), also from `fetch-assets.sh`; in the APK only with `./gradlew assembleDebug -PtestApps` | no |
| `AndroidLuna/tools/gen-fonts-css.py` | Regenerates `fonts.css` from the shipped Prelude files | yes |
| `AndroidLuna/tools/node-launcher.cpp` | Node's `main()`, built by `fetch-assets.sh` into `liblunacynode.so` for JS services | yes |
| `AndroidLuna/local-jni/` | `libnode.so` (nodejs-mobile 0.3.3), `libc++_shared.so` and the launcher, from `fetch-assets.sh` | no |
| `Workbench/probe/` | TouchPad probe apps that record the contract: `…lunacy.probe` (PalmSystem, the input model, WebSQL, the text indexer), `…lunacy.netprobe` (the network, from a 2.2.4 phone), `…lunacy.htmlprobe` (how the device's parser reads a self-closed tag), `…lunacy.cssprobe` (window metrics and the layout webOS apps were written against, no framework) and `…lunacy.mojoprobe` (a Mojo app that builds a piece of another app's scene with real widgets and logs the geometry) and `…lunacy.emuprobe` (what an app that carries no `uiRevision` is told about the machine - deliberately the only one without it), `…lunacy.winprobe` (the app-facing window calls and properties, driven by launch parameters; its `card` and `dash` actions open more windows), `…lunacy.slowprobe` (an app slow to call `stageReady`) and `…lunacy.headprobe` (a `noWindow` app that opens its first card late) | yes |
| `Workbench/*.sh`, `Workbench/cdp.mjs`, `Workbench/seams.py` | Device helper scripts, DevTools from the command line, and the border-image seam finder (both below) | yes |
| `Workbench/vendor/` | Local clones: enyo-1.0, LunaCE, luna-sysmgr, webos-catalog-service, and files pulled from the TouchPad (frameworks, `/etc/palm`, fonts, wallpapers, a WebView 64 APK) | no |
| `Workbench/vendor/palm-apps/` | Palm's own apps pulled off the reference TouchPad (Clock, Exhibition, Video Player), before they are bundled | no |
| `Workbench/vendor/touchpad/sysmgr-qml/` | The device's own `/usr/palm/sysmgr/uiComponents` QML, which is what the shell's Exhibition faces are drawn from | no |
| `Workbench/vendor/settings-apps/` | Palm's settings apps not yet shipped; `fetch-assets.sh` copies the ones that aren't already in `assets/apps/` into `local-test-apps/apps/` for testing | no |
| `Workbench/results/` | Screenshots and logs | no |
| `Meta/` | codepoet's artwork, including the launcher icon the mipmaps are made from | yes |
| `out/` | Where both modules' APKs are copied when they build | no |

## Build and run

```sh
AndroidLuna/fetch-assets.sh     # once, and whenever the vendor clones change (needs NDK r21e)
./gradlew assembleDebug         # both modules; :AndroidLuna:assembleDebug for the shell alone
./gradlew lintDebug             # NewApi is fatal: minSdk 21 must hold
adb install -r out/AndroidLuna-debug.apk        # every APK builds to out/
adb install -r out/LunaKeyboard-debug.apk
adb shell am start -n org.webosarchive.lunacy/.shell.ShellActivity \
    --es launch <appid> [--es params '<json>']
```

- `--es install <url or path>` installs a package through the same path the App Museum
  uses. To install from a local copy of the package host, serve it and forward the port:
  `python3 -m http.server 8123` in the folder, `adb reverse tcp:8123 tcp:8123`, then
  `--es install http://localhost:8123/<file>.ipk`. Installed apps live in the app's data
  (`adb shell run-as org.webosarchive.lunacy ls files/cryptofs/apps/usr/palm/applications`),
  and `adb shell pm clear org.webosarchive.lunacy` removes them all.
- **JS services** need the Android NDK r21e (`sdkmanager 'ndk;21.4.7075529'`, or set
  `ANDROID_NDK`) and nodejs-mobile 0.3.3's Android zip unpacked in
  `Workbench/vendor/nodejs-mobile/v0.3.3/`. Their logs appear as `svc [<name>] …`; their files are
  under `files/webos/` in the app's data (`/media/internal` is `files/webos/media/internal`).
- `--ei trimMemory <level>` hands the shell an Android memory warning (Android 5's `am` has
  no `send-trim-memory`): 10 is `RUNNING_LOW`, 15 `RUNNING_CRITICAL`. Apps hear it as
  `Mojo.lowMemoryNotification`, and "normal" follows once 30 s pass without another.
- The activity is `singleTask`. A second `am start` with `launch` arrives through
  `onNewIntent`, which launches or relaunches the app.
- **Lint matters.** A plain debug build does not stop calls to APIs newer than Android 5.
  `StaticLayout.Builder`, an Android 6 API, crashed the launcher before lint was set to make
  NewApi fatal.

## Android tablet (HP 10 G2, Android 5.0.1)

- **Screenshots:** `Workbench/lshot.sh <name> [portrait]`, saved upright to `Workbench/results/`.
- **Logs:** the MediaTek build floods the 256 KB log buffer, and `logcat -G` isn't
  supported. Stream the log while testing:
  `adb logcat -v brief Lunacy:V chromium:E AndroidRuntime:E '*:S'`.
- **The network shim logs every request** as `net [<appid>] <method> <url> <status> <size>`.
- **Every page load logs its viewport** (`[appid] loaded … viewport WxH dpr …`), which is
  useful for checking the scale.
- **Screen recording:** `adb shell screenrecord --time-limit 4 /sdcard/x.mp4`, then
  `ffmpeg -vf fps=8` to get frames. Use it for motion checks such as flick inertia. For
  "does it still pop in?", extract both runs at `fps=10`, `montage` the same frame numbers
  side by side, and - to put a number on it - measure each frame's difference from the last
  one over a region that holds only the chrome you care about; the frame where that settles
  is when the UI stopped assembling.
- **Rotation for tests:** set `settings put system accelerometer_rotation 0`, then
  `user_rotation 0` (portrait) or `3` (landscape, upright in screenshots; `1` is upside
  down). Put `accelerometer_rotation 1` back afterwards.
- **Fake battery states:** `dumpsys battery set level N`, `set usb 0` and `set ac 0`, then
  `reset`. Android 5 has no `unplug`.
- **Measuring smoothness.** SurfaceFlinger's own present times are the ground truth for what
  the screen showed, and they need no developer-options switch:
  `adb shell "dumpsys SurfaceFlinger --latency 'org.webosarchive.lunacy/…ShellActivity'"`
  gives 128 rows of `desired actual ready` in nanoseconds; the gaps between the *actual*
  column are the frames people see (17 ms is one vsync). Trigger the thing under test with
  `input keyevent 4` rather than `input swipe` where you can - `input` starts a whole JVM, and
  a 250 ms swipe overlaps what you are measuring.
  `dumpsys gfxinfo org.webosarchive.lunacy` (after `setprop debug.hwui.profile true`) breaks a
  frame into Draw/Prepare/Process/Execute, but on this device a blocked WebView shows up as
  "Process" and looks like drawing cost when it is really waiting. For that,
  `adb shell atrace -b 16384 -t 4 gfx view hwui webview` and pair the `B|`/`E|` marks: if the
  work inside a frame is small and the *gaps between frames* are large, something is blocking
  the pipeline rather than drawing slowly.
- **Measuring what a page waits for.** `./cdp.sh eval` with
  `performance.getEntriesByType("resource")` gives every request's `startTime` and
  `responseEnd` against `performance.timing`. That is how the framework art's arrival was
  timed, and it separates "the file is slow" from "the page asked late" - which turned out to
  be the whole story.
- **Inspecting pages:** `chrome://inspect` works, because Lunacy turns on WebView
  debugging.
- **Driving the tablet without hands:** `adb shell input tap <x> <y>`, `input swipe` (a long
  press is a swipe that ends where it started), and `input keyevent KEYCODE_BACK` for the home
  button. Coordinates are device pixels, which on the HP 10 G2 are TouchPad pixels. This is
  how the launcher's edit mode and the file picker were checked against the TouchPad.
  - **Wait until Lunacy is really up.** Just after `am start` the shell may not yet be the
    window in front, and a tap then lands on whatever app is underneath - it opened Google
    Camera, Photos and the Assistant on 2026-09-22 before that was understood. Allow 15-20 s
    after a cold start, and check `dumpsys window | grep mCurrentFocus` if in doubt.
  - **What `input` can't do** - press, hold, move, hold, let go, as dragging one launcher icon
    onto another needs - `Workbench/touch.py "x,y" hold:900 "x2,y2" hold:800` plays through
    `sendevent` on the touchscreen itself. Each `sendevent` is a process of its own, so a
    stepped move is much slower than a finger; `STEP=1000` makes a move one jump.
  - **Held or dragged a card?** A card-view drag longer than `TAP_RADIUS` in `input swipe` is
    a scroll or a throw; a hold is a swipe that stays still for 700 ms.
  - **The tablet's orientation** follows the sensor (`accelerometer_rotation` 1). For
    portrait shots, `settings put system user_rotation 0` with `accelerometer_rotation 0`, and
    put `accelerometer_rotation` back afterwards; `lshot.sh` is told `portrait` only when the
    screen really is.
- **DevTools from the command line:** `Workbench/cdp.sh` (Node 22+) forwards the running
  Lunacy's DevTools socket and talks to one page, chosen by a substring of its URL:
  - `./cdp.sh eval '<expr>' <appid>` prints the expression's value, for reading app state
    such as `enyo.$` components, computed styles or media elements. A promise is awaited, so
    a bus call can be made from a page: `new Promise(r => { var b = new PalmServiceBridge();
    b.onservicecallback = r; b.call(uri, params) })`;
  - `./cdp.sh send <Method> '<json>' <appid>` sends any DevTools method;
  - `./cdp.sh inject '<js>' <appid>` runs a script before the page's own and reloads it.
    Wrapping `setTimeout` and `clearTimeout` this way is what found the Enyo
    `webkitCancelRequestAnimationFrame` bug;
  - `./cdp.sh list` shows the open pages.
  Android 5's `adb shell` has no `pidof`, which is why the script reads `ps`.

## Reference TouchPad (webOS CE 3.1.0, LunaCE 5.0.0)

Reached over novacom (`novacom`, `novaterm` and the `palm-*` SDK tools).

A **bone-stock TouchPad (HP webOS 3.0.5)** is available too, and is the control for telling
HP's behaviour from LunaCE's. Its `/etc/palm` is byte-identical to the reference device's; see
the note in [luna-shell-reference.md](luna-shell-reference.md). `novacom -l` names them
`topaz-linux` either way, so connect one at a time.

- **`luna-send` needs a pty.** Its output is lost under `novacom run`. `Workbench/tp.sh
  '<command>' [wait]` runs a command through `novaterm`.
- **Screenshots:** `Workbench/tpshot.sh <name> [rotation]` (`-90` for portrait; `90`, then
  rotate `-90` again, for landscape; check the result). It uses
  `palm://com.palm.systemmanager/takeScreenShot`.
- **Closing an app** by id: `Workbench/tpclose.sh <appid>...` (the application manager closes
  by process id, which it looks up). A home press from the card view opens the launcher, and
  the launcher is how the reference launcher screenshots were taken without touching it.
- **When a screenshot can't be taken:** `takeScreenShot` takes a few seconds on the device, so
  a burst (`tpburst.sh`) is one frame every ~3 s. `/dev/fb0` is 1024 × 2304, three BGRA
  frames; `cat` it and slice it if the service is in doubt.
- **Home button:** `Workbench/tpkey.sh 350` injects key 232 (`KEY_CENTER`, octal 350) into
  `/dev/input/event0` (gpio-keys). There is no remote touch: the launcher and menus need
  codepoet to tap.
- **Wake and unlock:** `luna-send -n 1 palm://com.palm.display/control/setState
  '{"state":"unlock"}'`, and `setProperty '{"onWhenConnected":true,"timeout":1800}'` to keep
  it on.
- **Log reading:** `/var/log/messages` rotates fast. Use `palm-log -f <appid>` while
  launching.
- **Copying trees off the device:** `novacom run file:///bin/tar -- cf - -C <dir> <name> |
  tar xf -`. `novacom get` handles single files. The frameworks `fetch-assets.sh` copies into
  the APK come from there: `-C /usr/palm/frameworks` for `mojo`, `mojocommon`, `mojo2`,
  `prototype`, `mojoloader.js`, `mojo.core`, `underscore`, `foundations`, `globalization`,
  the `metascene.*` and the media ones, into `Workbench/vendor/touchpad/`. `media` there is a
  symlink to `/usr/lib/luna/luna-media-shim`, so copy that instead and name it `media`.
- **If the device's apps sit on a loading splash and `palm-install` says "file open failed",**
  it is in USB drive mode: `/media/internal` and `/media/cryptofs` are handed to the host, so
  nothing on the device can read them. `grep media/internal /proc/mounts` comes back empty.
  Nothing here can fix it - tap **Done** on the device. Don't mount the volume from this side
  while the device is sharing it.
- **If novacom hangs:** `novacom -l` times out when the daemon is stuck. Restart it with
  `sudo systemctl restart novacomd`, which works non-interactively here.
- **Driving the test app without touches:** `palm-launch -p '{"do":"push"}'
  org.webosarchive.lunacy.notifytest`. Actions are `banner`, `banners`, `push`, `pop` and
  `popup`. In Lunacy, pass the same JSON with `--es params`.
- **Package and install our apps:** `palm-package <dir>`, then `palm-install <ipk>`.
- **Putting the same markup on both machines.** When a layout comes out wrong here and right
  on the device, build it in `…lunacy.cssprobe` (plain CSS) or `…lunacy.mojoprobe` (a real
  Mojo scene) and read the two columns side by side before blaming the engine: on
  2026-09-22 the table-cell layout that looked like the cause of drPodder's stub of a
  playback bar turned out to lay out identically on the TouchPad. Both log to `palm-log` on a
  device and to the console in Lunacy (`adb logcat`, or `cdp.sh eval 'window.__cssprobe'`).
  A probe app needs `"uiRevision": 2` in its `appinfo.json`, or webOS runs it in a 320 x 480
  phone-emulation card and every measurement is of the wrong window.
- **The probe app** (`Workbench/probe`, 0.1.2 on the reference TouchPad) records the contract
  to `palm-log`. `net.js` in it measures cross-origin XHR against `httpbin.org`, which echoes
  what the server saw. Install the same `.ipk` in Lunacy (`adb push` it to
  `/data/local/tmp`, then `--es install`) and compare the two logs line by line. Run `palm-log -f org.webosarchive.lunacy.probe`, then `palm-launch` it, and
  add a check to `probe.js` when a question comes up.

## Reference Pre3 (HP webOS 2.2.4)

An AT&T Pre3, reached over novacom exactly as the TouchPad is — `Workbench/tp.sh` and
`novacom get` both work against it unchanged. It is the phone-shaped reference, and what it
reports is written up in [pre3.md](pre3.md).

- **It is patched, not upgraded** (community patches through Preware, no Luna or OS upgrade),
  so `/etc/palm` mixes stock and patched files. Modification time tells them apart: the build
  date is stock, and a patched file leaves a `.webosinternals.orig` beside it. Read the
  `.orig`.
- **`systemProperties/Get` takes one `key`** on 2.2.4, not the TouchPad's `keys` array.
- **No `takeScreenShot`** was tried here; the TouchPad's screenshot route is untested on 2.2.4.

## Reading a probe's output when palm-log won't

`palm-log -f <appid>` holds the novacom connection, and `palm-launch` then times out behind
it. The way round is to launch over the bus and read the log file afterwards, in one go:

```sh
Workbench/tp.sh 'luna-send -n 1 palm://com.palm.applicationManager/launch "{\"id\":\"<appid>\"}" >/dev/null
                 sleep 8; grep LUNACYPROBE /var/log/messages | grep user.notice |
                 sed "s/.*<appid>: //;s/, file:.*//"'
```

`user.notice` is `console.log`; the same line comes again as `user.crit` for `console.error`,
which is the only level webOS 2.2.4 carries (see [pre3.md](pre3.md)).

**If an app launches but no card appears**, `WebAppMgr` has died - `LunaSysMgr` stays up and
keeps logging, so the device looks healthy. Check for it, and reboot if it is gone:

```sh
Workbench/tp.sh 'for p in /proc/[0-9]*; do tr "\0" " " < $p/cmdline; echo; done | grep -c WebAppMgr'
```

`ps` under `novaterm` truncates its output, which makes this look like the device is empty.

- **Border-image seams:** `Workbench/seams.sh <appid-substring> <name>` screenshots the
  tablet, reads every border-imaged widget's geometry out of the running card, and reports
  the slice boundaries that show a one-pixel step. It *locates* candidates; the verdict is a
  reference comparison, because such a step can equally be the artwork's own highlight. See
  "border-image seams" in [fix-log.md](fix-log.md) for what that measurement settled.

## How changes are checked

Every visual change is compared with the TouchPad at the same scale. On the HP 10 G2 one
TouchPad px is one device px, so screenshots compare 1:1. Crop and zoom the area (PIL,
nearest-neighbour), and put the two devices side by side. codepoet judges feel; the
screenshots settle geometry.

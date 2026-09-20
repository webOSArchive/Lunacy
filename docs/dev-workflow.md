# Development workflow

How Lunacy is built, run and checked against a real TouchPad. Everything here was worked
out on the reference devices. Changes made to the Android device are listed separately in
[android5-setup.md](android5-setup.md).

## Layout

| Path | What | Committed |
|---|---|---|
| `android/` | The Lunacy app (Kotlin, Android Views, `minSdk 21`) | yes |
| `android/app/src/main/assets/luna/` | LunaCE images (Apache 2.0), Prelude fonts and TouchPad wallpapers (abandonware), each with a NOTICE | yes |
| `android/app/src/main/assets/lunacy/` | Injected page scripts: `compat.js` (input model, flicks, uncaught errors), `bridge.js` (PalmSystem, PalmServiceBridge), `net.js` (network shim), `fonts.css` (generated) | yes |
| `android/app/src/main/assets/certs/` | Mozilla CA roots for Lunacy's own HTTP (MPL 2.0), with a NOTICE | yes |
| `android/app/src/main/assets/apps/` | The bundled apps: Lunacy's own (Device Info, the shortcuts to Android's settings, `org.webosarchive.lunacy.notifytest`) and Palm's own settings apps that ship as abandonware (Screen & Lock, Help). Each carries a NOTICE | yes |
| `android/app/src/main/assets/luna-systemui/` | The system UI webOS served from the OS, at its own paths: Lunacy's file picker | yes |
| `android/framework/enyo-1.0/` | Lunacy's changes to Enyo, as patches against upstream, with CHANGES.md as the fork's change log | yes |
| `android/framework/mojo/` | Lunacy's changes to Palm's Mojo, likewise, with CHANGES.md explaining how Mojo is packaged | yes |
| `android/local-assets/` | Stock Enyo 1.0 with those patches applied, and third-party test apps (App Museum, Glimpse, Enyo samples), populated by `android/fetch-assets.sh` | no |
| `android/tools/gen-fonts-css.py` | Regenerates `fonts.css` from the shipped Prelude files | yes |
| `android/tools/node-launcher.cpp` | Node's `main()`, built by `fetch-assets.sh` into `liblunacynode.so` for JS services | yes |
| `android/local-jni/` | `libnode.so` (nodejs-mobile 0.3.3), `libc++_shared.so` and the launcher, from `fetch-assets.sh` | no |
| `android/art/` | codepoet's Lunacy launcher icon sources | yes |
| `spike/android/` | Spike 1 test app (superseded by `android/`) | yes |
| `spike/probe/` | TouchPad probe app that records the PalmSystem contract | yes |
| `spike/*.sh`, `spike/cdp.mjs` | Device helper scripts, and DevTools from the command line (below) | yes |
| `spike/vendor/` | Local clones: enyo-1.0, LunaCE, luna-sysmgr, webos-catalog-service, and files pulled from the TouchPad (frameworks, `/etc/palm`, fonts, wallpapers, a WebView 64 APK) | no |
| `spike/vendor/palm-apps/` | Palm's own apps pulled off the reference TouchPad (Clock, Exhibition), before they are bundled | no |
| `spike/vendor/touchpad/sysmgr-qml/` | The device's own `/usr/palm/sysmgr/uiComponents` QML, which is what the shell's Exhibition faces are drawn from | no |
| `spike/vendor/settings-apps/` | Palm's settings apps not yet shipped; `fetch-assets.sh` copies the ones that aren't already in `assets/apps/` into `local-assets/apps/` for testing | no |
| `spike/results/` | Screenshots and logs | no |

## Build and run

```sh
cd android
./fetch-assets.sh               # once, and whenever the vendor clones change (needs NDK r21e)
./gradlew assembleDebug
./gradlew lintDebug             # NewApi is fatal: minSdk 21 must hold
adb install -r app/build/outputs/apk/debug/app-debug.apk
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
  `spike/vendor/nodejs-mobile/v0.3.3/`. Their logs appear as `svc [<name>] …`; their files are
  under `files/webos/` in the app's data (`/media/internal` is `files/webos/media/internal`).
- The activity is `singleTask`. A second `am start` with `launch` arrives through
  `onNewIntent`, which launches or relaunches the app.
- **Lint matters.** A plain debug build does not stop calls to APIs newer than Android 5.
  `StaticLayout.Builder`, an Android 6 API, crashed the launcher before lint was set to make
  NewApi fatal.

## Android tablet (HP 10 G2, Android 5.0.1)

- **Screenshots:** `spike/lshot.sh <name> [portrait]`, saved upright to `spike/results/`.
- **Logs:** the MediaTek build floods the 256 KB log buffer, and `logcat -G` isn't
  supported. Stream the log while testing:
  `adb logcat -v brief Lunacy:V chromium:E AndroidRuntime:E '*:S'`.
- **The network shim logs every request** as `net [<appid>] <method> <url> <status> <size>`.
- **Every page load logs its viewport** (`[appid] loaded … viewport WxH dpr …`), which is
  useful for checking the scale.
- **Screen recording:** `adb shell screenrecord --time-limit 4 /sdcard/x.mp4`, then
  `ffmpeg -vf fps=8` to get frames. Use it for motion checks such as flick inertia.
- **Rotation for tests:** set `settings put system accelerometer_rotation 0`, then
  `user_rotation 0` (portrait) or `3` (landscape, upright in screenshots; `1` is upside
  down). Put `accelerometer_rotation 1` back afterwards.
- **Fake battery states:** `dumpsys battery set level N`, `set usb 0` and `set ac 0`, then
  `reset`. Android 5 has no `unplug`.
- **Inspecting pages:** `chrome://inspect` works, because Lunacy turns on WebView
  debugging.
- **Driving the tablet without hands:** `adb shell input tap <x> <y>`, `input swipe` (a long
  press is a swipe that ends where it started), and `input keyevent KEYCODE_BACK` for the home
  button. Coordinates are device pixels, which on the HP 10 G2 are TouchPad pixels. This is
  how the launcher's edit mode and the file picker were checked against the TouchPad.
- **DevTools from the command line:** `spike/cdp.sh` (Node 22+) forwards the running
  Lunacy's DevTools socket and talks to one page, chosen by a substring of its URL:
  - `./cdp.sh eval '<expr>' <appid>` prints the expression's value, for reading app state
    such as `enyo.$` components, computed styles or media elements;
  - `./cdp.sh send <Method> '<json>' <appid>` sends any DevTools method;
  - `./cdp.sh inject '<js>' <appid>` runs a script before the page's own and reloads it.
    Wrapping `setTimeout` and `clearTimeout` this way is what found the Enyo
    `webkitCancelRequestAnimationFrame` bug;
  - `./cdp.sh list` shows the open pages.
  Android 5's `adb shell` has no `pidof`, which is why the script reads `ps`.

## Reference TouchPad (webOS CE 3.1.0, LunaCE 5.0.0)

Reached over novacom (`novacom`, `novaterm` and the `palm-*` SDK tools).

- **`luna-send` needs a pty.** Its output is lost under `novacom run`. `spike/tp.sh
  '<command>' [wait]` runs a command through `novaterm`.
- **Screenshots:** `spike/tpshot.sh <name> [rotation]` (`-90` for portrait; `90`, then
  rotate `-90` again, for landscape; check the result). It uses
  `palm://com.palm.systemmanager/takeScreenShot`.
- **Home button:** `spike/tpkey.sh 350` injects key 232 (`KEY_CENTER`, octal 350) into
  `/dev/input/event0` (gpio-keys). There is no remote touch: the launcher and menus need
  codepoet to tap.
- **Wake and unlock:** `luna-send -n 1 palm://com.palm.display/control/setState
  '{"state":"unlock"}'`, and `setProperty '{"onWhenConnected":true,"timeout":1800}'` to keep
  it on.
- **Log reading:** `/var/log/messages` rotates fast. Use `palm-log -f <appid>` while
  launching.
- **Copying trees off the device:** `novacom run file:///bin/tar -- cf - -C <dir> <name> |
  tar xf -`. `novacom get` handles single files.
- **If novacom hangs:** `novacom -l` times out when the daemon is stuck. Restart it with
  `sudo systemctl restart novacomd`, which works non-interactively here.
- **Driving the test app without touches:** `palm-launch -p '{"do":"push"}'
  org.webosarchive.lunacy.notifytest`. Actions are `banner`, `banners`, `push`, `pop` and
  `popup`. In Lunacy, pass the same JSON with `--es params`.
- **Package and install our apps:** `palm-package <dir>`, then `palm-install <ipk>`.
- **The probe app** (`spike/probe`, 0.0.9 on the reference TouchPad) records the contract
  to `palm-log`. `net.js` in it measures cross-origin XHR against `httpbin.org`, which echoes
  what the server saw. Install the same `.ipk` in Lunacy (`adb push` it to
  `/data/local/tmp`, then `--es install`) and compare the two logs line by line. Run `palm-log -f org.webosarchive.lunacy.probe`, then `palm-launch` it, and
  add a check to `probe.js` when a question comes up.

## How changes are checked

Every visual change is compared with the TouchPad at the same scale. On the HP 10 G2 one
TouchPad px is one device px, so screenshots compare 1:1. Crop and zoom the area (PIL,
nearest-neighbour), and put the two devices side by side. codepoet judges feel; the
screenshots settle geometry.

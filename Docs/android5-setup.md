# Android 5 device setup

Everything that has been done to an Android 5 device to make Lunacy work, or to test it.
Keep this list current: it becomes the install guide for users, and the checklist for each
new test device.

**Reference device:** HP 10 G2 Tablet: Android 5.0.1 (build LRX21M), MT8127, 1 GB of RAM,
not rooted, no Google account.

**Target API 24 (2026-09-23).** Lunacy now targets API 24 with a minimum of 21. Installed over
the old build on the reference HP: Android 5 granted every permission at install, as before,
and nothing asked. Playback in drPodder was restarted a few times while testing its dashboard,
and the screen was turned to landscape (`user_rotation 3`) and back to how it was
(`user_rotation 0`, auto-rotate on).

## Required

| Step | How | Why |
|---|---|---|
| Allow apps from unknown sources | Settings → Security → Unknown sources | Lunacy is sideloaded, not installed from Play. |
| Install Lunacy | `adb install` during development; a downloaded APK for users | |

## Development only

| Step | How | Why |
|---|---|---|
| Developer options and USB debugging | Settings → About → tap Build number seven times, then Developer options → USB debugging | For `adb` and screenshots. |
| Inspect WebView cards | `chrome://inspect` on a desktop Chromium, with the device on USB | Lunacy turns on WebView debugging in development builds. |

## Installed packages

| Package | What | Notes |
|---|---|---|
| `org.webosarchive.lunacy` | Lunacy | Built from `AndroidLuna/`. Run `AndroidLuna/fetch-assets.sh` first to populate local assets. |
| `org.webosarchive.keyboard` | LunaKeyboard | The companion keyboard, built from [LunaKeyboard/](../LunaKeyboard/README.md). Optional, and nothing in Lunacy depends on it. |

## Settings changed during testing

| Setting | Why | State left |
|---|---|---|
| `accelerometer_rotation` / `user_rotation` | Fixed orientations for screenshot comparisons | Auto-rotate back on (`accelerometer_rotation 1`) |
| `dumpsys battery set …` | Testing the status bar battery icon, and leaving Exhibition when the charger goes | `dumpsys battery reset` |
| LunaKeyboard enabled and selected | `adb shell ime enable`/`ime set org.webosarchive.keyboard/.KeyboardService`; testing the companion keyboard | Left selected. Was `com.google.android.inputmethod.latin/.LatinIME`; `ime set` that to put it back |
| Daydream on, screen saver set to **Lunacy Exhibition** | Settings → Display → Daydream; testing the Exhibition dream | Left on and selected (`screensaver_components org.webosarchive.lunacy/…ExhibitionDream`) |
| `setprop debug.hwui.profile true` | Per-frame timings for the card animations (`dumpsys gfxinfo`) | Set back to `false`. It is a property, so a reboot clears it anyway |
| adb over Wi-Fi at `192.168.10.34:5555` (2026-10-04) | `adb tcpip 5555` over the cable, then `adb connect`; the VM's USB hung `adb shell` right after the switch, but the Wi-Fi connection worked | Reset by a reboot |
| Android's wallpaper replaced by Lunacy's (2026-10-04) | The 32-bit build with `HostWallpaper` installed over Wi-Fi; Screen & Lock's "Use on Android" turned on over DevTools (`Workbench/cdp.sh`), the webOS wallpaper changed to 05.jpg and back to 22.jpg, the switch turned off. Android 5's lock screen showed 22.jpg | Android's wallpaper is 22.jpg; the previous one is gone. The switch is off (`shared_prefs/hostwallpaper.xml`) |
| Lunacy with the Web app installed, twice (2026-10-04) | `adb install -r` of the 32-bit build over Wi-Fi; the Web app opened on Wikipedia and its bookmarks drawer opened and closed | The factory WebView (Chromium 37) draws the pages; Android 5's certificate store refuses Wikipedia's chain, which Lunacy's bundled roots accept. No bookmarks or files were added |
| Text-editing build installed (2026-10-04) | `adb install -r` of the 32-bit build over Wi-Fi; the Web app's address bar given test text over DevTools, held, selected and copied | Android's clipboard holds "bravo" |
| Debug builds replaced by the signed 0.5.5 release (2026-10-04) | `adb uninstall` of Lunacy and the keyboard (taking Lunacy's data: installed apps, db8, the webOS tree, settings), then `adb install out/AndroidLuna-arm32-release.apk` and `out/LunaKeyboard-release.apk`, `ime enable` and `ime set` for the keyboard; First Use run (Home Screen skipped), Web and Calculator opened | Signed with codepoet's key from now on; a debug build no longer installs over it. Re-install the apps the earlier rows list as needed. Build 151 installed over it the same day (`adb install -r`), then build 152; the Web app opened example.com, example.org and example.net from the address bar (history has them) |
| Launch-point build installed; PWA Installer installed and used (2026-10-05) | Over Wi-Fi: PWA Installer 1.0.0 from the package mirror (`adb push` to `/data/local/tmp`, `--es install`); `adb install -r out/AndroidLuna-arm32-release.apk` (signed, over the 0.5.5 release); PWA Installer driven over DevTools to fetch six sites (Wikipedia, GitHub, YouTube, BBC News, Squoosh, Element) and add a Wikipedia shortcut; the Web app's Add to Launcher used on Wikipedia, and that shortcut removed again from the launcher's edit mode; Lunacy force-stopped and restarted | The PWA Installer's Wikipedia shortcut is left on the launcher's "android" tab (`var/luna/launchpoints/982899` in the webOS root). Its fetched icons are in `/media/internal/.webosarchive` (Lunacy's own `files/webos/media/internal`) |
| First-tab and page-image builds installed (2026-10-05) | `adb install -r out/AndroidLuna-arm32-release.apk` twice over Wi-Fi; the Web app's Add to Launcher used on example.com, and its dialog opened once more and cancelled; the Example Domain and Briefing shortcuts launched | "Example Domain" (a Web app shortcut) and codepoet's "Briefing" (from the PWA Installer) are on the first tab |
| Novacom builds installed, SDK tools run against Lunacy (2026-10-05) | `adb tcpip 5555` over the cable and `adb connect 192.168.10.34:5555`; five `adb install -r` of signed 32-bit release builds (versionName still 0.5.5), four over Wi-Fi and one over the cable, which hung and was killed; `palm-install`, `palm-launch`, `palm-run`, `palm-log`, `novacom` and `novaterm` from the host through novacomd's adb relay; the screen turned off with the power key and woken by `palm-launch` | `org.webosarchive.lunacy.logtest` installed and removed again; Lunacy's `/var/log/messages` now exists; the protocol tests' files in its `/tmp` were deleted. A 40 MB test package (`…lunacy.bigtest`) was installed and removed four times to watch the launcher's pending icon, with Android's lock screen swiped away over adb; the tablet is left unlocked |
| Enyo rebased onto the TouchPad's own tree (2026-10-05) | Two `adb install -r` of signed 32-bit release builds over Wi-Fi; `svc power stayon true` while Clock, Calculator, Memos, Web, Device Info, App Catalog, Help, Screen & Lock, Sounds & Alerts and Exhibition were opened with `palm-launch` and screenshotted on each build (Wi-Fi opened Android's own settings), then `svc power stayon false` | Every app drew the same on both bases; only the clock's hands and Device Info's live storage and battery readings differed |
| Encrypt build installed; Keyring SD installed and used (2026-10-05) | Over Wi-Fi: `adb install -r out/AndroidLuna-arm32-release.apk` (a debug build was refused for its signature first); `novacom run mkdir -p /media/internal/.developer` by hand, then `palm-install` of Keyring SD 0.0.6 from the package mirror and `palm-launch`; driven with `adb shell input`: master password `Lun4cyTest` set, an item "Bank" saved, the app closed and relaunched, a wrong password tried, then unlocked | Keyring SD stays installed with that password and one test item; `/media/internal/.developer` exists (made by hand on this build; later builds make it at start) |
| Busybox, PDK runtime and OpenAL builds installed; Fieldrunners played (2026-10-05) | Four `adb install -r` of signed 32-bit release builds and one of `out/LunaKeyboard-release.apk` over Wi-Fi; `novacom run` checks; `palm-launch` of Fieldrunners 1.1.0 (already installed) | Fieldrunners plays at 61 frames/s with sound; its saved game and scores are on the tablet |
| Mojo fixes installed (2026-10-05) | Two `adb install -r` of signed 32-bit release builds over Wi-Fi; Keyring SD unlocked and an item opened (the tablet had turned landscape, so the taps opened "Bank") | Nothing changed in Keyring |

The **CPU governor cannot be changed** on this device: there is no `su`, and
`/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor` is MediaTek's `hotplug` (idling at
598 MHz with cores offline). Loading the CPU from `adb shell` does bring all four cores up at
1.3 GHz, which is enough to rule the governor in or out of a measurement. `yes` is not on this
build; `dd if=/dev/urandom of=/dev/null` is.

## Driving the tablet over adb

- `adb shell input tap x y` takes **display** coordinates, and `adb exec-out screencap`
  gives the **framebuffer**. On a tablet held the other way up these differ by 180 degrees,
  so a tap read off a screenshot has to be turned round: `x' = width - x`, `y' = height - y`.
  A tap that lands on the wrong control can look like a control that doesn't work.
- `adb shell am start -n com.android.systemui/.Somnambulator` starts the current screen saver,
  which is how the Exhibition dream is tested without waiting for the device to go idle.
- `adb shell dumpsys battery unplug` does **nothing** on this MediaTek build - the dump still
  reads "USB powered: true" and no `ACTION_POWER_DISCONNECTED` is broadcast. `dumpsys battery
  set usb 0` does work, and is how coming off the charger is simulated. `dumpsys battery reset`
  afterwards.

## Logging

The MediaTek build fills the 256 KB log buffer with `BufferQueueProducer` frame messages
within seconds, and `adb logcat -G` isn't supported. Stream the log while testing instead:
`adb logcat -v brief Lunacy:V chromium:E AndroidRuntime:E '*:S'`.

## WebView version (optional, and under test)

Lollipop's WebView is a separate, updatable package (`com.google.android.webview`). The
factory copy is **Chromium 37**. Lunacy aims to work on every WebView Android 5 can run, and
[spike 1](spike-1.md) tests 37 and 64.

| Step | How | Notes |
|---|---|---|
| Update WebView to 64.0.3282.137 | `adb install -r webview-64.0.3282.137.apk` | Installed on the reference device on 2026-09-19. |
| Revert to the factory WebView 37 | `adb shell pm uninstall com.google.android.webview` (removes only the update) | Not yet tried on the reference device. |

About the WebView 64 APK:
- **Source:** the [archive.org copy](https://archive.org/details/com.google.android.webview_64.0.3282.137_2026-02-21).
- **Checks before installing:**
  - It needs Android 5 (`minSdk 21`) or later.
  - It includes `armeabi-v7a` code.
  - Its v1 (JAR) signature is valid, from Google's WebView key (`CN=webview, O=Google Inc.`).
  - Android accepted it as an update to the system WebView, which requires a matching signature.
- **SHA-256:** `a3878cbc5a71f729de5ebfb4efb3c5f89b1f43ba0b1515c7c1f1e4568dc92f63`.

**Newer WebViews.** The last WebView for Android 5 is about 95/96. It comes from Play, which
needs a Google account on the device. The download sites that host it block scripted
downloads, so it can only be fetched by hand in a browser. Not yet tested.

## Not needed

- **Root.**
- **A Google account.** Only needed for WebView updates from Play.
- **Google Play services.**

## Apps installed for testing (2026-09-21)

The Mojo suite, installed into Lunacy from packages (`--es install`), not into Android:
drPodder Redux 1.6.0, IAmA reddit 0.3.0, MeTube 2.3.0, Check Mate (1.2.6, then 1.3.0 as
`com.palm.codepoet.checkmate`), SimpleChat (1.8.5, then 1.9.2 as
`com.palm.app.codepoet.simplechat`) and Palm's Video Player 1.0.0. They live in the app's own
data, so `adb shell pm clear org.webosarchive.lunacy` removes them all.

Two Android settings were changed for screenshots and left that way:
`settings put system accelerometer_rotation 0` and `user_rotation 3` (upright landscape), and
`screen_off_timeout 1800000`. Put `accelerometer_rotation 1` back to let the tablet rotate
again.

The reference **TouchPad** has the same five apps installed (`palm-install`), plus the five
probe apps. drPodder there has taken its default feeds, as it would on any first run.

**2026-09-22.** `org.webosarchive.lunacy.mojoprobe` was installed into Lunacy on the tablet
(again `--es install`, so it goes with a `pm clear`), and `…lunacy.cssprobe` and
`…lunacy.mojoprobe` onto the reference TouchPad. Nothing else on either device changed: the
download manager's contract was measured into `/media/internal/lunacyprobe` on the TouchPad
and that folder was deleted afterwards. drPodder on the **tablet** has since fetched its
feeds' album art into `files/webos/media/internal/drPodder/.albumArt`, which is the download
manager working and goes with a `pm clear` like the rest. The CSS probe on the **TouchPad**
is 0.0.3, which adds two coloured swatches for the camera; nothing else on either device
changed. `…lunacy.emuprobe` was added to both on 2026-09-22 - it is the one probe with no
`uiRevision`, so it runs in the phone-sized card and records what such an app is told.

**2026-10-05.** mojoprobe 0.0.3 (Keyring SD's item rows) replaced 0.0.2 on the reference
TouchPad (`palm-install -d usb`); its `/etc/openal/alsoft.conf` was read with `novacom get`.
Nothing else on it changed.

**2026-09-22, the shell's B items.** On the **tablet**: `…lunacy.winprobe` updated to 0.0.7
(`card` and `dash` actions) and `…lunacy.slowprobe` reinstalled, both with `--es install`;
Lunacy's launcher preferences now hold a user-arranged APPS page (Notify Test before Clock)
and the installed apps moved to DOWNLOADS by the new placement rule; the brightness slider
was tried and `screen_brightness` put back to 191. `accelerometer_rotation` is **1** again
(the tablet rotates; `user_rotation` 0), which supersedes the note above - rotations for
screenshots were set with `user_rotation` and undone. Android's "Touch sounds"
(`sound_effects_enabled`) is 0 out of the box and was left alone. On the **TouchPad**:
`…lunacy.winprobe` 0.0.7, `…lunacy.headprobe` 0.0.1 (a `noWindow` probe) and
`org.webosarchive.lunacy.notifytest` 1.0.0 (Lunacy's bundled test app, packaged from
`AndroidLuna/src/main/assets/apps`) installed; the wallpaper was set to a 600 × 400 test image
and put back to `22.jpg`, and the test image deleted from the wallpaper store.

## Accounts and installs (2026-09-26)

On the **HP 10 G2**, inside Lunacy (all of it goes with a `pm clear`):

- Installed through Lunacy's package manager: the webOS Community Account Manager 1.1.15 (its
  postinst ran and patched Lunacy's copy of the palmprofile service), `…lunacy.jsprobe` and
  `…lunacy.scriptprobe`. Compass 1.0.1 was installed and removed again through
  `appInstallService`.
- Signed in to the test webOS Account in the account app. That registered the tablet as a
  device on that account (twice, with one more from a request made by hand from the
  workstation, all under Lunacy's nduid).
- Check Mate was logged out of codepoet's own list and signed itself in to the test account's
  (the account restore being tested). codepoet's own log-in is no longer on this tablet.

On the **reference TouchPad** (a dev unit, codepoet's word; it can be reset): `…lunacy.jsprobe`
and the three `busprobe` packages (`org.webosarchive.…`, `com.palm.…`, `com.webos.…`)
installed with `palm-install`; Compass installed through `appinstaller/installNoVerify` and
removed, and a failed `appInstallService` install of it left in LunaDownloadMgr's history
(`/var/palm/data/com.palm.appInstallService`); probe scripts under `/tmp`; a throwaway db8
kind created and deleted, and keymanager keys stored and removed, as
`org.webosarchive.lunacy.probe`.

Later the same day, for Help, Calculator, Memos and the fonts. On the **HP 10 G2**, inside
Lunacy: `…lunacy.fontprobe` 0.0.5 and 0.0.7 installed through `--es install` (from
`/sdcard/Download`, where the packages were left); one memo ("Hello from Lunacy") saved in
Memos; Help, Calculator and Memos run. On the **reference TouchPad**: `…lunacy.fontprobe`
0.0.1 to 0.0.6 installed with `palm-install`, and Calculator and Memos opened.

Later again, for App Catalog installs, launcher dragging and Sounds & Alerts. On the **HP 10
G2**: App Catalog 6.2.2926 reinstalled twice inside Lunacy through `--es install`, from
unreleased working copies (no pause button, the webOS install fix); `/data/local/tmp/touch.sh`,
a `sendevent` helper for injecting touches on `mtk-tpd` (`/dev/input/event3`), pushed; the
launcher's saved layout (`shared_prefs/launcher.xml`) edited by hand to undo the groups and
moves those tests made, back to the order before them; the system volume and System Sounds
changed from Sounds & Alerts and put back (Lunacy's `systemSounds` preference is now stored as
`true` where it was unset). For the group rename, a test group (Clock and Memos) was written into the saved layout and taken out again, leaving codepoet's own Group1 as it was. On the **reference TouchPad**, App Catalog's installed `main/source/archive-patch.js`,
`archive-install.js` and `direct-install.js` were replaced again with the working copies (every
install failure goes to Preware), and Preware was opened once on a package's page to check its
`{type: "view", id}` launch; its `ipkgservice` restarted six times while it loaded. Codepoet
then reinstalled Lunacy on the **HP 10 G2** from scratch, and the build that bundles App Catalog
in place of the App Museum (and leaves out the test apps) was installed over it. On the **reference TouchPad**: App Catalog's installed
`main/build.js`, `main/source/archive-install.js` and `main/source/direct-install.js` replaced
with the working copies (the originals of the first two are in `/tmp/*.bak`); the system volume
set to 71 and back to 72; `/tmp/audio-probe*.sh`, `/tmp/shot.sh` and a screenshot left in
`/tmp`; Sounds & Ringtones opened.

## Display density

The tablet is 213 dpi, which Android reports as a density of 1.33125. Chromium lays a card's
page out in density-independent pixels and multiplies back, so a 1280 px card comes out as
1281 CSS px drawn into 1280: the page is scaled by 0.99922, and every border-image join lands
a hundredth of a pixel short of a whole one. That is where the seams in Mojo's and Enyo's
frames come from ("border-image seams" in [fix-log.md](fix-log.md)).

```sh
adb shell wm density 160     # 1 css px = 1 device px; adb shell wm density reset to undo
```

At 160 the same widget's profile matches the reference TouchPad's to the level. **Lunacy
itself is unaffected either way** - the shell rounds its own density to a whole number and
decodes its artwork at 1:1, and LunaKeyboard decodes with `inScaled = false` - so the only
thing that changes is the size of Android's own UI, which gets smaller. On a tablet given
over to Lunacy that is arguably what you want; it is codepoet's call, and it is not set on
the reference device.

## Android 14 test tablet (2026-09-23)

Added to reproduce community bug reports. This is not a supported target yet.

**Device:** Samsung Galaxy Tab A7 Lite (SM-T227U), Android 14, arm64-v8a, about 2.7 GB of RAM,
800x1340 at 213 dpi, WebView 131.0.6778.135. Airplane mode is on.

| Change | How | Note |
|---|---|---|
| Install Lunacy | `adb install -r out/AndroidLuna-debug.apk` | Until 2026-09-23 Lunacy targeted API 21, and Android 14 refused it (`INSTALL_FAILED_DEPRECATED_SDK_VERSION`) without `--bypass-low-target-sdk-block`. It targets 24 now and installs plainly; on a fresh install Android asks for storage at first launch, and for "Modify system settings" the first time the brightness is changed. |
| Legacy permission review | Accepted the defaults (everything allowed) on first launch | Android shows this for every app that targets below 23; gone since Lunacy targets 24. |
| "Isn't compatible with the latest version of Android" | Dismissed with OK on first launch | Android shows two of these stacked. One opened Play Store, which was closed. |
| Play Protect off for adb installs | `adb shell settings put global verifier_verify_adb_installs 0` and `... package_verifier_enable 0` | It scanned and warned on every reinstall of the low-target APK. Undo by setting both back to 1. |
| Lunacy reinstalled from scratch; storage and "Modify system settings" allowed | `adb uninstall`, then `adb install`; the storage prompt at first launch, and the settings screen Lunacy opens from the brightness slider | Checks the target-24 permission flow a new user sees. |
| drPodder installed in Lunacy; Lunacy reinstalled several times over it | codepoet installed drPodder; `adb install -r` for each test build | For the Mojo tap report. An event logger was put into drPodder's page over DevTools while testing; it went with the next reinstall. |
| Screen size and density overridden while testing | `adb shell wm size 1200x1920` and `wm density 240`, then `wm size reset` and `wm density reset` | Stands in for a 1920 x 1200, 240 dpi tablet (shell scale 2). Reset afterwards; the tablet is back at 800 x 1340. |
| Stay awake on USB | `adb shell svc power stayon usb` | Keeps the screen on while it is driven over adb. Undo with `svc power stayon false`. |

## Nexus 7 (2012), Android 5.1.1 (2026-10-01)

Added as a second API 21-class device. It arrived on Android 4.3, which was assessed and declined
as a target ([roadmap.md](roadmap.md), 2026-10-01); codepoet flashed Google's 5.1.1 factory image
(LMY47V). Not a supported target yet beyond what the HP already covers.

**Device:** Asus Nexus 7 (grouper), Android 5.1.1 (API 22, build LMY47V), `armeabi-v7a`, Tegra 3,
1 GB of RAM, 1280 x 800 at 213 dpi, WebView 39 (1836172-arm).

| Change | How | Note |
|---|---|---|
| USB debugging accepted | The prompt on the device, "Always allow" | Over the VM's USB the device drops during any large push (`failed to read copy response`), as the Nexus 5 did, so USB is used only to switch adb to TCP. |
| adb over Wi-Fi | `adb tcpip 5555` over the cable, then `adb connect 192.168.10.198:5555` | Android 5 has no wireless-debugging pairing; `tcpip` is reset by a reboot and must be issued over USB again. |
| Install Lunacy and the keyboard | `adb -s 192.168.10.198:5555 install -r out/AndroidLuna-arm32-debug.apk`, and the keyboard APK | The 32-bit flavour, target 24. |

For the Web app's look (2026-10-04). On the **reference TouchPad**: its Web app opened on
Wikipedia's webOS page over the bus (the card left open), and `…lunacy.enyoprobe` 0.0.1 installed
with `palm-install` and run. On the **HP 10 G2**, inside Lunacy: the same probe installed through
`--es install` from the workstation (`python3 -m http.server`, stopped afterwards) and run.

For text selection (2026-10-04). On the **reference TouchPad**, by codepoet's hand: a memo
written in Memos ("Jon was here this is complicated but I get it") and its words held, selected,
copied and pasted; the Web app opened on blog.jonandnic.com and page text held; Preware or IAmA
reddit tried. Screenshots under `Workbench/results/tp-sel-*`.

## Pixel Tablet, Android 17 (2026-10-01)

Added as the first 64-bit-only and first Android 17 device. Not a supported target yet.

**Device:** Google Pixel Tablet (tangorpro), Android 17 (API 37, build CP3A.260905.009), `arm64-v8a`
only, Tensor G2, 7.6 GB of RAM, 2560 x 1600 at 320 dpi (shell scale 2), 4 KB memory pages,
WebView 149.0.7827.5 (153.0.8010.36 by 2026-10-02). The memory limiter reports itself disabled on this build.

| Change | How | Note |
|---|---|---|
| Wireless debugging | Developer options → Wireless debugging; paired once with `adb pair <ip>:<pairing port> <code>`, then `adb connect <ip>:<port>` | Over USB the tablet enumerates and drops within a second on the VM's emulated USB 2.0 controller, so USB is not used. The connect port changes after a reboot; pairing holds. |
| Install Lunacy | `adb install -r out/AndroidLuna-arm64-debug.apk` | The 32-bit APK is refused first (`INSTALL_FAILED_NO_MATCHING_ABIS`), as expected on an arm64-only SoC. The 64-bit one installs plainly. Installed three times the same day: the split build at target 24, the one with the relinked launcher, and the `arm64` flavour at target 28. The last of these computed the device's derived id afresh with `Build.SERIAL` reading "unknown" (and stored it, which later installs keep), so this tablet's `nduid` is not the one the target-24 build reported earlier that day. No account was signed in. |
| Install the keyboard | `adb install -r out/LunaKeyboard-debug.apk` | |
| "Android App Compatibility" dialog | OK, each launch, until the 18:06 build | Android 17 shows it for a *debuggable* app whose native libraries aren't 16 KB-aligned, listing them: `libnode.so`, `libc++_shared.so`, and "Unknown error" for the two static executables (busybox, the launcher). The device itself has 4 KB pages. With libc++ and the launcher from NDK r28 (17:52 build) only `libnode.so` was still named, the rest "Unknown error" - which is how a passing library is listed beside a failing one. With `libnode.so` rebuilt 16 KB-aligned (18:06 build) the dialog is gone: no `AppWarnings` line at launch, Node 12.19.0 runs. |
| "Built for an older version of Android" dialog | OK, once | Android 17's warning threshold is target 28 (`ro.build.version.min_supported_target_sdk`); the install floor is still 24. Shown on the first launch after an install only, and gone with the `arm64` flavour at target 28. |
| Storage allowed | The permission prompt at first launch, accepted by codepoet | |
| Hello (`com.davidvogt.hello` 1.1.0) installed in Lunacy | `--es install http://localhost:8123/…` from the package mirror over `adb reverse`, the mirror served with `python3 -m http.server`; the server was stopped and the reverse removed afterwards | Proves plain http still works with the `arm64` flavour at target 28 (the network security config), and the install path on 64-bit. |
| Screen kept on | `adb shell svc power stayon true`, and `wm dismiss-keyguard` once after the screen had locked | Keeps the screen on while charging, so screenshots over Wi-Fi don't hit the lock screen. Undo with `svc power stayon false`. |
| "Modify system settings" allowed for Lunacy | Lunacy's First Use opened Android's screen for it; the toggle was turned on there | Testing First Use. The Screen & Lock brightness now sets Android's. |
| Storage revoked and re-granted, First Use reset | `pm revoke` of both storage permissions, the `firstUseDone` key deleted from `shared_prefs/device.xml` with `run-as`, then `pm grant` of both after Android had refused twice | Walking First Use's refusal paths. Android 17 makes a second refusal final (`USER_FIXED`), which is what the page's "Open Android Settings" button is for. First Use was then finished normally, so the flag is set. |
| Lunacy uninstalled and installed afresh, twice | `adb uninstall org.webosarchive.lunacy`, then `adb install out/AndroidLuna-arm64-debug.apk` | For codepoet to go through First Use by hand on a clean install; again after the Start Over and auto-prompt changes. Each uninstall took the installed apps, the derived device id and both grants with it; the keyboard stayed. In between, First Use was driven over DevTools (`Workbench/cdp.sh`, `adb forward` removed afterwards) to find why the Start Over link took no taps. |
| 64-bit build with the PDK runtime and emulator installed, several times (2026-10-02, afternoon) | `adb install -r out/AndroidLuna-arm64-debug.apk` over Wi-Fi; Keen's package and a throwaway probe package installed over the LAN with `--es install`; `--es sh` and `--es rawsh` dev hooks run | Keen's postinst died with exit 159 (SIGSYS) under Lunacy's runner but not under run-as: Alpine's busybox calls `setuid`/`setgid` at every applet start and Android's app seccomp blocklist kills for them (found with `qemu-aarch64 -strace` against bionic's SECCOMP_BLOCKLIST_APP). busybox is now built by `tools/build-busybox.sh` without that feature ([pdk.md](pdk.md)). The probe package was removed from the mirror and never installed. |
| Keen emulated (2026-10-02, afternoon) | the 64-bit build with Termux's qemu and the rebuilt busybox; Keen installed from the mirror and launched | Plays in a card. Probe files from the search (`files/pdk/qt`, `t*.sh`, `hello*`, `keen.bin`) and `/data/local/tmp/{pdk,qt,t*.sh,hello*,keen-postinst*}` removed afterwards. |
| First Use reset once more, home-app dialog cancelled | the `firstUseDone` key deleted again with `run-as`; Android's "Set Lunacy as your default home app?" dialog, opened from First Use's Home Screen page, was cancelled | Testing the Home Screen page. The Pixel Launcher stays the home app. |
| adb over Wi-Fi without pairing (2026-10-02) | `adb tcpip 5555` over the cable, then `adb connect 192.168.10.153:5555` | As on the Nexus 7: the cable only has to hold for the `tcpip` switch. Reset by a reboot. |
| Apollo 1.2.8 launched over adb (2026-10-02) | `am start -n org.webosarchive.lunacy/.shell.ShellActivity --es launch com.jmtk.apollo` | It sat on its splash: WebView 153 has no WebSQL (`openDatabase is not defined`), which is what the polyfill in [architecture.md](architecture.md) "WebSQL" is for. |
| Lunacy with the WebSQL polyfill installed (2026-10-02) | `adb install -r out/AndroidLuna-arm64-debug.apk` over Wi-Fi, then a force-stop and restart | Apollo now reaches its sign-in page and its settings survive a restart. The contract probe (arity, the two-argument open, `no such table: nosuchtable`, rollback, `changeVersion`) was run inside Apollo's card over DevTools; its two probe databases were deleted from `files/websql/com.jmtk.apollo/` afterwards. |
| Hello installed again, Quick Tip Calculator installed | `--es install` from the package mirror, as above | Hello (an Ares app) for the Mojo initialisation error; Quick Tip Calculator 1.0.1 as a Mojo app whose page carries the tag itself, to check the normal path after the mojo.js prelude. |
| 64-bit Node and busybox checked | `run-as org.webosarchive.lunacy`, running `liblunacynode.so -e …` (Node 12.19.0, arm64) and busybox through a temporary symlink named `busybox` in the app's data folder, removed afterwards | The webOS root had already linked `/bin/sh` and `/bin/busybox` to the arm64 busybox. |
| Android's wallpaper replaced by Lunacy's (2026-10-04) | the 64-bit build with `HostWallpaper` installed over Wi-Fi; Screen & Lock's "Use on Android" tapped on, the webOS wallpaper changed to 05.jpg and back to 22.jpg over DevTools (`Workbench/cdp.sh`), then the switch turned off | Android 17 took both home and lock (`which = 3`), and the lock screen showed 22.jpg. The Pixel's own wallpaper (a GradientColorWallpaper on the lock screen) is gone; 22.jpg stays on both until it is changed in Android's settings. The switch is off (`shared_prefs/hostwallpaper.xml`). |
| Lunacy with the Web app installed, several times (2026-10-04) | `adb install -r` of the 64-bit build over Wi-Fi; the Web app driven with taps and over DevTools (`Workbench/cdp.sh`, forwards removed afterwards) | Wikipedia, DuckDuckGo (whose bot check codepoet answered on the tablet), example.com and a `target=_blank` test link; "Copy To Photos" saved a Wikipedia image into Lunacy's `/media/internal`, deleted afterwards. One bookmark was added and is still there (Linux kernel - Wikipedia) |
| Clipboard and Calculator 3.2.0 builds installed; a phone's screen stood in (2026-10-04) | `adb install -r` of the 64-bit build, several times; SimpleChat and PWA Installer driven over DevTools for Edit-menu copy and paste (SimpleChat's compose field cleared each time, nothing sent; one message's Copy/Like popup opened by a stray tap and closed with Back); `wm size 1080x1920` and `wm density 480` with `accelerometer_rotation 0` and `user_rotation` 0 and 1 for the phone layout, then `wm size reset`, `wm density reset`, `user_rotation 0`, `accelerometer_rotation 1` | Put back as it was. Android's clipboard holds test text from these runs |
| Debug builds replaced by the signed 0.5.5 release (2026-10-04) | `adb uninstall` of Lunacy and the keyboard (taking Lunacy's data with them: SimpleChat, MeTube, the other installed apps, the bookmark), `adb install out/AndroidLuna-arm64-release.apk` and the keyboard, `ime set`; First Use run: storage allowed, "Modify system settings" granted with `appops set org.webosarchive.lunacy WRITE_SETTINGS allow`, Home Screen skipped; Calculator and Web opened | Signed with codepoet's key from now on. The Pixel Launcher stays the home app. Build 151 installed over it the same day |
| Encrypt and `.developer` build installed (2026-10-05) | `adb install -r out/AndroidLuna-arm64-release.apk` over Wi-Fi, the screen woken, Lunacy opened; `palm-install` of Keyring SD 0.0.6 tried twice | Not installed: the first try failed with `file open failed` (no `.developer` folder, on the earlier build); on the new build the package went across, but every novacom `run` (`mkdir`, `ls`, `cat` and the installer's `luna-send`) ends in "unexpected EOF" on this tablet, so `palm-install` gave "no response". Its `rm` of the package failed the same way, so `/media/internal/.developer/ru.shura0.keyringsd_0.0.6_all.ipk` is probably still there |
| novacom `run` diagnosed and fixed (2026-10-05) | Four `adb install -r` of signed 64-bit release builds over Wi-Fi (three with temporary logging in `Novacom.kt`, the last with the bionic busybox restored); `novacom run` of `echo` and `ls`; `palm-install` and `palm-launch` of Keyring SD 0.0.6; `adb shell input text` and two key events sent while the screen turned out to be off | Every `run` had died of SIGSYS (exit 159): the APK carried the prebuilt busyboxes again. Keyring SD is installed (not yet opened past launch); the keystrokes may have reached Android's lock screen |
| OpenAL builds installed; Fieldrunners installed and played (2026-10-05) | Three `adb install -r` of signed 64-bit release builds over Wi-Fi; `palm-install` of Fieldrunners 1.1.0 (codepoet's build) and `palm-launch`; the lock screen swiped away over adb, `svc power stayon true` while testing and `false` after; PLAY and Grasslands tapped | Fieldrunners is installed, with a game started on Grasslands |
| Keyring's focus and field height (2026-10-05) | Three `adb install -r` of signed 64-bit release builds over Wi-Fi; Keyring SD driven over adb and DevTools (`Workbench/cdp.sh`: listeners, focus loggers and `blur`/`focus` wrappers in the live page, all gone with the page) with codepoet's master password; test items started and cancelled; `palm-install` of mojoprobe 0.0.3 tried and refused while the shell was looping; the lock screen swiped away, `svc power stayon true` while testing, `false` after | Keyring's data unchanged: no test item saved |
| Launch-point builds installed (2026-10-05) | `adb install -r out/AndroidLuna-arm64-release.apk` twice over Wi-Fi (signed, over the 0.5.5 release); the Web app's Share → Add to Launcher used on example.com, its dialog opened twice more | An "Example Domain" shortcut is left on the launcher's first tab (codepoet's earlier one, made on the 0.5.5 build, never saved) |

## Nexus 5 test phone (2026-09-23)

Added to reproduce a community report. Since 2026-10-02 the phone layout's test device
([phone.md](phone.md)).

**Device:** LG Nexus 5 (hammerhead), Android 6.0.1 (API 23), armeabi-v7a, 1080 x 1920 at
480 dpi (shell scale 3), WebView 44.0.2403.117.

| Change | How | Note |
|---|---|---|
| Install Lunacy | `adb install -r --no-streaming out/AndroidLuna-debug.apk` | A streamed install of the 74 MB APK failed with `failed to read copy response` and left the phone `offline` to adb until it was replugged. `--no-streaming` pushes the file first and installs it on the phone. An older Lunacy build that was already on it was removed first. |
| Apollo for the Pre3 installed in Lunacy | `--es install` from the package mirror | For the emulated-card report. |
| Stay awake on USB | `adb shell svc power stayon usb` | Undo with `svc power stayon false`. |
| adb over Wi-Fi (2026-10-01) | `adb tcpip 5555` over the cable, then `adb connect 192.168.10.195:5555` | The VM's USB drops the phone during any large push, as on 2026-09-23; `tcpip` is a few bytes and goes through. Reset by a reboot. |
| Lunacy 32-bit flavour and the keyboard installed (2026-10-01) | `adb -s 192.168.10.195:5555 install -r out/AndroidLuna-arm32-debug.apk`, and the keyboard APK | Over Wi-Fi. First Use came up in portrait; it was finished over DevTools (`FirstUse.finish()`) rather than tapped through, and its flag deleted and set again while testing the portrait layout. |

| adb over Wi-Fi again (2026-10-02) | `adb kill-server` first (the phone didn't list over USB until then), `adb tcpip 5555`, `adb connect 192.168.10.195:5555` | The USB entry lingered beside the Wi-Fi one until unplugged. |
| Phone-layout builds installed (2026-10-02) | `adb -s 192.168.10.195:5555 install -r out/AndroidLuna-arm32-debug.apk`, three times through the day | The launcher, dock and app-scale screenshots in the session; opened over adb with `--ez launcher true`. |
| Fixed-viewport builds installed, Memos' switch flipped off and back (2026-10-02, later) | two more `adb install -r` of the 32-bit APK; `system/setFixedViewport` for `com.palm.app.notes` called from Device Info's page over DevTools, with a force-stop and restart between | Device Info's Software group, and Memos at the card's own width with the switch off. The switch is back on its default; `shared_prefs/viewport.xml` is empty. |
| PDK build installed, Commander Keen installed and run (2026-10-02, evening) | the 32-bit APK with the PDK runtime; Keen 1.6.0 from the package mirror served over the LAN (`--es install http://192.168.10.45:8123/…`; `adb reverse` wouldn't bind on the Wi-Fi connection); earlier a copy of the runtime and the game under `/data/local/tmp/pdk` for the first headless run, removed after | [pdk.md](pdk.md). Keen's postinst writes a udev rule; the webOS root gained `/etc/udev/rules.d` for it. |
| 32-bit build with SDL_mixer and the rebuilt busybox installed (2026-10-02, afternoon) | `adb install -r` of a clean build | Keen still runs natively; its mixer now a real SDL_mixer with Ogg Vorbis. |
| App layout width set to 0 and back to 640 | `system/setLayout` on Lunacy's service, called from Device Info's page over DevTools, with a force-stop and restart each time | To compare Memos with the app scale off. The setting is 640 again. |
| Debug builds replaced by the signed 0.5.5 release (2026-10-04) | `adb tcpip 5555` over the cable (by codepoet) and `adb connect 192.168.10.195:5555`; `adb uninstall` of Lunacy and the keyboard (taking Lunacy's data: Apollo, Keen, the other installed apps), `adb install --no-streaming out/AndroidLuna-arm32-release.apk` and the keyboard, `ime enable`/`ime set`; First Use run: storage allowed, "Modify system settings" granted with `appops set org.webosarchive.lunacy WRITE_SETTINGS allow`, Home Screen skipped; Calculator and Web opened | Signed with codepoet's key from now on. Seen on the way: First Use's Home Screen page pushes Skip off the right edge of a 360 px screen, and the Web app's address bar has no room for the address in a phone's card (both fixed in build 151, installed over it the same day; First Use was opened again to check its Home Screen page, and App Catalog's offer of its own update answered Later) |
| Wi-Fi adb, OpenAL builds, Fieldrunners (2026-10-05) | `adb tcpip 5555` over the cable, `adb connect 192.168.10.195:5555`; two `adb install -r` of signed 32-bit release builds over Wi-Fi; `palm-install` of Fieldrunners 1.1.0 and `palm-launch`; PLAY and a map tapped over adb | Fieldrunners is installed, with a game started on Grasslands. adb listens on 5555 until the next reboot |
| Where's My Water, libEGL and the GL state fix (2026-10-05) | The HP 10 G2 switched to Wi-Fi adb (`adb tcpip 5555`, `adb connect 192.168.10.34:5555`); signed release builds (0.6.0 with the PDK changes) installed with `adb install -r` on the HP 10 G2, Nexus 5 and Pixel Tablet, three rounds on the first two; `files/pdk/env` written over the SDK's novacom relay (`APKENV_GL_PROBE`, `APKENV_GL_UPLOADCHECK`, then `LUNACY_GL_TRACE`) and removed, `files/pdk/gldump` touched once; Where's My Water launched with `--es launch` | The game was already installed on all three. apkenv's log is `/media/internal/apkenv-wmw.log` in the webOS root, and its extracted libraries are under `/media/internal/.apkenv/`; both stay. On the TouchPad, `/tmp/eglprobe1`, `/tmp/eglprobe2` and `/media/internal/eglprobe.log` were left by the EGL probe, and `/tmp/swapprobe`, `/tmp/audioprobe` and their logs by the later two. Later the same evening, on the Nexus 5 and Pixel Tablet, builds with temporary audio diagnostics in the shell (since removed) recorded `files/pdk/audio.raw` on a trigger file put over novacom; both files were deleted afterwards. Where's My Water 1.0.3 (codepoet's rebuild) replaced 1.0.2 on the Nexus 5 and Pixel Tablet: `adb push` to `/sdcard/Download`, `cp` into `/media/internal/.developer` over novacom, `appinstaller/installNoVerify` with `luna-send` (a `palm-install` of the 24 MB package was reset by the relay twice, both on the Nexus 5); the pushed copies in `/sdcard/Download` were deleted, the ones in `.developer` stay, as `palm-install` leaves them. The final clean builds (no diagnostics) are on both. |

When the phone doesn't show in `adb devices` although it is on USB with debugging on, restart
the adb server (`adb kill-server`).


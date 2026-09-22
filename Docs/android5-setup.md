# Android 5 device setup

Everything that has been done to an Android 5 device to make Lunacy work, or to test it.
Keep this list current: it becomes the install guide for users, and the checklist for each
new test device.

**Reference device:** HP 10 G2 Tablet: Android 5.0.1 (build LRX21M), MT8127, 1 GB of RAM,
not rooted, no Google account.

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

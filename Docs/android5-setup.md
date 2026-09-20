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

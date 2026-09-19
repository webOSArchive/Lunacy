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
| `org.webosarchive.lunacy` | Lunacy | Built from `android/`. Run `android/fetch-assets.sh` first to populate local assets. |
| `org.webosarchive.lunacy.spike` | Spike 1 test app | Can be removed. |

## Settings changed during testing

| Setting | Why | State left |
|---|---|---|
| `accelerometer_rotation` / `user_rotation` | Fixed orientations for screenshot comparisons | Auto-rotate back on (`accelerometer_rotation 1`) |
| `dumpsys battery set …` | Testing the status bar battery icon | `dumpsys battery reset` |

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

# Building Lunacy

Two APKs come out of one Gradle build: **AndroidLuna**, the shell, and **LunaKeyboard**, the
optional companion keyboard. Neither depends on the other.

## What you need

- **JDK 17** and the **Android SDK** (`compileSdk 35`). Point Gradle at the SDK with a
  `local.properties` at the top of the repo: `sdk.dir=/path/to/Android/Sdk`.
- **Android NDK r21e**, only for `fetch-assets.sh`: it compiles the small launcher that lets
  Node run as a library for JS services. Skip it and everything but JS services still builds.
- **Python 3 with Pillow**, for the tools that generate assets and compare screenshots.
- A device or emulator running **Android 5.0 (API 21)** or later.

## Layout

| | |
|---|---|
| `AndroidLuna/` | The shell: Kotlin and Android Views, `minSdk 21`. Its `src/main/assets/` carries the bundled apps, the Luna artwork and the injected page scripts. |
| `LunaKeyboard/` | The keyboard, an `InputMethodService`. Self-contained; see [its README](LunaKeyboard/README.md). |
| `LunaRuntimes/` | Lunacy's changes to the frameworks apps load from the OS - `enyo-1.0/` and `mojo/` - as patch series with a CHANGES.md against upstream. Not compiled: `fetch-assets.sh` applies them to a stock tree. |
| `Docs/` | Architecture, roadmap, the shell reference, and the device notes. |
| `Workbench/` | Probes, device helper scripts and local clones. Not part of either APK. |
| `Meta/` | Artwork, including the launcher icon the mipmaps are made from. |
| `out/` | Where the built APKs land. Not committed. |

## Build

```sh
AndroidLuna/fetch-assets.sh      # once, and again whenever the vendor clones change
./gradlew assembleDebug          # both APKs
./gradlew lintDebug              # NewApi is fatal here: minSdk 21 has to hold
```

Both APKs land in **`out/`** at the top of the repo, named after their module and variant:
`out/AndroidLuna-debug.apk` and `out/LunaKeyboard-debug.apk`. (Gradle also leaves its own
copies under each module's `build/outputs/apk/`; `out/` is the one to reach for.) Build one
alone with `./gradlew :AndroidLuna:assembleDebug` or `./gradlew :LunaKeyboard:assembleDebug`,
and `./gradlew clean` empties `out/` along with the modules' build folders.

### What `fetch-assets.sh` does

It fills `AndroidLuna/local-assets/`, which is **not committed**: a stock Enyo 1.0 tree with
`LunaRuntimes/enyo-1.0/patches/` applied, Palm's Mojo from a reference TouchPad with its own
patches, third-party test apps, and Node for JS services. Glimpse is the exception: it goes to
`AndroidLuna/local-test-apps/`, also not committed, and is in the APK only when the build asks
for it:

```sh
./gradlew assembleDebug              # what users get: no Glimpse
./gradlew assembleDebug -PtestApps   # a development build, with it
```

A release is always built without `-PtestApps`. A patch that no longer applies is a
build failure, not a silent skip. It needs the local clones under `Workbench/vendor/`, so a fresh
checkout builds the shell but starts without Enyo until those are in place.

## Install and run

```sh
adb install -r out/AndroidLuna-debug.apk
adb install -r out/LunaKeyboard-debug.apk
adb shell am start -n org.webosarchive.lunacy/.shell.ShellActivity \
    --es launch <appid> [--es params '<json>']
```

Cards are inspectable from a desktop Chromium at `chrome://inspect` while the device is on
USB; development builds turn WebView debugging on. `Workbench/cdp.sh` drives the same protocol
from the command line.

## The icon

The launcher mipmaps are made from `Meta/Lunacy-512.png`:

```sh
python3 -c 'from PIL import Image; src=Image.open("Meta/Lunacy-512.png").convert("RGBA")
for d,s in [("mdpi",48),("hdpi",72),("xhdpi",96),("xxhdpi",144),("xxxhdpi",192)]:
    src.resize((s,s), Image.LANCZOS).save("AndroidLuna/src/main/res/mipmap-%s/ic_launcher.png" % d)'
```

## Before changing anything

Read [CLAUDE.md](CLAUDE.md) for the rules the project holds itself to - chiefly that no app is
ever patched, that unimplemented services return real errors rather than fake success, and that
every compatibility fix is logged in [Docs/fix-log.md](Docs/fix-log.md) with the layer it landed
in. [Docs/dev-workflow.md](Docs/dev-workflow.md) covers the devices, the reference TouchPad and
how visual changes are checked.

## Version and build number

The version name (`versionName` in `AndroidLuna/build.gradle.kts`) is changed by hand, when
codepoet decides. The build number (`versionCode`) counts itself: it is the number of commits
behind the build, so every commit is a new build. Device Info shows both. A build from a tree with uncommitted changes carries the last
commit's number.

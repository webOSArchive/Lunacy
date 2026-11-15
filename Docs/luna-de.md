# Luna Desktop Edition (Luna DE): plan

Status: **proposal, for review** (2026-11-15). Branch `luna-de`. Nothing here is built yet.

## Goals

1. **A developer tool for testing webOS apps without a real device.** A webOS app developer
   on Windows, macOS or Linux runs Luna DE, installs their app, and sees it run as it would on
   a TouchPad (other devices later). The old SDK's emulator, a slow VirtualBox image of a webOS that is now
   years out of date, is the thing it replaces.
2. **Enyo apps first.** The first useful release runs Enyo 1 apps well. Mojo apps follow,
   because the same layers serve them. PDK (native) apps are a "maybe, later".
3. **The SDK's command-line tools work against it.** `palm-install`, `palm-launch`,
   `palm-log`, `novacom` and `novaterm` treat Luna DE as a device, as they already do for
   Lunacy on Android.
4. **One code path.** Luna DE runs Lunacy's own code, not a copy of it. A fix to the bus, db8,
   the serve-time transforms, the compat layer or the frameworks reaches Android and the
   desktop in the same commit. Nothing is written twice.
5. **Distributed with the SDK.** webos-sdk-redux ships Luna DE, as it ships the frameworks
   Lunacy patches: it fetches a tagged Luna DE build. No Lunacy code is kept in that repo.

### Not goals

- Running Android apps, or launching the host's own apps. There are plenty of ways to do both.
- Being an end-user product. Luna DE is for developers; Lunacy on Android stays the product.
- Android integrations: contacts and calendar accounts, the Android media library,
  notifications, Wi-Fi and Bluetooth, hand-offs to Android apps. On the desktop those
  services answer with real errors (rule 2), never a stubbed success.
- PDK, for now (see goal 2).
- The full card view and launcher at first: an app runs in a window (see "Rule 0").

## Where the code lives

In this repo, beside the existing hosts, so that one commit builds every platform and
`test.sh` covers them all:

```
Lunacy/
  LunaCore/      NEW. Platform-neutral Kotlin/JVM: bus, db8, app server and transforms,
                 app lifecycle, packages, novacom, core services
  AndroidLuna/   Android host: shell, card view, Android services. Depends on LunaCore
  DesktopLuna/   NEW. Electron window plus a bundled JVM running LunaCore
  iOSLuna/       Unchanged: the single-app iOS toolkit
  CoreApps/, LunaRuntimes/   Shared, as today
```

A separate repo would drift by design. Putting Luna DE in webos-sdk-redux would mean the SDK
copying Lunacy into itself. webos-sdk-redux gets a fetch and packaging script, like
`update-frameworks.sh`, and the launcher entry points.

## Why a shared Kotlin core, not a JavaScript rewrite

Lunacy has two halves:

- **The JavaScript half** already runs anywhere: the compat layer in `assets/lunacy/`, the
  patched Enyo and Mojo, `CoreApps/`, and the JS services (Node). iOSLuna reuses this half
  unedited.
- **The native half** is about 33,700 lines of Kotlin in 136 files: the bus, db8,
  ActivityManager, AppServer and its transforms, package install, novacom and every service.
  121 of the 136 files import Android.

Rewriting the native half in JavaScript for Electron (the iOSLuna approach) creates exactly
the second code path that goal 4 rules out. Running the real APK in a bundled Android emulator
keeps one code path, but has these costs:

- Google's system images can't be redistributed.
- It needs hardware virtualisation and a download of several GB.
- It needs an x86_64 build of Lunacy.
- It risks repeating the old emulator's experience.

Most of the Android dependence is shallow, though. The commonest imports are `Context` (52
files), `Log` (47), `Handler`/`Looper` (25/21), `Intent`, `Build` and `Uri`. db8 uses
Android's SQLite. AppServer uses `AssetManager`. The bus itself imports only `Log`, plus
`org.json`. Kotlin runs on any JVM, so the core can move into a plain Kotlin/JVM module behind
a few small interfaces.

## Architecture

### LunaCore (shared)

A Gradle `kotlin("jvm")` module that AndroidLuna depends on. It has no Android imports; what
it needs from a platform comes through interfaces, each with an Android and a desktop
implementation:

| Interface | Android | Desktop |
|---|---|---|
| `Logger` | `android.util.Log` | stdout/file, plus `/var/log/messages` as today |
| `Scheduler` (post, postDelayed, a main thread) | `Handler`/`Looper` | a single-thread executor |
| `Storage` (data dir, webOS root, caches) | `Context` dirs | a per-user app-data dir |
| `Assets` (bundled files) | `AssetManager` | the packaged resources folder |
| `Sql` (db8 and WebSQL) | `android.database.sqlite` | SQLite over JDBC (xerial) |
| `Base64`, device info, locale | Android APIs | `java.util` and the JVM |

What moves into LunaCore, which is the part a webOS web app needs:

- **The bus:** `Bus`, private and public buses, caller identity from the origin (rule 10).
- **Serving:** `AppServer` and its serve-time transforms, the frameworks, `/__lunacy/` routes,
  `NetShim`/`Http`, `WebSql`.
- **db8.**
- **App lifecycle:** `ActivityManager`, `ActivityRequirements`, `AppRegistry`, `LaunchPoints`.
- **Install:** `Packages`, `AppInstallService`, `Configurator`, postinst scripts.
- **SDK tooling:** `Novacom` (the protocol and commands; the listener is the platform's),
  `SysLog`.
- **Core services:** `SystemService` (time, preferences), `DisplayService`, `Power`, and the
  JS service host's process management (`JsServices`, `ServiceProcess`).

Each class moves as it is. Where a class mixes core logic and Android glue, it is split along
that line, and the Android half stays in AndroidLuna.

### DesktopLuna (new)

- **Electron main process:** starts the JVM running LunaCore, keeps it alive, opens windows.
  The JRE is bundled with `jlink`, so developers install nothing else.
- **A card is a `BrowserWindow`.** Apps keep their real origins
  (`https://<id>.media.cryptofs.apps/...`, `AppServer.appUrl`). Electron's session protocol
  handler routes every request on those origins to LunaCore's AppServer, so the transforms and
  caller identity work as on Android.
- **The `window.LunacyNative` contract** (`AppWindow.kt`'s `Native`) gets an Electron
  implementation: a preload script exposes the same methods, carried to LunaCore over a local
  WebSocket or pipe. Several methods are synchronous (`netSendSync`, `sqlOpen`, `deviceInfo`,
  `screenSize` and others); Electron's `ipcRenderer.sendSync` covers those, through the main
  process.
- **The window is a TouchPad's screen:** 1024×768 landscape or 768×1024 portrait, switched
  by rotating, and the page renders at that width (rule 3). Other devices (Pre3, Veer, Pre,
  Pixi) come later as a device picker using `DeviceProfile`'s values (codepoet, 2026-11-15:
  "initial window sizes will be 1024x768 and 768x1024 to match touchpad").
- **DevTools** open in the window. That is the big improvement over the old emulator, and
  over `chrome://inspect` on a tablet.
- **JS services** run under a Node the same major version as nodejs-mobile's (Node 12), so
  their behaviour matches Android's. Electron's own Node is newer, so it's not a match.
- **Packaging:** electron-builder for Windows (installer and zip), macOS (dmg, signed and
  notarised when there's a key) and Linux (AppImage and tar).

### The SDK's tools

Lunacy on Android serves novacom's device protocol on an abstract socket, and the SDK's
novacomd reaches it through the adb server (webos-sdk-redux `novacomd/src/host/adb_relay.c`).
On the desktop there is no adb. Two ways:

- **A (recommended):** LunaCore's novacom listens on a fixed localhost TCP port. novacomd gains
  a small `local` transport, which probes that port the way `adb_relay.c` probes adb and
  splices each connection through unchanged. Luna DE is then listed in `novacom -l` like any
  device, `-d lunacy` still picks it, and the protocol stays the same.
- **B:** reuse novacomd's existing TCP transport (`novacom-tcp`). It speaks the device
  daemon's framing and nduid handshake, which Lunacy's novacom doesn't implement today, so
  this means more new protocol code in Lunacy.

`run file://` commands (postinsts, `novacom run`, `novaterm`) run in Lunacy's webOS root with
busybox on Android. On the desktop, Linux and macOS can use a host busybox or the system shell
with webOS paths mapped; Windows needs a bundled busybox-w32 or an equivalent. This is an
open question.

## Rule 0

codepoet, 2026-11-15: Luna DE can have rule 0 exemptions as needed, including First Use.

What that means in practice:

- **What an app draws stays rule 0.** The card's contents come from the same frameworks,
  compat layer and transforms as on Android, so an app looks as it does on a TouchPad.
  That fidelity is what makes it a useful test bench; the shared code gives it for free.
- **Luna DE's own interface is its own:** the window chrome, rotation, the menus,
  any launcher, the absence of a card view, First Use (skipped, or replaced by a developer's
  setup).
- **Engine differences:** Electron's Chromium is much newer than Chromium 37. Lunacy already
  runs on current WebViews (Android 17 on the Pixel Tablet), so this is not new ground.
  Anything that renders differently is recorded in `Docs/luna-deltas.md`, as now.

## Milestones

Each step is a pure move or an addition. After each one, `test.sh` passes and
`Workbench/device-test.sh` passes on the Fold3, the Pixel Tablet and the Nexus 7, so Lunacy on
Android can't regress.

1. **LunaCore module, empty.** The Gradle module, the platform interfaces, and their Android
   implementations; AndroidLuna depends on it. No behaviour changes.
2. **Move the bus,** then **db8** (SQL behind `Sql`), then **AppServer and the transforms**,
   one per commit series, each followed by the full test round.
3. **First light:** a minimal DesktopLuna serves one bundled Enyo app (Calculator, then Clock)
   into an Electron window, with the `LunacyNative` contract over IPC, on Linux.
4. **Lifecycle and services:** ActivityManager, SystemService and the JS service host move.
   Bundled Enyo CoreApps run, and an app can launch another.
5. **The SDK's tools:** Packages and Novacom move; the novacomd `local` transport lands in
   webos-sdk-redux. `palm-install`, `palm-launch` and `palm-log -d lunacy` work against Luna
   DE. **This is the first release worth giving developers.**
6. **Packaging** for Windows, macOS and Linux, and the fetch script and launcher in
   webos-sdk-redux.
7. **Mojo apps,** then whatever developers ask for. PDK only if it's decided later.

## Risks

- **Refactoring 1.0's code.** The moves touch the most-tested parts of a stable release. The
  mitigations: pure moves, small commits, the full device round after each step, and the
  branch until codepoet merges it.
- **`org.json` differences.** Android's `org.json` and the Maven artifact differ in small
  ways (number formatting, `null` handling, `opt*` edge cases). The bus's replies depend on
  them, so LunaCore's tests pin the Android behaviour, or LunaCore uses one implementation on
  both.
- **Threading.** Code written against Android's main `Looper` assumes one main thread. The
  desktop `Scheduler` must give the same guarantee, or races will appear only on the desktop.
- **Windows.** Paths, busybox and the JS services' shell-outs (e.g. `/usr/bin/curl`, which is
  `curl.js`) all assume a Unix root. Expect most of the porting effort to be here.
- **Size.** Electron (~100 MB) plus a jlinked JRE (~40 MB) plus Node 12 is large for an SDK
  component. It's acceptable for a developer tool, and still far smaller than the old
  emulator's image.

## Open questions

1. The novacom transport: A (a novacomd `local` transport) or B (the TCP transport)?
2. The webOS root's shell on Windows: a bundled busybox-w32, or a mapped minimal shell?
3. Does Luna DE share a version number with Lunacy, or have its own?
4. Is a launcher wanted, or is "one window per launched app, driven by `palm-launch` and a
   menu of installed apps" enough at first?
5. Is the single bundled JVM acceptable, or should LunaCore aim at Kotlin Multiplatform
   (compiling the core to JavaScript or native) to drop the JRE later? The recommendation is the
   JVM: it's the smaller change and keeps the core byte-for-byte the code Android runs.

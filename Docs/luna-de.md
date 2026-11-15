# Luna Desktop Edition (Luna DE): plan

Status: **first light** (2026-11-15). Branch `luna-de`. Reviewed, and being built.

## Where it stands (2026-11-15)

- **Milestone 1, done.** `LunaCore`, with the API 21 gate (Animal Sniffer, run before every
  Android build; it caught a Kotlin bridge to Java 8's `Map.getOrDefault` in AppServer on its
  first day), AOSP's `org.json`, and `core.Log` and `core.Scheduler`, which `LunacyApplication`
  sets to logcat and the main Looper in every process.
- **Milestone 2, in part.** Moved, packages unchanged: `Bus`, `AppServer` and its transforms,
  `AppInfo`/`AppFiles`/`AppRegistry`, `DeviceProfile`, `LaunchPoint`, `PageHooks`, `JsText`.
  What they took from Android goes through `core.Assets`, `core.Images`, `core.WebosPaths` and
  `ServerHooks`; Android keeps its call sites through extensions (`serve(Uri)`,
  `androidComponent`, `DeviceProfile.nduid(context)`). db8 and WebSQL haven't moved.
- **Milestone 3, first light.** `DesktopLuna/host` and `DesktopLuna/app` (Electron 44.9.3) on
  Linux: a launcher of the installed apps, and each app in a 1024 x 768 window. codepoet then
  asked for one window running the shell, with apps as cards in it, keeping the separate
  window as a pop-out (milestone 3b, being built). Calculator and
  the Enyo 1 Sampler run; bus calls go round with real errors (no services on the desktop yet).
  How it's wired: [DesktopLuna/README.md](../DesktopLuna/README.md).
- **Milestone 3b, first drawing.** One window as the device: the status bar, the launcher (its
  pages, layout and placement from LunaCore's `LauncherPages`, which AndroidLuna's `LauncherStore`
  now uses too), the quick-launch bar, apps as cards, the card view (tap, flick up to close,
  slide), headless apps opening their own cards (Calendar), pop-out, the bezel's home button.
  The cards' list is main.js's until AppLifecycle moves.
- **Next:** the services an Enyo app asks for first (systemservice's preferences and time,
  applicationManager's launch and open), the network shim and WebSQL (both need their native
  half: net.js and websql.js stand aside until then), then db8, then milestone 5's tools.
- **Ratchet items (desktop):** Electron runs with `--no-sandbox` in development (the Linux
  setuid helper needs root); app windows share the page's world with the preload
  (`contextIsolation: false`), as Android's WebView binding does.

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

## Where the code lives

In this repo, beside the existing hosts, so that one commit builds every platform and
`test.sh` covers them all (today it runs only AndroidLuna's unit tests; LunaCore's tests and a
desktop smoke test are added as each arrives):

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
| `Collation` (db8's sorted queries) | `java.text.Collator`, which is ICU there | ICU4J, so both sort alike |
| `Cookies` (`Http.kt` shares the page's jar) | `android.webkit.CookieManager` | the Electron session's cookies |
| `Images` (decode and scale: wallpapers) | `Bitmap`/`BitmapFactory` | `javax.imageio` |
| `Platform` (symlinks, where Node and busybox are) | `Os.symlink`, `nativeLibraryDir` | `java.nio.file`, the package's folder |

The table is what a read of the imports found on 2026-11-15, and it isn't complete: each move
starts by listing what the class takes from Android, and an interface is added when its first
user moves, not before. `AppServer` also answers with `WebResourceResponse` and reads
`android.net.Uri`; the move gives it a plain response type that each host converts.

**The API 21 gate.** AndroidLuna compiles against `android.jar` with `NewApi` lint fatal and no
desugaring, which is what keeps Android 5 working. A plain JVM module compiles against JDK 17,
where `java.time`, `java.nio.file`, streams and `InputStream.readAllBytes` all build, and would
crash only on the old devices. LunaCore's build therefore fails on any API newer than 21:
Animal Sniffer with the API 21 signature, or the same sources also compiled as an Android
library under the existing lint. This is part of milestone 1, before any code moves.

**`org.json`.** On Android the platform's `org.json` always wins over a copy in the APK, so
"one implementation on both" can't mean bundling one. The two differ where db8 depends on it:
Android's `JSONObject` keeps keys in insertion order (`LinkedHashMap`) and the Maven
`org.json:json` doesn't (`HashMap`; both checked in the jars, 2026-11-15). db8 stores
`o.toString()`, compares those strings and matches `"_del":true` in them. So LunaCore compiles
against AOSP's `org.json` (published on Maven as `com.vaadin.external.google:android-json`),
compile-only on Android, and the desktop runs that same code. The unit tests, which use
`org.json:json:20231013` today, change to it too.

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

Not every move is a pure one, and the plan doesn't pretend otherwise:

- **db8 and WebSQL are rewrites of their storage calls.** `Db8.kt` calls `SQLiteDatabase`,
  `ContentValues` and `Handler` directly; `WebSql.kt` leans on `SQLiteCursor`,
  `SQLiteProgram`, `DatabaseUtils` and Android's exception classes to produce WebSQL's error
  codes. Putting `Sql` under them changes the path that holds people's mail, contacts and
  calendars. Two tests come first: one suite run against both `Sql` implementations, and an
  upgrade test that opens a data directory written by 1.0 with the new build.
- **The bus's one-thread rule** (`Bus.mainOnly`) asks the `Looper`; it asks `Scheduler`.
- **`Power`, `DisplayService` and `ActivityRequirements` are mostly Android** (alarms,
  broadcasts, settings, the battery). What moves is their bus surface and state; the rest
  stays in AndroidLuna, and the desktop gets its own small implementations or real errors.

### DesktopLuna (new)

- **Electron main process:** starts the JVM running LunaCore, keeps it alive, opens windows.
  The JRE is bundled with `jlink`, so developers install nothing else.
- **One window is the device** (codepoet, 2026-11-15: "a single Electron window, running Luna
  CE and apps opening within that window"). The window is a TouchPad's screen, and the shell
  runs in it: the status bar, the launcher and the card view, drawn in HTML from the same LunaCE
  images AndroidLuna draws with. Each app window is a card: a native view (`WebContentsView`) the
  main process lays over the shell's card area. Not an Electron `<webview>`, which was tried
  first: a page's `window.open` can't make one, and webOS apps open their cards that way (a
  `noWindow` app's headless page opens Email's or Calendar's cards, and Enyo's window manager
  talks to them through the window objects), so each open becomes a card through Electron's
  `createWindow`, keeping its opener, as Android's onCreateWindow does. **Delta:** a native view
  can't be CSS-scaled, so the card view shows each card as a snapshot of its page, taken as it
  goes down and when the card view opens, where Lunacy's cards are live. Apps keep their real origins
  (`https://<id>.media.cryptofs.apps/...`, `AppServer.appUrl`).
- **Pop out, a developer's tool.** Any card can be peeled off into a window of its own (its live
  view moved into a window, the page carrying on as it was; first light's way of running an app), to sit
  beside the device with its own DevTools. Same-window is the default (codepoet, 2026-11-15:
  "don't throw the baby out with the bath water").
- **The shell's logic is shared; only its drawing is the platform's.** AndroidLuna's shell
  is Kotlin and Android Views (ShellChrome, CardHost, CardLayer, Launcher, NotificationHost),
  so a desktop shell is the one piece that has to be drawn twice. To keep that second drawing
  thin, what the shell *decides* moves into LunaCore: which apps run with which windows, a
  launch or a relaunch, a window opened or closed, an app ending with its last window
  (AppLifecycle), the cards' order and placement (CardHost's decisions), the launcher's pages
  and launch points (LauncherStore), the dashboards and banners (the notification model).
  Android keeps its Views over that model, and the desktop draws the same model in HTML. Electron's session protocol
  handler routes every request on those origins to LunaCore's AppServer, so the transforms and
  caller identity work as on Android.
- **The `window.LunacyNative` contract** (`AppWindow.kt`'s `Native`) gets an Electron
  implementation: a preload script exposes the same methods, carried to LunaCore over a local
  WebSocket or pipe. The contract is 48 methods and many return a value (`netSendSync`,
  `netResult`, `sqlOpen`, `sqlResult`, `deviceInfo`, `screenSize`, `processId`, `activityId`,
  `webViewCreate`, `pluginCall` and others); Electron's `ipcRenderer.sendSync` covers those,
  through the main process. On Android these run on the bridge's own thread; here each is a
  renderer → main → JVM round trip with the renderer stopped, and the main process must never
  wait on a renderer. The cost is measured at first light (milestone 3), before more code
  moves: if it is too slow, the transport changes while little depends on it.
- **Only Lunacy's origins are intercepted.** A handler for `https` sees every request in the
  session, so everything that isn't an app origin passes through to the network untouched.
- **The window is a TouchPad's screen:** 1024×768 landscape or 768×1024 portrait, switched
  by rotating, and the page renders at that width (rule 3). Other devices (Pre3, Veer, Pre,
  Pixi) come later as a device picker using `DeviceProfile`'s values (codepoet, 2026-11-15:
  "initial window sizes will be 1024x768 and 768x1024 to match touchpad").
- **DevTools** open in the window. That is the big improvement over the old emulator, and
  over `chrome://inspect` on a tablet.
- **JS services** run under a Node the same major version as nodejs-mobile's (Node 12), so
  their behaviour matches Android's. Electron's own Node is newer, so it's not a match.
  Node 12 has been out of support since 2022 and, to be confirmed, has no official builds for
  Apple Silicon or Windows on ARM, so those machines would run the x64 build under
  translation or need a Node 12 built for them (open question 6).
- **Packaging:** electron-builder for Windows (installer and zip), macOS (dmg, signed and
  notarised when there's a key) and Linux (AppImage and tar).

### The SDK's tools

Lunacy on Android serves novacom's device protocol on an abstract socket, and the SDK's
novacomd reaches it through the adb server (webos-sdk-redux `novacomd/src/host/adb_relay.c`).
On the desktop there is no adb. Two ways:

- **A (recommended):** LunaCore's novacom listens locally. novacomd gains a small `local`
  transport, which probes for it the way `adb_relay.c` probes adb and splices each connection
  through unchanged. Luna DE is then listed in `novacom -l` like any device, `-d lunacy` still
  picks it, and the protocol stays the same.
- **B:** reuse novacomd's existing TCP transport (`novacom-tcp`). It speaks the device
  daemon's framing and nduid handshake, which Lunacy's novacom doesn't implement today, so
  this means more new protocol code in Lunacy.

`run file://` commands (postinsts, `novacom run`, `novaterm`) run in Lunacy's webOS root with
busybox on Android. On the desktop, Linux and macOS can use a host busybox or the system shell
with webOS paths mapped; Windows needs a bundled busybox-w32 or an equivalent. This is an
open question.

Two things Android gave for free have to be built here:

- **Who may connect.** On Android, novacom serves only a peer running as root or adb's shell
  user (`Novacom.kt`, by the socket's peer credentials). A fixed localhost TCP port has no
  such check: any process on the machine could run commands as the developer. So the listener
  is a per-user Unix socket or named pipe (the JVM and Windows 10 both have them), or a TCP
  port that demands a token kept in the user's own files. Two running copies of Luna DE must
  not collide either.
- **Containment.** On Android a postinst or `novacom run` is held by the app's sandbox. On the
  desktop a script run by "the system shell with webOS paths mapped" runs as the developer on
  their real machine, and an absolute path in it is the host's path. Package scripts have to
  run, and have to be unable to reach outside the webOS root. How (a namespace or chroot
  where the host has one, a busybox that resolves paths inside the root, or something else)
  is open question 2, and it is settled before milestone 5 ships anything that installs
  packages.

## Rule 0

codepoet, 2026-11-15: Luna DE can have rule 0 exemptions as needed, including First Use.

What that means in practice:

- **What an app draws stays rule 0.** The card's contents come from the same frameworks,
  compat layer and transforms as on Android, so an app looks as it does on a TouchPad.
  That fidelity is what makes it a useful test bench; the shared code gives it for free.
- **The shell follows the TouchPad, with exemptions as needed.** It is drawn with Lunacy's
  LunaCE images and follows AndroidLuna's shell; where the desktop needs it to differ (a
  developer's tools, the pop-out, mouse instead of touch, no First Use), it may.
- **Luna DE's own interface is its own:** the window chrome, rotation, the menus, the
  developer's tools, First Use (skipped, or replaced by a developer's setup).
- **Engine differences:** Electron's Chromium is much newer than Chromium 37. Lunacy already
  runs on current WebViews (Android 17 on the Pixel Tablet), so this is not new ground.
  Anything that renders differently is recorded in `Docs/luna-deltas.md`, as now.

## Milestones

Each step is a move or an addition. After each one, `test.sh` passes and
`Workbench/device-test.sh` passes on the HP 10 G2 (API 21 and Chromium 37, where a too-new API
shows), the Nexus 7, the Fold3 and the A7 Lite (mail and EAS, for the db8 and WebSQL moves),
and on the Pixel Tablet when codepoet opens it, so Lunacy on Android can't regress. And
`Workbench/fresh-install.sh` passes on the Kyocera: a clean install, First Use, the Android
account, checked in that same run of Lunacy. The Configurator, First Use's end and the activity
manager all move into LunaCore, and that first run is exactly where 1.0 broke without any
round noticing (no contacts linker until a restart, 2026-11-15).

1. **LunaCore module, empty.** The Gradle module with its API 21 gate and AOSP's `org.json`,
   and the first interfaces (`Logger`, `Scheduler`) with their Android implementations;
   AndroidLuna depends on it. No behaviour changes.
2. **Move the bus,** then **db8** (SQL behind `Sql`), then **AppServer and the transforms**,
   one per commit series, each followed by the full test round.
3. **First light:** a minimal DesktopLuna serves one bundled Enyo app (Calculator, then Clock)
   into an Electron window, with the `LunacyNative` contract over IPC, on Linux. *(Done
   2026-11-15, as a launcher window and a window per app; that path stays as the pop-out.)*
3b. **The single-window shell.** One window as a TouchPad screen: the status bar, the launcher,
   apps as cards, the card view (switch, close by flicking up), pop-out to a window. Its first
   drawing keeps its own list of cards in the page; the shell-logic moves (AppLifecycle,
   CardHost's decisions, LauncherStore, the notification model) then replace that list with
   LunaCore's, one at a time, each with the full round on Android.
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
  mitigations: small commits, the API 21 gate, the full device round after each step, and the
  storage tests named under "LunaCore" before db8 and WebSQL move.
- **The branch.** codepoet, 2026-11-15: `luna-de` lives on its own branch until it is stable.
  Until then main takes bug fixes only, and each is cherry-picked onto `luna-de`; nothing goes
  from `luna-de` to main until the refactoring is done and proven. The cost is that the moves
  are applied to fixes one cherry-pick at a time, and one merge comes at the end: a fix to a
  file that has moved has to be carried by hand, so each cherry-pick is checked with the same
  round as a milestone.
- **`org.json` differences.** Key order, number formatting, `null` handling and `opt*` edge
  cases differ between Android's and the Maven artifact. Running AOSP's code on both (see
  "LunaCore") removes the difference instead of pinning it in tests.
- **Sorting.** db8's collated queries use `java.text.Collator`, which is ICU on Android and
  the JDK's own rules on the desktop. A test bench that orders results differently from a
  device misleads, hence ICU4J behind `Collation`.
- **The bridge's speed.** See "DesktopLuna": measured at first light.
- **Threading.** Code written against Android's main `Looper` assumes one main thread. The
  desktop `Scheduler` must give the same guarantee, or races will appear only on the desktop.
- **Windows.** Paths, busybox and the JS services' shell-outs (e.g. `/usr/bin/curl`, which is
  `curl.js`) all assume a Unix root. Expect most of the porting effort to be here.
- **Size.** Electron (~100 MB) plus a jlinked JRE (~40 MB) plus Node 12 is large for an SDK
  component. It's acceptable for a developer tool, and still far smaller than the old
  emulator's image.

## Open questions

Settled: `luna-de` stays on its branch until stable; main is bug fixes only meanwhile, cherry-picked
onto `luna-de`, and nothing goes back until the refactoring is done and proven (codepoet,
2026-11-15; "The branch" under Risks).

1. The novacom transport: A (a novacomd `local` transport) or B (the TCP transport)?
2. The webOS root's shell: how package scripts are kept inside the root on each host, and on
   Windows a bundled busybox-w32 or a mapped minimal shell?
3. Does Luna DE share a version number with Lunacy, or have its own?
4. *(Settled 2026-11-15: one window running the shell, with the launcher and card view; a card
   can pop out into a window of its own.)*
5. Is the single bundled JVM acceptable, or should LunaCore aim at Kotlin Multiplatform
   (compiling the core to JavaScript or native) to drop the JRE later? The recommendation is the
   JVM: it's the smaller change and keeps the core byte-for-byte the code Android runs.
6. Node on Apple Silicon and Windows on ARM: Node 12 x64 under translation, a Node 12 built
   for them, or a newer Node there with the difference recorded?

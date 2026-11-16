# Luna Desktop Edition (Luna DE): plan

Status: **runs Enyo apps with their data and the shell's notifications** (2026-11-16). Branch
`luna-de`. Reviewed, and being built.

## Where it stands (2026-11-16)

**In LunaCore now** (moved, packages unchanged, Android calling the same classes): the bus,
AppServer and its transforms, the app registry, **db8** (on `core.Sql`: android.database.sqlite
on Android, SQLite over JDBC on the desktop, with ICU's collation on both), the **Configurator**,
the webOS root's files (`RomFiles`), **SystemService**, **ApplicationManager**, **Packages** and
the install services, ContentHandlers, LaunchPoints, UserFiles, the **ActivityManager** with its
schedule, triggers and requirements, **ConnectionManager**, Http and NetShim. What each took from
Android is an interface in `org.webosarchive.lunacy.core` (`Sql`, `Collation`, `Links`, `Images`,
`HostFolders`, `BatteryWatch`, `PowerTimers`, `NetworkSource`, `Scheduler`, `Cookies`), or a
member of the class's own shell interface (`ApplicationManager.Shell`, `ScriptHost`).

**Tests that hold the moves.** `Db8Suite` and `SqlSuite` (LunaCore/src/sharedTest) run on both
SQLites: under Robolectric in AndroidLuna (`Db8Test`, `SqlTest`) and over JDBC in LunaCore
(`Db8JdbcTest`, `SqlJdbcTest`). They cover the queries, a collated sort, nested transactions and
their rollback, and a database file written the way 1.0 wrote it. `test.sh` runs both, with the
API 21 gate.

**Luna DE runs, on Linux:**
- One window as a TouchPad: the status bar, launcher, quick-launch bar, card view, and apps as
  cards. Memos, Clock and Calculator keep their data in db8; an app launches another through
  applicationManager; a running app is relaunched, not started twice.
- **A launch answers at once** (codepoet, 2026-11-16: "the *appearance* of responding quickly to
  user's actions matters"). The tapped icon glows (LunaCE's launch feedback, 90 px, up to 3 s), and
  the loading card is on the screen from the tap, before the host has been asked for anything:
  `loading-bg.png`, the app's `splashicon` at 192 px (else its launcher icon at half again), the
  glow pulsing behind it (CardSplash's timings). It rises into the card view, and maximizes when
  the app calls `stageReady` (or its page has loaded); the page then takes the splash's place over
  300 ms. A headless app's loading card takes the first card the app opens. The timings are open
  to refining; the order of what is seen is the TouchPad's.
- **The keyboard.** webOS's tablet keyboard is in the shell, drawn from LunaKeyboard's art and
  LunaCE's keymap at the art's own size (340 px: a 55 px number row and four of 70). It comes up
  when a field takes the focus and no real keyboard is attached, and the app hears what
  LunaSysMgr told it, in its order: `Mojo.keyboardShown(true)`, then its window shrinks to the
  space above the keyboard (or `Mojo.positiveSpaceChanged`, for a window that asked not to be
  resized); and the reverse when it goes, the field's focus going with it. Its keys type into the
  card. **A real keyboard** is a button in the bezel: lit (as Luna DE starts, a desktop having
  one) and the on-screen keyboard stays away, as it did with a Bluetooth keyboard on a TouchPad;
  unlit and it comes up. So an app is seen both ways, and the change while it runs.
  **Measured on the reference TouchPad, 2026-11-16** (ls-monitor and /var/log/messages while
  codepoet connected the HP TouchPad Wireless Keyboard, Memos up with a field focused and the
  on-screen keyboard showing):
  - The kernel adds the input device, and `com.palm.bluetooth` answers its notification
    subscribers `{"notification":"notifnconnected","profile":"hid","address":…,"name":"HP TouchPad
    Wireless Keyboard","error":0}` (twice, 100 ms apart), then `{"notification":"notifnbattery",
    …,"batterypercentage":41}`. The subscribers were all on the private bus: audiod, btmonitor,
    hidavrcp, the system UI (`/hid/subscribenotifications`), the Phone app and the Bluetooth app.
  - LunaSysMgr then logs "System request to hide the IME" and runs
    `Mojo.keyboardShown(false)` in the card's page, and the card goes from 1024 x 449 back to
    1024 x 740. Nothing else is sent to the page: **an app learns of a real keyboard as the
    on-screen one going away**, which is what Luna DE's button does.
  - Showing it, a moment before: `Mojo.keyboardShown(true)`, then the card resized to 449 high:
    a keyboard of 291 px. That TouchPad is set to the Small keyboard (its
    `x_palm_virtualkeyboard_settings` reads `"keyboard size":-1`), and LunaCE's presets are XS
    243, S (340 + 243) / 2 = 291, M the background's 340 (the default, size 0) and L 393
    (TabletKeyboard.cpp). Luna DE has the four, chosen from the bezel keyboard button's right-click
    menu and kept in the same preference; at S its card is 1024 x 449 too.
  - Hiding, LunaSysMgr's log has `keyboardShown(false)` before the resize back to 740. Luna DE
    (and AndroidLuna) resize first. The log is LunaSysMgr's order of calls, not what a page saw;
    to be settled with a probe page that records both.
  - Not measured: the notification when the keyboard disconnects.
- **Phone Zoom.** As a Pre3, the bezel's magnifying glass is the switch Lunacy has on a phone
  ([phone.md](phone.md), "Fixed viewport, per app"), for the app whose card is up: its page is
  laid out so the card's shorter side is 640 px and shown whole, scaled down (held tall, a
  640 x 1010 page at 0.75; held wide, 1168 x 640), for an app written for a TouchPad's screen.
  Changing it loads the card again. On by default for the same bundled apps as on Android (Clock,
  Calculator, Memos, App Catalog); the switches are kept in `phone-zoom.json` in the data
  folder, and an app's goes when the app is removed. Not done: Device Info's own Phone Zoom
  list (`system/setFixedViewport` on Lunacy's service) and its width setting, and the list of
  defaults is a copy of AndroidLuna's until FixedViewport moves into LunaCore.
- **Logs.** The bezel's `</>` button opens the log window: every app page's console (cards,
  dashboards, alerts, pages with no window) in one place, each line with its time, app id, level
  and source, kept from when Luna DE started, with a filter and the levels to show. It stands in
  for `palm-log -f` until the SDK's tools reach Luna DE, and nothing is held back: there is no
  system log level here to set. Right-clicking the button lists the
  open cards, to inspect one with Electron's developer tools (as F12 does).
- **Installing by drag and drop.** An `.ipk` dropped on the window installs (checked
  2026-11-16 with a scripted drop; it had not worked before: the dropped list did not cross
  into the preload, so files now cross one at a time).
- **Two devices.** The bezel's device button (a picture of the device) makes Luna DE an HP
  TouchPad or an HP Pre3, from LunaCore's measured profiles: the Pre3 is HP webOS 2.2.4 on a
  480 x 800 screen named tall, drawn at its ScaleFactor of 1.5, so a page lays out 320 wide and
  the status bar's 28 px are the screen's 42 (its luna-platform.conf, read off the device
  2026-11-16). An app there is told it is on a Pre3: deviceInfo, the user agent, the carrier.
  It has no on-screen keyboard (VirtualKeyboardEnabled=false on the device), so the keyboard
  button goes. Changing device closes the open apps, since a page is told its device once.
- **Turning and resizing.** The bezel has a rotate button in its corner (wide and tall; F6 to F9
  pick each way up). A popped-out card starts at the device's size and is then freely resizable:
  its page hears each change as a resize, and a change between wide and tall as the device
  turning, for that window alone. Either way PalmSystem.screenOrientation is set before the page's
  resize arrives, since Enyo reads it there.
- **Installing an app.** A package (.ipk) or an app's own folder (the one with appinfo.json) is
  dropped on the window, chosen with Ctrl/Cmd+O (Shift for a folder), or named in
  `LUNADE_INSTALL`. A package goes through Lunacy's package installer, as from the App Catalog; a
  folder is copied in under its id, and dropped again to replace the copy. A banner says how it
  went, and the app is on the launcher's Downloads tab. A right-click on an installed app's tile
  offers Remove. (`palm-install` comes with milestone 5.)
- **WebSQL** (`openDatabase`), which Electron's Chromium no longer has: WebSql is in LunaCore on
  the same `core.Sql` as db8, with the typed statements and SQLite's own error messages each
  platform has to give alike (`SqlError`). `WebSqlSuite` runs on both SQLites. Mojo's Depot and
  cookies work, and a Mojo app (SimpleChat) runs its scenes.
- **powerd's service** (`com.palm.power`): PowerService is in LunaCore, its timeouts kept in a
  file. A Mojo app's alarm fires and relaunches it; its banners and dashboards show as an Enyo
  app's do.
- **App Catalog installs.** Lunacy's own service is on the bus (`org.webosarchive.lunacy`, with
  `system/getEnvironment` answering for the desktop it runs on), which is how App Catalog knows to
  install through Lunacy's installer; the download manager is in LunaCore and on the bus too.
- **Pages say they are a TouchPad's browser**: the device's user agent, as Android sets it.
- **Notifications.** A page's `window.open` is placed by the kind it names: a card, a
  dashboard (in the status bar's drop-down, behind its app's icon) or a popup alert. Banners show
  in the status bar with the TouchPad's timings.
- **What the shell tells a page** is what AppWindow tells it on Android, by the same scripts:
  activation (`Mojo.stageActivated`), the back gesture, the orientation.
- **The app menu:** a tap on the status bar's title, as on the TouchPad.
- **Keys, the old SDK emulator's:** End (launcher), Home (minimise and maximise the card), Left and
  Right (cards), Esc (back), F6 to F9 (turn the device: the window becomes 768 x 1024). F12 is
  DevTools, Ctrl/Cmd+R reloads, F1 is a spare home button.
- **Services on the bus:** com.palm.db and tempdb, systemservice (time, zones, preferences),
  applicationManager, appinstaller and appInstallService, connectionmanager, activitymanager, and
  **every JS service** in the ROM or an installed package. Anything else answers "Service does
  not exist", as rule 2 asks.
- **JS services** run as on Android: JsServices and ServiceProcess (now in LunaCore) start each
  package's Node process on its first call, under Lunacy's own host.js. The Node is 12, the major
  version nodejs-mobile gives Android: `DesktopLuna/fetch-node.sh` fetches nodejs.org's build and
  checks it against their checksums (`LUNADE_NODE` names another). The accounts services run, so
  Calendar, Contacts and Accounts open with the on-device account.
- **No First Use.** At its first start Luna DE does what Lunacy's shell does when First Use ends:
  the profile account is made (createNovaAccount) and the activities the configurator holds back
  until then are sent.
- **It is a desktop environment, not an Android one** (codepoet, 2026-11-16). What is Android's
  side of Lunacy is left out: the launcher's Android tab, the Android account and its template,
  Android's notifications, First Use, the Google and Microsoft sign-in templates, Lunacy's APK
  updates, the screen saver's settings, and the tiles that only open one of Android's settings
  (Bluetooth, Date & Time, Language). Where a tile has a use for a developer it gets one:
  **Wi-Fi simulates a lost connection.** A tap, and the connection manager reports no network
  (subscribers hear it at once), native requests fail, and the status bar shows it; another tap
  brings it back. The machine's own network is untouched.

**Deltas, recorded:**
- A card in the card view is a snapshot of its page, not the live page (a native view can't be
  scaled).
- The shim's cookies are the host's own jar, not the Electron session's.
- **Package scripts don't run** (postinst, prerm): a package installs without them and the log
  says so, until open question 2 is settled.
- **A JS service runs as the developer**, in Node on their own machine: webOS paths are pointed
  into Luna DE's root (host.js), but nothing holds a service to it, and its shell commands run in
  the machine's `/bin/sh` (the root has no busybox). It is the developer's own code, as it is in
  any Node they run; a package from somewhere else deserves the same care as any script.
- The mail transports (Palm's native IMAP, SMTP and EAS, built for Android's ARM) aren't there:
  the Exchange account type is still offered, and adding one fails at its first call.
- The network is the machine's first interface with an address, shown as the TouchPad's Wi-Fi at
  full signal with no network name; the machine counts as on the mains.
- The shell's own lifecycle rules (which apps run, relaunch, an app ending with its last window)
  are written a second time, in `DesktopShell` and main.js, until AppLifecycle moves into LunaCore.
- An app installed from its folder is a copy: an edit to the folder shows after it is dropped
  again (a link to the folder would be read as outside the app's tree and refused).
- The on-screen keyboard has no press-and-hold alternates, no caret dragging from its trackball,
  no emoji layer and no auto-capitalisation yet; its Enter key always says "Enter".
- As a Pre3, the launcher follows AndroidLuna's phone rules too (Launcher.Phone: a 40 px tab bar
  of 150 px tabs that scrolls sideways, 84 x 112 cells, 56 px icons, 13 px bold labels; three
  columns held tall, six held wide). Not carried over: a phone's two permanent tabs, groups and
  edit mode, which Luna DE's launcher hasn't got on either device. The status bar is the
  TouchPad's 28 px at the Pre3's scale of 1.5, which is what AndroidLuna draws on a phone.
- As a Pre3, the quick-launch bar and Just Type's pill follow AndroidLuna's phone rules (a 95 px
  bar, four apps and the launcher button in five equal slots, icons at 78 % of a slot; the pill
  three quarters of the short side). The rest of the shell is still the TouchPad's drawn at the phone's size (its status bar, launcher,
  card view and the notifications' place at the top): the Pre3's own shell, which AndroidLuna has
  for phones, isn't drawn here yet.
- Mojo logs at every level. On a device `Mojo.Log` starts at errors only and an app raises it
  with `logLevel` in its framework_config.json; Luna DE holds it at 99 whatever the app set, a
  developer's tool being for seeing them (codepoet, 2026-11-16). Enyo's own level is 99 already.
- A dashboard is dismissed with a small close button that shows under the pointer, not a swipe.

**Not there yet, in the order planned:**
1. **luna-send for a service's child processes** (LunaSendServer, a Unix socket on Android): a
   service that shells out to `luna-send` gets an error. With it, the question of package scripts
   (open question 2).
2. *(Done 2026-11-16: WebSQL's native half is in LunaCore; see above.)*
3. AppLifecycle and CardHost's decisions into LunaCore, replacing `DesktopShell`'s copy.
4. Milestone 5's tools (Novacom, the novacomd `local` transport), then packaging.

**Checked on Android** after these moves (2026-11-16, luna-de build 668): `device-test.sh` on the
Fold3, installed over 1.0.1's data (three mail accounts, 231 contacts): 50 passed, 0 failed
(`Docs/test-results/668/fold3.txt`). Still owed: `device-test.sh` on an Android 5 device, and
`fresh-install.sh` (the Configurator, the activity manager and the package installer all moved,
and a first run is where 1.0 broke). **Since that check**, JsServices, ServiceProcess and SysLog moved too, and host.js and
curl.js changed for every platform (a path already inside the root is left alone; the shell is the
host's where the root has none): the unit tests pass, and no device has run them yet.

**Ratchet items (desktop):** Electron runs with `--no-sandbox` in development (the Linux
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
- **Keys: the old SDK emulator's, wherever they fit** (codepoet, 2026-11-15, from the SDK's
  emulator documentation). End (Fn+Right Arrow on a Mac) opens and closes the launcher; Esc is
  the back gesture, sent to the app as on Android; Home (Fn+Left Arrow) minimises the card to the
  card view and maximises it again; Left and Right switch cards in the card view; F6, F7, F8 and F9
  turn the device "up", "down", "right" and "left" (12, 6, 3 and 9 o'clock); F5 was "shake" there
  (not implemented in the emulator either). Luna DE's own keys move out of their way: reload
  becomes Ctrl/Cmd+R (F5 today), and the home button's F1 stays as a spare. F12 for DevTools is
  Luna DE's own.
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
`Workbench/fresh-install.sh` passes on the Galaxy Tab A7 Lite (never the Kyocera, which is
codepoet's F-Droid update test device): a clean install, First Use, the Android
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

## Packaging

`DesktopLuna/package.js` (`npm run dist`) builds a self-contained Luna DE for the machine it
runs on ([DesktopLuna/README.md](../DesktopLuna/README.md) has the steps). What a package holds,
under the app's resources folder, is a checkout in small: `AndroidLuna/src/main/assets`,
`AndroidLuna/local-assets` (not its PDK part), `CoreApps`, the keyboard's art and
`DesktopLuna/node`, so the host's `--repo` and the shell's pictures are found as they are in
development; and beside them `host/` (the jars) and `jre/` (a `jlink` runtime of 14 modules).
About 240 MB on Linux.

Decisions, and why:

- **The host is started directly** (`jre/bin/java -cp host/lib/*`), not through Gradle's launcher
  script, so nothing on the user's machine is consulted.
- **SQLite's native library is taken out of its jar** and put in `host/native/`, the other
  systems' dropped: a Mac's notarisation reads inside jars and refuses unsigned libraries there,
  and outside the jar the library is signed with the rest.
- **The host leaves with the window.** Its standard input is a pipe from Luna DE; when that
  closes (quit, kill or crash) the host exits and stops its services. Found in the first packaged
  run: a killed Luna DE had left its Java host and Node running.
- **Linux runs without Chromium's sandbox helper**, as `npm start` does: the helper must be
  setuid root, which an AppImage or a tarball can't deliver, and Chromium stops before the app's
  own code when it finds it otherwise. The program people start is a two-line script that adds
  `--no-sandbox`. A ratchet item: a `.deb` could install the helper properly.

Not yet tried, and what to expect:

- **macOS.** Signing and notarisation are configured (hardened runtime, with the entitlements
  the Java runtime and Node need) and have never run. Node 12 exists for Macs only as an Intel
  build, so on Apple Silicon the JS services need Rosetta; the rest is native.
  `AndroidLuna/fetch-assets.sh` hasn't been run on a Mac either.
- **Windows.** The package is built (2026-11-16, from Linux with `--win`: a 273 MB zip whose
  `Luna DE.exe`, Java runtime, `node.exe` and SQLite library are all Windows x64 programs) and
  **has never been started**: everything below is written from reading the code, not from a run.
  What was changed for it:
  - *Links.* Windows makes a symbolic link only for an administrator or with Developer Mode. Where
    it refuses, `DesktopLinks` makes a junction for a folder (`/media/cryptofs/apps`, a
    framework's `version/1.0`), which anyone may make, and copies a file. The junctions are
    remembered in `links.json` in the data folder.
  - *The package holds no links*: CoreApps' six are followed when it is staged.
  - *Paths.* A webOS path made from a real one has forward slashes whatever the host's has
    (`UserFiles.webosPath`). The service host maps webOS paths by putting the root in front,
    which Windows accepts with either slash.
  - *Starting the host.* By `java.exe` directly, packaged or from a checkout, never Gradle's `.bat`.
  - *JS services.* `node.exe` is found; `curl` is also written as `curl.cmd`. A service's shell
    commands are POSIX: they run in Git for Windows' `sh.exe` if that is installed, and fail
    as commands that couldn't start if it isn't.
  Expect the first run to show what reading missed. Not handled at all: `fetch-assets.sh` on
  Windows (the cross-build avoids needing it), the installer without Wine or a Windows build,
  and signing.
- No auto-update, and the version is still 0.0.1.

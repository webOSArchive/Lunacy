# Code review, 2026-11-03

A whole-project review at commit c6c529d, for codepoet. The brief: unnecessary
complexity, opportunities to improve organisation and human understandability,
and vulnerabilities, fragility and smells that a less capable model could address.
Comments were not asked for and none are recommended; where a comment is doing a
name's job, the fix is the name.

**Scope.** Every Kotlin file in `AndroidLuna/` (87 files, 27.6k lines) and
`LunaKeyboard/`, Lunacy's own JS (`AndroidLuna/src/main/assets/lunacy/`, 4.4k lines),
the Node-side services, the PDK C shims in `LunaRuntimes/pdk/`, the Gradle files,
`fetch-assets.sh`, `tools/`, `Workbench/`, the manifest and `res/`, git hygiene. Not
reviewed: `CoreApps/` (rule 5), the framework patch *contents*, `iOSLuna/`, vendored trees.

**Method.** Six reads in parallel, one per area, each reporting `path:line`; the
highest-impact claims were then re-read by hand against the source. Line numbers are
from c6c529d; re-read before editing.

**Path shorthand.** `card/` = `AndroidLuna/src/main/java/org/webosarchive/lunacy/card/`,
`shell/` = `…/lunacy/shell/`, `js/` = `AndroidLuna/src/main/assets/lunacy/`,
`pdk/` = `LunaRuntimes/pdk/`.

**The shape of it.** The code reads well file by file: one log tag, one reply helper,
consistent naming, measured constants, the rules in CLAUDE.md visibly followed. The
complexity is structural, and it is of three kinds:

1. **Four things are too big to hold in one's head.** `ShellActivity` (3,153 lines)
   is the application manager, the app lifecycle, the Exhibition controller, the
   notification host and the view builder in one class. `Launcher` (1,277) is a
   store, a dialog, a gesture handler and a physics engine. `ActivityManager` (1,065)
   is four classes behind `// ----` markers. `compat.js` (1,496) is thirty unrelated
   shims in one file. Each splits along seams the code already marks.
2. **The same mechanism is written once per view or once per service.** Sixteen
   shell mechanisms (a `ValueAnimator` idiom at 25 sites, centred-text at 16,
   long-press at 4, a dashboard row twice at 55 lines each…) and eleven service
   mechanisms (an own-app gate 14 times, a file-under-root check 9 ways, the db8
   insert sequence 4 times…) each want one helper. This is where most of the line
   count that isn't webOS behaviour comes from.
3. **Meaning carried by strings, flags and conventions instead of types.** Three
   boolean flags encode four window placements; menu actions, window types and tab
   indices are free strings and magic ints; replies are built five ways and checked
   by substring; the bus's main-thread rule is true everywhere and stated nowhere.

Nothing here is a rule-0 matter: every change below leaves the screen as it is.

---

## Triage (codepoet, 2026-11-03)

Three stages: before the next preview, between the preview and the OAuth work for EAS and
Google IMAP, and before 1.0.

**Done before the preview:** B1, B2, B3, B7, B8, B9, B10, B11, B12, B13; S3, S4 (wallpaper
names, ipk package names, the sound cache name, and the name "Copy to Photos" saves under),
S10, S12 (the key store left out of Android's backup, codepoet's choice over turning backup
off); §8's intent extras, delayed shell actions and `@Volatile`; §3.3 (the corner clamp
ported to `Luna.drawNineSlice`, the keyboard kept independent, codepoet); §9's first tests
(`./gradlew :AndroidLuna:testArm32DebugUnitTest`); §10's checked downloads, NDK variables,
`cdp.sh`'s `ps`, and the probe `.ipk`s untracked.

**Corrected on a second look:**
- **S1** is already a recorded ratchet item (architecture.md, "Frames can reach the bus"), and
  Accounts depends on it for Email's account wizard (`enyo.CrossAppUI`). It goes with the
  move to `WebMessageListener`; no decision is needed now.
- **S2** is rule 10, not security: JS services already run with Lunacy's permissions and see
  its whole data directory (a ratchet item, architecture.md). Done with §4.1, after the preview.
- **S4's image destinations and browser-view image commands** stay as they are: on the
  TouchPad both ran as root and wrote anywhere; here they are held to the webOS root already,
  and an installed app is inside the same boundary as its services.
- **S9** was not exploitable as written: `file:` URLs fail the `HttpURLConnection` cast. The
  file *name* it saved under could climb out of the folder; that is fixed.
- **S11** stays: release builds are tested over adb with `--es launch`, `layout` and
  `trimMemory`, and what they reach is opening a webOS app, as apps did for each other. The
  replay on re-creation (§8) is fixed.
- **S13** is robustness, not security: a PDK app is native code with Lunacy's own uid.
- **S14's pins** stay silent (codepoet).
- **B1** reaches nothing on a real account: mojomail-eas sends its dates without `dayOfWeek`,
  so the function never runs for it.

**Between the preview and the OAuth work:** §5, §4.2 (a debug-build log first, then the
`check`), §4.1 with §3.2 and S2, §2.6 then §2.1, §4.3 and §4.4. The OAuth work touches
the accounts and the services, so the service conventions come first.

**Done so far in that stage (2026-11-04):** S2; §4.2 (the bus checks its thread in debug
builds, after a logging build found no call off it); §4.1 with §3.2 (`Bus.own`,
`registerPrivate`, `UserFiles.inside` and `walkMedia`, `Db8.write`, `KindSpec`,
`AndroidAccountSide`, `GlStream`, `Bus.succeeded`/`errorText`, one way to keep a subscriber);
§5; §2.6 (`PdkHost.start`, `AppServer.serve`, `Configurator.run`, `WindowHost`, `ShellHooks`;
the two `onTouchEvent`s were already split); and from §2.1 `ApplicationManager` and
`ExhibitionController` out of `ShellActivity`, with `removeInstalled`, the handler table
parsed once, `realMetrics`, `ORIENTATIONS`, named request codes and the hand-over helper.
Left by judgment: raw `Thread`s to an executor (DownloadManager, `Packages.shell`,
`AndroidApps.list`) and DownloadManager's and UniversalSearch's locks, which are touched off
the main thread; `onDestroy`'s hand-written order, which is the leaked-shell fix (pages,
services, the bus, then what runs under it) and reads better spelled out than registered. Still to do:
`NotificationHost`, `AppLifecycle` and `ShellChrome`, then §4.3 and §4.4.

**Before 1.0:** §3.1, the other splits in §2, `compat.js` last; B14, B15, S13, the rest of §8
and §10. Measurements on the TouchPad (§11) decide B4, B5, B6, S5, S7/S8, S14 and §4.3.

---

## 1. The short list

The ten changes that buy the most understanding per line touched, in order:

| # | Change | Shrinks |
|---|--------|---------|
| 1 | Split `ShellActivity` into `ApplicationManager`, `AppLifecycle`, `ExhibitionController`, `ShellChrome`, `NotificationHost` (§2.1) | 3,153 lines → five classes of 400–700, each one subject |
| 2 | One `animateValue` helper for the 25 `anim?.cancel(); anim = ValueAnimator…` sites, and the rest of the per-view table (§3.1) | ~400 lines across the shell views; every view's gesture and animation code reads the same way |
| 3 | Pick one subscriber pattern, one way of reading a reply, one handler-registration style and one threading idiom across the services (§4.1) — the *internal* conventions; every service's reply shape stays the device's (see "Fidelity first") | the four ways of keeping a subscriber and the substring checks on replies |
| 4 | Make the bus's main-thread contract explicit with one `check` in `Bus` and delete the `runOnUiThread` wrappers that pretend otherwise (§4.2) | every reader's "which thread is this on?" question, and four misleading wrappers |
| 5 | Split `compat.js` into `input.js`, `layout.js`, `media.js`, `engine.js`; fold its WebSQL shim into `websql.js` (§2.4) | a 1,496-line file nobody can navigate, into four with one subject each |
| 6 | Replace `launch()`'s three boolean flags with a `Placement` enum; the stringly-typed menu actions, window types and tab indices with enums; the untyped engine lists and the `Triple` with classes (§4.3) | the places where a typo compiles |
| 7 | Extract `LauncherStore`, `RemoveDialog`, `KineticScroll` from `Launcher` (§2.2) | 1,277 → ~700 lines; the scroller becomes reusable and testable |
| 8 | One `inside(file, root)`, one `registerOwn`, one `registerPrivate`, one `Bus.Call.app`, one `write(old, o)` in db8 (§3.2) | nine, fourteen, seven, five and four copies respectively; and the one copy of each that is subtly different |
| 9 | Name the page hooks once on each side (`PageHooks` in Kotlin, one JS file injected first) (§4.4) | ten `__lunacyX` strings typed twice with a silent failure on mismatch |
| 10 | Move `Schedule`, `ActivityRequirements` and `TriggerMatch` out of `ActivityManager` (§2.3) | 1,065 → ~450 lines of what the class is for |

Then the readability sweep in §5 (misplaced KDoc, interleaved fields, shadowed names,
dead code), which is mechanical and safe to do a file at a time.

**Separately, before the release:** five findings that are not about complexity but
should not wait — a JS service can call the bus as any app (§6 S2), the WebSQL
polyfill allows `ATTACH` (§6 S3), caller strings used as file paths in four services
(§6 S4), the EAS "last weekday" date is wrong in some months (§7 B1), and a stale
animation callback in `CardLayer` (§7 B2). Each is a few lines.

---

## Fidelity first: what this document must not change

codepoet asked whether any guidance here could break webOS fidelity. Yes — a reviewer
looking for consistency sees "five ways of saying `returnValue:false`" and reaches for
one; but several of those five are measured. Different webOS services were written in
different eras by different teams and answer in different shapes, and Lunacy
reproduces each. Checked against the source after the first draft:

| What looks inconsistent | What it actually is | So the guidance is |
|---|---|---|
| `ImageService.fail` puts a *string* in `errorCode` and adds `subscribed:false` (`card/ImageService.kt:55`) | com.palm.image's own shape, measured on the TouchPad (file header, line 22) | keep; a helper may only *build* this shape, never replace it |
| `DownloadManager` errors carry `"errorCode":"-1"` as a string, or `"ticket_not_found"` (`card/DownloadManager.kt:42–47, 116–130, 240, 258`) | every reply shape measured on 2026-09-22 (line 18) | keep |
| `KeyManager.failed` has `errorText` and no `errorCode` (`card/KeyManager.kt:35`) | the device's, "quirks included" (line 11) | keep |
| `AppInstallService.answer` uses `reason` and `subscribed` (`card/AppInstallService.kt:79–83`) | measured, private bus (lines 16, 184, 222, 246) | keep |
| `Db8.error` orders keys `errorCode, errorText, returnValue` (`card/Db8.kt:1010`) | probably db8's own order; the file doesn't say it was measured | keep the order unless measured otherwise; key order is harmless to a JSON reader but the code itself has substring checks (§4.1) |
| `DockMode.SERVICE` is `"com.palm.applicationManager"` (`card/DockMode.kt`) | the dock-mode launch-point methods *were* applicationManager's; the reply shape is measured (line 17) | the service name stays; the only fix is to reference one constant rather than two literals |
| `PowerService.failure` has no `errorCode` (`card/PowerService.kt:153`) | not stated as measured | leave it; a different shape is not a fidelity gain |

The rule for the whole document: **the bus contract (service names, method names,
reply shapes, error codes and texts, field order where a file says it was measured)
and everything drawn on the screen are the TouchPad's and the Pre3's, and nothing in
§2–§5 may change a byte of either.** Every helper proposed takes the measured values as
parameters; where two views animate differently, the helper carries both sets of
numbers, it does not pick one. Specific places where the first draft's wording would
have crossed that line, now corrected in place:

- **§4.1 "Building a reply" and "Error helper"** — now say: unify the subscriber
  bookkeeping, the reply *reading*, the registration style and the threading idiom;
  leave each service's reply builder producing exactly what it produces today.
- **§4.1 "Service name"** — `DockMode` keeps `com.palm.applicationManager`; the fix is a
  shared constant, not a new name.
- **§3.1 scroll masks, drawers, dashboard rows** — the two scroll-mask animations, the
  `SystemMenu` drawer timing (Drawer.qml's) and the two dashboard `Row`s (TouchPad
  drop-down vs the Pre3's bottom area) each keep their own measured numbers and
  differences; the helper is parameterised, and any replacement animator is checked
  frame-by-frame against the current one before the old code goes.
- **§2.4 `compat.js` split** — the new files are loaded in the *same execution order* as
  the sections have today; the shims patch builtins and the order is part of the
  measured behaviour (the timer pacing at 202–242 in particular).
- **§7 B5 (activity manager `enable`/`disable`/`associate*` stubs) and B6
  (`resumeDownload`)** — rule 2 says don't stub success, but what the TouchPad answered
  is the reference: measure each with `luna-send` on the device first; if it answered
  `{"returnValue":true}` and did nothing visible, that *is* the behaviour, and the stub
  stays with a note saying so. (`DownloadManager.kt:57` already records that the device
  reported `canHandlePause:false`, so `resumeDownload` may well have answered as a
  status query there too.)
- **§7 B4 (`addLaunchPoint` answering true with `errorText`)** — same: measure before changing.
- **§4.3 `Db8.required` → code 22** — only if the device's "invalid type" error is
  measured; otherwise the current reply stays.
- **§6 S5 (activity callbacks as the creator)** — on webOS, activitymanager made
  callbacks with `LSCallFromApplication`, so the callee saw the sender as the activity
  manager *and* the creator's app id. Lunacy's `Bus.Call` has one `appId`. Changing it
  from `com.palm.activitymanager` to the creator changes what a callee that checks the
  sender sees; measure what a callback target saw on the TouchPad before choosing which
  id to send, and consider a second field on `Call` rather than swapping the one.
- **§6 S1 (refusing cross-origin subframes)** — a visible change if any webOS app
  iframed web content; the TouchPad's WebKit allowed it. Measure with an app that does
  before refusing anything; the safer first step is to inject `LunacyNative` only into
  the main frame, which Android cannot do directly, so the real options are: refuse
  the frame, or accept the exposure and record it as a ratchet item.
- **§6 S7 and S8 (what the Web app will serve or open)** — the TouchPad's browser
  opened `file:///usr/palm/applications/<app>/…` pages and `file:///media/internal/…`
  freely, and some apps relied on it (help and licence pages). The fixes must allow
  every *webOS* path (mapped through the root) and refuse only Android ones
  (`/data/data/…`); and serving other apps' origins to a page may be exactly what
  such an app needs — measure before narrowing.
- **§2.1 caching `handlerFor`** — `command-resource-handlers.json` is the ROM's, but
  the handler table can change at runtime (`addResourceHandler` and friends, if they
  are implemented in `ContentHandlers`); the cache must be invalidated by whatever
  changes the table, not only by `prepare()`.
- **§8 `Luna` logging decode failures** — logging only; the memoised miss stays, so
  nothing redraws differently.

Anything else in §2–§5 is a move, a rename, a type for a string, or a helper that
reproduces the same bytes and pixels; none of it is visible from a page or the screen.
When in doubt the test is the project's own: the same case on both machines.

---

## 2. Things too big to hold in one's head

### 2.1 `ShellActivity` — `shell/ShellActivity.kt`, 3,153 lines

How it is laid out today:

| Lines | What |
|---|---|
| 30–283 | ~60 fields (services, views, flags) interleaved with helper functions, `onStart`/`onStop`, package and shortcut receivers |
| 285–612 | `onCreate` (service bring-up, root thread, intent); `buildShell` (190 lines of view construction and callback wiring); `onDestroy` |
| 614–1000 | intents and dev extras, memory state, lock host, `launch` and card creation, window open/close, banners/icons/sounds, install, first use, `appsChanged` |
| 1200–1700 | `applicationManager` method bodies (handlers, resource info, packages, launch points), menus, notification placement, brightness, app activities, keyboard, permissions, page-ready callbacks |
| 1740–3153 | orientation and device info, `reshell`, wallpaper, Exhibition and dock mode, `registerServices` (230 lines: a 73-line launch handler, ~20 service constructions, 17 method registrations), quick-launch dock, home/launcher/Just Type overlays, cutout/immersive, key events |

The split, each step a move with no edit:

| New class | Takes with it |
|---|---|
| `card/ApplicationManager` | the `com.palm.applicationManager` service: the launch/open handler, `handlerFor` (cached, see below), `handlersForMime`, `resourceInfo`, `listPackages`, `listApps`, `running`, `close`, `rescan`, `getAppBasePath`, launch-point add/remove/update/changes and their watchers, the two installer adapters, `INSTALLERS`/`IPK_MIME`/`BROWSER`. Depends on `registry`, `handlers`, `addedLaunchPoints`, `files`, `packages` and a small `AppLauncher` interface (`launch`, `install`, `closeWindow`, `runningWindows`). `registerServices` becomes service construction only. |
| `shell/AppLifecycle` | `running`, `launch` (split into `launchAndroid`, `launchNative`, `relaunch`, `launchWeb`), `showAsCard`, `newCard`, placeholders and pending loads, `onWindowOpened`/`onWindowClosed`/`closeWindow`/`endApp`, app activities, `LAUNCH_AT_BOOT`/`KEEP_ALIVE`, memory-state notifications, `directApp`. Owns `cards` and `hidden`. |
| `shell/ExhibitionController` | `exhibitionOn`, `exhibitionApp`, `dockWindows`, `dockShown`, `DockLayer`, `setExhibition`, `exhibit`, `addDockWindow`, `layDockWindow`, `showDockFace`, `cardArrivedInExhibition`, dream start/end, `powerReceiver`/`dreamPlugged`, `toggleExhibitionMenu`/`chooseExhibition`. |
| `shell/ShellChrome` | `buildShell` (first split in place into `buildLauncher`, `buildJustType`, `buildDock`, `buildNotifications`, `buildMenus`), launcher and Just Type open/close, the quick-launch dock (`dock()`, `setDock`, `shownDock`, `showDock`, `dropOnDock`, `dockPrefs`), status-bar menus and the brightness slider, `fade`, `homePressed`, `onCardView`/`onMaximized`, wallpaper, keyboard watch, `reshell`, `placeNotifications`. |
| `shell/NotificationHost` | `notifications`, `area`, `barMenu`/`barPopups`, `notificationsAtBottom`, `icon(url, appId)`, `addBanner`/`systemBanner`/`copiedToClipboard`/`removeBanner`/`clearBanners`, `notificationIconsChanged`, `androidNotificationsChanged`, and the hand-over that `placeNotifications` and `reshell` both do today. |

`ShellActivity` keeps the Android lifecycle, intent handling, permissions and the
lock host, the `WindowHost` answers that need an Activity, and ownership of the five.

What falls out while moving:

- `launch()` (791–874) is 83 lines with three optional flags — `startupCard`,
  `launcher`, `dock` — that together encode four placements, and callers pass
  combinations (`startupCard = false, dock = true`) that only mean something together.
  One `enum class Placement { CARD, HEADLESS_CARD, NO_CARD, DOCK }` chosen by the
  caller turns the tail into a single `when`. The `launcher: String?` parameter
  shadows the `launcher` view field (also at 934): `launchedBy`.
- The "hand notifications over" sequence is written twice (1485–1496 and 2060–2086),
  with the magic `200` in both and at 998 → `Notifications.handOver()` /
  `takeOver()` and `DEFAULT_ALERT_HEIGHT`.
- The "remove an installed app" sequence is written three times (2667–2671,
  2680–2684, 2738–2745); the first copy forgets to close the app's windows and
  `showDock()`, the third alone calls `FixedViewport.forget` → one
  `removeInstalled(appId, done)`.
- `getRealMetrics` boilerplate eight times (450, 1852, 1982, 2103, 2444, 2473, 3048;
  `Luna.kt:32`) → `realMetrics()`.
- webOS path roots spelled out in six places (104, 305, 1192, 1277, 1343, 1383, 1397)
  → an `installedDir` field and `AppInfo.filePathOf(rel)`.
- Orientation strings validated at 985–987 and mapped at 1711–1721 → one
  `ORIENTATIONS: Map<String, Int>`.
- Window types as string literals (992–1010; `card/AppWindow.kt:246, 279, 414, 489`)
  → `enum class WindowType(val wire: String)`.
- `onDestroy` closes things two ways (576–612): some through `closeOnDestroy`, the
  rest by hand behind `isInitialized` guards that encode an ordering the reader has
  to re-derive. Register every stoppable in `closeOnDestroy` where it is created.
- Dead `isInitialized` guards at 480, 1138, 1474, 2648: `registerServices` always
  runs before `buildShell`, and `webos` is assigned at 309 before anything else.
- `packageReceiver` is unregistered on `::launcher.isInitialized` (583), an unrelated
  field; the other receivers use a flag.
- `handlerFor` (46–56) re-reads and re-parses `command-resource-handlers.json` and
  recompiles every regex on each `open`. Parse once; it belongs in `ContentHandlers`,
  and the cache is invalidated by whatever can change the table at runtime, not only
  by `prepare()`.
- Request codes `1`, `100`, `200`, `201` from four places (1633, 1638, 2577, 3091);
  `200` is unhandled in `onActivityResult`. Name them in the companion.
- TouchPad screen dimensions as literals (`Luna.kt:35`; 103, 451, 2113–2114, 2303) →
  `screenLong`/`screenShort`/`dpi` on `DeviceProfile`, which already knows which
  device is which.

### 2.2 `Launcher` — `shell/Launcher.kt`, 1,277 lines

One class holds JSON persistence and migration (256–306, 345–437), the tab bar with
add/rename (505–537, 712–774), edit-mode drag/reorder/grouping (776–956), the remove
dialog (958–1027), page/scroll gesture handling (1029–1167) and a kinetic scroller
with overscroll physics (1179–1276). Extract, in this order, each as a plain class
taking `luna`: `LauncherStore` (load/save of pages, `defaultLayout`, `keywordPages`,
`pageFor`), `RemoveDialog` (`dialogLayout`/`drawDialog`/`dialogTouch` with an
`onResult`), `KineticScroll` (fling/overscroll/settle on a `scrollY` and `maxScroll`).
Launcher drops to about 700 lines with no behaviour change.

Also: the phone/tablet metrics are 13 parallel `if (phone)` fields (171–184; the same
pattern in `shell/QuickLaunch.kt:48–53` and `shell/JustTypePanel.kt:489–502`). Adding
a metric means three edits and a mismatch is silent. One `class Metrics(val cell,
val rowH, …)` with `TABLET` and `PHONE` instances in the companion, and `m.cell` at
the use sites.

### 2.3 `ActivityManager` — `card/ActivityManager.kt`, 1,065 lines

The file already marks its sections with `// ---- … ----`: requirements with the
network and battery watchers (756–860, state at 758–760, 606–607, 822, 835), trigger
matching with `where`/`path`/`compareOp` (519–579), `Schedule` (661–754), persistence
(960–1048). The class's own job — create, subscribe, commands, the run queue — is
about 450 lines. Move `Schedule` to `ActivitySchedule.kt` (it needs only `SpecError`);
extract `class ActivityRequirements(context, bus)` exposing `met(name, v)`,
`current(name)`, `needsNetwork`, `onChanged`; extract `object TriggerMatch { fun
matches(trigger, reply, first) }`.

Inside what remains: `lookup` replies and returns through `null.also { call.reply(…) }`
three times (131–143), so `withActivity` callers can't see a reply was sent → plain
`if` and `return null`, named `activityOrReplyError`; `requirementsValue(name): Any =
true` exists only so line 813 can call it → delete, write `met(name, true)`;
`main.postDelayed({…}, 5000)` and `(Math.random() * 0xffffffffL)` (481, 463) → named
constants; a private `error(code, text)` (1050) beside `Bus.error(text, code)` with the
arguments flipped → delete (same for `Db8.kt:1010`, unless its key order is measured).

### 2.4 `compat.js` — `js/compat.js`, 1,496 lines

Thirty sections: touch→mouse 5–196; timer pacing 210–242; synthetic keypress
261–286; removeInputFocus 301–308; keep-field-visible 318–350; border-image 363–448;
swallow `error` 455–459; callable collections 473–504 (Proxy; never runs on Chromium
37); serviceWorker 508–510; `webkitCancelRequestAnimationFrame` 517–520; `webosEvent`
526–533; -webkit-box static position 545–610; -webkit-box edge margins 628–736; URL
mapping 743–772; img/iframe src 773–787; media src/Audio 791–813; ordered scripts
827–863; progress event 873–894; empty media src 900–912; http: markup 917–933;
WebSQL message/arity 952–1023; window/screen size 1049–1083; card background
1106–1161; viewport meta 1190–1363; pageDrawn 1372–1398; cookie flush 1405–1415; DOM
mutation events 1431–1471; box-center 1489–1496.

The injection list at `card/AppServer.kt:340` is one string, so a split costs one
line: `input.js` (5–350), `layout.js` (363–448, 545–736, 1106–1363), `media.js`
(743–933), `engine.js` (210–242, 455–533, 827–894, 1372–1471); the WebSQL shim
(952–1023) into `websql.js`, which already owns `openDatabase`. The sections patch
builtins, and the order they run in is part of the measured behaviour (the timer
pacing at 202–242 above all), so the files are injected in an order that keeps every
section running after the same ones it runs after today — which means `engine.js`'s
first part must load before `input.js`, or the split is by position rather than by
subject. Check the probe apps (`Workbench/probe`) after.

Inside it:
- `run()` creates a `MutationObserver` over the whole document for *every* short-timer
  callback (221–229), and Mojo and Enyo fire many per frame. One module-level
  observer, `takeRecords()` on entry.
- The "map the `src` attribute" patch is written three times (773–787, 791–802,
  900–912) → one `mapSrc(proto, map)` called four times.
- The frame-token/deliver pattern is copied into `bridge.js` (20–57, 88–99), `net.js`
  (15–27, 196–214) and `websql.js` (47–69): three lists on `window.top`, three id
  counters, three "try me, then every same-origin frame" loops → one `__lunacyFrames`
  helper in `bridge.js`, loaded first.
- `textedit.js:58–63` re-implements `__lunacyStyle` from `compat.js:1489–1495`.
- The `.box-center` rule (1496) is the only CSS in a JS file → `compat.css`.
- The `[100, 400, 1200, 3000]` polling schedule and its `load`/`resize` wiring appear
  twice (1158, 1359) → one `settle(fn)`.
- `__lunacyLocalBlocked` is a bare top-level `var`, read at 746 before its declaration
  at 755, while every other cross-section global is `window.__lunacyX`.
- `publish()`/`define()` in `bridge.js:205–220` and a third `define` in
  `crypt.js:225–229` are the same `Object.defineProperty` wrapper.

### 2.5 `BrowserViews.Browser` — `card/BrowserViews.kt:191–825`, 630 lines

Four concerns the file already marks with `// ----`: the email document and header
strip (473–695: `headerCss`, `clip`, `room`, `applyHeader`, `headerTouch`, `setHtml`,
`touchpadFonts`, `insertAfter`, `escape`/`unescape`/`linkify`), images (387–445), SSL
trust (87–95, 765–791), and the plugin command dispatch. Extract `EmailDocument` (pure
functions), `HeaderStrip` (the clip/scroll/touch state) and `SiteTrust` (`trustKey`,
`verified`, `trustedCertificates`, the `onReceivedSslError` decision); `Browser` keeps
`command()` and the clients.

### 2.6 Smaller splits

- `PdkHost.start()` is 120 lines of five jobs (`card/PdkHost.kt:92–211`: listen and
  framebuffer, choose the binary, build the command, build the environment, start and
  log) → `chooseBinary()`, `command(binary)`, `environment(pb, binary, appDir)`,
  `launch(pb)`. The file also holds `Portability` and `PdkRuntime`, each with its
  KDoc on the wrong declaration (435–442, 534–550) → their own files.
- `AppServer.serve` is one 70-line `when` with a 230-character line (166) and two
  functions named `html` (142 vs 330) → `lunacyRoute`, `frameworkRoute`, `rootRoute`,
  `userRoute`; `htmlResponse`. The "load this app's art list" block is copied in
  `artOf` and `noteArt` (467–473, 484–487) → `artSet(app)`.
- `Configurator.run()` is six phases in one function (`card/Configurator.kt:23–77`)
  → `systemKinds()`, `appKinds()`, `fileCacheTypes()`, `serviceKinds()`,
  `appActivities()`; the order comments become the names.
- `registerServices` (`ShellActivity.kt:2479–2709`) and `buildShell` (383–574) — see
  2.1; split them in place first, even before any class moves.
- `JustTypePanel.onTouchEvent` (191 lines, `shell/JustTypePanel.kt:150`) and
  `StatusBar.onTouchEvent` (143 lines, `shell/StatusBar.kt:142`): each is a `when` on
  action with the drag, tap and fling cases inline → one private function per case.
- `WindowHost` is a 30-member interface (`card/AppWindow.kt:22–94`) mixing lifecycle,
  banners/sound/clipboard, and device facts; `PdkWindow` and any test must implement
  all of it → `WindowLifecycle`, `WindowBanners`, `DeviceFacts`, with `WindowHost`
  extending all three so `ShellActivity` doesn't change.
- `LunacyService` takes fourteen lambdas with defaults that lie (`"Pinning is not
  available"`) (`card/LunacyService.kt:41–65`) → an `interface ShellHooks`, as
  `AppInstallService.Installer` and `SecurityPolicy.Host` already do.

---

## 3. One mechanism, many copies

### 3.1 Shell views

| Mechanism | Files (lines) | One helper |
|---|---|---|
| `anim?.cancel(); anim = ValueAnimator.ofFloat(a, b).apply { duration; interpolator; addUpdateListener { field = …; invalidate() }; start() }` — 25 sites | StatusBar 60, 81, 108, 160, 189, 222, 341; Launcher 523, 560, 897, 1172, 1271; Notifications 162, 335, 342; NotificationArea 177, 303; GroupOverlay 188; ExhibitionLayer 324; CardLayer 255 | `fun View.animateValue(from, to, ms, easing, onEnd = {}, set: (Float) -> Unit): ValueAnimator` in `Easing.kt`; a `cancelled`-flag variant for GroupOverlay 191–195 / Launcher 1221–1229 / CardLayer 701–707 |
| Vertically centred baseline `cy - (p.ascent() + p.descent()) / 2` — 16 sites | SystemMenu 333, 410; JustTypePanel 618; StatusBar 527, 593; ExhibitionMenu 84; TabDialog 139; GroupOverlay 254; Launcher 732, 743, 1011; ExhibitionLayer 185–186, 230, 258; CardLayer 189; NotificationArea 122 | `fun Paint.centredBaseline(cy: Float)` |
| Long-press `Runnable` + `postDelayed` + `removeCallbacks` on move/up/cancel | QuickLaunch 139–177; GroupOverlay 305–340; Launcher 761–770, 1038–1043, 1078–1114; CardLayer 1096–1107, 1173–1202 | `class Hold(view, ms, action)` with `arm()`, `disarm()` |
| VelocityTracker obtain / addMovement / compute / recycle, with `velocity!!` four times per handler | ExhibitionLayer 287–308; SystemMenu 506–573; Launcher 1035–1117; CardLayer 1088–1205, 1268–1269 | `class Flick(minFling)` with `begin(e)`, `move(e)`, `end(): PointF` |
| Keyboard show/hide via `InputMethodManager` | TabDialog 88, 94; GroupOverlay 287, 296; JustTypePanel 218, 223; JustType 74–75 | `View.showKeyboard(field)` / `hideKeyboard(field)` |
| Rename `EditText` (no background, single line, `LengthFilter(24)`, IME done) | TabDialog 48–62; GroupOverlay 130–155; JustTypePanel 163–184 | `renameField(context, luna, maxLen, onDone)` |
| Dialog panel / edit box paints, byte-identical | TabDialog 67–70; GroupOverlay 125–128 | `LunaDialogPaints(luna)` |
| Dashboard `Row`: drag-to-dismiss, `onInterceptTouchEvent`, synthetic tap — ~55 lines each, differing in one clamp and one flag. The two differences are the TouchPad drop-down's vs the Pre3 bottom area's and stay as parameters | Notifications 377–433; NotificationArea 405–464 | top-level `DashboardRow(context, luna, window, rightOnly, onDrag, onDismiss)` |
| Scroll-fade masks + arrows with a 70 ms fade — one with two `ValueAnimator`s, one with a hand-rolled time step. Both menus' measured timings are kept; check the replacement frame-by-frame against the old before deleting it | Notifications 329–363; SystemMenu 417–449 | `ScrollMasks(luna, view, fadeMs)` with `update(up, down)`, `draw(c, x0, x1, bottomY)` |
| Two-line elided `StaticLayout` label and its cache (GroupOverlay's never cleared) | Launcher 338, 699–710; GroupOverlay 260, 267–274 | `Labels(luna, paint, widthPx)` with `get(key, text)`, `clear()` |
| Android icon PNG cache, the `dpi` formula, `forget(pkg)` | AndroidApps 30–34, 67–81; PinnedShortcuts 46, 68, 143–159 | `AndroidIconCache(context, iconPx)` |
| MediaPlayer create / stream / listeners / prepare / start / track | Sounds 65–83, 93–105 | `Sounds.start(stream, log, source)` |
| Launch-feedback glow with the 3 s timeout | QuickLaunch 64–71, 112–115, 171; Launcher 441–455, 640–643, 661–664 | `LaunchGlow(view, luna, sizeTp)` |
| App icon with default fallback | QuickLaunch 116, 129; GroupOverlay 66, 266; Launcher 665; JustTypePanel 601 | `Luna.appIconOrDefault(app)` |
| Two-state sprite "upper/lower half" | QuickLaunch 120–122; Launcher 737; TabDialog 145; JustTypePanel 731–739 | `Luna.halfSprite(c, path, dst, pressed)` |
| `Easing.InOutQuad` re-declared privately; an inline cubic beside named easings | StatusBar 48; GroupOverlay 181 | use `Easing`; add `Easing.InCubic` |
| Drawer animation: `SystemMenu.Drawer` keeps `from/to/start/duration/interp` and reads `SystemClock` on every draw; `onDraw` also drives an `OverScroller` the same way. The duration and curve are Drawer.qml's and stay exactly; this is a change of mechanism only, verified against a burst of screenshots of the current build | SystemMenu 232–268, 280–281 | a `ValueAnimator` like every other view, with the same interpolator and duration |

### 3.2 Services

| Mechanism | Files | One helper |
|---|---|---|
| "Own apps only" gate with a hand-typed verb — 14 copies | `LunacyService.kt:88, 106, 126, 133, 151, 201, 209, 274, 331, 484, 501, 513, 525, 541` | `registerOwn(bus, method, what, handler)` doing the check once; hoist the ids at 561–562 and 253 into `OWN_APPS` / `ACCOUNT_PAGE_HOSTS` |
| "Is this file under that root" — nine spellings, one branch with none | `AppServer.kt:189, 193, 198–200`; `LunacyService.kt:580`; `SystemService.kt:430`; `ImageService.kt:64`; `LaunchPoints.kt:126`; `AppRegistry.kt:194, 198`; `JsServices.kt:130` (no check) | make `UserFiles.inside(f, root)` (77–81) public, or `Paths.under(root, rel): File?`; the three `AppServer` branches collapse to one |
| Private-bus-only gate | `AppInstallService.kt:66, 200, 203, 207` inline; `DeviceProfileService.kt:23–30` `absent()`; `Db8.kt:306` the literal `"novacomd"` instead of `Novacom.CALLER` | `Bus.registerPrivate(service, method, h)` |
| db8 "insert an object": `_rev`, `assignIds`, `applyRevSets`, `store`, `changed` — four copies; `{"id","rev"}` result — three | `Db8.kt:176–181, 223–228, 527–534, 576–581; 533, 582, 588` | `write(old, o)` and `idRev(o)`; `mergeOne` and `delOne` can use it too |
| db8 kind-spec and permission builders (`prop`/`named`/`index` lambdas, `permissions()`) | `MediaIndex.kt:225–234, 263–266`; `FileIndex.kt:80–100`; `AndroidCalendar.kt:482–488`; `AndroidContacts.kt:299–300` | `KindSpec.kt`: `kind()`, `index()`, `prop()`, `permissions()` |
| Account plumbing: prefs/`accountId()`, HandlerThread, ContentObserver, `onEnabled`, `onDelete`, the 8 s removal delay, `granted`, `startWatching`, `schedule`, `close` — ~70 lines each | `AndroidCalendar.kt:48–60, 73–77, 93–127, 155–174`; `AndroidContacts.kt:39–49, 58–64, 71–125` | `abstract class AndroidAccountSide(context, prefsName, permission, contentUri)` with abstract `sync()`, `remove(id)` |
| GL-stream start-up state machine (`gl`, `glMode`, `glVersion`, `early`, `startGl()`) | `PdkWindow.kt:159–207`; `HybridPlugins.kt:89–111` | `class GlStream(appId, answer)` with `onMode`, `onBatch`, `destroy`, `onStarted` |
| Active-network lookup with the API-23/pre-23 fallback | `NetTools.kt:65–72`; `HostNetwork.kt:45–49` | `HostNetwork.activeNetwork(cm)` |
| `/media/internal` tree walk with the same depth and file caps | `MediaIndex.kt:97–128, 303–304`; `FileIndex.kt:31–55, 114–115` | `UserFiles.walkMedia(root, maxDepth, maxFiles, each)` |
| Page-generation counter, all bumped at `AppWindow.kt:270` | `AppWindow.kt:130`; `NetShim.kt:34`; `WebSql.kt:53` | pass the window's `page` in; delete the shims' own |
| `call.appId.substringBefore(' ')` — LS2's `appId pid` form, never named | `ActivityManager.kt:127`; `AndroidContacts.kt:72, 83`; `AndroidCalendar.kt:84, 88` | `val Bus.Call.app get() = appId.substringBefore(' ')` |
| "What keeps a call open" — three slightly different rules | `Bus.kt:28`; `WebosRoot.kt:334–335`; `js/bridge.js:64–67` | `Bus.staysOpen(method, params)`; have `Native.call` return it so `bridge.js` stops parsing |
| Decode-save-recycle block; the four-key error object three times | `ImageService.kt:107–112` = `125–130`; `DownloadManager.kt:116–131` | `copy(src, dest, type)`; `refused(text)` |
| `fail` lambda defined twice in one file | `SystemService.kt:112, 155` | one private function |
| Shell-path regex in four places, already drifted (the Kotlin one accepts a backtick delimiter; `host.js:55` doesn't) | `WebosRoot.kt:248`; `js/services/host.js:37, 55`; `curl.js:15` | pass the roots list in one env var from `WebosRoot.environment`; build the regex from it |

### 3.3 Keyboard and shell

`LunaKeyboard` carries its own nine-slice, two-state sprite and Prelude loader
(`LunaKeyboard/…/Art.kt:29–66`) beside the shell's (`shell/Luna.kt:144–149,
226–236`); the keyboard's corner clamp (`Art.kt:54–55`) never reached `Luna`. The
module is deliberately independent (CLAUDE.md). A shared *library* module with no
shell dependency would keep that; otherwise port the clamp. codepoet's call.

---

## 4. Meaning carried by strings, flags and conventions

### 4.1 Pick one convention per row

| Convention | One way | Another |
|---|---|---|
| Building a reply — **shapes stay as they are** (see "Fidelity first"); only the raw string at `ActivityManager.kt:825` (`"{\"subscribe\":true}"`) is worth turning into a `JSONObject`, producing the same bytes | `Bus.ok()` / `Bus.error()`: LunacyService, DisplayService, DockMode, WebosLinks, ConnectionManager | `JSONObject().put(…)` chains: Db8, ActivityManager, SystemService, SecurityPolicy, AudioService, Keys; `JSONObject(mapOf(…))`: DownloadManager — each producing its service's measured shape |
| Error helper — **shapes stay**; the one safe change is `ActivityManager.error(code, text)`, whose output is byte-identical to `Bus.error(text, code)` with the arguments flipped, so it can be deleted | `Bus.error(text, code = -1)` | `Db8.error` (its own key order), `KeyManager.failed`, `PowerService.failure`, `ImageService.fail` (string errorCode), `AppInstallService.answer(ok, reason, code)` — each the device's shape |
| Reading a reply | `JSONObject(reply).optBoolean("returnValue")`: ActivityManager, SecurityPolicy, Db8 | `reply.contains("\"returnValue\":true")`: `Configurator.kt:117, 135–136, 218`, `PowerService.kt:145`; error-text matching `contains("Service does not exist")` at `ActivityManager.kt:475`, `startsWith("Service exited")` at `ServiceProcess.kt:152` |
| Subscriber bookkeeping | `LinkedHashSet<Call>` + `onCancel { remove }`: SystemService, DisplayService, Keys, ConnectionManager, DockMode, SecurityPolicy, LunacyService | `ArrayList` (Bus watchers as `Pair`/`Triple` lists, AudioService, AppInstallService); `ArrayList<Pair<Call, List<String>>>` (SystemService prefs); `synchronized(set)` (UniversalSearch); `synchronizedList` (DownloadManager) |
| Guard before keeping a subscription | `if (call.subscribe && !call.cancelled)` | no `cancelled` check at `Keys.kt:38`; ActivityManager relies on `onCancel` firing at once |
| Thread hop in `onCancel` | `main.post { }` (ActivityManager, LunacyService); `work { }` (Db8) | direct mutation (SystemService, DisplayService, Keys, ConnectionManager, DockMode, AudioService, AppInstallService, DownloadManager) |
| Off-main work | one `Executor` + `main.post(reply)`: Db8, SystemService, ImageService, LunacyService, Packages, AppInstallService | a raw `Thread` per job: `DownloadManager.fetch`, `Packages.shell`, `JsServices.extractfs` (a new Handler per call, `JsServices.kt:121`), `AndroidApps.list()` (`shell/AndroidApps.kt:43`) |
| Registering handlers | one `bus.register` per method with a lambda | a loop over a map (`ActivityManager.kt:103–113`); a list plus a `when` (`AppInstallService.kt:64–76`, `Db8.kt:267–271`, `ImageService.kt:42–49`) |
| Service name | `const val SERVICE` in the companion | literals at `SystemProperties.kt:44`, `Bus.kt:66–67`; `DockMode.SERVICE` spells `"com.palm.applicationManager"` a second time — the dock-mode methods really were applicationManager's, so the name stays and the two literals become one constant |
| Caller id | `call.appId` as-is | `call.appId.substringBefore(' ')` (see 3.2) |

None of these changes a byte on the bus; each row is one grep and a mechanical edit.
The two rows marked **shapes stay** are about how the code is written, not what it
sends.

### 4.2 The bus's main-thread contract — `card/Bus.kt:42–49, 135`

`handlers`, `services`, the watcher lists and most services' subscriber sets are plain
collections. Every caller happens to `main.post` first (`card/AppWindow.kt:506`,
`card/WebosRoot.kt:336`, `card/ServiceProcess.kt:59`), and nothing says or checks it.
Four handlers in `registerServices` wrap in `runOnUiThread` (`shell/ShellActivity.kt:2632,
2634, 2640, 2647`) while the rest touch views directly, which misleads the reader both
ways: the wrapped ones suggest the bus is multi-threaded, the unwrapped ones would
crash if it were. Fix: `check(Looper.myLooper() == Looper.getMainLooper()) { "bus:
off the main thread" }` at the top of `Bus.call`, `register`, `registerService` and
`Call.cancel`; delete the four wrappers; say "main thread" in the `Bus` class doc as
`ActivityManager` already does. (One exception to find and fix first: `listCalendars`
replies from the calendar's HandlerThread, `card/AndroidCalendar.kt:137–144`.)

### 4.3 Types instead of strings, flags and tuples

- `launch()`'s `startupCard`/`launcher`/`dock` flags → `Placement` enum (2.1).
- `SystemMenu.Row.Action.key` is a free string (`"wifiOnOff"`, `"btPrefs"`, …)
  matched in `act()` (`shell/SystemMenu.kt:145, 166–196, 591–607`); a typo compiles →
  `enum class Act`.
- Window types as literals in two files → `WindowType` (2.1).
- Tab index ↔ filter encoded three times with magic ints (`shell/JustTypePanel.kt:717,
  760, 800`) → `TABS = listOf(ALL, CONTACTS, CONTENT, ACTIONS)` with `cellOf`/`filterAt`.
- `UniversalSearch` engines are `listOf("duckduckgo", "DuckDuckGo", url, true)` then
  `id as String, on as Boolean` (`card/UniversalSearch.kt:48–54, 180–183`) → `class
  Engine(id, name, url, on)`; `param()` has two identical branches (225–229).
- `Configurator` packs two fields into `"$kind:$creator"` inside a `Triple` and splits
  them later (`card/Configurator.kt:57, 64, 72, 80–85, 112`) → `class ActivityFile`.
- `Db8.required(...) as JSONArray` turns a schema error into "internal error" with a
  stack trace (`card/Db8.kt:1006–1007`, ten uses) → typed `requiredArray/Object/String`.
  What they throw is whatever the device answered for a wrong-typed parameter: `purge`
  builds a code-22 "invalid type" by hand at 308, which suggests it was measured there;
  confirm with `luna-send` before using it for the other nine.
- `"device"` prefs name as a literal six times (`card/DeviceProfile.kt:279, 284, 298,
  321, 327`; `FormFactor.kt:43`) → one constant and a `prefs(context)`.

### 4.4 Page hooks named once — Kotlin `card/AppWindow.kt:217, 220, 350, 361, 369, 411, 431, 451, 510`, `shell/ShellActivity.kt:1077`, `card/HybridPlugins.kt:119`, `card/BrowserViews.kt:177`; JS `bridge.js:88, 278, 435`, `compat.js:302, 1362`, `net.js:205`, `websql.js:62`

`__lunacyNetDone`, `__lunacySqlDone`, `__lunacyPaste`, `__lunacyRelaunch`,
`__lunacyRemoveInputFocus`, `__lunacyViewport`, `__lunacyPlugin`, `__lunacyWebView`,
`PalmServiceBridge.__reply`, `Mojo.<fn>` are each typed on both sides, and a rename on
one side fails silently because every call is `window.X&&X(…)`. Fix: `object PageHooks`
with the constants and a `call(name, vararg args)` builder behind every
`evaluateJavascript`; one JS file injected first that defines the same names.

---

## 5. Readability sweep

Mechanical, low-risk, one file at a time.

- **KDoc on the wrong declaration** (IDE hover shows the wrong text):
  `shell/ShellActivity.kt:630–631, 2024–2043, 2243–2259, 2260–2270, 2713–2722`;
  `shell/Luna.kt:46–47`; `shell/Launcher.kt:138–139` (belongs at 186);
  `card/Db8.kt:766–771, 995–997`; `card/LunacyService.kt:459–473`;
  `card/Packages.kt:156–163`; `card/PdkHost.kt:435–442, 534–550`;
  `card/DeviceProfile.kt:6–44` (the class doc sits on `EmulatedCard`). Move each
  above the thing it names.
- **Fields and lifecycle interleaved** in `ShellActivity` (30–283; `memoryState` at
  703 after its use; `watchingPower` at 2220). Group the fields at the top in the
  order `buildShell` creates them. `AppWindow.kt` has `drawChild` before its fields
  (115–126) and `CookieFlush` at the end (668–674) → its own file.
- **Shadowing and collisions:** `enum class View` in `JustTypePanel` (116) shadows
  `android.view.View` → `Filter`; `launcher: String?` shadows the view field (2.1);
  `filter { dir -> … }` inside `remove(appId, dir, done)` (`card/Packages.kt:52`);
  `BannerSurface.invalidate()` collides with `View.invalidate()`, so `NotificationArea`
  overrides the latter and every internal invalidate also redraws the bar
  (`shell/Notifications.kt:34`, `shell/NotificationArea.kt:87`) → `redrawBanner()`;
  two `indexed()` with unrelated meanings (`card/Db8.kt:713, 772`) → `indexServes`,
  `indexedValue`; `purge` means three things (`Db8.kt:304, 505, 616, 637, 907`) →
  `erase(o)`, `hard`; `Exception_` (`card/AndroidCalendar.kt:269`) →
  `RecurrenceException`; `FOUR` (`card/AppWindow.kt:496`) → `ORIENTATIONS`;
  `Object()` as a lock (`AppWindow.kt:166`) → `Any()`.
- **Control flow:** `?.let { … } ?: fallback` where the `let` returns `Unit`
  (`ShellActivity.kt:1002–1007, 2274`); `updateLaunchPointIcon` looks up before testing
  the suffix (1381); `for (i in 0 until (ids?.length() ?: 0)) { ids!!… }` (1270–1272);
  `fan()` mutates group extents as a side effect and callers call it for that alone
  (`shell/CardLayer.kt:483–503, 536, 545`); a side effect on the *previous* row hidden
  inside a `LayoutParams.apply` (`shell/Notifications.kt:265–267`); an inverted nested
  `if` in `AndroidNotifications.posted` (94–102); `else if (…) {}` to ignore keys
  (`bridge.js:148–160`); `null.also { call.reply(…) }` (2.3).
- **Dead code:** `luna.px(if (glass) 30f else 30f)` (`shell/ExhibitionLayer.kt:181`);
  `Fn.NONE` (`LunaKeyboard/…/Keymap.kt:11`, `KeyboardView.kt:531`); `main.isLetter() &&
  !main.isDigit()` (`KeyboardView.kt:132`); `if (rec.httpStatus == 0) rec.httpStatus =
  0` (`card/DownloadManager.kt:193`); `LPDK_PDL_REPLY` and `pdlReply`
  (`pdk/sdl-lunacy/lunacy_protocol.h:34`, `SDL_lunacyevents.c:154`, `card/PdkHost.kt:408`);
  a viewport diagnostic evaluated on every page load (`card/AppWindow.kt:282–288`);
  identical ternary branches and a duplicated default in `FilePicker.titleFor`
  (`luna-systemui/FilePicker/source/FilePicker.js:66–73`); a PDK window builds a
  `NetShim`, a `WebSql` and a JS bridge it can never use (`card/AppWindow.kt:215–221,
  303`; `PdkWindow.kt:67` turns JS off) → `by lazy` and an `open val hasPage`;
  `Workbench/shot.sh` drives an app that no longer exists; `Workbench/snap.sh` is
  `lshot.sh` minus a flag.
- **Magic numbers:** `AppServer.kt:375` (64), `Packages.kt:196` (2000),
  `DownloadManager.kt:180` (250), `Db8.kt:1002` (1000), `ActivityManager.kt:481, 463`,
  the request codes in 2.1.
- **Fully-qualified names where an import would do:** `card/AppWindow.kt:164, 495,
  625, 629, 641, 649`; `card/PdkHost.kt:237–238, 373–374, 387, 569–570`;
  `card/MediaIndex.kt:70, 98, 139`; `card/NotificationIcons.kt:23`;
  `card/BrowserViews.kt:722–724`; most of `ShellActivity`'s
  `org.webosarchive.lunacy.card.X` references.
- **Stale text:** the permission error lists `storage, systemSettings or homeLauncher`
  but `contacts` and `calendar` are accepted (`card/LunacyService.kt:325, 278`); build
  it from the one list the `when` uses.
- **Hot-path recomputation that reads as complexity:** `JustTypePanel` rebuilds the
  whole row model on every draw and scroll move, so presses are tracked by coordinate
  (`shell/JustTypePanel.kt:146–147, 426–454, 557–559, 776`) → `rebuild()` on state
  change and `pressedRow: Row?`; `Paint()`, `TextPaint(x)`, `RectF`, `LinearGradient`,
  `SimpleDateFormat` created inside `onDraw` (`shell/StatusBar.kt:128–129, 488, 496,
  516, 561–576, 583, 591`; `CardLayer.kt:634, 638`; `SystemMenu.kt:375, 433, 439`;
  `Notifications.kt:353, 358`; `Launcher.kt:487–496, 618`; `JustTypePanel.kt:608, 624,
  667, 692`) → one reusable per view; `KeyboardView` sizes are recomputed getters on
  every IME layout (`KeyboardView.kt:90–108`) → `val`s; `PageDialog` decodes the same
  PNGs per dialog and per button (`card/PageDialog.kt:89, 139, 146–147`) → a cache;
  `Db8.permission()` runs a SQL query per object while kinds are cached in memory
  (`card/Db8.kt:410–424`) → a map loaded at init.

---

## 6. Security

Ordered by reach. "App" means any installed webOS app; "page" means any web content
inside a card, including third-party pages in the Web app. Each is a few lines.

- **S1. `LunacyNative` reaches cross-origin subframes** — `card/AppWindow.kt:267–268,
  303`. `shouldOverrideUrlLoading` hands off only main-frame navigations, so a
  subframe can load any https site, and the interface is injected into every frame;
  that frame can `call()` the bus as the app, `netSendSync()` with the WebView's
  cookies, `sqlOpen`, `setClipboard`, `pluginStart`, `webViewCreate`. The TouchPad's
  WebKit let an app iframe the web, so refusing is a visible change for any app that
  did. Measure first with an app that does; then codepoet chooses between refusing the
  frame and recording the exposure as a ratchet item (Android cannot inject into the
  main frame only).
- **S2. A service process names its own caller** — `card/ServiceProcess.kt:115–116`.
  `val caller = m.optString("appId").ifEmpty { name }`; every JS service from an
  installed ipk goes through here, so one can call as `com.palm.app.email` and pass
  `LunacyService.ownApp`, `KeyManager` and db8. Rule 10. Fix: honour `appId` only when
  `kind == "native service"` (line 32) or the package lives in the webOS root; else
  `name`; log and drop a mismatch.
- **S3. WebSQL polyfill allows `ATTACH DATABASE`** — `card/WebSql.kt:236–245`.
  `ATTACH '/data/data/org.webosarchive.lunacy/app_webview/Cookies' AS c` then `SELECT
  * FROM c.cookies`. Fix: a `FORBIDDEN = Regex("""^\s*(ATTACH|DETACH)\b""",
  IGNORE_CASE)` beside `LEADS_WITH_ROWS` (336); throw `SqlFailure(SYNTAX_ERR, "not
  authorized")` on a match.
- **S4. Caller strings used as paths.** Wallpaper names (`card/SystemService.kt:372–378,
  349–356`: `File(wallpaperDir, name)` then delete; `../../../var/luna/preferences/…`
  works) → reject `/`, `\`, `.`, `..`. ipk `Package:` names (`card/Packages.kt:83–84`,
  used at 107, 111, 135, 153, 169, including the script that is then run) → validate
  against `[A-Za-z0-9][A-Za-z0-9._+-]{0,127}` in `installNow`. `com.palm.image`
  destinations (`card/ImageService.kt:60–65, 85–87`: anything under the root) → allow
  `/media/internal/`, `/var/`, `/tmp/`. Browser-view image commands
  (`card/BrowserViews.kt:181–187, 350, 410–414`: `deleteImage`, `saveViewToFile`,
  `generateIconFromFile`, `resizeImage` on the whole root) → a `writableWebosFile`
  limited to `media/internal/` and `var/luna/data/`. Sound cache name from an
  app-supplied extension (`shell/Sounds.kt:166`) → drop the extension.
- **S5. Callbacks fired as the system** — `card/ActivityManager.kt:465, 521–523`,
  `card/PowerService.kt:145`. An app's `callback.method` or timeout `uri` fires via
  `bus.call(SERVICE, …, privateBus = true)`, reaching `appInstallService/install`
  (`card/AppInstallService.kt:66`) and `installNoVerify` (200). On webOS the activity
  manager made these with `LSCallFromApplication`: the callee saw the activity manager
  as sender *and* the creator as the application. `Bus.Call` has one id. Measure what a
  callback target saw on the TouchPad; then either add a second field to `Call` (the
  faithful shape) or call as the creator with `privateBus = Bus.privileged(thatId)`.
  Either way the private-bus right is the creator's, not the system's.
- **S6. db8's "related" rule grants prefixes** — `card/Db8.kt:389–390`.
  `owner.startsWith("$caller.")` makes an installed `com.palm.app` the owner of every
  `com.palm.app.*` kind. Fix: reverse direction only for installed package/service
  names, or refuse to install an id that is a strict prefix of an existing one.
- **S7. The Web app serves every app's origin to any page** — `card/BrowserViews.kt:716`.
  Any `*.media.cryptofs.apps` host is served; `AppServer.serve` has no caller check.
  The TouchPad's browser could open other apps' `file:///usr/palm/applications/…`
  pages (help and licence pages did this), so an app opening *its own or another
  app's* page in the Web app is webOS behaviour. The fix is narrower than "own app
  only": serve any app origin for a top-level navigation the Web app was asked to
  open, and refuse an app origin embedded by a third-party web page. Measure the
  help-page case before changing anything.
- **S8. `file:///data/data/…` browses private files in the Web app** —
  `card/BrowserViews.kt:738`. The TouchPad's browser opened `file:///media/internal/…`
  and `file:///usr/…` freely; those are webOS paths and stay. Fix: allow `file` for any
  path that maps through the webOS root (`WebosRoot.mapPaths`) and refuse an Android
  path (`/data/`, `/storage/`, `/proc/`, `/sdcard/`).
- **S9. "Copy to Photos" fetches any scheme** — `card/BrowserViews.kt:431–434`.
  `URL(src).openConnection()` on a page's `file:///data/data/…` image copies a private
  file into shared Pictures. Fix: `Http.connect` (refuses non-http(s) at `Http.kt:60`).
- **S10. Credentials follow a cross-host redirect** — `card/Http.kt:69`,
  `card/NetShim.kt:87–90`. Fix: skip `authorization` and `cookie` when the hop's host
  changes.
- **S11. The exported shell honours development extras from any app** —
  `shell/ShellActivity.kt:655–698`. `launch`/`params`, `layout` (persists a setting
  and rebuilds), `trimMemory` and `ACTION_OPEN_APP` are not gated on `devBuild` as
  `install`/`sh`/`rawsh` are. Fix: gate them; for the dream and pin intents, accept
  only the shell's own PendingIntents.
- **S12. `allowBackup` unset** — `AndroidLuna/src/main/AndroidManifest.xml:51`.
  `KeyManager` keeps passwords and tokens as plain JSON (`card/KeyManager.kt:19,
  80–81`); `adb backup` on Android 5–11 pulls them. Fix: `android:allowBackup="false"`.
- **S13. GL replay trusts the PDK app's lengths** — `pdk/libgles/gl_server.c:101,
  129–130, 842–846, 997–1000`. `padded = (len + 3) & ~3u` wraps to 0 for `len ≥
  0xFFFFFFFD` and the bounds check passes; `app_read` sizes its buffer from the client
  but `glReadPixels` writes `w*h*bpp`; the JNI `replay` never compares `len` to
  `GetArrayLength`. Fix: test `len > r->end - r->p` before `padded`; compute the read
  size from `w,h,format`; clamp JNI `len`.
- **S14. Smaller:** any app's bus call can open Android's write-settings or
  lock-chooser screens (`card/DisplayService.kt:157–167`, `card/SecurityPolicy.kt:263–278`)
  → own apps / private bus (measure the TouchPad first); `PinnedShortcuts.safeIntent`
  checks resolution with the selector still set (`shell/PinnedShortcuts.kt:118–127`) →
  clear it first; `PinShortcutActivity` accepts every pin silently
  (`shell/PinShortcutActivity.kt:26–29`) — codepoet's call; `icon()` decodes an image
  from any `*.lunacy` host a banner names (`shell/ShellActivity.kt:1052`); `WebSql`
  trusts the page's `location.host` for legacy migration (`js/websql.js:268` →
  `card/WebSql.kt:101–103`) → compute from `appId`; cleartext for every host
  (`res/xml/network_security_config.xml:5`) is a documented ratchet item, listed so
  it stays listed.

---

## 7. Bugs and rule-2 violations

- **B1. EAS "last weekday" is wrong when a month overflows five rows** —
  `card/SystemService.kt:238–249`. A 35-slot grid drops day 30/31 of a 31-day month
  starting Friday or Saturday; `coerceIn(0, 4)` then answers the row before:
  `([3,0,5,…], 2024)` gives 24, not 31 (EU DST start, Sunday 31 March 2024). Fix:
  `first = 1 + (d[1] - firstWday + 7) % 7; day = first + 7 * (d[2] - 1); if (day >
  last) day -= 7`. Pin with the test in §9.
- **B2. `CardLayer.settled` is never cleared** — `shell/CardLayer.kt:450, 683, 945–958`.
  `run()` stores `done`; `throwAway()` sets `anim` directly; `onSizeChanged` then
  invokes the *previous* run's completion after a rotation during a throw. Fix:
  `settled = null` in `onAnimationEnd` and at the top of `throwAway()`.
- **B3. U+2028/U+2029 break JS built with `JSONObject.quote`** — `card/AppWindow.kt:369,
  431, 510`, `shell/ShellActivity.kt:1077`, `card/HybridPlugins.kt:119`,
  `card/BrowserViews.kt:177`. A raw line separator is a SyntaxError in Chromium 37; a
  bus reply, a paste or a plugin event carrying one is dropped. Fix: one `jsString(s)`
  that also escapes those two, at all six sites.
- **B4. `returnValue: true` on a save failure** — `shell/ShellActivity.kt:1354`
  (`addLaunchPoint`). Rule 2 says `fail(…)` (defined at 1334) — but measure the
  TouchPad first; if it answered true with `errorText`, that is the reply.
- **B5. Rule-2 stubs in the activity manager** — `card/ActivityManager.kt:112,
  114–116`: `enable`/`disable` reply ok without touching `enabled`; the `associate*`,
  `dissociate*` and `unmapProcess` family are bare `Bus.ok()`. Measure each on the
  device with `luna-send`. `enable`/`disable` did something there, so wire them. If the
  association family answered `{"returnValue":true}` and nothing observable followed,
  the stubs are the behaviour and stay, with a one-line note at the registration
  saying they were measured; only if the device refused them does Lunacy refuse.
- **B6. `resumeDownload` answers as a status query** — `card/DownloadManager.kt:100`.
  The file records that the device reported `canHandlePause:false` (line 57); whether
  it answered a resume with the status or with an error was not recorded. Measure;
  change only if it differs.
- **B7. A db8 `watch` that fires at once stays open** — `card/Db8.kt:672–678` vs
  `888–891`. Fix: `if (method == "watch" && reply == FIRED) call.cancel()` in `handle()`.
- **B8. A malformed `netSend` kills the process** — `card/NetShim.kt:68–70`: the JSON
  parse is outside the `try`, on a pool thread. Fix: move it in; `runCatching` the
  pool body.
- **B9. Email content can crash the main thread** — `card/BrowserViews.kt:694, 649`:
  `.toInt()` on `&#99999999999;`; an empty Content-ID key makes `replace("", …)`
  insert between every character.
- **B10. `Extractfs` accepts an unbounded length** — `card/Extractfs.kt:83–85, 122`:
  `ByteArray(length)` with a page-chosen 2 GB; `OutOfMemoryError` passes `catch
  (Exception)`. Fix: bound it at 16 MB.
- **B11. `mapPaths` has no boundary after the folder name** — `card/WebosRoot.kt:248`:
  matches `/usrx` and `/library`. Fix: `(?![A-Za-z0-9_.-])` after the alternation.
- **B12. `openLaunching` re-posted after removal re-adds the card** —
  `shell/CardLayer.kt:887–898`. Fix: `if (card.group == null) return` after the width guard.
- **B13. `MediaServer`** — `card/MediaServer.kt:52, 64`: `URLDecoder` turns `+` into a
  space; an oversized Range throws past the response. Fix: `Uri.decode`; `toLongOrNull()`.
- **B14. `curl.js`** — `js/services/curl.js:132–140, 172`: headers merged
  case-sensitively; `--connect-timeout` implemented as an idle timeout.
- **B15. PDK C shims** — unchecked `realloc`/`calloc` with the capacity updated on
  failure (`libgles/gl_server.c:132, 136, 243, 318, 602, 620, 631, 965`;
  `client_transport.h:168, 180`; `liblunaservice/lunaservice.c:199, 315, 369, 410, 439,
  509, 529, 722`); `getcwd(NULL, 0)` then `strcpy` of a longer path
  (`libpreload/preload.c:553–557`); the JSON parser steps past the NUL on a trailing
  `\` or a short `\u` (`lunaservice.c:272–283`); PDL strings pasted into JSON
  unescaped and truncation sent anyway (`libpdl/pdl.c:151, 163–164`); a 32-bit length
  wrap in `malloc(h[2] + 1)` (`pdl.c:385–388`, `client_transport.h:49`); socket-name
  truncation and an unlocked shared socket with a `usleep` spin
  (`sdl-lunacy/SDL_lunacyvideo.c:76–77, 92–104`); `/proc/meminfo` rewrite past its
  buffer (`preload.c:248–256`); `REAL()` never checks `dlsym` (`preload.c:198`,
  `execpdk/execpdk.c:186`).

---

## 8. Fragility

- **Delayed callbacks posted on views that `reshell()` replaces** —
  `shell/ShellActivity.kt:733, 745` (`wallpaperView`), `1518–1530` (`shellRoot`),
  `1669` (`cards`). After a fold, `memoryCheck` stops and `memoryState` stays "low";
  a pending `releaseBrightness` is lost; the show-window timeout is dropped. The file
  already uses `window.decorView` elsewhere for exactly this (881, 911). Fix: one
  `Handler(Looper.getMainLooper())` for every delayed shell action.
- **Intent extras not all consumed** — `shell/ShellActivity.kt:656, 686–697`:
  `install`, `launch`/`params`, `exhibition`, `trimMemory` survive in the singleTask
  intent and replay on re-creation (the comment at 677–681 names the hazard). Fix: a
  `takeExtra(intent, key)` that reads and removes, for every key.
- **Missing `@Volatile`** — `card/PdkHost.kt:81` (`control`), `card/AppWindow.kt:186`
  (`hasBrowserViews`); their neighbours `glOut`/`jsOut` are volatile.
- **PDK 2D frames tear** — `card/PdkHost.kt:333–341`, `card/PdkWindow.kt:209–217,
  403–415`: two bitmaps alternate with no sync against `onDraw`. An `AtomicBoolean
  drawing`.
- **Static `pending` browser windows leak** — `card/BrowserViews.kt:86, 816–821`.
- **`Luna` swallows nine decode failures silently** — `shell/Luna.kt:59, 71, 77, 87,
  103, 145, 162, 167, 202`: a mistyped asset path draws nothing with no log line and
  the miss is memoised. One `quietly(what) { … }` that logs.
- **`Db8.syncAccount` logs only the exception class and never calls `done`** —
  `card/Db8.kt:196`.
- **`GroupOverlay.labels` is never cleared** — `shell/GroupOverlay.kt:260`.
- **`CardSplash` starts its animator in `init`** — `shell/CardSplash.kt:55–60`.
- **`KeyboardView` never clears its posted runnables on detach** —
  `LunaKeyboard/…/KeyboardView.kt:351–358, 436–449`; `repeat` can keep deleting after
  the service swaps the view.
- **`bridge.js` cross-origin frames pick a random token range** (1-in-2000 collision;
  `foreign` never pruned; one bit under `Int` overflow at `card/AppWindow.kt:504`) —
  `js/bridge.js:28–34, 47, 97–98`. Hand out bases from a counter in the main frame.
- **`LunaSendServer.serve` reads the request line through a `BufferedReader` and
  then waits for EOF on the raw stream** — `card/WebosRoot.kt:324, 343`.
- **`FilePicker` builds a CSS `url()` with `encodeURI` only** — `FilePicker.js:118`:
  `trip (1).jpg` gets no thumbnail. Quote it.

---

## 9. Tests: where to start

There are none (no `src/test`, no `testImplementation`). Plain-JVM, `junit:junit:4.13.2`
and `org.json:json` where a reply is parsed; each is pure or one small lift from it.

| Function | Where | Pin |
|---|---|---|
| `easDayOfMonth` | `card/SystemService.kt:238` (lift to `internal`) | `([3,0,5,…],2024)` → 31 (today 24); `([3,0,2,…],2026)` → 8; `([10,0,5,…],2026)` → 25 |
| `WebosRoot.mapPaths` / `relativePath` | `card/WebosRoot.kt:232, 140` (pass `rootPath`) | `"ls /usr/bin"` → `ls /r/usr/bin`; `"/usrx"` unchanged (today mapped); a quoted `file:///usr/…` unchanged |
| `isNewerVersion` | `card/AppRegistry.kt:129` | `("1.2.10","1.2.9")` true; `("1.0","1.0.0")` false; `("1.0.1b","1.0.0")` — pin whatever it does |
| `HtmlTransforms.selfClosingTags` | `card/AppServer.kt:595` | `<script src="x.js" />` expands; `<br/>` unchanged; a `<div/>` inside a script string unchanged |
| `JsTransforms.sloppyMode`, `CssTransforms.mouseTargetIgnore`, `borderImageNeedsStyle` | `card/AppServer.kt:673, 722, 737` | same-length rewrite of `"use strict"`; the `pointer-events` pair |
| `Bus.redact` / `privileged` / `error` | `card/Bus.kt:174, 166, 192` | a password with an escaped quote redacted; `com.palm.app.email` privileged; `error("x")` → `returnValue=false, errorCode=-1` |
| `Portability.nappId` / `emulate` | `card/PdkHost.kt:478, 480` | `nappId(501)` → 601; a list with 601 → `Device(501,480,800)`; 701 → null |
| `FormFactor.Clues.kind` | `card/FormFactor.kt:103` | phone / tablet / aspect-decides |

After those: `KineticScroll` once extracted (2.2), `Db8` query translation against an
in-memory SQLite, and `WebSql.statement` with the ATTACH refusal.

---

## 10. Build, scripts and repository

**Clean:** no secrets or signing material tracked (`keystore.properties` and
`local.properties` are untracked and ignored; the former holds paths and an alias);
no build outputs or editor files; no dangling `CoreApps` symlinks; patch series and
`CHANGES.md` agree in all three runtimes; every manifest permission is used; exported
components are all guarded or required; the ProGuard keep rule is narrow.

- **Unverified downloads and `curl` without `-f`** — `AndroidLuna/tools/build-pdk.sh:30,
  35, 47, 98, 136, 139, 150, 162, 176, 193, 227, 416, 440`; `build-busybox.sh:30`. Only
  `openal-soft` is checked (343). Without `-f` a 404 page is cached as a tarball; line
  30 pipes through `zcat` with no pipefail, so a failed fetch caches an empty
  `Packages` index. Fix: `curl -sfL` to a `.tmp` then `mv`; a `sha256sum -c` per
  tarball; read `SHA256:` from the Debian/Termux index for the `.deb`s.
- **NDK variables** — `fetch-assets.sh:167` (r28c as `ANDROID_NDK`),
  `tools/build-node.sh:27` (needs r21, reads the same name), `build-busybox.sh:23`
  and `build-pdk.sh:15` (read `NDK`). `BUILDING.md` sets `ANDROID_NDK` for r28c, so
  node's configure fails. Fix: `ANDROID_NDK_R21` for node; `${NDK:-${ANDROID_NDK:-…}}`
  for the others.
- **Workbench scripts only work from inside `Workbench/`** — `tpshot.sh:3–4`,
  `tplaunch.sh:3`, `tpburst.sh:5–6`, `tpkey.sh:4`, `lshot.sh:3`, `snap.sh:2`, while
  `Docs/dev-workflow.md:107, 187, 190, 196, 246` run them from the root. `tpclose.sh`,
  `seams.sh`, `cdp.sh` already `cd "$(dirname "$0")"`. Also: `cdp.sh:10` uses `adb
  shell ps`, which lists only the shell user's processes from Android 8 → `ps -A`;
  `TP_DEVICE` default typed four times; `touch.py:9` and `seams.sh:27` bake in a
  device path and a status-bar height; `seams.sh:26` unescapes JSON with a sed chain
  where `return out;` would do; `shot.sh` and `snap.sh` are dead.
- **Gradle** — `afterEvaluate` nested inside `onVariants` (`build.gradle.kts:27`) works
  by AGP ordering luck → `tasks.matching { … }.configureEach`; `kotlinOptions {}` is
  deprecated in Kotlin 2.x (`AndroidLuna/build.gradle.kts:34`,
  `LunaKeyboard/build.gradle.kts:34`); `versionName = "0.8.4"` typed in two modules
  with `build.sh:95` reading only one, and the `buildNumber` block copied verbatim →
  one property in `gradle.properties`; `targetSdk = 24` in `defaultConfig` (26) is
  overridden by both flavours and its six-line comment describes what the flavours
  decide.
- **`fetch-assets.sh` and tools** — the calendar patch loop has no empty-glob guard
  (117–118; Enyo 22 and Mojo 80 have one); Mojo patches apply with fuzz (81; the other
  two pass `--fuzz=0`); build failure reasons go to stdout, which line 585 discards
  (`build-pdk.sh:65, 71, 78, 125, 171, 187, 188, 200, 343, 352`; `build-busybox.sh:56`)
  → `>&2`; `WC=$(mktemp -d)` leaks on failure (491–501) → `trap`; `make-enyo-patch.sh`
  is Enyo-only so Mojo patches are made by hand and carry raw `diff -u` timestamps →
  a framework argument; `build.sh --help` cuts off its last header line (23).
- **GL server and shims** — hard-coded `/data/data/org.webosarchive.lunacy/files/pdk/…`
  (`gl_server.c:746, 831, 954`), wrong for a secondary user or an applicationId
  suffix; opcodes `#define`d on the client and bare hex on the server (863–869);
  `LUNACY_PDK_MEDIA` parsed four times with differing case rules (`preload.c:105, 123,
  443, 579`); `read_all`, `type_size`/`attrib_type_size`,
  `upload_bytes`/`image_bytes_aligned` and the screen defaults each duplicated across
  the shims → one `lunacy_protocol.h` and one `lunacy_io.h` included from both sides.
- **23 superseded probe `.ipk`s tracked** in `Workbench/probe/` → remove; ignore `*.ipk`.

---

## 11. Suggested order of work

Two tracks. The first is what was asked for; the second is short and should not wait
for it.

**Track A — simpler, clearer code.** Each batch is self-contained, verified by a build
and a device check that the touched feature still works, and changes nothing on screen.

1. **Readability sweep** (§5): misplaced KDoc, interleaved fields, shadowed names,
   dead code, imports, magic numbers. One file per commit. No behaviour change, and
   it makes every later step easier to read in a diff.
2. **One convention per row** (§4.1) and the **bus contract** (§4.2): one grep each,
   mechanical edits, one `check`.
3. **Types for strings and flags** (§4.3) and **page hooks named once** (§4.4).
4. **One helper, many sites** (§3.1 and §3.2): one table row per commit, the helper
   first, then each site; the `animateValue`, `inside`, `registerOwn` and db8 `write`
   rows pay the most.
5. **In-place splits** of `buildShell`, `registerServices`, `launch` (with
   `Placement`), `PdkHost.start`, `AppServer.serve`, `Configurator.run`, the two long
   `onTouchEvent`s (§2.6): private functions in the same class, no moves yet.
6. **The decompositions** (§2.1–2.5): one new class per commit, each a move with no
   edit, in the order listed. `compat.js` and `Launcher` first (fewest dependants),
   `ShellActivity` last (most). Batches 1–5 will have shrunk it by then.
7. **Tests** (§9) alongside whichever batch touches the function; `KineticScroll` and
   `easDayOfMonth` first.

**Track B — before the release.** §6 S2–S10 and S12, §7 B1–B3 and B7–B10, the build
download checks in §10. Each a few lines; do them first or in parallel with batch 1.
S1 waits on codepoet's iframe decision.

### To measure on the TouchPad before the corresponding change

Each is one `luna-send` or one app launch on the reference device, and the answer
decides the change; "Fidelity first" above has the reasoning.

- B4: what `addLaunchPoint` answers when the save fails.
- B5: what `enable`, `disable` and each of the `associate*`/`dissociate*`/`unmapProcess`
  methods answer, and whether anything observable follows.
- B6: what `resumeDownload` answers for a known ticket.
- §4.3: db8's reply to a wrong-typed parameter on `put`, `get`, `del`, `find`.
- S1: whether an app's page can iframe a web origin, with an app that does.
- S5: what a service receiving an activity callback sees as the sender and the app id.
- S7/S8: the Web app opening another app's `file:///usr/palm/applications/…` page.
- S14: whether `setDevicePasscode`/`matchDevicePasscode` answer on the public bus.

### Measured on the TouchPad (2026-11-03)

Measured with `luna-send` on the reference TouchPad (webOS CE 3.1.0), except S14, which needed
a probe app, and B4, read from LunaCE's source. The three changes were tested on the Galaxy
Z Fold3 on 2026-11-04 over the novacom relay's `luna-send` (private, and `-P` for public), each
answer the device's word for word; they go into the release after 0.9.1.

| Item | What the device does | So |
|---|---|---|
| B4 | A failed save answers `{"returnValue":true,"errorText":"Failed to save launch point"}`, true and all (LunaCE `servicecallback_addLaunchPoint`); an unknown id is "Unable to find id: <id>" | Lunacy already answers both; no change |
| B5 | `enable` and `disable` are **global** switches for the whole activity manager (not per activity), and answer `{"returnValue":true}` to `{}`. Every `associate*`, `dissociate*` and `unmapProcess` answers `{"returnValue":true}`, even for an activity that doesn't exist | The association stubs are the device's behaviour: keep, with a note. `enable`/`disable` done 2026-11-04 as Open webOS has them: private bus only (error 13 for others), and `disable` holds new activities until `enable` (tested on the Fold3) |
| B6 | `resumeDownload` answers `{"subscribed":false,"returnValue":false,"errorCode":"Ticket provided does not correspond to an interrupted transfer in history"}` for a finished, cancelled or unknown ticket, and "Missing ticket parameter" without one. `pauseDownload` (which Lunacy didn't register) answers "Ticket provided does not correspond to a downloading transfer", and on a live download (7.4 of 11.6 MB in) it **stops the transfer**: its state becomes "cancelled" | Change: refuse resumes as the device does; register `pauseDownload`, which stops a live download |
| §4.3 | A wrong-typed parameter is code 22, "invalid parameters: caller='<id>' error='invalid type for property '<name>''" (`put` objects "x" or [5], `get` ids "x", `del` ids 5, `merge` objects {}); a `query` that isn't an object is "required property not found - 'from' for property 'query'"; a `from` that isn't a string, "invalid type for property 'from' for property 'query'" | Change: typed parameters with these texts in place of "internal error" |
| S5 | The callee of an activity's callback sees `caller` and `sender` = `com.palm.activitymanager`; the creator appears only in the payload's `$activity.creator` (`appId`, or `anonId` for a shell) | Lunacy already calls as the activity manager; no change |
| S7/S8 | The Web app opens `file:///usr/palm/applications/<app>/index.html` and a `/media/internal` file; `/etc/palm/luna.conf` gets "Cannot open MIME type" (refused by type, not by path) | Any fix may refuse only Android paths. Done 2026-11-04 (codepoet's call), tested on the Kyocera: a `file:` URL is a webOS path or refused; a website's loads of an app origin are refused |
| S14 | `com.palm.systemmanager` has no public side: an `org.webosarchive` app gets "Service does not exist: com.palm.systemmanager." from every method, `getDeviceLockMode` included | Change: private-bus only |

### Decided (codepoet)

- S1: no decision needed now; a recorded ratchet item that goes with the move to
  `WebMessageListener` (2026-11-03).
- S14: pins stay silent (2026-11-03). Of its smaller items (2026-11-04): Android's
  write-settings screen opens only for a private caller, and WebSQL's migration takes the
  app's origin from its id (both done); a notification icon from another app's origin stays.
- §3.3: the corner clamp ported to `Luna.drawNineSlice`; the keyboard stays independent
  (2026-11-03).

Still open: whether `Docs/` is where this document should live, or whether it goes once acted on.

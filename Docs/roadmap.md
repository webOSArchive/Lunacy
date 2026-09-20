# Roadmap

Priority order: Enyo 1 → Mojo → JS services → PDK native. Each phase has an exit criterion,
and a phase is finished when its criterion is met, not when its task list runs out.

## Where things stand (2026-09-20)

Apps now install from the App Museum and run: Apollo plays music, Plex plays direct and
transcoded video, Sound Cloud Player streams through its JS service, AccuWeather and USA Today
show live data. Per-app results and every fix, with the layer it landed in, are in
[fix-log.md](fix-log.md).

- **Spike 1:** done, on Chromium 37 and 64, with a TouchPad reference probe. The probe
  (`Workbench/probe`, 0.0.9 on the TouchPad) has since measured uncaught errors, the network
  contract, media with an empty `src` and connection timeouts; `db8probe.sh` and
  `db8watch.sh` measured db8.
- **Corpus survey:** started. A scan of codepoet's package mirror (4,318 packages) found 121
  with JS services and 45 with db8 configuration files. The ranking by `palm://` URIs is
  still to do.
- **Phase 0 (skeleton):** done.
- **Phase 1 (Enyo runs):** mostly done.
  - Done: touch-as-mouse input and flicks, `palmGetResource`, the PalmSystem surface,
    Prelude, the border-image transform, window types, the app menu (relaunch with
    `open-app-menu`, `PalmSystem.isActivated`), the keyboard (automatic and manual modes,
    card resize), relaunch parameters, and the network shim (cross-origin XHR as the
    TouchPad sent it, 9 s connect timeout, bundled Mozilla roots).
  - Compat fixes from real apps: uncaught errors reach only the console, the TouchPad's user
    agent and version (webOS CE 3.1.0), no service workers,
    `webkitCancelRequestAnimationFrame`, media with an empty `src`, and `file:///media/internal`
    URLs.
  - The Enyo 1 contract was read end to end on 2026-09-20 - every `PalmSystem` member, every
    `Mojo` call, every service and file the framework itself reaches for - and the gaps it
    turned up are closed: g11n's formats, `enyo.WebView`, `applicationManager/open` and
    `getAppBasePath`, the headset/media/display startup calls, and `tellurium_config.json`.
  - Not yet: a test suite, runs on the factory WebView 37, and the border-image seams in Enyo
    dialogs (see fix-log.md).
- **Phase 2 (bus):** subscriptions and cancellation work. Answering: applicationManager
  (launch, open, listApps, Preware installs), connectionmanager, preferences
  `systemProperties/Get`, activitymanager (foreground activities), and db8 and tempdb
  ([db8.md](db8.md)). Everything else returns webOS's own errors. Not yet: keys, display,
  zeroconf (codepoet: never worked, skip), background activities, the media indexer.
- **Phase 3 (shell):** well along. Card view, launcher, dock, status bar, banners (now
  matching the TouchPad), dashboards and popup alerts. The launcher has the launch glow and
  edit mode: reordering, moving icons between tabs (with the tab highlight), removing
  installed apps, and arranging the dock (add, swap, reorder, drag up and out to remove),
  checked against the TouchPad at the same scale.
- **Phase 4 (App Museum):** installs work through the Museum's own Preware route, from
  `http` and `https`; apps can be removed from the launcher; packages' db8 kinds and
  permissions register at install. Not yet: the compatibility score, activities from
  packages.
- **Phase 6 (JS services):** brought forward and running. Node 12 (nodejs-mobile 0.3.3) per
  service package, Palm's mojoservice and foundations from the TouchPad, webOS paths, a curl
  in JS, and a loopback media server for services' HLS. See "JS services" in the
  architecture doc.
- **The LunaCE spec revision landed.** [luna-shell-reference.md](luna-shell-reference.md) now
  uses the reference TouchPad's own `/etc/palm` values as authoritative and marks what was
  measured on the device. Where the doc and the code disagree, the code's comments give the
  measured value.
- **Ready to show (2026-09-20).** The project is laid out as one Gradle root building two
  APKs into `out/` - `AndroidLuna` and `LunaKeyboard` - with the framework forks beside them
  in `LunaRuntimes/`. A fresh install starts with Clock on APPS, the App Museum (now bundled)
  on DOWNLOADS, nothing on GAMES and every settings app on SETTINGS, and a splash carries the
  logo through the first run's unpacking. [BUILDING.md](../BUILDING.md) is the developers'
  entry point; the README's "Using it" is the owners'.
- **The keyboard, and somewhere to try it (2026-09-20).** A companion APK in
  [LunaKeyboard/](../LunaKeyboard/README.md) puts the TouchPad's own keyboard on Android, scroll ball
  and all; it is separate from Lunacy and optional. The "Just type" pill is now a real input
  field so there is somewhere in the shell to type: Return is swallowed until Just Type has
  something to do.
- **Settings (2026-09-20).** Three cases, described under "Settings" in the architecture doc:
  Lunacy's own app where webOS's reported a webOS device, Palm's own app where Lunacy can
  answer it, and an icon that opens Android's screen where the setting belongs to Android.
  - **Device Info** is Lunacy's own, in Enyo 1 (Palm's is Mojo), keeping Palm's id and icons.
    It reports the device, Android, the WebView, Lunacy's version, Node and the display, from
    `palm://org.webosarchive.lunacy/system/getEnvironment` - Lunacy's own service name.
  - **Screen & Lock** is Palm's, unchanged: `com.palm.systemservice` (preferences and the
    wallpaper store) and `com.palm.display/control` (Android's brightness and screen-off
    timeout) answer it, and Change Wallpaper works end to end.
  - **A file picker** at webOS's own system-UI path, so `enyo.FilePicker` works in every app.
    Android's shared folders are mapped into `/media/internal` under webOS's own names, so it
    shows the user's real files: Papyrus now imports and reads an ePub from Downloads.
  - **Wi-Fi** and **Sounds & Alerts** are shortcuts to Android's settings.
  - An app its owner enables now shows its own exhibition view: the shell launches it with
    `dockMode`, which is how webOS told an app to show that view rather than its ordinary one.
  - **Help** is Palm's, unchanged, and runs; its articles need a path webOS Archive's help host
    doesn't serve yet (see fix-log.md).
  - Palm's settings apps stay in `local-assets/` for now: shipping Palm's code in the APK is
    codepoet's call, like Mojo's.
- **Mojo runs (2026-09-20).** Flying Toasters, a Mojo app installed from a package, runs and
  works as an Exhibition app. Phase 5 is open early; see below.
- **Exhibition (2026-09-20).** Palm's Clock and Exhibition apps are bundled and run unchanged.
  The Exhibition app's Start Exhibition button puts the shell into webOS's dock mode, where it
  draws the Time face itself as LunaSysMgr did - all four of the reference TouchPad's faces,
  swipeable, and an app's own exhibition view when it has one: AccuWeather, Flying Toasters
  and Glimpse all exhibit.
- **Exhibition is Android's screen saver (2026-09-20).** Lunacy offers itself as a Daydream;
  choosing it means the tablet on its charger shows what a TouchPad on its Touchstone showed,
  the chosen app included. Verified on the reference tablet: the dream starts the shell, the
  gear in Android's settings opens Palm's Exhibition app, coming off the charger leaves the
  mode, and leaving returns to whatever the screen saver interrupted.
- **Next candidates:**
  - the remaining startup services (keys, display) and the media indexer's db8 kinds;
  - the rest of the settings apps: Date & Time, Language, Backup, Accounts, Updates, Location
    (each is one of the three cases above);
  - `activitymanager`'s scheduled activities, which is what the Clock's alarms need;

  - the border-image seams in Enyo dialogs;
  - a test suite from the apps in fix-log.md, run on WebView 37 and 64;
  - card stacks/groups;
  - the status-bar system menu (wifi, brightness, battery, rotation lock), which is QML in
    LunaCE, per spec §4.3;
  - the card-loading splash (§2.4);
  - Just Type search (the pill takes text, but nothing acts on it yet);
  - tuning scroll inertia;
  - the one remaining seam inside the Sampler's radio-button graphic.

## Before phase 0: two checks

Both are cheap, and both can change the plan. **Spike 1 is done, on Chromium 37 and 64, plus a
reference probe on a real TouchPad: see [spike-1.md](spike-1.md).**

- **Device spike.** Test on the first target, an HP 10 G2 tablet running Android 5.0.1: a
  bare WebView, stock Enyo 1.0 and three real apps. So far the evidence is desktop Safari
  with a mouse; the target is Android Chromium with touch, which is where wcl struggled.
  Run the same apps on the factory WebView 37 and on the updated WebView (about Chromium
  95), and pick the baseline. The spike also checks:
  - whether WebView 37 can reach current HTTPS sites, including the App Museum;
  - how many live cards 1 GB of RAM can hold.

  A day or two of work shows how narrow phase 1 really is.
- **Corpus survey.** A static scan of the App Museum's packages: framework used (Enyo 1,
  Enyo 2 or bundled, Mojo), framework script paths, `palm://` URIs by frequency,
  `openDatabase` use, bundled JS services and PDK plugins. It sets the service build order,
  and sizes each phase with data instead of guesses.

**Exit:** a written result for each, and the roadmap adjusted to match.

## 0: Skeleton

- Android project: Kotlin with Android Views, `minSdk 21`, and AndroidX pinned to releases
  that still support API 21. One full-screen activity hosts the shell scaffold, and it is
  installed over `adb` on the HP 10 G2.
- The card host serves one bundled Enyo app from its own origin, at its webOS filesystem
  path, so relative framework paths resolve.
- Framework route: `/usr/palm/frameworks/enyo/*` serves an Enyo fork, forked from
  [enyojs/enyo-1.0](https://github.com/enyojs/enyo-1.0) and vendored or added as a submodule.
- The bridge: a JavaScript interface, with the bridge script added to each app's HTML at
  serve time. It provides `PalmSystem` and `PalmServiceBridge`, plus the bus router with
  honest errors and the coverage log. All of Lunacy's JS runs on Chromium 37.

**Exit:** a bundled Enyo sample launches from an icon into a card and renders.

## 1: Enyo runs

Start from stock Enyo 1.0. It already runs many popular apps in modern Safari, so the work
is expected to be narrow:
- differences between WebKit and Chromium: border-image, `-webkit-box`, scrollers, touch and
  mouse events;
- making the `PalmSystem` calls that apps expect actually work.

Other work:
- The services apps call while starting (system preferences, locale and time, connection
  status, device info), since apps rarely handle errors from them.
- App lifecycle: headless root windows, `window.open` for extra cards, launch parameters
  and relaunch, `stageReady`, activate and deactivate, back as Escape, the keyboard.
- Global compat layer: the webOS input model (touch delivered as mouse events), injected
  polyfills, serve-time CSS transforms (starting with the border-image rule from spike 1), and the network shim for cross-origin and
  plain-`http` requests.
- `PalmSystem` complete enough for Enyo's own code path (`palmGetResource` with JSON hints,
  `simulateMouseClick`), the `enyo.WebView` control, and the system files apps read.
- Start the Enyo fork from stock, and import the differences the TouchPad's shipped copy
  has where they matter: small code changes, the `resources/` localization folders and
  `lib/networkproxy`. See [spike-1.md](spike-1.md).
- A change log in the Enyo fork, and a fix log recording where each fix landed (framework,
  compat layer, or nowhere general): [fix-log.md](fix-log.md).
- A compatibility test suite: the Enyo samples, codepoet's open apps, and at least one each
  of: a multi-window app, a `noWindow` app, an app that bundles its own Enyo, an Enyo 2 app.
  The suite runs on both the factory and the updated WebView until the spike settles the
  baseline.

**Exit:** the test suite renders and responds correctly on the HP 10 G2 in both orientations,
with no per-app code, and the fix log shows fixes landing in general layers. Phone form
factor is covered too (on a small Android 5 phone, or an emulator at phone size).

## 2: Bus core

- Router (from phase 0) gains cancellation and subscriptions that deliver updates.
- Services in the order the corpus survey and coverage log give. Expected: applicationManager,
  systemservice, connectionmanager, power, preferences.
- db8 on SQLite: kinds, queries, put/merge/del, watch. Its depth follows the survey; third-
  party apps may lean on WebSQL and localStorage far more than on db8.

**Exit:** the test-suite apps that store data keep it across restarts, and no call returns
a faked success.

## 3: Shell

**Brought forward (2026-09-19).** The App Museum already runs and looks right, so shell
work started ahead of phases 1 and 2. The shell is the strongest signal that this is webOS.

- **Reference sources:**
  - [LunaCE](https://github.com/webOSArchive/LunaCE) (Apache 2.0): the source of the build
    running on the reference TouchPad, and of the shell images Lunacy uses.
  - [Docs/luna-shell-reference.md](luna-shell-reference.md): a spec drawn from that source.
  - Screenshots from the TouchPad.
- **Done in the first pass:**
  - status bar (title, wifi, battery, clock);
  - wallpaper;
  - live cards in a card view, with LunaCE's shadow and rounded corners;
  - tap to maximize;
  - swipe up from the bottom edge to minimize, with the card following the finger;
  - horizontal card scrolling;
  - flick up to throw a card away;
  - the "Just type" pill;
  - the quick-launch dock;
  - app launch and relaunch through `applicationManager/launch` and `open`;
  - `noWindow` apps with headless roots;
  - `window.open` opening new cards;
  - `Mojo.stageActivated`/`stageDeactivated`.
- **Second pass:**
  - geometry and timings tuned to the reference TouchPad's configuration (card ratios
    0.55/0.50, easing curves, throw-away thresholds);
  - the launcher (tabs, icon grid, paging, open/close animation);
  - the dock and "Just type" pill to spec;
  - immersive full screen;
  - TouchPad proportions;
  - back button as home;
  - "Lunacy" as the carrier text;
  - app content in TouchPad-sized CSS pixels;
  - dock icons level with the launcher button (54 px below the bar top, measured on the
    TouchPad).
- **Third pass:**
  - whole-number shell and content scale (no seams);
  - Prelude served to apps;
  - flick inertia;
  - status bar backgrounds, separators and ▾ per LunaCE;
  - live battery and wifi;
  - notifications: banners, dashboards with the drop-down, popup alerts;
  - the Lunacy launcher icon.
- **Needs fine tuning:** scroll inertia in apps. Flicks now coast (codepoet: "not perfect,
  but feels better"). Candidates:
  - the order of the flick relative to `mouseup`;
  - Android touch sample rate versus the TouchPad's;
  - Enyo's fixed 20 ms simulation step per animation frame.
- **Next:**
  - card groups/stacks;
  - banners and the dashboard;
  - landscape and portrait layouts;
  - status bar menus.

Planned scope:

- Card view with several live cards; throw-away and minimize gestures; launcher.
- Status bar, banners and the notification dashboard, fed from the bus. Dashboard and popup
  alert windows opened by apps.
- The gesture bar, with immersive mode on Android 5.
- The card memory policy, tuned to 1 GB of RAM.
- Rotation and resizing; phone and tablet layouts.

**Exit:** using several apps at once feels like a TouchPad. codepoet is the judge.

## 4: App Museum

- Bundle the App Museum's own Enyo app (it already runs in modern Safari).
- The `.ipk` reader, ported from wcl, and the package manager.
- Answer the Museum's install requests: `applicationManager/open` for Preware's id with
  `{type: "install", file}`, handled by the package manager.
- At install, note what an app needs that Lunacy lacks (a JS service, a PDK plugin), so the
  compatibility score can say why an app falls short.
- A per-app compatibility score from the coverage log, fed back through the catalog service
  (which webOS Archive owns).

**Exit:** a user can find, install and run popular Enyo apps from the App Museum, all
inside Lunacy.

## 5: Mojo

**Started 2026-09-20**, ahead of its phase: an Exhibition app from the Museum (Flying Toasters)
turned out to be Mojo, and it runs. Palm's own Mojo is served from the reference TouchPad with
its own patch series and NOTICE, as Enyo is; see "Mojo" in the architecture doc for how it is
packaged and what had to change. One app is not a phase, though - the exit criterion stands.

- ~~Modernized Mojo at `/usr/palm/frameworks/mojo/…`, handled like Enyo, with its own change
  log.~~ Done in outline.
- Multi-stage windows mapped to cards; scene transitions.
- The services Mojo apps lean on that Lunacy hasn't got yet.

**Exit:** a Mojo test suite of popular App Museum apps runs without per-app code.

## 6: JS services (brought forward, running; see the architecture doc)

- An embedded Node runtime (e.g. nodejs-mobile); app services register on the bus.

**Exit:** popular App Museum apps that ship a JS service work, service included, without
per-app code.

## 7: PDK native

- A glibc/SDL 1.2/PDL loader over Android (apkenv in reverse). The 32-bit ARMv7 test devices
  can run the original binaries; recompile only for 64-bit-only SoCs.

**Exit:** a handful of well-known PDK apps run at full speed on a current device. The
corpus survey says how many apps this phase can reach, and whether it is worth doing.

## Later

- **Newer Android targets, tightening security at each step.**
  - Move the bridge to `WebMessageListener` restricted to Lunacy's origins, with
    document-start scripts.
  - Recover through `onRenderProcessGone` (Android 8 and later).
  - Handle the conflict between the gesture bar and Android's gesture navigation (Android 10
    and later).
  - Add the WebSQL polyfill when a target's WebView drops WebSQL.
  - Move to current AndroidX once `minSdk` rises.
- Launcher mode: Lunacy as the Android home screen, with Android apps shown alongside webOS
  cards.
- Just Type (universal search).

## Open questions


- **Apps that check `location.protocol`.** A TouchPad reports `file:`, and Lunacy can't. The
  corpus survey should count apps that check it.


## Decided

- **Lunacy answers as a webOS device, fully** (codepoet, 2026-09-20): a TouchPad on a
  tablet-sized screen, a Pre3 on a phone. The environment is most like a TouchPad, and with
  that hardware dying off this is how it lives on. One place decides it (`DeviceProfile.kt`)
  and every app-visible surface follows: `deviceInfo`, the user agent in the page and on the
  wire, the system properties, and `X-Palm-Carrier`. The serial and `nduid` are generated per
  install rather than copied from a real device. Lunacy's own Device Info, the shell and the
  bus's service names stay truthful - the spoof is for apps, not a claim to be webOS. The
  Pre3's values are the community's record and still need measuring on hardware.
- **The device id is derived and can be carried over** (codepoet, 2026-09-20): `nduid` and the
  serial come from this device's own hardware ids, so a reinstall gives the same id back and
  the services' analytics don't see a new device; and Device Info lets its owner type in a
  TouchPad's id, so a migration off dying hardware keeps that device's history. No random
  regeneration, for the same reason.
- **webOS version:** Lunacy reports webOS CE 3.1.0, the community-supported version, as the
  reference TouchPad does (codepoet, 2026-09-19); a phone would report the Pre3's 2.2.4.
- **Palm's code ships** (codepoet, 2026-09-20): Palm's own settings apps go in the APK with a
  NOTICE, as abandonware, like Mojo, the Prelude fonts and the TouchPad wallpapers. Nobody has
  asserted rights in nearly a decade of webOS Archive doing the same.
- **No service workers:** `navigator.serviceWorker` is hidden, as the TouchPad's WebKit had
  none (codepoet, 2026-09-19).
- **App Museum:** bundle the real Museum app rather than write a catalog client. It installs
  through Preware's `applicationManager/open` contract (found in spike 1).
- **Shell styling:** LunaCE, with TouchPad 3.0.5 as the fallback reference.
- **HP fonts and wallpapers:** ship them as abandonware, like Mojo (codepoet, 2026-09-19).
- **Android back button = TouchPad home button:** webOS tablets had no back button. Back
  follows LunaCE's home-button order: close what's open on top, else minimize the
  maximized card, else toggle the launcher. A hardware keyboard's Escape still reaches apps
  as webOS back (codepoet, 2026-09-19).
- **First platform:** Android 5.0.1 on the HP 10 G2. Security is tightened as later Android
  versions are targeted.
- **Serve-time transforms:** global, mechanical transforms of app files are allowed; per-app
  edits are not.
- **Origin:** `https://<appid>.media.cryptofs.apps`, serving
  `/media/cryptofs/apps/usr/palm/applications/<appid>/`. It matches the TouchPad's path, and
  the hostname check apps use (TouchPad hostname:
  `.media.cryptofs.apps.usr.palm.applications.<appid>`).
- **WebView range:** Lunacy supports every WebView Android 5 can run. It is tested on
  Chromium 37 and 64, with global transforms for the differences. Chromium 95 is added
  when it can be obtained.

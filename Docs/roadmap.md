# Roadmap

Priority order: Enyo 1 → Mojo → JS services → PDK native. Each phase has an exit criterion,
and a phase is finished when its criterion is met, not when its task list runs out.

## Where things stand (2026-09-22)

Apps now install from the App Museum and run: Apollo plays music, Plex plays direct and
transcoded video, Sound Cloud Player streams through its JS service, AccuWeather and USA Today
show live data. Per-app results and every fix, with the layer it landed in, are in
[fix-log.md](fix-log.md).

**2026-09-22, the evening: [luna-deltas.md](luna-deltas.md)'s A and B lists, released as the
0.2.0 community preview.** Every item was measured on the reference TouchPad before and after,
with two new probes (`winprobe`, which drives every app-facing window call from its launch
parameters, and `headprobe`, a `noWindow` app). What came of it:

- *For apps:* full-screen cards, a card fixing the screen's orientation, `statusBarColor`,
  banner and notification sounds, low-memory notices, paste/copy/cut through Android's
  clipboard, and a slow app's card waiting in the card view with its icon pulsing until the app
  is ready - each as the device does it, several of them corrected by the measurement.
- *The card view:* cards dim when they stop being active, an app's cards stack in fanned
  groups, a held card can be walked through and between groups, and a card pulled off the
  bottom closes. Found on the way: Chromium 37's WebView draws nothing when turned the "wrong"
  way or faded, so those cards are drawn from a layer.
- *The status bar:* animated as LunaSysMgr did, with the Bluetooth, rotation-lock, mute,
  airplane and VPN icons, and the start of the system menu (date, battery, brightness).
- *Dashboards:* the drop-down scrolls, a dragged row shows its backing, Enyo dashboards swipe
  their own layers.
- *The launcher:* installed apps on DOWNLOADS, the device's column spacing and scrolling, the
  empty page, install progress, and LunaCE's tab editing and app folders.
- *Just Type:* the pill opens it; launch results and "Search DuckDuckGo" (the rest of Just
  Type is for later, codepoet's call).
- *Phone-sized apps* get their chrome: title bar, back strip, keyboard button, corners.
- *Sounds:* LunaSysMgr's own, copied from the device.

Four A/B items turned out to be the device doing nothing, and one (a card at launch for a
headless app) not to happen on the device at all; all are in luna-deltas.md's section D so
nobody chases them. Still open there: A9 (what a free card reports as its orientation, which
needs the TouchPad turned by hand) and A10 (a page's first script sees a 980-px layout).

**2026-09-22, a day on Mojo fidelity.** codepoet ran the suite beside the reference TouchPad
and reported what looked wrong, three times over as each round was fixed. Eleven differences,
nine causes, and the pattern worth keeping is that **almost none of them was the engine**:
they were Lunacy answering a question wrongly, a webOS idea this engine has never had, or -
three times - a fix of mine from earlier the same day.

*What Lunacy was telling apps, wrongly*

- **`window.innerWidth` was a pixel wider than the page was laid out into**, and `screen` came
  back in Android's density-independent pixels. On a device every one of them was the card's
  own pixels. reddit sizes a pane from `innerWidth`, so one pixel dropped the whole article
  below the fold.
- **`PalmSystem.windowOrientation` answered "free"**, which is not an orientation: LunaSysMgr
  reads the window's own out of that property and writes the app's *request* into a different
  one. Mojo asks for "free" on every app's behalf, so every orientation branch in every Mojo
  app fell through, and drPodder's playback slider was never sized.
- **`uiRevision` was read and ignored**, so an app written for a Pre was stretched across a
  tablet card instead of running in the phone-sized one a TouchPad gave it. Most of the
  catalogue predates the TouchPad, so this is the common case.
- **A launching card was blank** where webOS held its space with the app's own icon.

*webOS ideas this engine hasn't got*

- **`-webkit-palm-mouse-target: ignore`**, for an element that takes no touches. Mojo uses it
  nineteen times and it is load-bearing: a page header's back icon sits under the title.
  Translated to `pointer-events: none` - with a companion rule, because that one inherits and
  webOS's did not.
- **`-webkit-palm-overflow`**, webOS's scrolling model, which is what gives a Mojo scene its
  height. Without it a scene whose content is all out of flow is zero pixels tall and every
  percentage-height child collapses with it.
- **extractfs**, the thumbnailer - a FUSE filesystem rather than a service, which is why it
  was easy to miss. The card host serves its paths now.
- **A card is the viewport.** This engine is a mobile browser underneath and shrinks a page
  that lays out wider than its window; webOS let content hang off the edge. That is now
  settled by declaring the viewport rather than leaving it to be inferred - see below.

*Mine, from earlier the same day*

The card-background fix was applied to every element rather than only the card's background,
and it read its own output back on the next pass, so a rotated card kept the wrong size. The
`pointer-events` translation was applied without its companion rule and made Mojo's menus
untouchable. Each is recorded in [fix-log.md](fix-log.md) as it happened rather than quietly
settled, because the measurement that decides each one is the useful part.

*The viewport, which is the piece to know about*

`useWideViewPort` was off, so the `<meta name="viewport">` every webOS app carries never
reached the engine at all, and the engine worked the viewport out from the page's own layout
width. It is now declared: the card's width and height and a scale pinned at 1, **merged into**
what the app said rather than replacing it. That constraint is codepoet's and it is the right
one - on webOS that meta is a statement about the *card* (`height=device-height` keeps a
Pre-shaped app out of the Pre3's letterbox, as `"uiRevision": 2` keeps it out of the
TouchPad's phone frame), so `device-width` and `device-height` are translated into this card's
numbers and an app that names its own width or scale keeps it. The rules are in
[architecture.md](architecture.md); [fix-log.md](fix-log.md) also lists the four Android-side
levers that don't work, which are worth reading before anyone tries them again.

It paid for itself twice over: it took ninety lines of compat layer back out, because fixed
elements no longer need correcting one by one, and it is the lever the next screen size will
need.

*Left different, on purpose or for later*

- An emulated card has no status bar, gesture strip or keyboard button of its own, and in card
  view its thumbnail is a tablet-shaped card with a phone in it rather than a phone.
- webOS created a launching card when the app was *launched*; Lunacy creates it when the app
  opens its window, so a `noWindow` app gets its placeholder a little later.
- drPodder's playback row comes out 17/65/17 where the device gives 20/60/20, and this
  Prelude draws its title 300 px wide where the device draws it 290.

*Two probe apps came out of it*, because the fastest way to tell an engine difference from an
app one is to put the same markup on both machines: `…lunacy.cssprobe` (plain CSS and the
window's own measurements) and `…lunacy.mojoprobe` (a Mojo app with real widgets), plus
`…lunacy.emuprobe`, the one deliberately without a `uiRevision`.

**2026-09-21, Mojo and fidelity.** The five Mojo apps codepoet named all run, and Palm's own
Video Player with them (Mojo 2). Then a pass on how it *feels*, each fix measured against the
reference TouchPad or against LunaCE's own source rather than guessed at: the active card now
holds the page's focus (without it a WebView dispatches no focus events at all, so nothing an
app focused itself raised the keyboard); putting the keyboard away takes the field's focus with
it, as `IMEController::hideIME` did, which is the only way a Mojo app can tell; the keyboard's
shift reaches the number row's symbols, as `TabletKeymap::map` says it should, while shift-lock
does not, as `isShiftActive` against `isCapActive` says it should not; Device Info's
rows take their metrics and their disabled grey from Mojo; and a framework's widget art is
asked for before the page needs it, which is what stopped the widgets popping in. Two
differences are recorded and accepted rather than fixed - the card animations' dropped frames,
and the space the keyboard and Android's navigation bar cost a card. Both are in
[fix-log.md](fix-log.md), with the numbers.

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
  `systemProperties/Get`, activitymanager (foreground activities), keys (headset and media),
  display, and db8 and tempdb ([db8.md](db8.md)). Everything else returns webOS's own errors.
  Not yet: zeroconf (codepoet: never worked, skip), background activities, the media indexer.
  The download manager was added on 2026-09-22, measured method by method.
- **Phase 3 (shell):** well along. Card view (with groups, reordering and dimming),
  launcher, dock, status bar (with the start of the system menu), banners, dashboards, popup
  alerts, Just Type's launch and web search, and the emulated card's chrome. The launcher has
  the launch glow and edit mode: reordering, moving icons between tabs (with the tab
  highlight), removing installed apps, arranging the dock (add, swap, reorder, drag up and out
  to remove), LunaCE's tab editing and app folders - all checked against the TouchPad at the
  same scale.
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
  and all; it is separate from Lunacy and optional. (The "Just type" pill was a text field
  until Just Type itself arrived on 2026-09-22; typing now happens there.)
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
  - **Palm's Video Player is bundled (2026-09-21)**, on codepoet's call: it is platform
    rather than an app someone chooses, and apps that play video launch it by id. It has no
    launcher icon, because its `appinfo.json` says `"visible": "false"` and Lunacy now reads
    that, as webOS did.
- **Mojo runs (2026-09-20).** Flying Toasters, a Mojo app installed from a package, runs and
  works as an Exhibition app. Phase 5 is open early; see below.
- **The Mojo suite runs (2026-09-21).** drPodder Redux, IAmA reddit, MeTube, Check Mate and
  SimpleChat - the apps codepoet named as the ones that must work - all run, and so does
  Palm's own **Video Player**, which is a **Mojo 2** app and which MeTube hands every video
  to. MeTube works end to end: search, the server-side conversion, and playback in the Video
  Player's own card. Check Mate creates an account, logs in and syncs a task; SimpleChat
  shows the live chat log; both updated themselves from the App Museum on the way (each
  update changes the app id, so the old copy stays until it is removed). Nine fixes, all in
  general layers, three of them settled by measuring the reference TouchPad: what webOS's
  parser does with `<script src="x" />`, what WebSQL's `openDatabase` and `SQLError.message`
  look like there, and what `PalmSystem.runTextIndexer` returns. [mojo.md](mojo.md) has them;
  [fix-log.md](fix-log.md) has the rest, and the services these apps still ask for that
  nobody answers.
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
- **The Pre3 measured (2026-09-21).** A reference Pre3 was connected and surveyed:
  [pre3.md](pre3.md). It settles the phone profile's values, and several of the community's
  records it was built from are wrong (`PRODoID` is `HSTNH-F30CN`, not `P160UNA`; the user
  agent carries a `Linux; ` prefix). It also shows the Pre3 runs LunaSysMgr's **tablet UI
  path** at `ScaleFactor=1.5`, not the old phone shell, with its own card ratios (0.659/0.61).
  `DeviceProfile.PRE3` is corrected from it, and the TouchPad profile's output is unchanged.
  The user agent and `X-Palm-Carrier` were read off the wire from an app: the phone's product
  token is `webOSSystem`, not the TouchPad's `wOSSystem`, so it could not have been reasoned
  out. Two discrepancies in the *TouchPad* profile turned up on the way and are left alone,
  corrected too: `bluetoothAvailable` and `dockModeEnabled` both disagreed with the reference
  device. The TouchPad profile now reproduces that device's `deviceInfo` member for member,
  checked on the HP 10 G2 against the reference device's recorded output.
- **Stock 3.0.5 compared with CE 3.1.0 (2026-09-21).** A bone-stock TouchPad was connected
  and its `/etc/palm` diffed against the reference device's. Every shell config file is
  **byte-identical**; CE's only change is three added lines in `defaultPreferences.txt`
  (the custom carrier string, which reads "webOS CE", and a virtual-keyboard setting). So the
  geometry and easing values [luna-shell-reference.md](luna-shell-reference.md) takes from the
  reference device are HP's own, not LunaCE's - a standing doubt in that doc, now closed.
  `PRODoID` (`HSTNH-I29C`) and `boardType` (`topaz-Wifi-pvt\n`, newline included) are the same
  on both; only `com.palm.properties.version` differs (`HP webOS 3.0.5` against
  `webOS CE 3.1.0`).
- **Next candidates** (the full list of what still differs from LunaCE, with pointers into both
  trees, is [luna-deltas.md](luna-deltas.md)):
  - **Next priority (codepoet, 2026-09-22): Palm's App Catalog**, `com.palm.app.enyo-findapps`
    6.1.2923 (the webOS Doctor CE pre-install, `AddToImage/PreInstall/`). This version talks to
    webOS Archive's servers, which serve both the magazine and the catalog listings, so every
    failure is Lunacy's. Installed on the tablet, it stops at its loading spinner; its log
    names `com.palm.accountservices` (the Google property ID), `applicationManager/
    launchPointChanges`, `connectCellularDataService` (for its proxy), and the magazine
    edition request failing and then reading a null reply (`'nom' of null`).
  - `activitymanager`'s scheduled activities and `com.palm.power/timeout`, which are one want
    and not two: the Clock's alarms, SimpleChat's half-hourly refresh, reddit's message check
    and drPodder's feed update all ask for them, and Android's `AlarmManager` backs them all;
  - the emulated card's chrome turning with the screen (the strip and bar go to the sides in
    landscape);
  - the media indexer's db8 kinds;
  - the rest of the settings apps: Date & Time, Language, Backup, Accounts, Updates, Location
    (each is one of the three cases above);
  - the border-image seams in Enyo dialogs;
  - a test suite from the apps in fix-log.md, run on WebView 37 and 64;
  - the rest of the system menu: Wi-Fi, VPN, Bluetooth, airplane mode, rotation lock and mute
    (date, battery and brightness are in), per spec §4.3 and the device's QML;
  - the rest of Just Type (the filter tabs, "Search using…", contacts, content, actions) and
    the bus side it needs: `applicationManager/searchApps` and `listLaunchPoints`,
    `com.palm.universalsearch`;
  - A9 and A10 in luna-deltas.md;
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
packaged, and [mojo.md](mojo.md) for everything measured.

**2026-09-21:** the suite codepoet named as the apps that must work all run - drPodder Redux,
IAmA reddit, MeTube, Check Mate and SimpleChat - and with them **Mojo 2**, because MeTube
hands every video to Palm's own Video Player, which is a Mojo 2 app. Every fix landed in a
general layer; none names an app. See [fix-log.md](fix-log.md) for the nine, and for what
each app still asks for that nobody answers.

- ~~Modernized Mojo at `/usr/palm/frameworks/mojo/…`, handled like Enyo, with its own change
  log.~~ Done in outline.
- ~~Mojo 2 (`mojo2`, submission 205), MojoLoader and the frameworks tree.~~ Done; the
  frameworks copied into the APK are only the ones something has needed.
**2026-09-21, later:** a fidelity pass over the same suite. The faults it turned up were all
Lunacy's, and all general: no card's page held Android's focus, so a WebView dispatched no
focus events at all and nothing an app focused itself raised the keyboard; the keyboard hid
without taking the field's focus, which is the only signal Mojo gives an app that it has gone;
and the framework's widget art was asked for only once a widget wanted it. See
[fix-log.md](fix-log.md).

- Multi-stage windows mapped to cards; scene transitions
  (`PalmSystem.prepareSceneTransition` and `runSceneTransition` are logged no-ops).
- Full-screen cards (`PalmSystem.enableFullScreenMode`), which a video player wants.
- The services Mojo apps lean on that Lunacy hasn't got yet: the download manager,
  `com.palm.power`'s timeouts, `com.palm.audio`, `com.palm.accountservices`.

**Exit:** a Mojo test suite of popular App Museum apps runs without per-app code. The suite
exists as a list in [fix-log.md](fix-log.md); what it still needs is to be *run* rather than
driven by hand, which is the same missing test suite phase 1 is waiting on.

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

- **The card animations stay as they are, and card view keeps live pages** (codepoet,
  2026-09-21): a card's minimize drops its first four frames because Chromium re-rasters a live
  WebView when the card's transform changes. Measured, with everything else ruled out, in
  [fix-log.md](fix-log.md). **Snapshotting the cards - what LunaSysMgr did - is declined**: it
  would rebuild card view around frozen pages to work around one device's speed, and faster
  hardware will simply fit the frames. Don't propose it again; if it ever comes back it should
  be because a *newer* target still can't draw a transformed WebView in 16 ms.
- **`deviceInfo` reports this screen, accurately** (codepoet, 2026-09-21): 1280 x 800 on the
  HP 10 G2, not the TouchPad's 1024 x 768, because more devices are coming and the number has
  to mean the screen. An app that subtracts a TouchPad-sized constant from it therefore gets a
  short layout (SimpleChat's chat log; see [fix-log.md](fix-log.md)) - that is the accepted
  cost. **Android's navigation bar** is accepted too: it takes 48 px no webOS device gave up,
  and it goes away on the later Android versions that have gesture navigation.
- **Lunacy answers as a webOS device, fully** (codepoet, 2026-09-20): a TouchPad on a
  tablet-sized screen, a Pre3 on a phone. The environment is most like a TouchPad, and with
  that hardware dying off this is how it lives on. One place decides it (`DeviceProfile.kt`)
  and every app-visible surface follows: `deviceInfo`, the user agent in the page and on the
  wire, the system properties, and `X-Palm-Carrier`. The serial and `nduid` are generated per
  install rather than copied from a real device. Lunacy's own Device Info, the shell and the
  bus's service names stay truthful - the spoof is for apps, not a claim to be webOS. The
  Pre3's values were **measured on hardware on 2026-09-21** ([pre3.md](pre3.md)) and several
  of the community's are wrong - `DeviceProfile.PRE3` has not been corrected yet.
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

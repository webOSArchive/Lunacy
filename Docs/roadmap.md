# Roadmap

Priority order: Enyo 1 → Mojo → JS services → PDK native. Each phase has an exit criterion,
and a phase is finished when its criterion is met, not when its task list runs out.

## Where things stand (2026-10-14)

**Released: 0.7.2 release candidate, build 249** (commit 10c5550, pushed); codepoet makes the
release. **Since then, on `main` and not pushed** (five commits, b216b67..1633974):

- **Foldables:** the shell decides phone or tablet again whenever the screen changes size, and
  `ShellActivity.reshell` rebuilds its views in the other layout with every card kept, running,
  in its group and state; the layout setting goes the same way instead of recreating the
  activity ([phone.md](phone.md) §1, "Foldables"). Checked on a Galaxy Z Fold3 folded and
  opened by hand.
- **Memos** runs its wall and grid to the card's bottom on cards taller than a TouchPad's
  (codepoet's exception to rule 5, [luna-deltas.md](luna-deltas.md) A22).
- **compat:** the A12 margin fix no longer shakes scrolling rows (World Today's rows jumped
  2 px every frame while flicked), and it leaves a box child's own items alone.
- **Device Info:** a long service id breaks instead of widening the page, which had turned on
  Enyo's sideways scrolling on phone-width screens (a user's Android 16 report).

Builds: 252 (arm64 release) on the Pixel Tablet; the arm64 debug build on the Fold3.

Open from this round: measure on the TouchPad whether a `-webkit-box` child's own margin
collapses through its box (the A12 note; it decided World Today's first row); check Device
Info's fix on the Fold3's outer screen with the installed build (proved live there, installed
build checked only open).

## How it got here

One line per milestone; the detail is in [fix-log.md](fix-log.md), the topic docs and git.

- **2026-09-20, ready to show:** one Gradle root, two APKs (shell and the optional keyboard);
  Settings as three cases (Lunacy's own Device Info, Palm's Screen & Lock, Android shortcuts);
  Exhibition, also as Android's screen saver; the file picker; Mojo's first app.
- **2026-09-21:** the Mojo suite codepoet named runs, with Palm's Video Player (Mojo 2); the
  Pre3 and a stock 3.0.5 TouchPad measured ([pre3.md](pre3.md); HP's `/etc/palm` is
  byte-identical to CE's).
- **2026-09-22, 0.2.0:** [luna-deltas.md](luna-deltas.md)'s A and B lists measured and done;
  the viewport declared rather than inferred (the rules in [architecture.md](architecture.md)).
- **2026-09-23, 0.2.5:** the first community reports: other screen scales, dashboards'
  viewports, the tap radius, Enyo patch 0003, target API 24.
- **2026-09-26, 0.3.0:** a webOS root filesystem, install scripts, webOS Accounts, App Catalog
  installs through `appInstallService`; the APK from 82 to 48 MB; the startup card first.
- **2026-10-01, 0.4.0:** arm64 and Android 17, one APK per ABI, First Use, the Nexus 7.
- **2026-10-02:** the phone layout ([phone.md](phone.md)); WebSQL over SQLite where the WebView
  has none; the first PDK apps (Keen, Transformers G1; [pdk.md](pdk.md)).
- **2026-10-03, 0.5.0:** Mojo flicks, the launcher's over-scroll, busybox built against bionic,
  the layout choice in Screen & Lock (B11), the keyboard at whole-number scales.
- **2026-10-04, 0.5.5:** PVRTC decoding, Android's wallpaper following webOS's, Palm's Web app
  on a native WebView, Calculator 3.2.0, Paste, webOS text selection.
- **2026-10-05:** the SDK's tools over adb (webos-sdk-redux 0.4's novacomd); the Enyo fork
  rebased on the TouchPad's own framework; apkenv's Android ports run as PDK apps.
- **2026-10-06, 0.6.0 and 0.6.1:** native PDK on Android 10+ (`libenosys`), GPU driver fixes
  (Tegra 3, Adreno 620 and 330); foldables move between screens without a restart prompt.
- **2026-10-07, 0.7.0 built:** Email on IMAP/SMTP with Palm's native mail services, Calendar and
  Contacts (webOS CE's, local), hybrid apps (Quick Office, Adobe Reader), documents' index.
- **2026-10-08:** sleep and wake mapped to Android; Photos & Videos with the media indexer.
- **2026-10-09, 0.7.0 released** (b2362ec, build 216): local-network proxy, WebView 44 fixes,
  Enyo 0010, content handlers, A12; then A19 and time-zone rules.
- **2026-10-10:** Exchange ActiveSync with its security policies ([eas.md](eas.md)); CoreApps/;
  Messaging 3.2.0 with webOS CE's IM stack ([messaging.md](messaging.md)).
- **2026-10-12/13, 0.7.2 RC:** webOS Chat live over `oscar.webosarchive.org`; the IM transport
  under qemu; chat threader 1.1.0.3; Db8 null and `incDel` fixes.

## Open, carried forward

Gathered from the dated notes this section replaced (git has them in full). Check each before
starting; some may have been settled since without a note.

- **Chat:** longevity on the devices; webOS CE's startup hang (PmLogLib's semaphore; take CE's
  fix if it lands in the transport); the Add Buddy invitation message, which the transport drops.
- **Exchange:** a real server's policy, tasks, attachments.
- **Email:** attachments; the Android 5 devices untried; PmLogLib's levels need PmLogDaemon's
  shared memory (only errors are logged unless a service is started with `files/pdk/args`).
- **Photos & Videos 3.2.0:** the editor dragged with a finger, and on the 32-bit build's
  devices (Chromium 37); on a 320-px-wide card (the Fold3's cover screen) its toolbars and
  the single-photo toolbar run off the right edge, the app's own layout; whether the
  TouchPad's thumbnails of EXIF-rotated photos were upright (architecture, "Photos and
  videos"); Replace Original on an Android 11+ photo another app owns.
- **Calendar:** repeats, snooze. **Contacts:** vCards, favorites. Both untried beyond the A7 Lite.
- **Hybrid apps:** a visible plugin's picture; callable collections on Chromium 37.
- **PDK:** OpenSSL 0.9.8 and curl for the apps that link them; the other ten apkenv ports; on
  the A7 Lite's 64-bit build Where's My Water levels take 20 to 65 s to load under qemu;
  `palm-install` of a 24 MB package was reset by the relay.
- **SDK:** compare `palm-log` output for the Web app's plugin calls with the TouchPad's; Mojo
  samples don't open in a desktop browser (webos-sdk-redux FRAMEWORKS.md).
- **Web app:** the download manager's history; the TouchPad's default bookmarks.
- **Mojo:** the `WebView` widget (`BrowserAdapter`), so Quick Tip Calculator's first scene is
  an empty panel.
- **WebSQL:** the copy-in of a WebView's own WebSQL files is untested; not yet run on the
  32-bit build's devices.
- **Phones:** whether a focused field should raise the keyboard when an app focuses it itself
  (a Pre3 had a hardware keyboard). First Use not yet run on the HP 10 G2.
- **Installs and accounts:** App Catalog 6.2 as Lunacy's pack-in; the phones catalog on a phone;
  cookies are shared between apps' origins (`.media.cryptofs.apps`); an app a package script put
  in the rootfs can't be removed from the launcher; Enyo's libraries other than `networkproxy`
  are upstream's, not the TouchPad's newer ones.
- **Shell (the 2026-09-22 candidates list):** the emulated card's chrome turning with the
  screen; the rest of the settings apps (Date & Time, Language, Backup, Accounts, Updates,
  Location); the rest of the system menu (Wi-Fi, VPN, Bluetooth, airplane, rotation lock,
  mute); the rest of Just Type (filter tabs, "Search using…", contacts, content, actions, and
  `searchApps`/`listLaunchPoints`/`universalsearch`); the border-image seams in Enyo dialogs
  and the Sampler's radio button; a test suite from the apps in fix-log.md, on WebView 37 and
  a current one; luna-deltas A9 and A10.

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
  - status bar menus;

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

- ~~A glibc/SDL 1.2/PDL loader over Android (apkenv in reverse).~~ Started 2026-10-02: the
  binaries run as they are on 32-bit ARM, through the glibc loader with SDL 1.2 rebuilt to
  talk to the shell and Lunacy's own libpdl; Commander Keen plays on the Nexus 5
  ([pdk.md](pdk.md)). Since done, the same day: SDL_mixer, SDL_image, SDL_ttf, SDL_net and
  the image and font libraries apps link by name; GLES 1.1 and GLES 2 streamed to the shell
  and replayed into a framebuffer of the app's own size; the TouchPad's way of showing a
  buffer, measured on it; the accelerometer as SDL joystick 0; qemu for 64-bit-only devices,
  inside the arm64 APK. Codepoet's first tests passed.
- **Next:**
  - ~~**Hybrid apps**: web apps that embed a PDK plugin (`<object type="application/x-palm-remote">`),
    called from JavaScript.~~ Done 2026-10-07 for plugins that draw nothing on the page (Quick
    Office, Adobe Reader; [pdk.md](pdk.md) §6c). Still to do: a plugin drawing into a region of
    the page, as the chess app tried on 2026-10-02 may.
  - **OpenSSL 0.9.8 and curl**: about 50 apps link `libcrypto.so.0.9.8`, `libssl.so.0.9.8`
    or `libcurl.so.4`, which the runtime doesn't provide yet.
  - **`PDL_ServiceCall` to the bus**, as JS services have it.
  - **A keyboard** for apps that ask for one (`PDL_SetKeyboardState`).
  - **The launcher's runnable state** for a `pdk` app where the runtime is absent.

**Exit:** a handful of well-known PDK apps run at full speed on a current device. The
corpus survey says how many apps this phase can reach, and whether it is worth doing.

## Later

- **Newer Android targets, tightening security at each step.**
  - Move the bridge to `WebMessageListener` restricted to Lunacy's origins, with
    document-start scripts.
  - Recover through `onRenderProcessGone` (Android 8 and later).
  - Handle the conflict between the gesture bar and Android's gesture navigation (Android 10
    and later).
  - ~~Add the WebSQL polyfill when a target's WebView drops WebSQL.~~ Done 2026-10-02
    (`websql.js` and `WebSql.kt`), for the Pixel Tablet's WebView 153. Its migration of a
    WebView's own WebSQL files is written but untested: it needs a device whose WebView was
    updated across the removal.
  - Move to current AndroidX once `minSdk` rises.
  - **Developer verification** (Google, not an Android version: it arrives through Google
    System Updates). From 2026-09-30 in Brazil, Indonesia, Singapore and Thailand, and
    worldwide in 2027, an APK from an unregistered developer installs only over `adb` or
    through Android's "advanced flow" (a developer option, a reboot and a 24-hour wait).
    Either codepoet registers (a limited-distribution account opened in August 2026) or the
    install guide documents the advanced flow. Both APKs are affected. codepoet decides.
  - **16 KB memory pages: test on a device that has them.** The 64-bit build's libraries are
    all 16 KB-aligned now (2026-10-01, below), and Android 17's dialog is gone on the 4 KB
    Pixel Tablet, but no 16 KB-page device has run it yet.
  - **Target 28 on the 64-bit build: what is left to check.** Android's autofill reaches
    WebView forms from a target of 26; see what it does in a card against the TouchPad.
    Android 8.0 alone refuses an orientation lock on a translucent activity (the shell's
    theme is opaque, so this should not bite). The 32-bit build stays at 24, so the HP is
    unaffected.
  - **Android 17's memory limiter** kills any app's process past a RAM-derived limit (exit
    reason `MemoryLimiter:AnonSwap`; the figures aren't published). On the Pixel Tablet's
    Android 17 it reports itself disabled, so there was nothing to measure; check again on
    another 17 device or a later build.
  - **If the target ever reaches 37:** `ACCESS_LOCAL_NETWORK` for the loopback app server and
    LAN services such as Plex; orientation locks ignored on tablets; the shorter WebView
    user agent. None of it applies at target 24.
  - On 17 the keyboard no longer comes back by itself after a rotation; check what the
    TouchPad did. Add WebView 140 to the versions the suite runs on.
- **First Use: what is left** (the app is in, 2026-10-01). No rule 0 pass against the
  TouchPad's own First Use (codepoet): that app is platform-specific, and Lunacy's asks for
  different things; Palm's look is kept, not its measurements. Pages still to come as the
  targets rise:
  Bluetooth from a target of 31, notification access for launcher mode, the local network at
  37. A way to run it again from Device Info.
- Launcher mode: Lunacy as the Android home screen, with Android apps shown alongside webOS
  cards. A proof of concept is in (`shell/AndroidApps.kt`): Android's apps in the launcher,
  the dock and Just Type, launched and uninstalled through Android. Reviewed 2026-09-26; what
  it still lacks, none of it started:
  - **Running Android apps have no cards.** Switching between them is Android's Recents,
    so "alongside webOS cards" isn't met. This is the largest gap.
  - **Android's notifications don't reach Lunacy's notification area.** It needs a
    `NotificationListenerService`, which the user grants on Android's own settings screen.
  - **Android apps are listed whether or not Lunacy is the home screen**, and the favorites
    page is renamed "android" for everyone. Decide whether both should follow being the
    default home screen.
  - **Starting as the home screen hasn't been timed.** Android starts Lunacy at boot and
    after every low-memory kill, and on a 1 GB device each start first readies the webOS
    root (`WebosRoot`).
  - Not planned (codepoet, 2026-09-26): Android widgets. webOS had none.
  - Android's own wallpaper, opt-in (codepoet, 2026-10-04): Screen & Lock's "Use on Android"
    sets Android's home screen wallpaper (and lock screen, from Android 7) to webOS's, and
    keeps it following (`HostWallpaper`). Off by default. Works on the Pixel Tablet (Android
    17, home and lock) and the HP 10 G2 (Android 5.0.1, whose lock screen shows the one
    wallpaper).
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

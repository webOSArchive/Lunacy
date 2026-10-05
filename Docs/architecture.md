# Architecture

Lunacy is a single Android app written in Kotlin. It hosts webOS web apps in WebViews and
simulates everything around them: the shell, the frameworks' OS surface (`PalmSystem`,
`PalmServiceBridge`) and the Luna service bus.

## Platform target

Lunacy starts on **Android 5.0.1 (API 21)** and moves up to newer versions later.
- **Cheap test devices.** HP's Android tablets from that era are close to the TouchPad in
  spec and cost almost nothing, so they make good development and test devices.
- **A permissive platform.** Android 5 lets things work first. Security is tightened as
  later Android versions are targeted.

**First device: HP 10 G2 Tablet.**

| | |
|---|---|
| Android | 5.0.1 |
| SoC | MediaTek MT8127, 32-bit `armeabi-v7a` |
| RAM | 1 GB |
| Screen | 800×1280, 213 dpi |
| Stock WebView | Chromium 37 |
| Rooted | No |

What this target means:
- **A range of WebViews.**
  - The factory WebView is Chromium 37, a 2014 Blink much closer to the WebKit Enyo 1 was
    written for.
  - Lollipop's WebView can be updated, up to about Chromium 95.
  - Lunacy supports the whole range. Newer WebViews are brought back in line with 37 by
    global serve-time transforms: spike 1 showed one CSS rule makes Chromium 64 render Enyo
    like 37.
  - The test suite runs on 37 and 64.
  - Everything done to the test devices is listed in [android5-setup.md](android5-setup.md).
- **No Jetpack Compose.** Current AndroidX and Compose releases need API 23. The shell is
  built with classic Android Views in Kotlin. Any AndroidX library used is pinned to a
  release that still supports API 21.
- **Lunacy's own JS runs on the oldest WebView.** The bridge, shims, polyfills and every
  change to the frameworks must run on Chromium 37. In practice that means ES5, or a build
  step that transpiles down to it.
- **Target API 24 (32-bit) and 28 (64-bit), minimum API 21.** Android 14 refuses to install an
  app that targets below API 23, and Android 15 below 24 (Android 16 and 17 keep that floor),
  so the 32-bit build targets 24: the proven configuration for the Android 5 devices, which
  are all 32-bit. Android 17 installs a target-24 app but warns at first launch ("built for an
  older version of Android") below target 28, measured on a Pixel Tablet, so the 64-bit build,
  whose devices run Android 8 or later in practice, targets 28: the last target with legacy
  external storage. What 26 to 28 add on those devices: plain http needs the network security
  config (`res/xml/network_security_config.xml`), WebView Safe Browsing is on unless the
  manifest turns it off (it is off: a Chrome interstitial in a card is not webOS), `Build.SERIAL`
  reads "unknown" (the derived device id is stored the first time it is computed, so an
  install keeps its id across that change), background-started services and manifest
  receivers for implicit broadcasts are refused (Lunacy has neither), and Android's autofill
  reaches WebView forms (to check against the TouchPad).
- **First Use.** A webOS device put a First Use app in front of a new owner. Lunacy's
  (`assets/apps/org.webosarchive.lunacy.firstuse`, no launcher icon) is launched by the shell
  on the first start and asks for the Android permissions above, one page each, in Palm's
  First Use look; on Android 5, which granted them at install, it is a welcome and Done. It
  talks to Lunacy's own service: `permissions/status`, `permissions/request` (storage puts up
  Android's dialog and answers when the user has, with whether Android would ask again;
  "Modify system settings" opens Android's own screen; the home screen asks for the home role
  from Android 10, and opens the Home settings before that) and `firstUse/done`. A page that
  waits on something granted on Android's own screen asks for the status again every couple
  of seconds while it waits. Only Lunacy's own
  apps may call the last two. After it has run, the shell asks for storage itself at startup
  if it is still missing, as it did before. Android 5 ignores a target above its
  own level, so this changes nothing there. On Android 6 and later it means: storage and
  `WRITE_SETTINGS` are asked for while the app runs (see those entries below), and the shell
  opts out of split screen (`resizeableActivity="false"`), which a target of 24 turns on by
  default. Anything that would need a target above 24 is a later step, taken on purpose.
- **Security is ratcheted, not ignored.** Each shortcut that Android 5 allows (the bridge
  transport, mixed content, how widely the bus is exposed) is listed below, so it can be
  tightened when newer Android versions are targeted.
- **TLS.** Android 5's trust store predates current certificate chains. Native HTTP (the
  network shim, the Museum's downloads) trusts bundled Mozilla roots as well; Android 5's
  own TLS 1.2 has been enough so far. Whether WebView 37 itself can reach current HTTPS
  sites is still a spike question.

```
Lunacy (Android app)
├─ Shell (simulated Luna)
│    card view · gesture bar · launcher · status bar · banners/dashboard · Just Type
├─ Card host: one WebView per window (card, dashboard, alert), one origin per app
│    ├─ webOS paths       each origin mirrors the webOS filesystem layout
│    ├─ framework route   /usr/palm/frameworks/*  →  modernized Enyo (later Mojo)
│    ├─ compat layer      global polyfills, network shim, serve-time CSS transforms
│    └─ PalmSystem + PalmServiceBridge (JS)  ⇄  native bridge (async bus semantics)
├─ App lifecycle          headless root windows · window.open · relaunch · activate/deactivate
├─ JS services         one Node process per package · Palm's mojoservice · webOS paths · curl
├─ Luna bus (simulated, in-process)
│    ├─ router            service/method registry · subscriptions · coverage log
│    ├─ Lunacy-backed     applicationManager · db8 · preferences · notifications ·
│    │                    downloadmanager · Preware install handler
│    └─ Android-backed    connectionmanager · power · systemservice · audio · intents
├─ WebSQL                 native in Lollipop's WebView; websql.js + per-app SQLite on Chromium 119+
├─ Package manager        .ipk install · appinfo.json · icons · per-app data
└─ App Catalog            the TouchPad's own client (webOS Archive's 6.2), bundled; installs via the bus
```

## Shell

**Phone or tablet** is decided once, by `FormFactor` (the screen's size in dp, its size in
inches and its shape, or the owner's setting), and both the shell's layout and the device
apps are told about follow it. On a phone the shell draws the TouchPad's parts tightened for
the width, and lays app pages out wider than the card, scaled to fit: [phone.md](phone.md).

The shell is a **simulator**: native Android Views that recreates the webOS experience. The
styling target is **LunaCE**, with stock TouchPad 3.0.5 as the reference where LunaCE
doesn't differ. It doesn't run LunaSysMgr or any Palm code. The webOS look and feel *is* the
product; being "close enough" is not the goal.

- **Card view.** Live cards that can be thrown away with a swipe up, stacked in groups the
  way LunaSysMgr grouped an app's cards, and reordered with a tap-and-hold.
  - A card an app opens while its own card is up joins that card's group; anything else starts
    a group just right of the active one. The active group fans; the others collapse to a
    10 px stagger. A card dims to 0.8 when it stops being the active one.
  - Chromium 37's WebView draws nothing when its view is turned by a negative angle or has an
    alpha below 1, so a tilted or see-through card is drawn from a hardware layer.
  - A card pulled off the bottom closes like one thrown off the top.
- **Status bar and system menu.** Drawn and animated as LunaSysMgr drew them (title
  cross-fade, the ▾ only while an app is up, info icons from Android's own state). The ▾ at the
  right opens the system menu, so far with the date, the battery and a brightness slider that
  sets Android's brightness.
- **Phone-sized apps** (an app without `"uiRevision": 2`) run in the emulated card with its
  chrome: its own title bar (the app menu), the core-navi strip (back), the keyboard button,
  rounded corners; in the card view, a black card with the page drawn larger. The chrome
  doesn't yet turn with the screen.
- **Sounds.** Banner and notification sounds by class; LunaSysMgr's own feedback samples for a
  card closing, the launcher opening and closing and the angry card (upside-down landscape
  only); `battery_full.mp3` at 100 %. On Android's system and notification streams, so the
  ringer and the volumes are Android's.
- **Full screen and buttons.**
  - Lunacy runs in Android's immersive sticky mode, so the whole screen is the shell. A swipe
    from the edge shows Android's buttons for a moment.
  - Android's back button acts as the TouchPad's single home button (webOS tablets had no
    back button), in LunaCE's order: close what's open on top, else minimize the maximized
    card, else toggle the launcher.
  - A swipe up from the bottom edge minimizes, as in LunaCE (15 px edge band, 15 px
    trigger). **A deliberate difference:** the card follows the finger down to the card view
    (LunaCE's fluid mode, `sysUiGestureDetection` 2). The reference TouchPad has that setting
    at 0: nothing moves until the finger lifts with a flick. codepoet chose the fluid mode
    on 2026-09-22.
  - The gesture dead zone is the device's: a touch that starts in the 15 px band at the
    bottom, or at the left or right edge below the status bar, never reaches a maximized
    app (`SystemUiController`, `sysUiEnableGestureDeadzone`; the reference TouchPad has the
    edge gestures on, which is what switches it on).
- **Proportions.** Shell sizes are in TouchPad pixels.
  - On tablets, one TouchPad px is (screen short side ÷ 768) device pixels, rounded to a
    whole number: 1 on the HP 10 G2.
  - The rounding matters. At fractional scales, the slices of nine-patch and
    `-webkit-border-image` graphics fall between device pixels and show faint seams.
  - The effect is that the shell has the TouchPad's proportions, within the rounding,
    whatever density the vendor chose. So the status bar,
  dock and pill take the same share of the screen as on a TouchPad. Phones will get their own
  layout.
- **Gestures on newer Android.** On Android 10 and
  later, gesture navigation owns the swipe up from the bottom edge. There the bar sits above
  the system zone, and in launcher mode Android's home gesture lands back in Lunacy anyway.
- **Just Type.** A tap on the pill opens it under the status bar (150 ms), drawn natively to
  the measurements of Palm's own Just Type app and with its art (`JustTypePanel`): the field,
  a LAUNCH group of matching apps - LunaSysMgr's `searchApps` rule, the matched start
  underlined, the first highlighted and launched by Return - and "Search DuckDuckGo" with the
  device's own URL. The filter tabs, "Search using…" and the rest are for later (codepoet,
  2026-09-22).
- **Starting up.** Unpacking the bundled apps on a first run takes a couple of seconds, so
  ShellActivity carries a splash theme whose `windowBackground` is the Lunacy logo on the dark
  of its own icon. Android paints that before the process exists, so it covers the whole cold
  start; the shell drops the drawable once it has drawn its first frame.
- **Card memory and crashes.** On Android 5 the WebView renderer runs inside Lunacy's own
  process, so a renderer crash takes down the whole app. With 1 GB of RAM, live cards are
  expensive. The shell therefore limits how many cards stay live: older cards become a
  snapshot and reload on return. From Android 8, the renderer is a separate process, and
  the shell also recovers through `onRenderProcessGone`.
- **Launcher, status bar, banners and dashboard.** Driven by the bus (the applicationManager
  and notification services).
  - A tapped icon, in the launcher or the dock, glows (`launcher-touch-feedback.png`, 90 px,
    centred on the icon) until the launcher closes, or for LunaCE's 3 s at most.
  - Holding an icon enters edit mode, LunaCE's reorder mode: every icon gets its frame
    (`edit-icon-bg.png`), apps installed from packages get the delete decorator (bundled apps
    can't be removed), a Done button joins the tab bar, and icons follow the finger to a new
    place while the others slide aside (300 ms, InQuad). Done or the home button leaves it.
    Each tab's order is kept.
  - **Where an app starts.** Apps the user hasn't placed follow the default page layout in
    `assets/luna/launcher-pages.json`, which is Lunacy's
    `/etc/palm/default-launcher-page-layout.json` and has the device's own shape. It is what
    puts Clock on APPS, the App Museum alone on DOWNLOADS and every settings app on SETTINGS -
    Exhibition included, which carries no keywords for the keyword map to work from. webOS's
    own file names no favourites page, so GAMES starts empty here too. Anything the layout
    doesn't name falls back to its category and keywords (LunaCE's
    `AppMonitor::pageDesignatorForWebOSApp`), then to the first page, alphabetically.
  - A dragged icon moves to another tab when it touches that tab, as LunaCE's tab bar takes
    it, or when it's held within 50 px of the screen's left or right edge (1500 ms,
    `pagePanForIconMoveDelayMs`); held within 20 px of the page's top or bottom, the page
    scrolls 150 px every 800 ms. The layout is only looked at while the finger rests, as
    ReorderablePage's velocity sampling has it, so an icon passing over others doesn't
    shuffle them. Held over the middle 60 % of another icon for 300 ms, an app is dropped
    *onto* it and the two make a group ([LunaCE]; below). The tab under the
    icon (or under a pressing finger) shows `tab-highlight.png`, LunaCE's highlighted tab.
  - Edit-mode geometry uses the reference TouchPad's `/etc/palm/launcher3` overrides where
    they differ from LunaCE's defaults: the delete decorator at (-50, -50), and the Done
    button 8 px from the bar's right edge with its label ("DONE") 3 px up. Checked against a
    TouchPad screenshot at the same scale.
  - The dock: holding a dock icon picks it up for that drag (the launcher's edit mode isn't
    involved), and in edit mode a drag picks one up too. As LunaCE's QuickLaunchBar does, the
    icon lifts to sit 15 px above the finger (MOVING_ICON_Y_OFFSET) and follows it, the others
    make room, and above the dock it turns half transparent. Let go on the dock and it takes that slot,
    let go above the dock and it leaves the dock (the app stays in the launcher), as on
    webOS. A launcher icon dropped on the dock joins it at that slot (one already there moves).
    On a full dock (five) it takes the place of the icon it lands on; LunaCE refused a sixth
    icon, and the swap is Lunacy's. The dock's apps are kept.
  - The delete decorator opens LunaCE's AppInfoDialog ("Remove Application?", "<title> -
    v.<version>", Cancel and Remove). Remove closes the app's windows and uninstalls its
    package: the app, the services its `packageinfo.json` names, and the package record. Its
    db8 data stays for now.
- **Launcher pages.** Four, as LunaCE has them, named as the reference TouchPad names them:
  APPS, DOWNLOADS, GAMES, SETTINGS (its `app-keywords-to-designator-map.txt` renames favorites
  to "games" and prefs to "settings"). An app the user hasn't placed finds its page the way
  LunaCE's `AppMonitor::pageDesignatorForWebOSApp` does: its `appinfo.json` category first,
  then each of its keywords, against the map in `assets/luna/launcher-pages.json`; failing
  that, an app the user installed goes on DOWNLOADS (`installedAppsPageIndex`), which is where
  the reference TouchPad has every one, and a bundled app on the first page.
  - Columns are spaced by LunaCE's own formula over the whole page width (measured: 149 px
    apart on a portrait TouchPad). Scrolling is LunaCE's KineticScroller. An empty page shows
    its picture and "Tap and hold any app to drag it to this page." A package being installed
    shows on DOWNLOADS as a faded icon with the `loading-strip.png` progress filmstrip.
  - [LunaCE] Tabs can be renamed (hold a tab), added (hold the empty bar for the "+") and,
    past the four stock tabs, deleted, their icons going to the first page; six at most.
    The dialog is LunaCE's RenameDialog, drawn around a real text field for Android's
    keyboard. Tabs are saved with their designators and names.
  - [LunaCE] App groups: a group is GroupIcon's composite (a backplate with up to four member
    thumbnails); a tap opens its panel (GroupOverlay), where a member launches, a held member
    comes back out onto the page, and a tap on the name renames it. A group of one dissolves.
- **Layouts.** Tablet and phone layouts from the start; the tablet layout is the reference.
- **Launcher mode.** Lunacy starts as a normal full-screen app. Nothing in the shell should
  assume that, so it can later become the Android home launcher, with Android apps appearing
  as icons or cards.

## Card host

- **One WebView per window.** A card is a *window*, not an app. One app can own several
  cards, plus dashboards and popup alerts; see "App lifecycle" below.
- **One origin per app**: `https://<appid>.media.cryptofs.apps`, served from the installed
  package. Apps check `location.hostname` for `.media.cryptofs.apps` to detect webOS (App
  Museum II does), so the name matters. Requests to these hosts never reach the network via an asset-loader style `shouldInterceptRequest`. localStorage, IndexedDB and
  permissions are then kept per app, and pages never run from `file://`.
- **webOS paths.** Each origin mirrors the webOS filesystem. The app is served at
  `/media/cryptofs/apps/usr/palm/applications/<appid>/`, not at the root. Many apps load
  their framework through relative paths (`../../../../usr/palm/frameworks/…`), and at the
  root those `../` segments would collapse and miss the route. With the real layout, every
  relative and absolute path resolves as it did on a device.
- **Framework route.** Requests under `/usr/palm/frameworks/…` go to Lunacy's modernized
  frameworks, including the version aliases apps used (`enyo/0.10`, `enyo/1.0`). Apps are
  not rewritten; the path they already use gets a better library behind it.
- **The framework's art is asked for before the page needs it.** A framework asks for a piece
  of its widget art only once a widget using it has been laid out - and a dialog's own frame
  only when the dialog opens - so the layout arrives before its chrome and a dialog shows as
  unframed text for half a second. Serving faster doesn't help: the card host answers the
  median request in under a millisecond, and the wait is the page's, not the file's. So the
  host remembers which files under `/usr/palm/frameworks/` each app asked for, and asks for
  those again at the top of the page next time. It keeps no list of its own and knows nothing
  about any app: it is a cache, filled by the same rule for every one of them, and an app that
  has never run gets no preload. Preloading a framework's whole theme instead was measured and
  is worse - it pays for the fifty images nobody asked for and the page appears half a second
  later ([fix-log.md](fix-log.md)).
- **The fork is stock Enyo plus patches.** `LunaRuntimes/enyo-1.0/patches/` holds Lunacy's
  changes as diffs against upstream, and `fetch-assets.sh` applies them after copying the
  upstream clone, failing the build if one no longer applies. So every framework change stays
  readable as a diff and nothing can drift in unrecorded;
  [CHANGES.md](../LunaRuntimes/enyo-1.0/CHANGES.md) says what each one is and why. Enyo
  serves its built file (`framework/build/enyo-build.js`) rather than `source/`, so a patch
  changes both.
- **Apps that bundle their own framework** (all Enyo 2 apps, and some Enyo 1 apps) never hit
  the route. They rely on the compat layer and the bus alone. Enyo 2 was written for
  ordinary browsers, so this is the easier case.
- **Compatibility layer.** Global, and applied to every card; see "No per-app hacks" below.
  It has four parts:
  - *The webOS input model.* webOS's WebKit delivered touches to web content as mouse
    events, and Enyo 1 and Mojo only listen for those. Chromium synthesizes mouse events for
    taps only, not drags, so without help no scroller moves. A global script converts
    touches into `mousedown`/`mousemove`/`mouseup` and `click`, and cancels native touch
    handling. Spike 1 showed it fixes every scroller at once. Moves are filtered the way
    LunaSysMgr's `EventThrottler` filtered them for web apps: none reaches the page until the
    finger leaves a tap radius around where it went down (the TouchPad's `luna.conf`: 25 px,
    shrinking 10% every 200 ms held, to 5 px), and after that a move to the same spot is
    dropped. Mojo depends on it - on a device it takes any `mousemove` after a `mousedown` as
    the start of a drag and drops the tap. LunaSysMgr also capped moves at 30 a second
    (`MaxPenMoveFreq`); that cap is not reproduced.
  - *The keyboard, the other half of that model.* LunaSysMgr's virtual keyboard put real key
    events into the page, so a keystroke arrived as `keydown`, `keypress` and `keyup` with
    the character's own code. Android's keyboards are input methods: a soft keyboard commits
    text through the IME, `keydown` and `keyup` carry 229 (the "ask the IME" sentinel) and
    **no `keypress` is dispatched at all**. Code written for webOS reads `keypress`, so with
    a soft keyboard it never runs - Mojo hides a text field's hint from its keypress handler,
    which is why an app's placeholder stayed behind what was being typed. The compat layer
    sends the keypress a device sent, from the `textInput` event: it fires before the text is
    inserted and is cancelable, where a device's keypress sat, so a listener that stops it
    still keeps the character out. Only when no real keypress came.
  - *Flicks.* Enyo's and Mojo's scrollers only coast after a flick the host reports. On
    webOS, LunaSysMgr measured it (FlickGestureRecognizer: the last 3 samples, above
    500 px/s, velocity = displacement ÷ (elapsed × samples)). WindowedWebApp then called
    `Mojo.handleGesture('flick', {x, y, timeStamp, xVel, yVel})` in the page. The compat
    layer does the same when a fast drag ends.
  - *Injected polyfills* (CSS and JS at document start) for other behavior apps depend on
    outside the framework: `-webkit-box`, old event quirks.
  - *System fonts.* webOS had the Prelude family installed, and Enyo and apps name it
    (`Prelude`, `Prelude Medium`, `PreludeWGL Bold`, `PreludeWGL Light` and others). Every
    page gets an injected `fonts.css`, generated by `AndroidLuna/tools/gen-fonts-css.py`. Its
    `@font-face` rules map each of those names to the shipped Prelude files, the way the
    reference TouchPad resolves them (measured with `Workbench/probe/fontprobe.sh`): a face's
    full name keeps that face at any weight, and a name the device hasn't got is Prelude.
    Installed fonts were ready from a page's first line; web fonts are ready only after the
    running script ends, so every page holds its own scripts, behind the splash card, until
    the default faces are in (`AppServer`, "the fonts"; `shell/FontWarmer.kt`). It also makes
    Prelude the default page font, as it was on webOS. Android's WebView can't be given a
    system default font, so an explicit `sans-serif` in an app still falls back to Roboto.
  - *Serve-time transforms.* Lunacy serves every file, so it can apply mechanical rewrites to
    app CSS on the way through. The first one: every rule that sets a
    `-webkit-border-image` gets `border-style: solid; border-color: transparent` appended,
    because 2011 WebKit drew border images whatever the border style, and newer Chromium
    doesn't. A transform is a rule applied to every app. Installed files are never
    changed, and no transform may name an app.
  - *Uncaught errors reach only the console.* The reference TouchPad's WebKit has
    `window.onerror` but never calls it, and sends no `error` event to window listeners
    (measured with `Workbench/probe` 0.0.5). Some apps install error overlays that never showed
    on a device. The compat layer adds the first `error` listener on `window`, and it stops
    script errors before the page's own listeners and `onerror` see them. Errors on elements
    (images, script loads) are untouched, and the console still logs every error.
  - *Old WebKit names.* Chromium dropped `webkitCancelRequestAnimationFrame` but kept
    `webkitRequestAnimationFrame`. Enyo 1 then cancels frames with `clearTimeout`, which
    kills unrelated timers, so the old name is mapped to `cancelAnimationFrame`.
  - *Media with an empty source.* On the TouchPad, `audio.src = ""` left the element empty
    with no event; Chromium fires `error`. `enyo.Sound` does that to every new sound, so
    setting `""` removes the attribute instead.
  - *No service workers.* The TouchPad's WebKit had none. Apps written to run on the web too
    register one when `navigator.serviceWorker` exists, and it can't work on Lunacy's served
    origins, so the compat layer removes it.
  - *Network shim.* webOS apps ran from `file://` and could call any web API. On an `https://`
    origin, those calls hit CORS, and plain `http://` APIs count as mixed content. The
    WebView allows mixed content, and a global XHR shim sends cross-origin requests over the
    bridge to a native HTTP client. (`shouldInterceptRequest` alone isn't enough, because it
    can't see request bodies.)
    - **How.** `net.js` replaces `XMLHttpRequest` in every page. Requests to the page's own
      origin, other app origins and non-HTTP schemes go to the real XHR unchanged. Any other
      `http`/`https` request goes to `NetShim.kt` and `Http.kt`: asynchronously as a queued
      call answered through `evaluateJavascript`, or on the bridge's synchronous path for a
      synchronous XHR, which blocks only that page's script, as it did on webOS.
    - **The contract, measured on the TouchPad** (`Workbench/probe` 0.0.7, `net.js`): no CORS
      check at all. Pages may set any header, Cookie, Host, Origin and User-Agent included.
      Every request carries `Accept: */*`, `Accept-Language: en-us,en;q=0.5` and
      `Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.3`. Methods other than GET and HEAD send
      `Origin: file://.media.cryptofs.apps.usr.palm.applications.<appid>` (from the calling
      window, never the page). A string body with no type goes as `application/xml`, and a
      type the page gave without a charset gets `; charset=UTF-8`. Cookies come from and go
      to a jar (Lunacy uses the WebView's, shared with the pages), and `Set-Cookie` is
      readable. Redirects send the previous URL as Referer, 301/302/303 turn a POST into a
      GET, and the final response shows every hop's headers merged by name. `statusText`
      is always empty. `readyState` runs 1, 2, 3, 4 (no 3 for an empty body; 1, 4 for a
      synchronous request). A failure gives status 0, readyState 4 and `error`, and a
      synchronous `send()` then throws `NETWORK_ERR: XMLHttpRequest Exception 101`. A server
      that never answers fails after about 9 s (probe 0.0.9), and the shim's connect
      timeout is the same; a synchronous request blocks its page that long, as on webOS.
      `x-user-defined` maps bytes above 0x7F to U+F700 + byte.
    - **Beyond the TouchPad.** The TouchPad's XHR had no `responseType`, `response`,
      `timeout` or `onloadend`; Lunacy keeps them, as the real XHR has them, so Enyo 2 apps
      get `arraybuffer`, `blob`, `json` and `document` responses.
    - **Not the same.** Android's HTTP client sends `Accept-Encoding: gzip` where the TouchPad
      sent `gzip, deflate`; it is the client's own header, and setting it by hand would stop
      the client decompressing the reply, so it stays as it is. Cross-origin `send()`
      of `FormData` or a `Blob` fails with an error logged; `fetch()`, Web Workers' XHR and
      `WebSocket` aren't shimmed.
    - **TLS.** Android 5's trust store lacks current roots (ISRG Root X1 for Let's Encrypt,
      Amazon's), which the TouchPad reached fine. `Http.kt` trusts Android's store and the
      bundled Mozilla roots in `assets/certs` (MPL 2.0, see its NOTICE). Package downloads
      use the same client. HTTPS loaded by the WebView itself (images, scripts) still uses
      Android's store only.
    - **Unrestricted HTTP for pages (ratchet item).** Any page, and any frame that reaches the
      bridge, can send any request with the user's cookies and read the answer, as `file://`
      apps could on webOS. Later targets should limit the shim to app origins, along with the
      bridge itself.
- **CSS pixels are TouchPad pixels** on tablets, like the shell, at the same whole-number
  scale. Each card's WebView is set to that initial scale, with wide-viewport mode off, so
  the layout width is the card width in TouchPad px (1281 × 772 in landscape on the HP 10
  G2). Apps lay out and size text as
  they did on a TouchPad, whatever density the vendor chose. `devicePixelRatio` still
  reports Android's value (1.33 on the HP 10 G2); a TouchPad reported 1.
- **Screen size.** Cards render at the device's real width. Enyo's flex boxes and panels
  fill the vertical space and split the horizontal space into panes, so apps adapt on their
  own. A per-app **fixed-viewport fallback** exists for the few apps that hardcode sizes:
  the page is laid out at a set width (640 px) and scaled down into a narrower card. It is
  a user setting, on by default only for the bundled Palm apps known to need it
  ([phone.md](phone.md), "Fixed viewport").
- **PalmSystem / PalmServiceBridge.** JS objects matching the webOS API, backed by a native
  bridge.
  - **Transport.** Android 5 offers only `addJavascriptInterface`: `WebMessageListener` and
    message ports need newer WebViews or newer Android versions.
  - **Bus calls stay asynchronous.** The interface method only queues the call and returns at
    once. The reply comes back through `evaluateJavascript`, so the page never waits on the
    bus.
  - **Synchronous `PalmSystem` parts** can use the interface's synchronous path directly:
    properties such as `deviceInfo`, `launchParams`, `identifier`, `locale`, `timeFormat`
    and orientation, `palmGetResource`, and methods that return a value at once (like
    `addBannerMessage`, which returns an id). `palmGetResource` with a `json` hint (Enyo
    passes `"const json"`) returns the parsed object, not a string.
  - **Loading.** There is no document-start script API on this WebView. The bridge script is
    added to each app's HTML at serve time, as the first `<script>` in `<head>`. This is a
    global serve-time transform.
  - **The other serve-time transforms** (`CssTransforms`, `HtmlTransforms`), each one
    mechanical and applied to every app's files: a border image is given a border style,
    because newer Chromium computes the width to 0 without one; a self-closed non-void tag
    becomes an open and close pair, because webOS's parser read it that way; and
    `-webkit-palm-mouse-target: ignore` - webOS's own property for an element that takes no
    touches - becomes `pointer-events: none`. See [fix-log.md](fix-log.md) for what each one
    was found by.
  - **A card holds its space while the app loads.** webOS put a card in the switcher as soon
    as an app was launched, with the app's own `splashicon` on a dark background until it had
    drawn. Lunacy draws the same placeholder (`CardSplash`), and the page tells the shell when
    it has actually put a frame up - not when the framework announces itself, which is earlier
    than it sounds. The card goes into the card view *before* the page loads, and maximizes
    when the app is ready: the page's scripts run on the shell's thread (the WebView is
    single-process), so the load starts once the card has slid into the card view
    (`ShellActivity.loadWhenShown`), and a headless app gets a placeholder card its first
    card window fills. Not a launch from Exhibition, which shows only what the app opens for
    the dock. [luna-deltas.md](luna-deltas.md) D, "The startup card", has the measurements.
  - **An app that was written for a phone gets one.** `appinfo.json`'s `uiRevision` is how an
    app says which screen it was laid out for, and a TouchPad ran an app that didn't say `2`
    in a phone-sized card - LunaSysMgr's `Window::Type_Emulated_Card`, drawn as a little phone
    in the middle of the tablet. Lunacy does the same: the page is 320 x 452 centred in the
    card with LunaCE's own frame behind it, and `deviceInfo`, `screen` and the window's
    orientation report that card rather than the screen. What does *not* change is who the
    device is: the model, the version, the serial and the user agent stay the TouchPad's,
    exactly as they do there.
  - **The viewport is declared, not inferred.** A webOS app carries a
    `<meta name="viewport">`, and on a device it says something about the *card*: the SDK told
    developers to put `height=device-height` in index.html so a Pre/Pre2-shaped app isn't
    letterboxed on a Pre3, and `"uiRevision": 2` in appinfo.json so it isn't put in the phone
    simulator frame on a TouchPad. What it never carried is a width, because a card was the
    viewport and there was nothing to say.
    This engine needs telling, and left to itself it takes the page's own layout width and
    draws the page smaller when that is wider than the card. So the compat layer writes a
    viewport meta carrying the card's width and a scale pinned at 1, **merged into** what the
    app declared: every directive the app wrote is kept, `device-width` and `device-height`
    are rewritten as the card's own numbers (they mean the card, and this engine would read
    them as the display in density-independent pixels), and what the app left unsaid is filled
    in - the card's height as well as its width, since with only the width given the engine
    works the height out itself and lands a pixel short, leaving the bottom row of the card
    showing under the page. The scale is pinned only for an app that says nothing about it:
    one that does is controlling its own. The app's own element is left where it is; the merged
    copy is appended after it. The width follows the card, so it is written again whenever the
    card is resized - a rotation, or the keyboard taking half of it.
  - **extractfs.** webOS's thumbnailer was a FUSE filesystem rather than a service: reading
    `/var/luna/data/extractfs<path>:<x>:<y>:<w>:<h>:<mode>` gave that image scaled to fit the
    box. The card host answers the same paths, so an app that shows artwork at a fixed size
    gets its thumbnails.
  - **Later platforms.** When Lunacy targets them, the transport can move to
    `WebMessageListener` and document-start scripts. Nothing above the bridge changes.
- **Device identity: Lunacy answers as a webOS device.** Apps were written against a device,
  not against a browser: they branch on the model, the platform version and the user agent,
  and the servers they talk to were built for those values. The webOS device closest to an
  Android tablet is the TouchPad, and to a phone the Pre3, so Lunacy reports itself as one of
  those two - completely, and from one place (`DeviceProfile.kt`), so the surfaces an app can
  look at never disagree:
  - `PalmSystem.deviceInfo`, a JSON *string* with the keys a TouchPad reports, including the
    card-size and keyboard fields;
  - the user agent, in `navigator.userAgent`, the page's own loads and the network shim;
  - `com.palm.preferences/systemProperties/Get` (`DMMODEL`, `PRODoID`, `boardType`,
    `deviceName`, `version`, the build fields);
  - `X-Palm-Carrier` on every request the shim sends (`c090-01`, the WiFi TouchPad's).
  - **The device's identity is derived, not random, and can be carried over.** webOS Archive's
    services know a device by its `nduid` - the App Museum and the shared updater library both
    send it as `clientid` - and app licences were tied to it, so it has to be stable: Lunacy
    derives it (and the HP-shaped serial that `ProdSN` and `deviceInfo.serialNumber` share)
    from this device's own hardware ids, as a SHA-1, which is 40 hex digits exactly as an
    nduid is. Reinstalling Lunacy gives the same id back, so a service doesn't count a new
    device each time, and the hardware ids themselves never leave the device.
  - Someone moving off failing hardware can type their TouchPad's own id into Device Info and
    be the same device here; "Use This Device's ID" puts back the derived one. There is no
    "new random id": every change is one the owner asked for. Only Lunacy's own bundled apps
    may set it (`org.webosarchive.lunacy/system/setDeviceId`), and the caller is taken from
    the window, never from the page (rule 10), so an installed app can't change the device's
    identity underneath its owner.
  - The screen is the real one. A TouchPad reported its own screen, and an app that lays out
    from `deviceInfo` should use the room it actually has; like a device's, the values don't
    change when the screen turns (see "Rotation" below).
  - `locale`, `localeRegion`, `phoneRegion` and `timeFormat` follow Android's settings, as
    webOS's followed the device's, so an app formats dates and times the way the shell's own
    clock does.
  - `PalmSystem.version` is `"Webkit4/V8; device"`, as the TouchPad reports it - not the
    webOS version.
  - The Pre3's values are **not measured on hardware**; they are the community's record, and
    the first target is a tablet, so nothing depends on them yet.
  - **What is never spoofed:** Lunacy's own Device Info app, which reports the real device,
    Android, WebView and Lunacy version; the shell, which is plainly Lunacy's; and the bus,
    where Lunacy's own service carries Lunacy's name and an unimplemented service returns a
    real error. The spoof is for apps' benefit, not a claim to be webOS.
- **The `PalmSystem` object is a host object**, as it is on a device: none of its members
  enumerate, so `for..in` and `Object.keys` see nothing. `getResource` and `getIdentifier` are
  undefined here too, because they are undefined on a TouchPad (Docs/spike-1.md).
- **Caller identity.** The bridge takes the calling app's id from the WebView it is attached
  to, never from anything the page says.
- **Frames can reach the bus (ratchet item).** A JavaScript interface is visible to every
  frame in the WebView, including remote iframes. On Android 5 this is accepted. On later
  targets, the bridge moves to `WebMessageListener` restricted to `*.media.cryptofs.apps`.
- **Enyo's `WebView` control.** On webOS, `enyo.WebView` wrapped the native `BrowserAdapter`
  plugin, which drew a page that browserserver, another process, had loaded. In a Lunacy card
  the plugin is a native Android WebView (`BrowserViews.kt`), a child of the card's own
  WebView so that it moves, scales and clips with the card; the page's node is an empty box
  the native view is kept over. The control, its events and the app are Enyo's and the app's
  own code; only the bottom layer - the node, and the one call every command goes through -
  changes. The plugin's commands and callbacks cross by name: loading and its progress, the
  title with back and forward, load errors, the page's alert/confirm/prompt, certificate and
  password dialogs, downloads, links to other schemes, new windows (`createPage`, which the
  app answers with a card of its own), and holding a link or image (the app's context menu).
  The plugin drew into the page, so Enyo's popups came out over the web content; while one is
  open over the box, the native view is drawn into a picture that takes its place in the page,
  and steps aside until the popup has gone. Outside a Lunacy card the fork still draws an
  iframe. See [the fork's change log](../LunaRuntimes/enyo-1.0/CHANGES.md), 0002 and 0005.
- **The Web app.** Palm's own (`com.palm.app.browser`) ships with Lunacy, unchanged, and is
  where web links go: `applicationManager/open` with an `http`, `https` or `data` target opens
  it in a card of its own, as the TouchPad's `command-resource-handlers.json` said; so does
  Just Type's search. What it asks of the system is answered: its db8 kinds (bookmarks,
  history, preferences) are in the ROM's `/etc/palm`, `com.palm.universalsearch` gives the
  TouchPad's web search engines, `applicationManager/getResourceInfo` sends a download to the
  app that handles it (video to the video player, anything else to the Web app, which
  downloads it, as measured), and its bookmark thumbnails are served from
  `/var/luna/data/browser`. Its pages are drawn light whatever Android's theme is, and it sends
  the WebView's own user agent: the TouchPad's would get today's sites' unsupported-browser
  pages, while app cards keep the TouchPad's for the services they talk to. Two things
  are honest errors: `browserServer/clearCookies`, because Android's WebView has one cookie jar
  for the whole of Lunacy and clearing it would sign every app out; and
  `applicationManager/addLaunchPoint` (Add to Launcher), which Lunacy's launcher hasn't got.
- **System files.** Apps ask for files beyond the framework, e.g.
  `/usr/palm/command-resource-handlers.json`. On a TouchPad most of those requests throw,
  because apps can't read arbitrary local files. Lunacy matches that: it serves the
  framework and the few system files apps really could read, fails the rest, and logs every
  request for a system path the same way the bus logs unknown services.
- **`palmGetResource(url, hint)`** takes an absolute URL. It returns the parsed object with a
  `json` hint, the text otherwise, and `null` for a missing or disallowed file (measured on
  a TouchPad).
- **Text editing** (`assets/lunacy/textedit.js`). The compat layer cancels every touch to give
  apps webOS's mouse events, so the engine never places a caret or selects anything. What the
  TouchPad's system did in every editable field, Enyo or Mojo alike, is done here instead, as
  measured on the reference TouchPad: a tap puts the caret under the finger; holding a second
  puts it there and shows "Select | Select All" (with "| Paste" when the clipboard has text)
  under it; Select and Select All highlight in yellow at 60% with a grey handle at each end; a
  touch on the selection or a dragged handle shows "Cut | Copy"; a double tap selects a word
  without handles; a tap elsewhere clears it all. The popup is drawn to the TouchPad's
  measurements and goes over the text where there is no room under it. An input or textarea is
  measured through a copy laid over it for a moment, since a page can't see where its text
  is. Touches that start on the popup or a handle are taken by position, not by their target:
  the engine gives a touch a few px from a focused field to the field. Text that isn't
  editable can't be selected in an app, as on the TouchPad.
- **Two Enyo code paths.** Enyo behaves differently when `PalmSystem` exists: for example,
  it focuses inputs through `PalmSystem.simulateMouseClick`. Lunacy provides `PalmSystem`, so
  that is the path the fork is fixed and tested on.
- **Renderer drift.** The WebView can update itself, so the renderer is a moving target. On
  Android 5 it stops at about Chromium 95. The test suite runs against every WebView
  version Lunacy supports, and against WebView beta once newer Android versions are
  targeted.

## Notifications

Following LunaCE's tablet mode (Docs/luna-shell-reference.md §4 and §5):

- **Banners.**
  - Created with `PalmSystem.addBannerMessage(message, launchParams, icon, …)`, which
    returns an id at once. `removeBannerMessage` and `clearBannerMessages` also work.
  - A banner unrolls leftward inside the status bar, from the notification area, over the
    area's full maximum width (319 px) with no backing, text left-aligned. Timing:
    1 s in (OutCubic), held 5 s, or 2 s when others are queued, then 1 s out (linear, to 25 %
    opacity). Banners queue first-in, first-out.
  - Tapping the notification area while a banner shows relaunches its app with the
    banner's parameters.
- **Window types.**
  - Enyo and Mojo open windows with `window.open(url, name, "attributes={…}")`, and
    `attributes.window` is `card`, `dashboard` or `popupalert`.
  - Android's `onCreateWindow` never sees the features string, so the bridge wraps
    `window.open`: it hands the attributes to native just before the real open.
- **Dashboards.**
  - Each dashboard puts its icon (`attributes.icon`, else the app's mini icon - its
    `miniicon`, or its launcher icon at 28 px turned grey, as measured) in the status bar's
    notification group, newest leftmost; icons slide in and out over 1000 ms.
  - Tapping the group drops down LunaCE's menu frame (`menu-dropdown-bg.png`), with every
    dashboard as a live 320 × 52 px window, newest first. Past 410 px it scrolls behind
    LunaCE's fade masks. A row swiped right by more than a quarter of its width closes that
    window, uncovering `menu-dropdown-swipe-bg.png` as it goes; a dashboard opened with
    `webosDragMode: "manual"` (all of Enyo's) is dragged only from its 50 px badge, and the
    rest of its touches are the app's, which swipes its own layers.
  - Dashboard windows keep running while the menu is closed.
- **Icon sizes** follow webOS conventions:
  - Status-bar notification icons are drawn at natural size and scaled down only if taller
    than 26 px. Stock icons are 24 × 24 or 32 × 32 canvases with a white glyph.
  - Dashboard layer icons are 48 × 48 (Enyo's `.palm-dashboard-icon`).
  - While the drop-down is open, the notification group sits on
    `status-bar-menu-dropdown-tab.png`.
- **Popup alerts** sit 320 px wide in `popup-bg.png`, 5 px from the top right.
- **Transparent pages.** Dashboard and popup pages are transparent over Luna's dark frames.
  The WebView background is reset to transparent after each load, because the
  `window.open` transport resets it.
- **Home button order.** It closes the drop-down first, then the newest popup alert.
- **Test app.** `org.webosarchive.lunacy.notifytest` (in `AndroidLuna/test-apps/`, in the APK
  only with `-PtestApps`) exercises all of this with
  standard Enyo 1 calls. Launch parameters (`{"do": "banner" | "banners" | "push" | "pop" |
  "popup"}`) drive it without touches, on a TouchPad (`palm-launch -p`) and in Lunacy (the
  `launch` and `params` intent extras).

## Settings

webOS's settings were ordinary Enyo apps talking to system services, and most of them are
still the right UI - they just need somebody to answer. Lunacy answers only for what it
actually owns, and each settings app falls into one of three cases:

- **Lunacy's own app**, where webOS's version reported a webOS device and Lunacy isn't one.
  Device Info is the first: it reports the device, Android, the WebView, Lunacy's own version,
  the Node that runs JS services and the display, all from
  `palm://org.webosarchive.lunacy/system/getEnvironment`. That service carries Lunacy's own
  name, so nothing on a webOS service name is Lunacy pretending (see "Luna bus" above). The
  app keeps Palm's app id and icons, so it sits where webOS users look for it.
- **Palm's app, unchanged**, where Lunacy can answer what it asks for. Screen & Lock is the
  first: `com.palm.systemservice` stores its preferences and holds the wallpaper,
  `com.palm.display/control` really does set Android's brightness and screen-off timeout, and
  Change Wallpaper opens the file picker below. What Lunacy hasn't got (a lock screen:
  `com.palm.systemmanager`) returns the bus's honest error, and the app shows what it showed
  on a device when a service failed.
- **A shortcut to Android's own screen**, where the setting belongs to the host OS and a copy
  of webOS's UI could only pretend. Wi-Fi is an icon that opens Android's
  settings through `palm://org.webosarchive.lunacy/android/openSettings`. They declare it with
  `"lunacyAndroidSettings": "<panel>"` in `appinfo.json` - a Lunacy extension, only ever used
  by apps Lunacy ships - and launching one opens no window.
- **A Lunacy app under Palm's id**, where Lunacy owns part of a setting: Device Info reports
  the environment apps really run in, and Sounds & Alerts keeps Palm's layout for the sounds
  the shell plays (through `com.palm.audio` and the system service's preferences). Each has a
  button to Android's own screen for the part Android owns.

A settings app Lunacy can neither answer nor hand over isn't shipped at all. Palm's own
settings apps that Lunacy ships (Screen & Lock, Help) are in the APK with a NOTICE, as
abandonware, like Mojo and the Prelude fonts (codepoet, 2026-09-20).

**Palm's Video Player** is in the APK on the same terms, and for a different reason: it is
part of the platform rather than an app someone chooses. webOS apps play video by launching
`com.palm.app.videoplayer` with a target, and MeTube - among others - has no player of its
own, so without it those apps get an honest error and no film. Its `appinfo.json` says
`"visible": "false"`, which is how webOS kept it out of the launcher; Lunacy reads that too,
so it has no icon but still runs, still answers `listApps` and can still be launched by id
(codepoet, 2026-09-21).

- **The preference store.** `com.palm.systemservice` is a store, as it was on webOS: it keeps
  whatever key an app gives it, and whoever owns the thing a key names acts on it. The shell
  owns `wallpaper` (and `enableALS`, which is Android's brightness mode). A key nothing owns is
  kept and nothing more, exactly as on a device - the service isn't claiming to have acted.
  `getPreferences` returns only the keys that exist, and a subscription hears about the keys it
  asked for, both measured on the reference TouchPad.
- **Wallpapers.** `wallpaper/importWallpaper` copies the picked file into
  `/media/internal/.wallpapers/` with a thumbnail beside it in `thumbs/`, and answers with
  `{wallpaperName, wallpaperFile, wallpaperThumbFile}` - the shape the reference TouchPad's own
  preference has. Lunacy ships the TouchPad's wallpapers into `/media/internal/wallpapers/`,
  where a device kept them, so there is something to pick on a fresh install.
- **Android's wallpaper, opt-in.** Screen & Lock's "Use on Android" (a [Lunacy] switch, off by
  default) calls `org.webosarchive.lunacy` `system/setHostWallpaper`, which sets Android's
  wallpaper to webOS's through `WallpaperManager` (`SET_WALLPAPER`, a normal permission), and
  from then on the shell does again whenever the `wallpaper` preference changes
  (`HostWallpaper.kt`). From Android 7 the lock screen is set as well; Android 5 and 6 have one
  wallpaper, which their lock screen shows. Nothing Lunacy draws changes. Turning it off leaves
  Android's wallpaper as it is.
- **Android's settings, written for real.** `WRITE_SETTINGS` is granted at install on
  Android 5. Lunacy targets API 24, so from Android 6 it needs the user's consent: the first
  refused write in a run opens Android's "Modify system settings" screen for Lunacy
  (`DisplayService.write`). That write still fails, and the bus says so; the ones after the
  user allows it take.
- **Bluetooth state for the status bar (ratchet item).** The Bluetooth icon reads the adapter
  and its A2DP and headset connections with `BLUETOOTH`, a normal permission on API 21. From
  API 31 that needs `BLUETOOTH_CONNECT`, which the user grants; until it is asked for, the
  status bar catches the refusal and shows no Bluetooth icon.

### Exhibition

webOS's Exhibition mode is what a TouchPad did on its Touchstone: the screen becomes a clock,
or whatever app its owner chose. It is one of the things people remember about the device, so
Lunacy has it - inside the environment, and as Android's own screen saver.

- **Palm's Exhibition app runs unchanged** (`com.palm.app.exhibitionpreferences`, bundled). It
  lists the apps that offer a face, switches them on and off, and its Start Exhibition button
  is `com.palm.display/control/setState {"state":"dock"}` - which the shell answers by
  entering the mode. The home button comes back out, and the screen is kept awake while it is
  exhibiting, which is the point of a dock.
- **The Time face is the shell's**, as it was LunaSysMgr's: the Exhibition app's own list calls
  it "Time", adds it itself and won't let you switch it off, because no app provides it. Lunacy
  draws it from the reference TouchPad's own QML
  (`/usr/palm/sysmgr/uiComponents/DockModeTime`), not LunaCE's repo copy: webOS CE put a plain
  face first and kept the three stock ones (glass analog, digital flipper, matte analog) behind
  it, so there are four and dock mode opens on the plain one. Swipe to change face.
- **Which apps offer a face** comes from `appinfo.json`, either way webOS accepted and both
  present on the reference device: `"dockMode": true`, or `"exhibitionMode": true` with an
  optional `"exhibitionModeOptions": {"title": ...}`.
  `applicationManager/listDockModeLaunchPoints` answers with the records a TouchPad returns,
  and `addDockModeLaunchPoint`/`removeDockModeLaunchPoint` turn one on or off, telling every
  subscriber.
- **The title changes face.** While exhibiting, the status bar's title drops down the faces to
  choose from - the shell's Time, and every app its owner turned on - and picking one switches
  without leaving the mode, as LunaSysMgr's DockModeAppMenu did. Rows are LunaCE's 70 px, icon
  and dock title, in the same menu frame as the notification drop-down.
- **Android's screen saver is Exhibition** (`ExhibitionDream`). Android starts a dream when a
  device is docked or charging and left alone, which is the same occasion a Touchstone was, so
  Lunacy offers itself as one: Settings > Display > Daydream > **Lunacy Exhibition**. The
  service draws nothing of its own - it starts the shell with `exhibition` set and stands
  down, so there is one way into the mode and an exhibiting app is a real card with the whole
  bus behind it. The gear beside the entry opens Palm's Exhibition app, because which app
  exhibits is webOS's setting, not an Android one.
  - **The mode outlasts the dream, deliberately.** Android ends a dream at the first touch;
    webOS's Exhibition survived being touched, and its faces are swiped between. So the shell
    takes on the rest of a dream's job while it stands in for one: the screen is held on, the
    window shows over the lock screen (without dismissing it), and the mode ends when the
    device comes off its charger - which is also what leaving a Touchstone did. Leaving puts
    the shell back where the screen saver found it.
  - Whether to daydream at all, and with what, stays the owner's choice in Android's settings.
    Lunacy never sets it.
- **An app's own exhibition view.** The chosen app is launched with exactly the parameters
  LunaSysMgr's `DockModeWindowManager::launchApp` built - `{"windowType":"dockModeWindow",
  "dockMode":true}` - and apps test both keys, so a launch missing either shows the app's
  ordinary view instead (AccuWeather does exactly that). That test is Palm's own: the SDK's
  ExhibitionMode sample is where the code comes from, and the Clock, Photos and Music apps
  all read the same two keys, at relaunch as well as launch. Many of these apps are `noWindow`:
  the launch runs a headless dispatcher, which opens its exhibition view as a window with
  `attributes={"window":"dockMode"}`, the same window-type path dashboards and popup alerts
  take. Leaving the mode closes what entering it opened - webOS's `closeApp` did - while an
  app its owner already had open keeps its own cards and loses only its dock window.

### System UI

webOS served some UI from the OS itself, and apps reach it at its absolute path: Enyo 1's
`enyo.FilePicker` loads
`/usr/lib/luna/system/luna-systemui/app/FilePicker/filepicker.html` in an iframe, passes its
parameters in the query string and takes the result back through `postMessage`
(`enyoCrossAppResult=`). Lunacy serves that path on every app origin, with its own page behind
it, so the control works in every app without any app being changed.

- Palm's picker listed what the media indexer had in db8. Lunacy has no media indexer, so its
  picker reads the webOS tree through `palm://org.webosarchive.lunacy/files/list`, grouped by
  folder the way Palm's showed albums.
- **`/media/internal` is the user's own storage**, as it was on a device, so Android's shared
  folders are mapped into it under the names webOS used: `downloads`, `music`, `photos`,
  `documents`, `camera`, `video` (`UserFiles.kt`). The card host, the media server and the
  picker all resolve paths through it, and nothing outside those folders is reachable.
  Reading them needs `READ_EXTERNAL_STORAGE`, which Android 5 grants at install. From
  Android 6 the shell asks for it once at startup (`ShellActivity.askForStorage`). If it's
  refused, the mapped folders just aren't there.
- Thumbnails come from `?__lunacy_thumb=<px>` on an image under `/media/internal`, which the
  card host answers with a scaled JPEG. Full-size photos would not fit in 1 GB of RAM. The
  parameter carries Lunacy's own prefix so no app can stumble into it.
- **Frames can use the bus.** On webOS every frame of a window had its own `PalmServiceBridge`
  binding. Here a reply arrives through `evaluateJavascript`, which only ever runs in the main
  frame, so a window's same-origin frames share one token counter and the main frame hands each
  reply to the frame that is waiting for it. The network shim does the same with its request
  ids. A cross-origin frame can't join in, and shouldn't - see the ratchet item above.

## App lifecycle

On webOS an app is not a page. The shell and the app's windows share a contract, and Enyo 1
apps depend on it as much as Mojo apps do.

- **Headless root window.** An app with `noWindow: true` in `appinfo.json` starts as an
  invisible window (its `index.html`), which then opens its cards. Lunacy runs it in a
  WebView that is never shown.
- **`window.open` makes windows.** Cards, dashboards and popup alerts are all opened with
  `window.open`; the window type comes from its attributes. Lunacy handles this through
  `onCreateWindow`, which keeps the opener link. `enyo.windows` makes synchronous calls
  between an app's windows, and that works because they share an origin and a renderer.
- **Launch and relaunch.** First launch passes `launchParams`. Launching an app that is
  already running doesn't reload it. As LunaSysMgr did, the root window's
  `PalmSystem.launchParams` becomes the new parameters and `Mojo.relaunch()` runs; if it
  returns false, the shell brings the app's first card forward. An app that handles the
  relaunch brings its own window forward with `PalmSystem.activate()`.
- **Focus.** The maximized card's window has `PalmSystem.isActivated` true and gets
  `Mojo.stageActivated`; the others have it false and get `stageDeactivated`. Enyo finds its
  active window through `isActivated`. A page hears it once its framework has called `stageReady` (or it has
  drawn), so a card maximized at launch still gets its `windowActivated`, and each change is
  sent once.
- **Window properties.** `setWindowProperties` acts on `blockScreenTimeout`, `statusBarColor`
  (the bar's fill, taken up at the next maximize) and `fullScreen` (as
  `enableFullScreenMode`: the card grows over the bar's space and the bar slides off while it
  is maximized). `suppressBannerMessages`, `suppressGestures` and `rotationLockMaximized` are
  accepted and do nothing, which is what LunaSysMgr did with them on a TouchPad
  ([luna-deltas.md](luna-deltas.md), D).
- **App menu.** Tapping the title (with its ▾) in the status bar relaunches the maximized
  card's app with `{"palm-command":"open-app-menu"}`, as LunaSysMgr's
  `SystemUiController::toggleCurrentAppMenu` did. Enyo 1 and Mojo open their app menu on
  that relaunch.
- **Stage events.** `stageReady` tells the shell a card can be shown. The shell sends activate
  and deactivate events when cards gain or lose focus, and keeps background cards running as
  webOS did.
- **How the host calls the app.** LunaSysMgr called functions on a global `Mojo` object,
  and Enyo defines them: `stageActivated`, `stageDeactivated`, `relaunch`, `show`, `hide`,
  `handleGesture`, `keyboardShown`, `positiveSpaceChanged`, `screenOrientationChanged` and
  `lowMemoryNotification`. Lunacy's shell drives app lifecycle through the same functions,
  so both Enyo and Mojo receive events the way they expect.
- **Back gesture.** Delivered to the focused card as the Escape key event Enyo and Mojo
  expect. Android's back maps to the same event.
- **Keyboard.** The Android keyboard follows webOS's two modes (Enyo's `enyo.keyboard`).
  In automatic mode, the default, it shows while a text field in the active card has focus -
  whoever focused it, the app itself as much as a tap - and a tap on an already focused field
  brings it back. "Active" includes a card still animating open, because an app often focuses
  a field as its first scene is built; LunaSysMgr made the same allowance. The active card's
  window holds Android's focus, without which a WebView dispatches no focus events at all.
  Putting the keyboard away takes the field's focus with it, which is how webOS did it:
  `IMEController::hideIME` asked the page to `removeInputFocus` and the keyboard followed the
  blur, rather than the other way round. That matters because Mojo gives an app no other way
  to notice the keyboard. In manual mode
  (`PalmSystem.setManualKeyboardEnabled(true)`) only `keyboardShow` and `keyboardHide` move
  it. While it shows, the card shrinks to the space above it: `Mojo.keyboardShown(true)`
  comes first, and on hiding the card grows back before `Mojo.keyboardShown(false)`. A
  window that called `allowResizeOnPositiveSpaceChange(false)` isn't resized; it gets
  `Mojo.positiveSpaceChanged(width, height)` instead. Android shows its own bars with the
  keyboard, and the shell hides them again afterwards. The keyboard is Android's, not
  webOS's.
- **Window close.** Throwing a card away closes that window and fires its unload handlers.
  The app ends when its last window closes (or its headless root closes itself).

## Luna bus

A simulated, in-process bus. It is not `ls-hubd`, but it speaks the same JSON request/response
model, so apps can't tell the difference.

- **Router.** URI `palm://service/method` → registered handler. It supports single calls,
  cancellation and **subscriptions that actually deliver updates**. A call with
  `"subscribe": true` stays open: the service keeps replying as things change, until the
  page calls `PalmServiceBridge.cancel()`, calls again on the same bridge, or its window
  closes. Services that don't change just answer once, as the TouchPad's do.
- **Honesty.** Unknown services or methods return a real error, in webOS's exact form:
  `{"returnValue":false,"errorCode":-1,"errorText":"Service does not exist: <service>."}`. The bus never fakes success.
- **Coverage log.** Every call is recorded: app, service, method, and whether it was handled.
  This shows which services to build next and feeds the per-app compatibility score.
- **Lunacy-backed services.**
  - `com.palm.applicationManager`: launch/open/listApps, wired to the shell and package manager.
    `listApps` returns the fields a TouchPad returns, with `main` as a `file://` URL and
    `icon` as an absolute path under `/media/cryptofs/apps`.
  - `com.palm.db` (db8) and `com.palm.tempdb` on SQLite: kinds, `find`/`search` with
    queries and index rules, `put`/`get`/`merge`/`del`, `batch`, permissions and one-shot
    watches, answering as the TouchPad does; see [db8.md](db8.md). Packages' kinds and
    permissions (`configuration/db/`) are registered at install by `Configurator`.
  - System preferences and the notification/banner services, wired to the shell.
    `preferences/systemProperties/Get` answers with the reference TouchPad's values (webOS CE
    3.1.0, HP TouchPad). `nduid` is a 40-hex id each install generates and keeps, and
    `ProdSN` matches `deviceInfo`'s serial. An unknown key gets the TouchPad's
    `{"returnValue": false, "errorText": "no such key"}`.
  - `com.palm.systemservice`: webOS's preference store and its wallpaper store, and
    `com.palm.display/control` for the screen's brightness and timeout. See "Settings" below.
  - `com.palm.bus/signal/registerServerStatus`: whether a service is on the bus. On webOS this
    is ls-hubd's own signal rather than a service, so Lunacy's router answers it, and answers
    again when a service comes or goes (a package's JS services do). Apps wait for it before
    calling a service; Palm's Help app does, before the connection manager.
  - `org.webosarchive.lunacy`: Lunacy's own service, under its own name - the environment
    Lunacy runs in, Android's settings screens, and the file listing its picker needs. Nothing
    of Lunacy's sits on a webOS service name.
  - `com.palm.downloadmanager`: webOS's downloader, which apps hand every file fetch to rather
    than doing it themselves - drPodder's episodes and album art, MeTube's "download first",
    Glimpse. Files land in the webOS tree through `UserFiles`, and a subscriber gets the
    ticket, then progress, then the completion record, in the shapes measured on the
    reference device. **Ratchet item:** a download lives only as long as the shell's process,
    where webOS's survived the app closing and a reboot.
  - Installs: the App Museum calls `applicationManager/open` for Preware's id
    (`org.webosinternals.preware`, or `org.webosports.app.preware` on LuneOS) with
    `{type: "install", file: <ipk url>}`. Lunacy's package manager answers for those ids.
- **Startup calls.** Apps call the bus while they start, and their error paths were rarely
  exercised on real devices, where system services always answered. So the router and the
  services apps call at startup arrive with phase 1, not after it. Honest errors are for
  services Lunacy doesn't have, not a substitute for the ones every app assumes.
- **Build order comes from data.** A static scan of the App Museum's packages counts
  `palm://` URIs, framework paths, `openDatabase` use and bundled services. That ranking,
  and then the live coverage log, decide which services get built first.
- **Android-backed services.** Connectivity, power/battery, time/locale/timezone, audio, and
  opening URLs, phone numbers and email addresses through intents.
  - `connectionmanager/getstatus` (and `getStatus`, which the TouchPad also accepts) reports
    Android's connectivity in the TouchPad's shape (`isInternetConnectionAvailable`, then
    `wifi`, `wan`, `vpn` and `bridge`, each with a `state`). Subscribers get a new reply
    when the status changes. Wi-Fi reports its real address, SSID and signal as
    `networkConfidenceLevel`; `interfaceName` is Android's `wlan0`, not the TouchPad's `eth0`.
- **Permissions.** A service that exposes Android capabilities checks the calling app's
  `appinfo.json` and asks the user where Android requires it.

## WebSQL

Mojo apps keep their data in WebSQL (`Mojo.Depot` is a table layout over `openDatabase`), and
many Enyo 1 apps call `openDatabase()` directly. Every WebView available on Android 5
(Chromium 37 to about 95) still has native WebSQL, stored per origin, so Lunacy uses it as it
is, with the compat layer correcting the two things WebKit 534.6 did differently
([mojo.md](mojo.md), "What a browser gets wrong about WebSQL"). Chromium removed WebSQL in
version 119, so on a WebView from then on (the Pixel Tablet's 153, 2026-10-02) the page gets
no `openDatabase` at all, and Lunacy provides it in JS instead:

- **`websql.js`**, injected after the network shim and doing nothing where the native one
  exists, is the API as the device had it: `openDatabase` with an arity of 0 and the
  two-argument form, `Database.transaction`, `readTransaction` and `changeVersion` with its
  preflight, transactions run one at a time per database, statement callbacks that queue
  more statements into the same transaction, an error callback that keeps the transaction
  alive by returning `false`, `SQLResultSet` with `rows.item()`, `rowsAffected` and an
  `insertId` that throws when nothing was inserted, and `SQLError` with WebKit's codes and
  SQLite's own wording.
- **`WebSql.kt`** is its store, one per window like the network shim: the window's statements
  run in order on a thread of their own, never the main thread, through the same request id,
  `evaluateJavascript` delivery and fetch-back as `net.js`. Each transaction is a native
  SQLite transaction held open across the round trips, with each batch of statements run in
  order up to the first failure, which the page then rules on.
- **One SQLite file per app and database name**, under `files/websql/<app id>/`, so an app's
  data is the app's whichever origin its windows load from (the native store was per origin,
  and an app updated from bundled to installed changed origin). The version lives inside the
  file in WebKit's `__WebKitDatabaseInfo__` table, which Chromium kept, so a database the
  WebView stored before it lost WebSQL is copied in whole, version and all, the first time
  the polyfill opens that name (Chromium's `Databases.db` index names the file). The
  migration is written to Chromium's layout and has not yet been exercised on a device whose
  WebView was updated across the removal.
- Not over the bus, as first planned: a service would put every statement through two
  main-thread hops and give it a name no webOS app ever saw, where the window's own bridge
  is invisible to the page and keeps the statements off the main thread.

## Package manager and App Catalog

- **The bundled catalogue** is App Catalog 6.2 (`com.palm.app.enyo-findapps`), the
  TouchPad's own client as webOS Archive restored it. It replaced the bundled App Museum on
  2026-09-26 (codepoet); the Museum still installs from its own package.

- **Install from `.ipk`.** Read the `ar` archive (or the tar.gz form some packagers wrote),
  unpack `data.tar.gz` (stripping the `./` prefixes), parse `appinfo.json`, and register the
  app and its icon. Package filenames can contain odd bytes, including control characters, so
  installed files live on disk as they are, not as Android assets.
  - **Where.** The package's tree is unpacked into Lunacy's own copy of
    `/media/cryptofs/apps` (`files/cryptofs/apps` in the app's data). Every app origin serves
    that tree first and the bundled apps second, so an installed version of a bundled app
    replaces it, as an update would on a device.
  - **Replacing.** A package is unpacked to a staging folder first. Each app, package and
    service folder it holds then replaces the installed one whole, so no files from an
    older version linger.
  - **Refused.** Entries that climb out of the tree (`..`) fail the install. Symlinks and
    devices are skipped.
  - **Native apps.** A `pdk` or `game` app installs and appears in the launcher; on the
    32-bit build it runs through the PDK runtime ([pdk.md](pdk.md)). Elsewhere, as before:
    launching it shows a banner saying Lunacy can't run it yet, and a bus launch returns an
    error.
  - **Feedback.** The launcher's pending icon is named after the package file until the
    package is on the device, then after the title in its app's `appinfo.json`.
    Preware showed its own progress. Lunacy shows a banner when the install
    starts and another when it ends; tapping "installed" launches the app.
  - **Plain `http` (ratchet item).** Packages are fetched as the Museum names them, usually
    plain `http`, with no signature check, as on webOS. Android 9 blocks cleartext by
    default, so later targets need a network security config or an `https` catalog.
  - **For development,** `--es install <url or path>` on the launch intent installs a
    package; see [dev-workflow.md](dev-workflow.md).
  - **Install scripts, as Preware ran them.** Lunacy stands in for Preware, whose
    ipkgservice unpacked a package with ipkg and then ran its `postinst` as root
    (`IPKG_OFFLINE_ROOT=/media/cryptofs/apps /bin/sh <script>`), reverting the install if it
    failed, and ran `prerm` on removal. Lunacy does the same - `preinst install|upgrade`,
    unpack, `postinst configure`; `prerm remove`, the listed files, `postrm remove` - in the
    webOS root (below), and keeps ipkg's records (`<pkg>.control`, `.list` and the scripts)
    in `/media/cryptofs/apps/usr/lib/ipkg/info`. A package may hold no apps at all: the
    webOS Community Account Manager stages files, and its postinst puts its app in
    `/usr/palm/applications` and patches the palmprofile service, as it does on a TouchPad.
    **Ratchet item:** scripts run with Lunacy's own permissions, as root's stand-in.
  - **App Catalog's installer.** `com.palm.appInstallService` (on the private bus, measured
    on the reference TouchPad) takes a package's URL and reports icon download, ipk download
    with progress, installing and installed through its `status` subscription, over the same
    install path as Preware's; `appinstaller/queryInstallCapacity` answers its check for room,
    and `applicationManager/listPackages` lists packages with their apps and services. `applicationManager/listAllHandlersForMime` names Preware as
    the handler for `.ipk` (Lunacy answers for Preware's ids), and `launchPointChanges`
    tells subscribers of apps added and removed.
- **Secrets.** `com.palm.keymanager` keeps each caller's keys, as the accounts service keeps
  every account's credentials. **Ratchet item:** the device kept them encrypted; Lunacy keeps
  them in a file in its private storage, which a later target can wrap with Android's keystore.
- **The webOS root.** `files/webos` is the filesystem JS services and package scripts see as
  `/` (`WebosRoot.kt`). Its ROM, `assets/rootfs/` from `fetch-assets.sh`, holds the service
  frameworks, the TouchPad's own palmprofile and accounts services (with the db8 kinds and
  permissions in `/etc/palm/db` that the Configurator registers) and the palmprofile account
  template. It is laid down off the main thread after each APK update, and a file a script
  has changed since the last APK laid it down is kept, as a package manager keeps changed
  config files. The device's symlinks (`version/1.0` is a link to `submission/N`) are made as
  links, from the manifest `fetch-assets.sh` records (`rootfs.links`), since an APK can't hold
  one; the frameworks served to pages get the same treatment in the app server (`fw.links`,
  `AppServer.followLinks`), which is what lets Mojo's images link into mojocommon file by file
  without shipping them twice. `/bin` and `/usr/bin` are busybox's commands (a static build, packaged as
  `libbusybox.so` so Android lets it run), as they were on webOS; `/bin/sh` is its ash;
  `/usr/bin/curl` and `luna-send` are Lunacy's own, in Node. `luna-send` reaches the bus
  over a socket in Lunacy's private storage, as the script's package or the service it runs
  under - identity comes from a token Lunacy gave the process, never from `-a`. Apps a script
  puts in `/usr/palm/applications` are system apps: they launch and list like any other, and
  aren't removable from the launcher.
- **Developer tools: the SDK over adb.** The Palm SDK's tools (`palm-install`, `palm-launch`,
  `palm-run`, `palm-log`, `novacom`, `novaterm`) reach Lunacy as they reach a TouchPad, through
  [webos-sdk-redux](https://github.com/webOSArchive/webos-sdk-redux)'s novacomd, which finds
  Lunacy through the host's adb server and lists it as `adb lunacy` (its
  [LUNACY.md](https://github.com/webOSArchive/webos-sdk-redux/blob/main/LUNACY.md)).
  `Novacom.kt` is novacomd's device side on the abstract socket
  `org.webosarchive.lunacy.novacomd`: `get` and `put` of files in the webOS root, `run` of a
  command in it (busybox, path mapping and luna-send as a package script gets them, calling
  the bus as `novacomd` on the private bus, as root's luna-send did), and `open tty` for
  novaterm, through busybox's `script`. It serves only peers that are root or adb's shell
  user, so who may use it comes from the socket (rule 10): adb's USB debugging is Android's
  developer mode, as novacom was webOS's. **Ratchet item:** commands run with Lunacy's own
  permissions, as root's stand-in, as package scripts do. What the tools need from the rest
  of Lunacy, each measured on the reference TouchPad:
  - `/etc/palm-build-info` says `PRODUCT_VERSION_STRING=Lunacy <version>`: Lunacy says what it
    is, and the SDK recognises it by name rather than Lunacy posing as a TouchPad (codepoet).
    `/proc/nduid`, read through novacom, is Lunacy's nduid.
  - `appinstaller/installNoVerify` and `appinstaller/remove` (private bus) install and remove
    through the package manager, with the device's tickets and statuses and no banners (the
    TouchPad showed nothing until the icon arrived; Lunacy shows its pending launcher icon,
    as for App Catalog's installs, which codepoet asked to keep);
    `applicationManager/running` lists every window and its process id, and `close` closes one.
  - `display/control/setState` `"on"`, which palm-launch and palm-run send first, wakes the
    screen (Android's `WAKE_LOCK`, a permission granted at install), and is ignored in
    Exhibition, as LunaSysMgr ignored it in dock mode.
  - `/var/log/messages` (`SysLog.kt`) has every app's console output as LunaSysMgr logged it
    (`… user.notice LunaSysMgr: {LunaSysMgrJS}: <appid>: <message>, <file>:<line>`), with
    `lunacy` in the machine field, kept under 1 MB by moving it to `messages.0`.
- **Hybrid apps.** Some web apps declare `"plug-ins": true` and ship native PDK plugins
  (Kindle does). They can run only as far as their web side goes until the PDK layer
  exists, and the compatibility score says so.
- **App Museum: the real app, not a client.** The [App Museum](https://appcatalog.webosarchive.org)
  is itself an Enyo app that already runs in modern Safari, and webOS Archive owns its
  [catalog service](https://github.com/webOSArchive/webos-catalog-service). Lunacy bundles
  the Museum app. Its install requests go to Preware through `applicationManager/open`, and
  Lunacy's package manager answers them. The catalog server supports plain `http` and CORS,
  so the Museum needs no network shim. The Museum doubles as
  the first real-world compatibility test. Compatibility scores from the coverage log can be
  fed back through the catalog service.
- **Bundled demo apps.** Apps that codepoet can license (their own apps and the Enyo
  samples). They are the first-run showcase and the compatibility test suite.

## Rules

0. **Look and feel fidelity is inviolate.** What a person sees and feels is the TouchPad's:
   the shell's every surface, motion and timing is measured on the reference device and
   matched at the same scale, and the apps' runtime is what webOS gave them. Nothing buys a
   visible difference - not speed, not a simpler design, not a shortcut the engine allows. A
   slow path is fixed by moving the work (off the main thread, behind the startup card),
   never by changing what the screen does. Where the engine forces a difference, it is
   measured, kept as small as the engine allows and recorded in
   [luna-deltas.md](luna-deltas.md) with why; codepoet decides every exception. The
   launching card of 2026-09-26 is the cautionary case: made faster by maximizing it at once,
   which is not what a device did, and put back to waiting in the card view the same day.
1. **No per-app hacks.** A fix goes into the modernized framework or the global compat layer.
   An app that needs a special case points to a missing general fix. The one allowed per-app
   switch is the fixed-viewport fallback.
   - Global serve-time transforms are allowed: a mechanical rule applied to every app's files
     as they are served. Editing one app's code is not.
   - Every fix is logged with where it landed: framework, compat layer, or nowhere general.
     If the share of general fixes doesn't grow over time, the approach is failing and needs
     rethinking. That is the test wcl never had.
2. **The bus tells the truth.** No stubbed success.
3. **Every framework change is traceable.** The Enyo (and later Mojo) forks keep a change log
   against their upstream.
4. **Licensing.**
   - Enyo 1.0 is Apache 2.0.
   - LunaCE, and so the shell images, is Apache 2.0 (see the NOTICE file next to the images).
   - Mojo, the Prelude fonts and the TouchPad wallpapers are treated as abandonware (no owner
     has asserted rights since webOS was discontinued), so they ship in the APK. Each has a
     NOTICE file saying so.

## JS services

webOS apps could ship JS services (`usr/palm/services/<id>/`, with `services.json` and
`sources.json`), which the TouchPad ran on node 0.4.12 with Palm's `mojoservice` framework.

- **Node.** nodejs-mobile 0.3.3 (Node 12.19), the last release whose `libnode.so` loads on
  Android 5 (later ones need API 24: `getifaddrs`, `pthread_barrier_*` and more). A two-line
  launcher (`AndroidLuna/tools/node-launcher.cpp`, packaged as `liblunacynode.so`) makes it an
  executable, so each service package runs in its own process: its memory is its own, a crash
  can't take the shell down, and stopping it is ending the process. Built for 32-bit and
  64-bit ARM (Node, its libc++, the launcher and busybox); Android installs the device's own.
  The 64-bit build is 16 KB-page clean: `libnode.so` is rebuilt from nodejs-mobile's source
  with 16 KB-aligned segments (`AndroidLuna/tools/build-node.sh`), libc++ and the launcher
  come from NDK r28, and Android 17 shows no compatibility dialog for it (measured on a Pixel
  Tablet). The 32-bit build keeps nodejs-mobile's 4 KB prebuilt, the one proven on the
  Android 5 devices; no 32-bit device has 16 KB pages.
- **Install.** A package's services install with its apps. Their bus names come from
  `services.json` and are registered on install and at startup. The first call starts the
  package's process; it ends when mojoservice's activity timeout exits it, or when the package
  is reinstalled.
- **The host** (`assets/lunacy/services/host.js`) does what `run-js-service` and
  `jsservicelauncher/bootstrap-node.js` did, then runs Palm's own bootstrap and frameworks
  (`foundations*`, `mojoservice*`, `underscore`, `mojoloader.js`, copied from the reference
  TouchPad by `fetch-assets.sh`). It supplies webOS's native node modules: `palmbus` (the bus,
  over the process's stdin and stdout, one JSON message per line), `webos` (`include`, and the
  per-library contexts MojoLoader's `require` expects, with `root` as the service's global) and
  `pmloglib` (logs). Node 0.4 names that are gone (`path.exists`, `sys`) are put back.
- **The webOS filesystem.** Services use absolute webOS paths. Paths under `/usr`, `/media`,
  `/bin`, `/var`, `/etc`, `/tmp` and a few more are mapped into Lunacy's copy of that tree
  (`files/webos`) for `fs`, for `child_process` (the executable, arguments, and `sh -c` command
  lines) and for `require`. There, `/media/cryptofs/apps` is the installed packages,
  `/media/internal` is user storage, and `/bin/sh` is Android's shell.
- **curl.** Services shell out to `/usr/bin/curl` (webOS Internals' `curl-tls13` on a TouchPad).
  A static curl can't resolve names on Android (musl reads `/etc/resolv.conf`, which Android
  lacks), so `/usr/bin/curl` and `curl11` run `curl.js`: the curl options services use
  (`-sSLfIk`, `-o`, `-D`, `-w`, `-H`, `-X`, `-d`/`--data-binary`, `-A`, `-K` config files,
  `--max-time`, `--compressed`) over Node's HTTP, with curl's exit codes. Any other option
  fails as curl does (`option …: is unknown`, exit 2).
- **The bus.** Calls to a service's names go to its process with the calling app as sender,
  over the public bus (a method only on the private bus is unknown to apps, as on webOS).
  Subscriptions stay open until cancelled. A service's own calls go out as that service.
- **Services' files in pages.** Apps play or read what services write to `/media/internal`
  through `file:///media/internal/…` URLs. The compat layer maps them in XHR to the app
  origin, which serves `/media/internal/`, and in media `src` (property, `setAttribute`,
  `new Audio(url)`) to `MediaServer`: a read-only HTTP server on `127.0.0.1` with a random
  port and path token, supporting byte ranges. Media needs that because Chromium hands HLS to
  Android's MediaPlayer, which runs in the mediaserver process and can't reach app origins
  (`LiveSession` fails with -1008). Plex's transcode playlists play that way.
  - **Loopback server (ratchet item).** Other apps on the device could read `/media/internal`
    through it if they learned the port and token. Later targets can hand media players a
    content provider URI instead.
- **Activity manager.** mojoservice wraps every command in a foreground activity, so
  `com.palm.activitymanager` answers `create`, `start`, `complete`, `cancel` and `stop` with the
  TouchPad's replies and events. Scheduled, triggered and callback activities return an error
  until they're built.
- **System services.** The webOS root's own `/usr/palm/services` (the ROM's, or a package
  script's) register alongside installed packages'. A page's call reaches a service's private
  methods only if the app's id is privileged (`com.palm.*`, measured); services' own calls and
  `luna-send` are private, as on webOS.
- **Not yet:** services in Mojo apps' own frameworks, db8 (next), `activitymanager` background
  work, and a jail: services run with Lunacy's own permissions
  (ratchet item).

## Mojo

What was learned bringing it up, in full: [mojo.md](mojo.md).

Started 2026-09-20, ahead of its phase, because an Exhibition app from the App Museum turned
out to be a Mojo one. Lunacy serves **Palm's own Mojo** from the reference TouchPad, as it
serves Enyo, with its changes as a patch series
([CHANGES.md](../LunaRuntimes/mojo/CHANGES.md)) and a NOTICE: abandonware, like the
Prelude fonts.

Mojo isn't packaged the way Enyo is, and that shapes what Lunacy has to do:

- **The framework was part of the browser.** A page loads `mojo.js`, which looks for a global
  `palmInitFramework<submission>` that webOS's WebKit provided and calls it. The submission on
  disk carries only assets; `builtins/` carries the code, as the V8 *native* scripts the
  browser was compiled from. Without the global, mojo.js tries to fetch a `loader.js` webOS
  doesn't ship - which is the "The load of framework submission 506 failed" error.
- **So the builtins are served in front of the app's own tag**, by the same kind of global
  serve-time transform that injects Lunacy's own scripts: a page whose HTML loads
  `/usr/palm/frameworks/mojo/mojo.js` gets Prototype and the framework before it, with the
  submission taken from the tag's own `x-mojo-version` (1 is submission 506, as mojo.js maps
  it). No app is named and no app is changed. A tag a script writes - an Ares app's `ares.js`
  writes it with `document.write` - is out of that transform's sight, so `mojo.js` itself goes
  out with a prelude (`lunacy/mojo-prelude.js`) that, when the builtins aren't there yet,
  fetches them synchronously and evaluates them in the global scope before mojo.js's own code
  runs; where the HTML had the tag, the prelude finds the global and does nothing.
- **The builtins are patched to load as ordinary scripts**: V8's `global`, `%SetProperty`, the
  `$Object`-style aliases and `builtinEval` are given their plain JavaScript meanings. The
  last one matters more than it looks - webOS's Prototype parses JSON with it, so without it
  every `evalJSON` failed and an app never saw its launch parameters.
- **`mojocommon`** is a framework of its own beside Mojo, which the submission symlinks into;
  Lunacy serves it at its own path. Its files are dereferenced when they are copied, because
  Gradle's asset merger won't take symlinks.
- **Mojo's own device test already works**: `Mojo.Host.current` is "palm-sys-mgr" when
  `window.palmGetResource` exists, which the bridge provides. What needed teaching was the
  origin - Mojo knew `file://` (a device) and `http://` (a desktop), and Lunacy is `https://`.
- **Mojo 2 is a second framework beside it**, at `/usr/palm/frameworks/mojo2`, with its own
  loader, its own submission (205) and MojoLoader handing out libraries from
  `/usr/palm/frameworks/<name>/version/<v>/`. Palm's Video Player is one, and apps hand video
  to it. The card host serves the whole frameworks tree for it, and the same builtin
  injection puts Mojo 2's framework and libraries in front of such a page.
- **Reading a file is not loading a page.** Mojo reads every widget template and every scene
  with `palmGetResource`, which on a device read the file off the disk. That call marks its
  request so the card host serves the file as it is, without the serve-time script injection
  a *page* gets; otherwise every template arrives with Lunacy's boot scripts at the front.
- **The parser is older than the standard.** webOS's WebKit closes `<script src="x" />`, and
  a whole app's markup can sit after such a tag. A global transform closes self-closed
  non-void tags in every piece of HTML Lunacy serves - measured on the device, see
  [mojo.md](mojo.md).

## Later layers

- **Mojo's multi-stage windows** map to cards, as Enyo's `window.open` windows do. Only the
  single-stage path is exercised so far.
- **PDK native apps** run as their own processes through the glibc loader, with SDL 1.2
  rebuilt to reach the shell and Lunacy's own libpdl: [pdk.md](pdk.md). The first, Commander
  Keen, ran on 2026-10-02.
  - Current SoCs are often 64-bit only and can't run 32-bit ARM code, so recompiling may be
    needed there.
  - The Android 5 test devices are 32-bit ARMv7, the same CPU class as webOS hardware, so on
    them the original binaries can run without recompiling.

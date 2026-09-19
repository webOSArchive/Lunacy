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
│    card view · gesture bar · launcher · status bar · banners/dashboard · (later) Just Type
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
├─ WebSQL                 native in Lollipop's WebView; polyfill when targets drop it
├─ Package manager        .ipk install · appinfo.json · icons · per-app data
└─ App Museum             the Museum's own Enyo app, bundled; installs via the bus
```

## Shell

The shell is a **simulator**: native Android Views that recreates the webOS experience. The
styling target is **LunaCE**, with stock TouchPad 3.0.5 as the reference where LunaCE
doesn't differ. It doesn't run LunaSysMgr or any Palm code. The webOS look and feel *is* the
product; being "close enough" is not the goal.

- **Card view.** Live cards that can be thrown away with a swipe up, reordered, and (later)
  stacked.
- **Full screen and buttons.**
  - Lunacy runs in Android's immersive sticky mode, so the whole screen is the shell. A swipe
    from the edge shows Android's buttons for a moment.
  - Android's back button acts as the TouchPad's single home button (webOS tablets had no
    back button), in LunaCE's order: close what's open on top, else minimize the maximized
    card, else toggle the launcher.
  - A swipe up from the bottom edge minimizes, as in LunaCE (15 px edge band, 15 px
    trigger).
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
- **Card memory and crashes.** On Android 5 the WebView renderer runs inside Lunacy's own
  process, so a renderer crash takes down the whole app. With 1 GB of RAM, live cards are
  expensive. The shell therefore limits how many cards stay live: older cards become a
  snapshot and reload on return. From Android 8, the renderer is a separate process, and
  the shell also recovers through `onRenderProcessGone`.
- **Launcher, status bar, banners and dashboard.** Driven by the bus (the applicationManager
  and notification services).
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
- **Apps that bundle their own framework** (all Enyo 2 apps, and some Enyo 1 apps) never hit
  the route. They rely on the compat layer and the bus alone. Enyo 2 was written for
  ordinary browsers, so this is the easier case.
- **Compatibility layer.** Global, and applied to every card; see "No per-app hacks" below.
  It has four parts:
  - *The webOS input model.* webOS's WebKit delivered touches to web content as mouse
    events, and Enyo 1 and Mojo only listen for those. Chromium synthesizes mouse events for
    taps only, not drags, so without help no scroller moves. A global script converts
    touches into `mousedown`/`mousemove`/`mouseup` and `click`, and cancels native touch
    handling. Spike 1 showed it fixes every scroller at once.
  - *Flicks.* Enyo's and Mojo's scrollers only coast after a flick the host reports. On
    webOS, LunaSysMgr measured it (FlickGestureRecognizer: the last 3 samples, above
    500 px/s, velocity = displacement ÷ (elapsed × samples)). WindowedWebApp then called
    `Mojo.handleGesture('flick', {x, y, timeStamp, xVel, yVel})` in the page. The compat
    layer does the same when a fast drag ends.
  - *Injected polyfills* (CSS and JS at document start) for other behavior apps depend on
    outside the framework: `-webkit-box`, old event quirks.
  - *System fonts.* webOS had the Prelude family installed, and Enyo and apps name it
    (`Prelude`, `Prelude Medium`, `PreludeWGL Bold`, `PreludeWGL Light` and others). Every
    page gets an injected `fonts.css`, generated by `android/tools/gen-fonts-css.py`. Its
    `@font-face` rules map each of those names to the shipped Prelude files. It also makes
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
    (measured with `spike/probe` 0.0.5). Some apps install error overlays that never showed
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
    - **The contract, measured on the TouchPad** (`spike/probe` 0.0.7, `net.js`): no CORS
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
    - **Not the same.** The TouchPad also sent `X-Palm-Carrier`, which Lunacy has no value
      for. Cross-origin `send()`
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
  own. A per-app **fixed-viewport fallback** (1024×768, scaled to fit) exists for the few
  apps that hardcode sizes. It is a metadata or user setting, never a default.
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
  - **Later platforms.** When Lunacy targets them, the transport can move to
    `WebMessageListener` and document-start scripts. Nothing above the bridge changes.
- **Device identity.**
  - `deviceInfo` reports a TouchPad on tablet-size screens and a Pre3 on phone-size
    screens, the only two device classes that ran Enyo 1. Screen dimensions are always the
    real ones.
  - Like webOS, `deviceInfo` is a JSON *string* with the same keys a TouchPad reports,
    including the card-size and keyboard fields. See "TouchPad reference" in
    [spike-1.md](spike-1.md) for the full `PalmSystem` surface measured on a real device.
  - `launchParams` is `""` when there are none.
  - The platform version is webOS CE 3.1.0 (`platformVersion` "3.1.0"), the
    community-supported version the reference TouchPad runs. `PalmSystem.version` is
    `"Webkit4/V8; device"`, as the TouchPad reports it.
  - The user agent is the reference TouchPad's, measured verbatim:
    `Mozilla/5.0 (hp-tablet; Linux; hpwOS/3.1.0; U; en-US) AppleWebKit/534.6 (KHTML, like
    Gecko) wOSSystem/234.83 Safari/534.6 TouchPad/1.0`. It is set on every app WebView, so
    `navigator.userAgent`, the page's own loads and the network shim all send it. Apps tell
    webOS apart through `PalmSystem` and their framework rather than the user agent (Enyo 2
    looks for `webOS.`, which the TouchPad's doesn't contain either).
- **Caller identity.** The bridge takes the calling app's id from the WebView it is attached
  to, never from anything the page says.
- **Frames can reach the bus (ratchet item).** A JavaScript interface is visible to every
  frame in the WebView, including remote iframes. On Android 5 this is accepted. On later
  targets, the bridge moves to `WebMessageListener` restricted to `*.media.cryptofs.apps`.
- **Enyo's `WebView` control.** On webOS, `enyo.WebView` wrapped the native `BrowserAdapter`
  plugin. Lunacy supplies it, as an iframe or as a native WebView overlaid on the card. Apps
  with a built-in browser or reader view need it.
- **System files.** Apps ask for files beyond the framework, e.g.
  `/usr/palm/command-resource-handlers.json`. On a TouchPad most of those requests throw,
  because apps can't read arbitrary local files. Lunacy matches that: it serves the
  framework and the few system files apps really could read, fails the rest, and logs every
  request for a system path the same way the bus logs unknown services.
- **`palmGetResource(url, hint)`** takes an absolute URL. It returns the parsed object with a
  `json` hint, the text otherwise, and `null` for a missing or disallowed file (measured on
  a TouchPad).
- **Two Enyo code paths.** Enyo behaves differently when `PalmSystem` exists: for example,
  it focuses inputs through `PalmSystem.simulateMouseClick`. Lunacy provides `PalmSystem`, so
  that is the path the fork is fixed and tested on.
- **Renderer drift.** The WebView can update itself, so the renderer is a moving target. On
  Android 5 it stops at about Chromium 95. The test suite runs against every WebView
  version Lunacy supports, and against WebView beta once newer Android versions are
  targeted.

## Notifications

Following LunaCE's tablet mode (docs/luna-shell-reference.md §4 and §5):

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
  - Each dashboard puts its icon (`attributes.icon`, else the app icon) in the status bar's
    notification group.
  - Tapping the group drops down LunaCE's menu frame (`menu-dropdown-bg.png`), with every
    dashboard as a live 320 × 52 px window, newest first. A row swiped right by more than a
    quarter of its width closes that window.
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
- **Test app.** `org.webosarchive.lunacy.notifytest` (bundled) exercises all of this with
  standard Enyo 1 calls. Launch parameters (`{"do": "banner" | "banners" | "push" | "pop" |
  "popup"}`) drive it without touches, on a TouchPad (`palm-launch -p`) and in Lunacy (the
  `launch` and `params` intent extras).

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
  active window through `isActivated`.
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
  In automatic mode, the default, it shows while a text field in the maximized card has
  focus, and a tap on a focused field brings it back. In manual mode
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
  - `com.palm.downloadmanager`, used by apps like Glimpse.
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

Many Enyo 1 apps call `openDatabase()` directly. Every WebView available on Android 5
(Chromium 37 to about 95) still has native WebSQL, stored per origin, so Lunacy uses it as
it is. When a later platform target's WebView drops WebSQL, Lunacy provides
`window.openDatabase` in JS instead. The polyfill is backed by per-app SQLite databases
over the bus, keeps the original async transaction API, and migrates the existing data.

## Package manager and App Museum

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
  - **Native apps.** A `pdk` or `game` app installs and appears in the launcher, but
    launching it shows a banner saying Lunacy can't run it yet, and a bus launch returns an
    error.
  - **Feedback.** Preware showed its own progress. Lunacy shows a banner when the install
    starts and another when it ends; tapping "installed" launches the app.
  - **Plain `http` (ratchet item).** Packages are fetched as the Museum names them, usually
    plain `http`, with no signature check, as on webOS. Android 9 blocks cleartext by
    default, so later targets need a network security config or an `https` catalog.
  - **For development,** `--es install <url or path>` on the launch intent installs a
    package; see [dev-workflow.md](dev-workflow.md).
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
  launcher (`android/tools/node-launcher.cpp`, packaged as `liblunacynode.so`) makes it an
  executable, so each service package runs in its own process: its memory is its own, a crash
  can't take the shell down, and stopping it is ending the process. 32-bit ARM only for now.
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
- **Not yet:** services in Mojo apps' own frameworks, db8 (next), `activitymanager` background
  work, ABIs other than 32-bit ARM, and a jail: services run with Lunacy's own permissions
  (ratchet item).

## Later layers

- **Mojo** replaces `/usr/palm/frameworks/mojo/…` the same way. Its multi-stage windows map
  to cards.
- **PDK native apps** need a glibc/SDL 1.2/PDL loader over Android, which is roughly
  [apkenv](https://github.com/Android-to-webOS-Ports/apkenv) in reverse.
  - Current SoCs are often 64-bit only and can't run 32-bit ARM code, so recompiling may be
    needed there.
  - The Android 5 test devices are 32-bit ARMv7, the same CPU class as webOS hardware, so on
    them the original binaries can run without recompiling.

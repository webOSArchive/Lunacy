# Fix log

Where each compatibility fix landed. Rule 1 in [architecture.md](architecture.md) says fixes
go into general layers: the Enyo (later Mojo) fork, the global compat layer, or the bus. A
fix that fits nowhere general is logged as such. If the general share stops growing, the
approach is failing.

Layers: **framework** (the Enyo fork: stock Enyo plus the patches in
`LunaRuntimes/enyo-1.0/`, whose [CHANGES.md](../LunaRuntimes/enyo-1.0/CHANGES.md) is
its change log against upstream), **compat** (compat.js, bridge.js, serve-time transforms),
**bus** (a service answering as webOS did), **nowhere general** (a per-app setting; only the
fixed-viewport fallback is allowed).

## Fixes

| Date | Symptom | Seen in | Cause | Landed |
|---|---|---|---|---|
| before 2026-09-19 | Scrollers don't move | every Enyo app | webOS delivered touches as mouse events; Chromium only synthesizes them for taps | compat (input model) |
| before 2026-09-19 | Scrolling feels sticky, no coasting | every Enyo app | Inertia came from LunaSysMgr's `Mojo.handleGesture('flick')` | compat (flicks) |
| before 2026-09-19 | Border-image graphics vanish | Enyo's own controls | Newer Chromium draws no border image when `border-style` is none | compat (serve-time CSS transform) |
| before 2026-09-19 | Text in Roboto | every app | Prelude was installed system-wide on webOS | compat (`fonts.css`) |
| 2026-09-19 | Full-screen "DIAG ERROR" overlay at startup | USA Today 1.4.3 | The app's `window.onerror` overlay catches the parse error Enyo's `enyo.WebView` throws on reading `command-resource-handlers.json`. The TouchPad throws the same error but never calls `onerror` | compat (uncaught errors reach only the console) |
| 2026-09-19 | "Checking for filemgr" gets an error | Papyrus 1.6.1 | `applicationManager/listApps` was missing | bus |
| 2026-09-19 | Cross-origin requests fail ("Unable to connect to Pandora", no Terms of Service) | Apollo 1.2.8, CheckMate HD 2.4.0 | webOS apps had no CORS check; pages here are on https origins | compat (network shim, `net.js` with native HTTP) |
| 2026-09-19 | Servers see an Android browser | every app | The WebView's own user agent | compat (the TouchPad's user agent on every app WebView) |
| 2026-09-19 | Failed service worker registrations at startup | Papyrus 1.6.1, CheckMate HD 2.4.0 | `navigator.serviceWorker` exists in Chromium, never on the TouchPad | compat (removed) |
| 2026-09-19 | Wrong version reported | every app | `deviceInfo` said 3.0.5 and `PalmSystem.version` "3.0.5"; the TouchPad reports 3.1.0 and "Webkit4/V8; device" | compat (bridge values) |
| 2026-09-19 | No app menu: many apps can't be configured | App Museum, most Enyo apps | The status bar title didn't relaunch the app with `open-app-menu`; relaunch passed its parameters as an argument, not in `PalmSystem.launchParams`; `PalmSystem.isActivated` was never set | shell and compat (bridge) |
| 2026-09-19 | No keyboard for text fields | every app | The compat layer's touch handling focuses fields itself, so Android never raised its keyboard | shell and compat (bridge: webOS keyboard modes) |
| 2026-09-19 | Pandora rejects Apollo's login | Apollo 1.2.8 | `preferences/systemProperties/Get` (`nduid`) was missing | bus |
| 2026-09-19 | Interface stuck half-transparent over the splash, taps blocked | Apollo 1.2.8 (any Enyo 1 app: Pane fades, animators) | Enyo 1 cancels animation frames with `webkitCancelRequestAnimationFrame`, which Chromium dropped (it kept `webkitRequestAnimationFrame`). Enyo's fallback, `clearTimeout(frameId)`, cancelled the Pane fade's timer with the same number. Found by tracing `clearTimeout` over DevTools | compat (the old name mapped to `cancelAnimationFrame`) |
| 2026-09-19 | "A network connection is not available" | Apollo 1.2.8 | `connectionmanager/getstatus` was missing, and the bridge dropped subscriptions after the first reply | bus (connection manager, subscriptions) |
| 2026-09-19 | Tracks fail at once and retry until "Network Error" | Apollo 1.2.8 (any app using `enyo.Sound`) | `enyo.Sound` sets `src = ""` on each new sound; Chromium fires `error` for that, the TouchPad fired nothing (Workbench/probe 0.0.8) | compat (empty media `src` removes the attribute) |
| 2026-09-19 | Banners on a black box, sized to their text | every banner | Lunacy's own drawing: LunaCE's StatusBarScroll draws no backing and unrolls over the notification area's full maximum width (319 px), text left-aligned | shell (matched to LunaCE and the TouchPad) |
| 2026-09-19 | App frozen for 30 s after a tap | Plex client 1.0.0, with its server unreachable from the tablet | A synchronous XHR to a server that never answers blocks the page until the connect timeout, which was a guessed 30 s; the TouchPad gives up after about 9 s (probe 0.0.9), with an `error` event, not `timeout` | compat (network shim timeout; `timeout` only for the page's own `xhr.timeout`) |
| 2026-09-19 | Apps with a JS service can't reach it | Sound Cloud Player 4.4.2, Plex 1.0.0 | No JS service runtime | JS services (Node, host, activity manager) |
| 2026-09-19 | Service's buffered audio won't play ("Media load rejected by URL safety check") | Sound Cloud Player 4.4.2 | The page plays `file:///media/internal/…`, which an https page can't load; SoundManager2 passes it to `new Audio(url)` | compat (`file:///media/internal/` mapped to the app origin, which serves it) |
| 2026-09-19 | Transcoded video opens the player but never plays | Plex 1.0.0 (its service's HLS playlists) | Chromium passes HLS to Android's MediaPlayer, whose own network stack can't reach app origins (`LiveSession` -1008) | compat and card host (`file:///media/internal/` media served from a loopback `MediaServer`) |
| 2026-09-19 | db8 missing | apps storing data in db8, and every package with configuration/db files | No `com.palm.db` | bus (db8 on SQLite, measured on the TouchPad; configurator for packages' kinds) |
| 2026-09-19 | HTTPS to Let's Encrypt and Amazon sites fails from native code | the network shim | Android 5's trust store lacks current roots | compat (bundled Mozilla roots) |
| 2026-09-20 | A bus call from a frame never gets its reply; a cross-origin XHR from one never finishes | every app that opens webOS's cross-app UI (`enyo.FilePicker`, `enyo.CrossAppUI`) | Replies come back through `evaluateJavascript`, which only runs in the main frame; on webOS every frame had its own `PalmServiceBridge` binding | compat (bridge and net: one token counter for a window's same-origin frames, and the reply is handed to the frame waiting for it) |
| 2026-09-20 | "No Network Connection" although the tablet is online | Help 2.0.0 (any app that waits for a service) | `com.palm.bus/signal/registerServerStatus` was missing, so the app never went on to ask the connection manager. It is ls-hubd's own signal, not a service's | bus (the router answers it, and again when a service comes or goes) |
| 2026-09-20 | Help picks the wrong device's content | Help 2.0.0 | `com.palm.properties.PRODoID`, which apps key off to tell webOS devices apart, was missing | bus (system properties, measured on the reference TouchPad) |
| 2026-09-20 | Apps never learn the screen turned | every Enyo app | `PalmSystem.screenOrientation` was fixed at "up" and `Mojo.screenOrientationChanged` was never called; Enyo reads the property when the page's own resize arrives | shell and compat (bridge: the orientation is set before the relayout) |
| 2026-09-20 | Settings apps pile onto the first launcher page | every app with a category or keywords | Apps with no place of their own all went to page 0; LunaCE places them by category, then by keyword (AppMonitor::pageDesignatorForWebOSApp) | shell (the page map in `assets/luna/launcher-pages.json`) |
| 2026-09-20 | The Done button and the delete badge sit a pixel or two out, and the button is too tall | the launcher's edit mode | Two-state sprites were split into halves; LunaCE draws a documented rect inside a larger canvas (§3.8) | shell (the sprites' own rects, checked against the reference TouchPad's screenshot) |
| 2026-09-20 | A Mojo app shows nothing: "The load of framework submission 506 failed" | Flying Toasters 1.1.2, every Mojo app | Mojo's code was part of webOS's browser, not the framework on disk: mojo.js calls a global the browser provided, and falls back to a loader.js webOS doesn't ship. The files that carry it are V8 native scripts, which only V8's own compiler accepts | **framework** (Palm's Mojo served from the TouchPad, its builtins injected in front of the app's tag, and V8's `global`, `%SetProperty`, `$Object` aliases and `builtinEval` given plain meanings) |
| 2026-09-20 | A Mojo app never sees its launch parameters, so it opens the wrong scene | Flying Toasters (its exhibition view) | webOS's Prototype parses JSON with `builtinEval`, V8's own eval; without it `evalJSON` threw "Badly formed JSON string" and Mojo handed the app a raw string | **framework** (an indirect `(0, eval)`, which is the same thing in a page) |
| 2026-09-20 | Mojo dies before it starts on an https origin | every Mojo app | `Mojo._calculateAppRootPath` knows `file://` (a device) and `http://` (a desktop); Lunacy is neither, so the match was null | **framework** (both accept https) |
| 2026-09-20 | Mojo asks for the time at startup and gets nothing | every Mojo app, and apps that show a clock | `com.palm.systemservice/time/getSystemTime` was missing | bus (Android's clock and zone, in the TouchPad's measured shape) |
| 2026-09-20 | A bundled app's db8 kinds are never registered, so it can't store anything | Palm's Clock (its alarms and preferences), any bundled app with `configuration/` | The configurator only walked the installed packages on disk; a bundled app's configuration is in the APK | bus (the configurator reads apps through AppFiles, which merges installed and bundled) |
| 2026-09-20 | Every app's dates, times and numbers throw: `enyo.g11n.DateFmt` dies with "Cannot read property 'longDate' of null" | every Enyo app that formats a date, a time or a number | Self-inflicted, the same morning: `palmGetResource` was tightened to refuse a path that isn't absolute, on the measurement that a TouchPad returns null for `"appinfo.json"`. It also refused `/usr/palm/...`, which is the form Enyo builds here - on a device the same file is `file:///usr/palm/...`, and only the origin makes the difference. g11n reads its format data that way | compat (bridge: a rooted path is enough, a bare relative one still isn't) |
| 2026-09-20 | Every app's Help menu, and "open link", "share", "email us" everywhere, do nothing | every Enyo app (the Help item is in Enyo's own app menu) | `applicationManager/open` with a `target`, or with the id of one of webOS's built-in apps, had nobody to answer: those apps were part of the OS and Lunacy hasn't got them | bus (`WebosLinks`: the built-in ids and a bare target go to Android's browser, mail composer, dialer, messaging or maps) |
| 2026-09-20 | `enyo.WebView` shows nothing and throws when called | apps with a reader or a built-in browser (USA Today's overlay came from this) | The control is webOS's BrowserAdapter, an NPAPI plugin; there is none here, so it rendered an `<object>` that answered nothing | **framework** (the control over an iframe: load, events and the commands an iframe can do; the plugin-only calls log once) |
| 2026-09-20 | Apps' startup calls for the headset, the media keys and the screen get nothing | Apollo asks for all three while starting | `com.palm.keys/headset/status`, `keys/media/status` and `display/control/status` were missing | bus (Android-backed, in the TouchPad's measured shapes - including its own wording for a call that arrives without `subscribe`) |
| 2026-09-20 | Enyo's cross-app UI can't find another app's files | any app opening one (the file picker takes a path instead) | `applicationManager/getAppBasePath` was missing | bus |
| 2026-09-20 | `tellurium_config.json` 404s where a device answers | every Enyo app, at startup | The TouchPad answers that one file with 200 and an empty body | card host (system files) |
| 2026-09-20 | No file to pick: the user's own files aren't in the webOS tree | Papyrus 1.6.1's ePub import, Screen & Lock's wallpaper | `/media/internal` was Lunacy's own copy only, while the files a person has are in Android's shared storage | card host (`UserFiles`: Android's folders mapped into `/media/internal` under webOS's own names) |
| 2026-09-20 | A page could tell it wasn't on a device: `PalmSystem.getResource` and `getIdentifier` existed, its members enumerated, and `palmGetResource` returned `undefined` where a TouchPad returns `null` | every app that feature-detects | Lunacy's `PalmSystem` was a plain object with extra members | compat (bridge: a host object with nothing enumerable, and the measured surface exactly) |
| 2026-09-20 | Servers that tell webOS devices apart didn't see one | every app on the wire | `X-Palm-Carrier` was never sent; the TouchPad puts `c090-01` on every request (Workbench/results/touchpad-net.txt) | compat (network shim) |
| 2026-09-20 | Apps were told the wrong locale and clock format | every app | `locale` and `timeFormat` were hardcoded `en_us`/`HH12` while the shell's clock followed Android | shell and compat (both follow Android's settings now) |
| 2026-09-20 | An app showing the network interface saw Android's | apps that show connection details | `connectionmanager` reported `wlan0`; a TouchPad's Wi-Fi is `eth0` | bus |
| 2026-09-20 | Turning the tablet end for end doesn't reach apps at all | every Enyo app | Two layers. The shell: Android reports no configuration change when the rotation turns 180° (same orientation, same size), so `PalmSystem.screenOrientation` went stale - a display listener catches every rotation instead. Enyo: it decides the window has rotated by watching `window.resize`, which a 180° turn doesn't fire, and leaves the host's `Mojo.screenOrientationChanged` an empty stub. Its own note says why - the callback wasn't made on one of Palm's devices - and that sysmgr does make it, which Lunacy's shell does | shell, and **framework** (the stub now feeds `enyo.sendOrientationChange`, which only dispatches when the orientation really changed, so the resize path can't double up) |
| 2026-09-20 | Exhibition shows the app's ordinary view, not its exhibition one | AccuWeather 2.3.1 | The shell launched a dock-mode app with `dockMode` and an invented `touchstoneMode`. LunaSysMgr sent `{"windowType":"dockModeWindow","dockMode":true}` (`DockModeWindowManager::launchApp`), and apps test both keys | shell (the device's own launch parameters) |
| 2026-09-20 | An app's exhibition view is left behind as a card | AccuWeather 2.3.1 | Leaving the mode didn't close what entering it opened, as webOS's `DockModeWindowManager::closeApp` did | shell |
| 2026-09-20 | Every app listed twice in Exhibition, and again on each toggle | Exhibition 1.0.0 | `listDockModeLaunchPoints` pushed the whole list to subscribers whenever a launch point was enabled or disabled. A TouchPad sends nothing: measured with `luna-send` - subscribe, then add and remove, and exactly one reply arrives. Palm's app means to clear its list before redrawing and writes the old contents straight back (`this.dockApps.splice(0, len)` returns what it removed), so any second reply draws everything again | bus (the list changes when apps are installed or removed, not when one is switched on) |
| 2026-09-21 | A phone-shaped screen answers as a Pre3 that never existed | every app on a phone, and any server that tells webOS devices apart | `DeviceProfile.PRE3` was built from the community's record, not hardware: `PRODoID` was a retail SKU (`P160UNA`), the user agent was missing its `Linux; ` prefix and named the wrong product token, and `deviceInfo` reported phone-sized cards the device doesn't report. Measured on a Pre3 (Docs/pre3.md) | compat and shell (the profile, and the three places that read it: deviceInfo, systemProperties, the network shim) |
| 2026-09-21 | An app is told the tablet has no Bluetooth and no dock mode | every app that feature-detects either | `deviceInfo` reported `bluetoothAvailable: false` and `dockModeEnabled: false`; the reference TouchPad reports both `true`, and Lunacy has had dock mode since Exhibition landed. Neither value had been checked against the device | shell (the profile, and deviceInfo) |
| 2026-09-21 | A page loads nothing but its first script: no stylesheet, no body | drPodder Redux 1.6.0, any app whose HTML is XHTML-shaped | `<script src="…" />` does not close in HTML, so the script swallowed the rest of the file as its own text content. webOS's WebKit keeps the pre-HTML5 tokenizer, which honoured the trailing slash on any tag: measured with a probe page in drPodder's shape, which reported `selfClosed=true`, its marker element present and its stylesheet applied, with `document.xmlVersion` null - so the device was parsing HTML and simply closed the tag | card host (a global HTML transform closes a self-closed non-void tag, in pages and templates alike, skipping script and style content) |
| 2026-09-21 | Every Mojo widget with a template throws: `_Menu widget 'undefined' setup(): Cannot read property 'parentNode' of null` | Check Mate 1.2.6, IAmA reddit 0.3.0, every Mojo app | `palmGetResource` fetched through the card host, which injects Lunacy's boot scripts into anything it serves as HTML. Mojo reads every widget template and every scene that way, so the rendered node's first element was the injected `<link>` and the widget's own lookup found nothing. On a device the call read the file off the disk; no browser was involved | compat and card host (`palmGetResource` marks the request as a file read, and the host then serves the file as it is) |
| 2026-09-21 | "Error Creating DB!" and the app never starts | drPodder Redux 1.6.0, any app opening its database webOS's way | `openDatabase(name, version)`: the standard made the display name and size hint required and Chromium throws "4 arguments required". The reference TouchPad's arity is 0 and the two-argument call opens at that version (Workbench/probe 0.1.0) | compat (the two missing arguments filled in with the database's own name and webOS's 5 MB allowance) |
| 2026-09-21 | The database opens but stays empty: "Loading Feeds" for ever | drPodder Redux 1.6.0 | The app creates its tables when `error.message === "no such table: feed"`. The TouchPad reports SQLite's own message; Chromium wraps it as "could not prepare statement (1 no such table: feed)" (probe 0.1.0) | compat (the wrapper stripped back off, so the message is the one apps were written against) |
| 2026-09-21 | The chat log never renders; every poll throws `Cannot read property 'length' of undefined` | webOS SimpleChat 1.9.2, any app whose text goes through `Mojo.Format.runTextIndexer` | `PalmSystem.runTextIndexer` was a logged no-op returning undefined. On the TouchPad it returns the text with web addresses, e-mail addresses and telephone numbers wrapped in anchors and everything else untouched (probe 0.1.2, ten shapes) | compat (bridge: the linkifier, in one naive pass as the device's own) |
| 2026-09-21 | Video hands off to webOS's Video Player, which Lunacy hasn't got | MeTube 2.3.0 (both its playback strategies), any app that plays video through the OS | Palm's Video Player is a **Mojo 2** app, and Mojo 2 was unbuilt: its loader pulls in `mojoloader.js` and asks MojoLoader for `mojo.core`, and the framework itself is another V8 native script | **framework** (Mojo 2's builtins patched like Mojo 1's, a route for the rest of `/usr/palm/frameworks`, and the library builtins served in front of a Mojo 2 page so MojoLoader finds them) |
| 2026-09-21 | The screen goes out during a film | MeTube 2.3.0, Palm's Video Player, any app that blocks the screen timeout | `PalmSystem.setWindowProperties` was a logged no-op | shell and compat (`blockScreenTimeout` holds Android's screen on while any window asks; the other properties are still logged) |
| 2026-09-21 | An uncaught TypeError appears in the app's console at every card open | every app with a `window.open` window | Lunacy's own page-finished diagnostic read `document.body` unguarded, and a window.open transport finishes with no body. It looked like the app's fault | shell (guarded) |
| 2026-09-21 | The shell crashes at startup and keeps crashing | development over adb, with a malformed `--es params` | The activity is singleTask, so Android replays the intent that crashed it; `JSONObject(params)` threw out of `onCreate` | shell (a bad `params` is reported and the app launches without them) |
| 2026-09-21 | Typed text lands on top of a field's placeholder, which never goes away | webOS SimpleChat 1.9.2 (any Mojo text field with `hintText`, and any app that reads `keypress`) | LunaSysMgr's virtual keyboard put real key events into the page; Android's soft keyboards are input methods, so `keydown`/`keyup` carry 229 and **no `keypress` is dispatched**. Mojo hides the hint from its keypress handler, and the same handler's `charsAllow` filter never ran either. Measured on the HP 10 G2: the soft keyboard gives `keydown 229 → textInput → input → keyup 229`, key injection gives `keydown → keypress → textInput → input → keyup` | compat (the character is delivered as the keypress a device sent, from `textInput`, and only when no real keypress came) |
| 2026-09-21 | Rows read thin, and the blue label sits high in the row | Device Info (Lunacy's own) | Lunacy's own CSS was written by eye rather than from the device. Palm's Device Info draws Mojo's list rows, and Mojo's stylesheet is the measurement: `.palm-row` is 51 px, `.palm-row .label` is 14 px #1f75bf with `text-shadow: #ffffff 0 1px 0` and sits 18 px from the top, `.palm-row .title` is 20 px 13 px down. Lunacy had #4c7ba6, no shadow, an 18 px value and a flex-centred label - measured against the device, its ink sat 5 px high and read lighter for want of the shadow | Lunacy's own app (its CSS takes Mojo's numbers; the shipped Prelude was already the device's own Prelude-Medium) |
| 2026-09-21 | The label is jammed against the row's left margin and the value against its right, and the values are darker than the device's | Device Info (Lunacy's own) | Two mistakes in the row above. `className` on an Enyo 1 kind *replaces* the base kind's class, so `className: "info-row"` dropped `enyo-item` and with it Item's own 10 px of padding; Mojo's offsets, applied on top of that, had nothing to sit inside. And the values were #555 by eye: Palm's Device Info draws every row as `palm-row disabled` (`app/views/list/item.html`), because these rows are read-only, so the value's grey is Mojo's disabled colour #898989 and it comes from the row. Both measured off screenshots of the two screens side by side: ink 85 against the device's 137, now 137 against 137 | Lunacy's own app (`enyo-item info-row`, the offsets less Enyo's 10 px, and #898989 on the row) |
| 2026-09-21 | Tapping a text field doesn't focus it, so there is no keyboard and no way to type | webOS SimpleChat's compose box (every Mojo text field whose tap lands on its hint text rather than the input) | Lunacy's own. Mojo's text field focuses itself from the tap gesture it gets on `mouseup`; the compat layer's touch handling then blurred whatever was focused, one line later, because the tapped element was the field's *hint* and not the input - Mojo draws the hint as a sibling. Traced with a patched `HTMLElement.blur`: `BLUR TEXTAREA <- compat.js:65` | compat (the tap's effect on focus is decided after the tap has been handled, not during it: if something took focus it stands, and a tap inside the focused field's own widget is not a tap away from it) |
| 2026-09-21 | A field focused by the app itself gets no keyboard | every Mojo text field | The bridge raises the keyboard from `focusin`, and none arrived. Read at the time as Mojo focusing through a saved reference to the field's own `focus` (`inputArea.originalFocus`) - **wrong**; see the row below for what it really was. The stopgap was to raise the keyboard from the tap instead, which is still wanted for its own case | compat (a tap that leaves a field focused raises the keyboard, whoever focused it) |
| 2026-09-21 | An Enyo app's widget art visibly arrives after its layout: the chrome fills in, and a dialog shows as an unframed block of text before its panel appears | every Enyo and Mojo app | A framework asks for a piece of its art only once a widget using it has been laid out, and a dialog's frame (Onyx's `popup.png`) only when the dialog opens. Measured on the HP 10 G2 with the App Museum: `serve` answers the median request in under a millisecond, so the file isn't the wait - the request is simply made late. Preloading the framework's whole theme is a bad trade (61 images, art in hand at 0.90 s but DOMContentLoaded 2.84 s against 2.25 s); preloading only the eleven pieces the app actually uses costs nothing (art at 0.43 s, DOMContentLoaded 2.26 s, load 2.83 s against 3.09 s) | shell (the card host remembers what each app asked the *frameworks* for and asks for it again at the top of the page - a cache, filled by the same rule for every app; an app that has never run gets no preload) |
| 2026-09-21 | Shift capitalises the letters but doesn't reach the number row's symbols: shift+`1` types `1`, not `!` | LunaKeyboard, in any app | Lunacy's own. The keyboard sent a key's alternate character only from the symbol page. webOS split the two, and not where you would guess (LunaCE's `TabletKeymap::map`): a *letter* takes its alternate from the symbol key and shift only capitalises it, while everything that isn't a letter - the number row and the punctuation - takes its alternate from **shift**. Shift-lock is not shift for this (`isShiftActive` against `isCapActive`): locked, the letters come out capital and the number row still types numbers | LunaKeyboard (the rule, and the key cap now inks whichever character would be typed, as `TabletKeyboard::drawKeyCap` did) |
| 2026-09-21 | Nothing an app focuses itself raises the keyboard, and no page gets a `focus`, `blur` or `focusin` event at all | every app; webOS SimpleChat focuses its compose box as its first scene is built, so it opened with the keyboard's space reserved and no keyboard | Lunacy's own. No card's WebView ever took Android's focus, so `document.hasFocus()` was false in every card - and Chromium, in an unfocused document, sets `activeElement` without dispatching any focus event. That is why the bridge's `focusin` listener never ran. Measured on the HP 10 G2: `el.blur(); el.focus()` logged nothing and `hasFocus` was false; with the card focused the same two lines log `focusin` | shell (the active card's window takes the focus when it is maximized, as webOS's active card held it) |
| 2026-09-21 | Putting the keyboard away leaves the space it took reserved | webOS SimpleChat (any app that reads `document.activeElement` to find out whether the keyboard is up - Mojo gives it no other way) | webOS never simply hid the keyboard. `IMEController::hideIME` asked the web app to `removeInputFocus` (`View_RemoveInputFocus` -> `page()->removeInputFocus()`), WebKit blurred the focused node, and the keyboard went because nothing was focused. Android's IME is the other way round: it hides and the field keeps focus, so SimpleChat - which sizes its chat log with a 600 px bottom buffer while its field is focused and 260 px otherwise - went on reserving room for a keyboard that wasn't there | compat + shell (the keyboard going down takes the page's input focus with it: `AppWindow.removeInputFocus` -> `__lunacyRemoveInputFocus`) |
| 2026-09-21 | A keyboard asked for while the app's card is still opening never appears | any app that focuses a field as its first scene is built | The request was dropped because the card wasn't `maximized` yet - the card layer only records that when the open animation ends. LunaSysMgr allowed for the same thing in as many words: `CardWindow::slotShowIME` asks for the *active* window, "to handle cases where keyboard is being brought up when an app is being maximized" | shell (the card layer has an `active` card - maximized, or on its way there - and the request is repeated once it settles) |
| 2026-09-21 | The field being typed into sits under the app's own toolbar once the keyboard is up | webOS SimpleChat's compose box | The card shrinks to the space above the keyboard, as LunaSysMgr did (`CardWindowManagerState::resizeWindow`), and Mojo re-lays the scene out - but this card has less room than a TouchPad's (see below), so the scene overflows and an app's bottom-anchored bar lands on the field | compat (a focused field that has fallen to the bottom edge is scrolled clear of it) |
| 2026-09-21 | An app meant to be invisible gets a launcher icon | Palm's Video Player (any app with `"visible": "false"`) | `appinfo.json`'s `visible` was never read. webOS used it for apps that are part of the platform and are launched by another app | shell (the launcher and the dock draw only the visible apps; `listApps` and launching by id are unchanged, as on a device) |

## Known gaps, by the layer they belong to

Found while testing the apps below; each is general, not tied to one app.

- ~~**bus, startup services:** `keys/headset`, `keys/media`, `display/status`~~ — done
  2026-09-20, Android-backed and in the measured shapes.
- **bus, zeroconf:** `com.palm.zeroconf/browse` (Bonjour discovery; Plex 0.8.0 looks for
  `_plexmediasvr._tcp` with it, then falls back to servers added by hand). Android's
  `NsdManager` could back it.
- **system services, media indexer:** the `com.palm.media.*` kinds in db8 (Papyrus asks for
  `com.palm.media.types:1` before its file picker; db8 itself now answers, with
  "kind not registered").
- **system UI:** ~~the FilePicker cross-app UI~~ — done 2026-09-20. Lunacy serves its own
  picker at webOS's path (`/usr/lib/luna/system/luna-systemui/app/FilePicker/filepicker.html`),
  so `enyo.FilePicker` works in every app. It lists the webOS tree through Lunacy's own service
  rather than the media indexer, and Android's shared folders are mapped into `/media/internal`
  under the names a webOS device used (`downloads`, `music`, `photos`, `documents`, `camera`,
  `video`), so it shows the files the user actually has. Proved end to end: Papyrus imported
  and read an ePub from Android's Downloads folder, unchanged.
- **bus, not implemented:** `com.palm.systemmanager` (`getSecurityPolicy`, `getDeviceLockMode`,
  `setDevicePasscode`). Lunacy has no lock screen — Android's is the real one — so Screen &
  Lock's Secure Unlock shows Off and gets an honest error if it is changed.
- ~~**system files:** `tellurium_config.json`~~ — done 2026-09-20.
- **framework, enyo.WebView:** what only the plugin could do is logged, not done: saving the
  view or an image to a file, resizing an image, the plugin's own dialogs, printing and
  find-in-page. A page on another origin keeps its window to itself, so history and title work
  only for pages Lunacy serves.
- **visual, cause found, no fix in Lunacy's gift:** faint lines along a frame's border-image
  slice boundaries. Seen in Enyo dialogs (Apollo's, AccuWeather's), in Mojo's widgets, and in
  the App Museum's own; codepoet's report on 2026-09-21 is what got it measured properly.

  **The page is drawn through a scale of 0.99922, and that is the whole of it.** A card is
  1280 device px wide and its CSS pixel is meant to be a device pixel, but Chromium works in
  density-independent pixels: on this 213 dpi screen it divides by 1.33125, rounds 961.5 up
  to 962, multiplies back, and the page lays out **1281** CSS px to be drawn into 1280. A
  border-image is drawn as nine separate pieces, so each join lands a hundredth of a pixel
  short of a whole one and is antialiased on its own, leaving a line.

  Measured across a toolbar button's 16 px slice boundary in Palm's Clock, which runs on both
  machines, at 1:1:

  | | profile across the boundary |
  |---|---|
  | reference TouchPad | `99 99 99 99 99 99 99 99 99` |
  | HP 10 G2, landscape | `97 97 97 97 97 `**`92`**` 97 97 97` |
  | HP 10 G2, portrait (800 css px into 800 device px) | `98 98 98 98 98 98 98 98 98` |
  | HP 10 G2, landscape at `wm density 160` (1280 into 1280) | `99 99 99 99 99 99 99 99 99` |

  The last row is the device's own profile, to the level. So an exact one-to-one mapping
  removes it completely.

  **It is per axis, and per screen size, not a property of the screen.** The chain is
  `dip = round(px / density)`, `css = round(dip * density)`, and the seams appear on whichever
  axis doesn't come back the number it started from. The same tablet is off in landscape
  across the width (1280 -> 1281) and exact down the height, and in portrait exact across the
  width and off down the height (1252 -> 1251) - so the portrait row above is clean because
  the scan was horizontal. Most configurations land exactly: 1920x1200 at 240 dpi, 2560x1600
  and 2048x1536 at 320 dpi, anything at 160 dpi, and 1920 at this tablet's own 213 dpi all
  come back unchanged. 1280 at 213 dpi is close to the worst case there is - 1280/1.33125 is
  961.5, a perfect half. Higher density does not make it worse; it makes the seam physically
  smaller, and the awkward cases are fractional densities that happen to round badly at a
  particular width.

  Lunacy now says which it is: `pixelGrid` in `getEnvironment`, shown on Device Info's **Card**
  row as "1280 x 772 TouchPad px - page 1281 x 772" when they don't match, and nothing extra
  when they do. A screen Lunacy has not run on before reports it rather than leaving it to be
  found in the artwork.

  **Three fixes tried inside Lunacy, none of which work**, so none is in the tree:
  - laying the card's window out 1281 px wide, so that the page's own 1281 css px land on
    whole pixels: Chromium still rounds through dip and the seam stays (and moves by one);
  - rendering the page through a `createConfigurationContext` at 160 dpi: the WebView takes
    its scale from the display, not from its context, and `devicePixelRatio` stays 1.33125;
  - `setInitialScale`, which is an integer percentage and cannot express 133.125.

  The only widths that map exactly on this screen are multiples of 213 px; the nearest below
  1280 is 1278, which would leave a two-pixel strip of wallpaper down the side of every
  maximized card. **What does work is the display density**, which is a device setting rather
  than Lunacy's: see "Display density" in [android5-setup.md](android5-setup.md). Neither the
  shell nor the keyboard depends on it - both decode their artwork at 1:1 on purpose - so it
  costs nothing but the size of Android's own UI.

  Two things said earlier the same day and since disproved, for whoever reads the history:
  the seams are **not** position-dependent (shifting a widget a pixel at a time leaves the
  same step at the same offset in the widget), and the keyboard does **not** change how a
  widget is drawn (a card that keeps its size is pixel-identical with the keyboard up and
  down). The keyboard changes which widgets are on screen, and, behind a translucent Mojo
  dialog, what shows through it.

  **The artwork can't fix it** (codepoet asked, 2026-09-21). Taking Enyo's `radiobutton.png`,
  the asset behind the profile above:
  - the source is **uniform** `(44, 100, 154)` for twenty columns either side of the slice
    line, and **fully opaque** there, so the seam's colour is in neither the pixels nor the
    alpha;
  - nothing leaks through from behind: painting the element's border box magenta leaves the
    seam pixel bit-identical, so the nine pieces do cover it;
  - narrowing the middle slice from eight source columns to one, served as a data URL,
    changes the rendering not at all - so it isn't the interpolation across the stretch;
  - and the clincher: the *same* source pixel comes out **99** where it is drawn 1:1 in a cap
    and **96** where it is drawn stretched in the middle. Which of the nine pieces a pixel
    belongs to decides its colour, and no edit to the image can reach that.

  **A different WebView doesn't fix it either.** The factory WebView 37 and WebView 64 are
  wrong in different ways, neither matching the device (the caps are columns 0-15 and 36-51,
  the stretched middle lies between):

  | | across the button |
  |---|---|
  | reference TouchPad | `99 99 99  99 99 99 99 99 99 99 99  99 99 99` |
  | WebView 37 | `99 99 99  ` **`96 96 96 96 96 96 96 96`** ` 99 99 99` |
  | WebView 64 | `97 97 97  ` **`92`** ` 97 97 97 97 97 97 ` **`92`** ` 97 97 97` |

  37 draws the caps exactly and tints the stretched middle three levels; 64 draws the whole
  widget two levels off and puts a one-pixel line at each join. (Palm's Clock does render
  correctly on the factory WebView 37, which is the first time the shipped app has been seen
  on it - phase 1's "runs on WebView 37" is closer than the roadmap says.)

  So the only thing that removes it is an exact one-to-one mapping, and the only lever on
  that is the display density. What would also work, and is not worth it for three levels of
  grey, is not using `-webkit-border-image` for these frames at all - re-authoring Enyo's and
  Mojo's theme CSS widget by widget, changing how they look in the process.

  `Workbench/seams.sh` finds the candidates in a running card; the verdict is still a
  reference comparison, because a one-pixel step at a slice boundary can equally be the
  artwork's own highlight.
- **app timing, to compare on the TouchPad:** AccuWeather sometimes logs
  `this.appModel.getWeatherModel is not a function` at startup: a view updates before its
  `appModel` is set. It runs normally afterwards.
- **third party, nowhere general:** AccuWeather's radar loads today's Google Maps script,
  which uses syntax WebView 64 can't parse.
- **bus, download manager:** `com.palm.downloadmanager/download` and its subscription.
  drPodder fetches album art and episodes with it, and MeTube's "download first" strategy uses
  it too; both get an honest error. It writes into the webOS tree, so it belongs beside
  `UserFiles` and the media server.
- **bus, power:** `com.palm.power/timeout/set` and `/clear` (webOS's wake-up alarms, keyed by
  a string) and `activityStart`/`activityEnd`. SimpleChat's half-hourly refresh, reddit's
  message check and drPodder's feed update all ask for them. Android's `AlarmManager` is the
  obvious backing, and it is the same want as the Clock's alarms
  (`activitymanager`'s scheduled activities).
- **bus, audio:** `com.palm.audio/media/lockVolumeKeys`, which Palm's Video Player subscribes
  to while it plays, and the `com.palm.audio/*` endpoints apps set volume through.
- **bus, accounts:** `com.palm.accountservices/getAccountToken`. Check Mate 1.3.0 and
  SimpleChat 1.9.2 both offer to sign in with a webOS Community Account and ask for it first;
  both carry on when it fails. What a Lunacy account would even mean is codepoet's call.
- **card host, the rest of `/usr/palm/frameworks`:** the tree is served now, but only the
  frameworks something has needed are copied into the APK. `mediaextension` (drPodder asks
  MojoLoader for it) and the other libraries 404 until they are added to `fetch-assets.sh`.
- **shell, full-screen cards:** `PalmSystem.enableFullScreenMode` is still a logged no-op, so
  a video player's card keeps the status bar where a device hides it.
- **shell, a card's open and close animations drop frames, and the minimize is the worse of
  the two.** Measured on the HP 10 G2 from SurfaceFlinger's own present times
  (`dumpsys SurfaceFlinger --latency`), Device Info, back key so no touch injection is in the
  way. The card view's 200 ms minimize arrives as **four frames of 53-101 ms** and then runs at
  17 ms; the 300 ms maximize is 17 ms all the way and drops its last three (100, 84, 67). So
  there is a fixed ~300 ms of cost at the transition, and at 200 ms that *is* the animation -
  stretched to 600 ms the same four frames are bad and the remaining 110 are 17 ms each.

  It is the live WebView being drawn while the card is transformed. Hiding the page for the
  length of the animation makes it perfectly smooth - 14-18 ms every frame, nothing dropped -
  and an `atrace` shows why: every frame does a synchronous round trip to the renderer
  (`SyncCompositorMsg_DemandDrawHwAsync`, `SyncChannel::Send`), and Chromium re-rasters its
  tiles when the transform's scale changes. Ruled out by measurement, each on its own: the
  card shadow's nine-slice, the rounded outline clip, `LAYER_TYPE_HARDWARE` on the card,
  the scale itself (translation only is just as bad), `setStageActive` and the app's JS,
  `WebView.onPause()`, and the CPU governor - with all four cores online at 1.3 GHz the four
  frames still cost 51-84 ms.

  **Landed nowhere yet.** The fix webOS itself used is to draw a *texture*: LunaSysMgr's card
  view showed each card's last painted buffer, not a live page, which is also why a TouchPad
  could hold a dozen cards. Android 5 has no cheap way to snapshot a hardware-accelerated
  WebView - `PixelCopy` is API 24 - and the software path costs, measured on the App Museum,
  385 ms at full size, 130 ms at card-view scale and 85 ms at a third. So it has to be taken
  ahead of time rather than when the gesture starts, which changes what card view *is* (frozen
  pages, refreshed when?) and is codepoet's call.

- **shell, the card has less room than a TouchPad's while the keyboard is up.** Measured on
  SimpleChat, landscape, field focused, against the reference device:

  | | TouchPad | HP 10 G2 |
  |---|---|---|
  | screen | 1024 x 768 | 1280 x 800 |
  | keyboard | 291 | 338 |
  | Android's navigation bar | - | 48 |
  | card left for the app | 449 | 385 |
  | `deviceInfo.screenHeight` the app reads | 768 | 800 |
  | the app's own `screenHeight - 600` chat log | 168 | 200 |
  | scene it then builds | ~431 | 463 |

  Three separate differences, all Lunacy's side of the line:
  - **`deviceInfo` reports this screen, not the TouchPad's.** webOS's own formula is
    `hardwareScreenWidth / screenDensity` (`DeviceInfo.cpp`), so 1280 x 800 is right *by that
    formula* - but an app that has been told it is on a TouchPad and subtracts a TouchPad-sized
    constant gets a number no TouchPad could give it. Reporting 1024 x 768 makes this app's
    chat log exactly the device's 168 (tried, and it does); the cost is that `maximumCardWidth`
    would then be 1024 while the card really is 1280. Which way round that should go is
    codepoet's call.
  - **The keyboard is 338 px tall where the device's landscape keyboard is 291.** LunaKeyboard
    draws its art at a fixed height (`keyboard-bg.png` is 340); webOS set the height per
    orientation (`m_keyboardHeight` in `Src/ime/`), shorter in landscape where the keys are wider.
  - **Android's navigation bar takes another 48 px** that no webOS device has, and it cannot be
    suppressed while an IME is up on Android 5 - asking for immersive again on the keyboard's
    way up changes nothing (tried).

  Neither of the first two alone closes the 78 px gap; together they would, with about a pixel
  to spare. Until then the compat layer scrolls the focused field clear of the bottom edge, so
  what is being typed into is always visible.
- **compat, Enter from a soft keyboard:** the keypress fix covers printable characters,
  which is what the IME swallows. Enter still arrives as a real `keydown`/`keyup` with
  keyCode 13 and no `keypress`, where a device sent one. Mojo reads Enter on keyup, so the
  suite is unaffected, but an app that submits on a keypress of 13 would not.

## Apps tested

On the HP 10 G2 with WebView 64, landscape, installed from `.ipk` through the package manager
on 2026-09-19. Not yet repeated on the factory WebView 37.

| App | Kind | Result |
|---|---|---|
| AccuWeather 2.3.1 | Enyo 1 | Runs with live data after the Terms dialog. Radar map fails (third party). Its Exhibition view runs. |
| App Museum 2.9.5 | Enyo 1, bundled | App menu (Preferences, About) and search with the keyboard work. |
| USA Today (World Today) 1.4.3 | Enyo 1 | Runs with live news once uncaught errors stopped reaching `onerror`. |
| Apollo 1.2.8 | Enyo 1 | Logs in with the keyboard, shows its stations and plays music. |
| Papyrus 1.6.1 | Enyo 1 | Library renders. Import works as of 2026-09-20: the file picker lists Android's Downloads folder, and the book imports into db8 and reads. |
| CheckMate HD 2.4.0 | Enyo 2, bundled | Renders its full UI and loads its Terms of Service; webOS Account missing (honest bus error). |
| Plex for webOS 1.0.0 | Enyo 1 with a JS service | Its service starts transcodes and writes HLS playlists; direct play and transcoded video both play. |
| Plex for webOS 0.8.0 | Enyo 1 | Finds a server added by hand, browses its library and plays video; Bonjour discovery needs zeroconf. |
| toodleTasks HD 1.5.0 | Enyo 1 with db8 kinds in its package | Its kinds and permissions register at install; its watched and ordered finds answer; its sign-in screen and dashboard show. Toodledo's service itself is untested. |
| Sound Cloud Player 4.4.2 | installed from the App Museum | Installs from the live package host and launches. |

Added 2026-09-20 (same tablet and WebView):

| App | Kind | Result |
|---|---|---|
| Flying Toasters 1.1.2 | **Mojo**, installed from a package | Runs, and runs as an Exhibition app: enabling it in Palm's Exhibition app and starting Exhibition launches it with `dockMode`, and it shows its toasters rather than its settings. The first Mojo app to run in Lunacy. |
| Clock 3.1.1904 (Palm's, unchanged) | Enyo 1, `noWindow`, bundled | Runs: the analog clock face and the alarm list, with its preferences and alarms in db8. Alarms won't fire yet - that needs `activitymanager`'s scheduled activities. |
| Exhibition 1.0.0 (Palm's, unchanged) | Enyo 1, bundled | Lists the apps that offer an Exhibition face (Glimpse turns up on its own), and Start Exhibition puts the shell into the webOS clock. |
| Device Info (Lunacy's) | Enyo 1, bundled | Reports the device, Android, the WebView, Lunacy and the display; re-reads them when the screen turns. |
| Screen & Lock 1.0.0 (Palm's, unchanged) | Enyo 1 | Renders as on the TouchPad. Brightness and "Turn off After" set Android's own; Change Wallpaper opens Lunacy's file picker and the shell's wallpaper follows. Auto Dim sets Android's brightness mode. Secure Unlock has nothing behind it (no `com.palm.systemmanager`). |
| Help 2.0.0 (Palm's, unchanged) | Enyo 1, `noWindow` | Starts, finds the network and opens its own card. Its articles come from `help.webosarchive.org`, which answers 404 for the path this copy asks for (see below). |
| Wi-Fi, Sounds & Alerts | Lunacy shortcuts | Open Android's Wi-Fi and sound settings. |

**Help's content, for webOS Archive rather than Lunacy.** The app asks for
`http://help.webosarchive.org/<locale>/<carrier>/index.json` — this copy's `UrlManager` has the
device segment commented out ("Remove device code from URL"), while the host keeps the content a
level deeper, under the device (`/en-us/c000-01/d500-01/index.json` exists, and
`/en-us/c000-01/index.json` is a 404). Either the app's URL or the host's layout needs to move.
Lunacy now reports `com.palm.properties.PRODoID`, so the app resolves the TouchPad's `d500-01`
correctly once the segment is back.

Added 2026-09-21 (same tablet and WebView). The Mojo suite codepoet named as the apps that
must work, plus the system app two of them hand off to:

| App | Kind | Result |
|---|---|---|
| drPodder Redux 1.6.0 | Mojo, `noWindow`, installed from a package | Runs. Loaded nothing at all before the self-closing-tag fix (its index.html is XHTML throughout), then stopped at "Error Creating DB!", then at "Loading Feeds". Now: first run offers the default feeds, fetches them, and lists episodes with their counts. Album art doesn't arrive - it comes through `com.palm.downloadmanager`, which nobody answers - and `MojoLoader.require({name: "mediaextension"})` 404s until that framework is served. |
| IAmA reddit 0.3.0 | Mojo, `noWindow`, `dockMode` | Runs: the split front page loads and lists articles with their thumbnails. The empty pane's banding is the app's own 480 x 480 background tile repeating over a wider card, not a rendering fault. `com.palm.power/timeout/clear` gets an honest error. |
| MeTube 2.3.0 | Mojo | Runs end to end: search, the server-side conversion with its progress, and playback - which it hands to `com.palm.app.videoplayer`, below. |
| Check Mate 1.2.6 → 1.3.0 | Mojo | Updates from the App Museum (the update changes the app id to `com.palm.codepoet.checkmate`, so the old one stays until it is removed). A new account, the terms, the assigned credentials, logging in, adding a task and the write reaching the service all work. `com.palm.accountservices/getAccountToken` gets an honest error, which is what the app's optional webOS Account login asks for. |
| webOS SimpleChat 1.8.5 → 1.9.2 | Mojo, `noWindow` | Updates from the Museum (again a new app id, `com.palm.app.codepoet.simplechat`). The chat log renders and the compose box is there. Its half-hourly refresh alarm asks `com.palm.power/timeout/set`, which gets an honest error. |
| Video Player 1.0.0 (Palm's, unchanged) | **Mojo 2**, `noWindow`, bundled, no launcher icon | Runs, which is the first Mojo 2 app in Lunacy. Launched by another app with a `target`, it opens its own card, plays the stream and shows the transport controls, title bar and its own error dialog. `com.palm.audio/media/lockVolumeKeys` and `com.palm.bus/signal/addmatch` get honest errors, and `PalmSystem.enableFullScreenMode` is still a logged no-op, so its card keeps the status bar. |

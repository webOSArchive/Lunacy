# Fix log

Where each compatibility fix landed. Rule 1 in [architecture.md](architecture.md) says fixes
go into general layers: the Enyo (later Mojo) fork, the global compat layer, or the bus. A
fix that fits nowhere general is logged as such. If the general share stops growing, the
approach is failing.

Layers: **framework** (the Enyo fork: stock Enyo plus the patches in
`android/framework/enyo-1.0/`, whose [CHANGES.md](../android/framework/enyo-1.0/CHANGES.md) is
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
| 2026-09-19 | Tracks fail at once and retry until "Network Error" | Apollo 1.2.8 (any app using `enyo.Sound`) | `enyo.Sound` sets `src = ""` on each new sound; Chromium fires `error` for that, the TouchPad fired nothing (spike/probe 0.0.8) | compat (empty media `src` removes the attribute) |
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
| 2026-09-20 | A bundled app's db8 kinds are never registered, so it can't store anything | Palm's Clock (its alarms and preferences), any bundled app with `configuration/` | The configurator only walked the installed packages on disk; a bundled app's configuration is in the APK | bus (the configurator reads apps through AppFiles, which merges installed and bundled) |
| 2026-09-20 | Every app's dates, times and numbers throw: `enyo.g11n.DateFmt` dies with "Cannot read property 'longDate' of null" | every Enyo app that formats a date, a time or a number | Self-inflicted, the same morning: `palmGetResource` was tightened to refuse a path that isn't absolute, on the measurement that a TouchPad returns null for `"appinfo.json"`. It also refused `/usr/palm/...`, which is the form Enyo builds here - on a device the same file is `file:///usr/palm/...`, and only the origin makes the difference. g11n reads its format data that way | compat (bridge: a rooted path is enough, a bare relative one still isn't) |
| 2026-09-20 | Every app's Help menu, and "open link", "share", "email us" everywhere, do nothing | every Enyo app (the Help item is in Enyo's own app menu) | `applicationManager/open` with a `target`, or with the id of one of webOS's built-in apps, had nobody to answer: those apps were part of the OS and Lunacy hasn't got them | bus (`WebosLinks`: the built-in ids and a bare target go to Android's browser, mail composer, dialer, messaging or maps) |
| 2026-09-20 | `enyo.WebView` shows nothing and throws when called | apps with a reader or a built-in browser (USA Today's overlay came from this) | The control is webOS's BrowserAdapter, an NPAPI plugin; there is none here, so it rendered an `<object>` that answered nothing | **framework** (the control over an iframe: load, events and the commands an iframe can do; the plugin-only calls log once) |
| 2026-09-20 | Apps' startup calls for the headset, the media keys and the screen get nothing | Apollo asks for all three while starting | `com.palm.keys/headset/status`, `keys/media/status` and `display/control/status` were missing | bus (Android-backed, in the TouchPad's measured shapes - including its own wording for a call that arrives without `subscribe`) |
| 2026-09-20 | Enyo's cross-app UI can't find another app's files | any app opening one (the file picker takes a path instead) | `applicationManager/getAppBasePath` was missing | bus |
| 2026-09-20 | `tellurium_config.json` 404s where a device answers | every Enyo app, at startup | The TouchPad answers that one file with 200 and an empty body | card host (system files) |
| 2026-09-20 | No file to pick: the user's own files aren't in the webOS tree | Papyrus 1.6.1's ePub import, Screen & Lock's wallpaper | `/media/internal` was Lunacy's own copy only, while the files a person has are in Android's shared storage | card host (`UserFiles`: Android's folders mapped into `/media/internal` under webOS's own names) |
| 2026-09-20 | A page could tell it wasn't on a device: `PalmSystem.getResource` and `getIdentifier` existed, its members enumerated, and `palmGetResource` returned `undefined` where a TouchPad returns `null` | every app that feature-detects | Lunacy's `PalmSystem` was a plain object with extra members | compat (bridge: a host object with nothing enumerable, and the measured surface exactly) |
| 2026-09-20 | Servers that tell webOS devices apart didn't see one | every app on the wire | `X-Palm-Carrier` was never sent; the TouchPad puts `c090-01` on every request (spike/results/touchpad-net.txt) | compat (network shim) |
| 2026-09-20 | Apps were told the wrong locale and clock format | every app | `locale` and `timeFormat` were hardcoded `en_us`/`HH12` while the shell's clock followed Android | shell and compat (both follow Android's settings now) |
| 2026-09-20 | An app showing the network interface saw Android's | apps that show connection details | `connectionmanager` reported `wlan0`; a TouchPad's Wi-Fi is `eth0` | bus |
| 2026-09-20 | Turning the tablet end for end doesn't reach apps at all | every Enyo app | Two layers. The shell: Android reports no configuration change when the rotation turns 180° (same orientation, same size), so `PalmSystem.screenOrientation` went stale - a display listener catches every rotation instead. Enyo: it decides the window has rotated by watching `window.resize`, which a 180° turn doesn't fire, and leaves the host's `Mojo.screenOrientationChanged` an empty stub. Its own note says why - the callback wasn't made on one of Palm's devices - and that sysmgr does make it, which Lunacy's shell does | shell, and **framework** (the stub now feeds `enyo.sendOrientationChange`, which only dispatches when the orientation really changed, so the resize path can't double up) |

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
- **visual, not yet solved:** Enyo dialogs (Apollo's, AccuWeather's) show faint lines along
  the frame's border-image slice boundaries: the page behind shows through by a few levels.
  Measured in Apollo: they sit exactly on the slice edges, at whole and half pixels alike,
  and survive moving the dialog, putting it on its own layer, `image-rendering: pixelated`
  and repeat instead of stretch. The likely cause is that this WebView rasterizes at
  Android's density (1.33125) under a 0.75 page scale; not confirmed. Next: compare on the
  factory WebView 37, and look for a way to raster at 1:1.
- **app timing, to compare on the TouchPad:** AccuWeather sometimes logs
  `this.appModel.getWeatherModel is not a function` at startup: a view updates before its
  `appModel` is set. It runs normally afterwards.
- **third party, nowhere general:** AccuWeather's radar loads today's Google Maps script,
  which uses syntax WebView 64 can't parse.

## Apps tested

On the HP 10 G2 with WebView 64, landscape, installed from `.ipk` through the package manager
on 2026-09-19. Not yet repeated on the factory WebView 37.

| App | Kind | Result |
|---|---|---|
| AccuWeather 2.3.1 | Enyo 1 | Runs with live data after the Terms dialog. Radar map fails (third party). |
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

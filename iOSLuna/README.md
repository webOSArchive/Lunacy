# iOSLuna

**A toolkit for porting one webOS app to iOS, unmodified.** `LunaKit` is a Swift package that
hosts a webOS app in a WKWebView and gives it the contract it was written against, using
Lunacy's own compatibility layer and modernized Enyo, not new copies of them.

Apple's rules mean iOS can't have what Lunacy is on Android: an environment that installs
and runs apps from the App Museum (downloading executable code is not allowed). So iOSLuna is
the other half of Lunacy's design, without the shell: one webOS app is bundled into one iOS
app, and runs as a single full-screen card. The first port is Apollo, a Pandora client for the
TouchPad and the Pre3.

Lunacy's rules apply ([CLAUDE.md](../CLAUDE.md)): the look and feel is the device's, an app is
never patched by the host, a fix goes into the shared layer or the frameworks, and the bus
never fakes a success.

## What is shared, and what is iOS's own

Shared with AndroidLuna, used as they are (copied in by `assemble-assets.sh`, never edited):

- `AndroidLuna/src/main/assets/lunacy/`: `compat.js`, `bridge.js`, `net.js`, `websql.js`,
  `textedit.js`, `crypt.js`, `fonts.css`, and the Prelude fonts.
- Enyo 1.0, the TouchPad's with Lunacy's patches, as
  [webos-sdk-redux](https://github.com/webOSArchive/webos-sdk-redux) ships it
  (`0.4.1/share/framework/enyo/1.0`).

iOS's own (`Sources/LunaKit/`), the native half of the `window.LunacyNative` contract that
AndroidLuna provides in Kotlin:

| File | What it is | Android counterpart |
|---|---|---|
| `JS/ios-native.js` | `window.LunacyNative` on WKWebView, media keys, background keep-alive | `AppWindow.kt`'s JavaScript interface |
| `LunaCard.swift` | The card: one WKWebView per webOS window, the native dispatch | `AppWindow.kt` |
| `AppServer.swift` | Serves the app, the frameworks and `/__lunacy/` on the app's origin | `AppServer.kt` |
| `Transforms.swift` | The serve-time HTML, JS and CSS transforms | `AppServer.kt` (`HtmlTransforms` etc.) |
| `NetShim.swift` | Cross-origin XHR, sent as the TouchPad sent it | `NetShim.kt`, `Http.kt` |
| `NetRoute.swift` | `/__lunacy/net`: http images and media through the host | `LocalNet.kt` |
| `WebSql.swift` | WebSQL on SQLite | `WebSql.kt` |
| `Bus.swift`, `Services.swift` | The bus, and the services apps have needed | `Bus.kt` and the services |
| `DeviceProfile.swift` | TouchPad on a tablet, Pre3 on a phone | `DeviceProfile.kt` |
| `Log.swift` | The unified log, public text with secrets blanked | `Bus.redact` |

## Porting an app

1. Add the package (`LunaKit`) to an iOS app, and show a `LunaCardViewController`:

   ```swift
   let webos = Bundle.main.resourceURL!.appendingPathComponent("webos")
   let app = LunaApp(appId: "com.example.app",
                     package: webos.appendingPathComponent("apps/com.example.app"),
                     assets: webos)
   window.rootViewController = LunaCardViewController(app: app)
   ```

2. A build phase copies the app, as packaged, to `webos/apps/<appid>/` in the bundle, and runs
   `assemble-assets.sh "$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/webos"`. The phase
   reads outside the project, so it needs `ENABLE_USER_SCRIPT_SANDBOXING = NO`. Enyo comes from
   a webos-sdk-redux clone beside this one, or from `ENYO_FRAMEWORK`.
3. Info.plist: `UIBackgroundModes` `audio` for an app that plays, and an App Transport
   Security exception for an app that speaks plain http (most did).

An app can only be shown what iOS allows: it is one card, its other windows (dashboards, popup
alerts) run but are not shown, and it can't launch another webOS app.

## Where iOS differs, and why

Each of these is iOS's, not a choice; Lunacy's [luna-deltas](../Docs/luna-deltas.md) is the
Android list.

- **The origin is `webos://<appid>.media.cryptofs.apps`**, not `https://`: WKWebView can't
  intercept http or https. The host name, which apps and Lunacy's scripts look at, is the same.
- **No synchronous bridge.** Android's interface is synchronous; WKWebView's isn't. Results a
  request is waiting for are pushed into the page before Lunacy's own "done" hook is called, so
  `netResult` and `sqlResult` answer from memory; the few calls that must answer at once go
  through `prompt()` (see `ios-native.js`).
- **Media comes through `/__lunacy/net`** (`localNetworkBlocked()` is true). AVFoundation
  believes a server's declared type, where the TouchPad's media server and Chromium looked at
  the bytes: Pandora's CDN serves ADTS AAC as `….mp4`, `application/octet-stream`, which
  AVFoundation fails to open. `NetRoute` declares what the bytes are.
- **Media keys come from the Media Session API.** While a page plays, WebKit owns the lock
  screen's, Control Center's and a car's buttons; `ios-native.js` registers handlers and hands
  them to `com.palm.keys/media/status` as the keys a TouchPad's Bluetooth stack sent.
  `MPRemoteCommandCenter` is only a fallback. When a car or headset connects, what's playing is
  announced again (some head units read the clock only then).
- **Playing on in the background.** iOS suspends a web page seconds after its sound stops; a
  webOS app kept running. In the background, whenever an app's media stops for any reason but
  the person's own pause or stop key, the page plays silence until real playback resumes, for
  three minutes at most.
- **Logs.** NSLog's arguments are `<private>` in a log collected off a device, so LunaKit logs
  through `os.Logger` (subsystem `org.webosarchive.lunakit`) as public text, with tokens,
  passwords and email addresses blanked.
- **A server that never answers** fails after URLSession's read timeout, not the TouchPad's
  9 s: URLSession has no connect timeout.
- **CSS px** are points: an iPad point is about a TouchPad pixel. `DeviceProfile.pointsPerPixel`
  can scale an app up on a bigger screen.

## Still to do

- `com.palm.display/status` (no category), which Apollo asks for, answers "Unknown method" here
  as on Android; what a TouchPad answers hasn't been measured.
- The webOS media key names are taken as `play`, `pause`, `togglePause`, `next`, `stop`; they
  haven't been measured on a device.
- The services are the ones Apollo has needed; others answer with the bus's honest error.

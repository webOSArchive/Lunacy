# Spike 1: stock Enyo on Android 5

**Question:** does stock Enyo 1.0, unmodified, run real apps in an Android 5 WebView with
touch? The only earlier evidence was desktop Safari with a mouse.

**Answer: yes, with one global input fix.** The App Museum runs fully: three panes, live
catalog data, scrolling, selection, and install requests reaching the bus. No app was
edited, and there are no per-app fixes.

## Setup

- **Device.** HP 10 G2 tablet: Android 5.0.1, MT8127, 1 GB of RAM, factory WebView
  Chromium 37.
- **Framework.** Stock [enyojs/enyo-1.0](https://github.com/enyojs/enyo-1.0), unmodified.
  It is served at `/usr/palm/frameworks/enyo/<any version>/`.
- **Spike app.** A bare Kotlin activity with one WebView per window, serving apps and the
  framework through `shouldInterceptRequest`.
- **Origin.** Each app gets `https://<appid>.media.cryptofs.apps`, and is served at
  `/media/cryptofs/apps/usr/palm/applications/<appid>/`.
- **Scripts added to every page at serve time:**
  - a compat script that converts touch to mouse events;
  - a minimal bridge with `PalmSystem`, `PalmServiceBridge` and `palmGetResource`. Every bus
    call returns an honest "not implemented" error.
- **Test apps:**
  - the Enyo HelloWorld and Sampler examples;
  - App Museum II 2.9.5;
  - Glimpse 1.3.5 (a `noWindow` app);
  - Amazon Kindle Beta 0.12.50.

The spike code is in `spike/android`. Screenshots and logs stay local.

## Results

| App | Result |
|---|---|
| HelloWorld, Sampler | Render correctly with and without the bridge. Toolbars, border-image buttons, radio groups and toggles all look right. |
| App Museum II | Works: catalog loads, lists scroll, tapping selects, sliding panes animate, and the install button reaches the bus. |
| Glimpse | Its hidden `noWindow` root opens a card through `window.open`, and the layout renders. The Web pane is black because the `WebView` control is missing (see below). Weather and RSS are dead third-party APIs, not a Lunacy problem. |
| Kindle | Shows its background, then retries a `db.find` in a loop. It also ships a native plugin. Not a target; startup check only. |

**Performance.** The compositor holds about 60 fps. A card with the Museum open uses about
72 MB (PSS), so 1 GB of RAM can hold several live cards.

**Network.** WebView 37 reaches current HTTPS sites (webosarchive.org, example.com). The
catalog server supports plain `http` and sends `Access-Control-Allow-Origin: *`. With
mixed content allowed, the Museum's requests work without the network shim. GitHub renders
blank, most likely because its modern JavaScript doesn't run on Chromium 37, not a TLS
failure.

## Findings

1. **Touch has to arrive as mouse events. This is the big one.**
   - Enyo 1 listens only for mouse events, because webOS's WebKit delivered touches as
     `mousedown`/`mousemove`/`mouseup`.
   - Chromium synthesizes mouse events for taps only, never for drags, so no scroller
     moved.
   - A global compat script restores the webOS input model: it converts touches to mouse
     events and cancels native touch handling. After that, every scroller and pane
     worked.
   - It belongs in the compat layer rather than the Enyo fork, because webOS did this for
     all web content, Mojo included.
2. **`palmGetResource(path, "const json")` returns a parsed object.**
   - When `PalmSystem` exists, Enyo reads `appinfo.json` and `framework_config.json` this
     way.
   - Returning a string broke `enyo.fetchAppInfo()`. Glimpse's db8 kind became
     `undefined:1`, and the Museum's updater crashed.
3. **Apps check `location.hostname`.** The Museum decides it is on webOS when the hostname
   contains `.media.cryptofs.apps`; its LuneOS branch checks for `file:///media/cryptofs`.
   So the origin `https://<appid>.media.cryptofs.apps` isn't cosmetic: apps take different
   code paths depending on it. Confirmed on a TouchPad: see "TouchPad reference" below.
4. **The Museum installs through Preware.**
   - It calls `palm://com.palm.applicationManager/open` with
     `{id: "org.webosinternals.preware", params: {type: "install", file: <ipk url>}}`, or
     `org.webosports.app.preware` on LuneOS.
   - So Lunacy's package manager answers `open` requests for Preware's id. The Museum
     itself needs no `downloadmanager` or installer service.
   - This closes the roadmap's open question.
5. **Enyo takes a different code path when `PalmSystem` exists.** It calls
   `PalmSystem.useSimulatedMouseClicks()` and `PalmSystem.simulateMouseClick(x, y, down)` to
   focus inputs. On webOS these made the host inject a real click. The spike focuses the
   element under the point.
6. **Enyo's `WebView` control needs webOS's native browser plugin.** On webOS it wrapped
   `BrowserAdapter`. Lunacy has to supply it, either as an iframe or as a native WebView
   overlaid on the card. Glimpse's Web pane and "read later" view depend on it, and it
   probably appears in many apps. The corpus survey should count it.
7. **Apps read system files as well as framework files.** Seen so far:
   - `/usr/palm/command-resource-handlers.json`;
   - `/usr/palm/frameworks/tellurium/tellurium_config.json`;
   - `framework_config.json` files.

   On a TouchPad, apps can't read `/usr/palm/command-resource-handlers.json`: the request
   throws. So Lunacy fails those requests the same way, serves only the framework and
   system files apps really could read, and logs every request for a system path, as the
   bus logs unknown services.
8. **Apps load Enyo by two version paths**, `enyo/0.10` (the Museum, Kindle) and `enyo/1.0`
   (Glimpse). They are the same framework: on a TouchPad, `1.0` is a symlink to `0.10`. For
   how stock compares with what the TouchPad shipped, see "TouchPad reference" below.
9. **The services apps call at startup matter.**
   - Kindle retries `db.find` (with `watch`) and a `connectionmanager/getstatus`
     subscription again and again when they fail.
   - The Museum asks for `preferences/systemProperties` (`nduid`).
   - Glimpse calls `db.putKind`.
   - This confirms that db8 and the basic system services belong with phase 1.
10. **Hybrid apps exist.** Kindle declares `"plug-ins": true` and ships an ARM native plugin.
    Web apps with PDK plugins are their own category, and the corpus survey should count
    them.
11. **Packages contain odd filenames.** Kindle has a filename containing a control
    character. The package manager must cope, e.g. by keeping names as bytes on disk
    rather than as Android assets.

## WebView 64

The tablet's WebView was updated to 64.0.3282.137 (see [android5-setup.md](android5-setup.md))
and the same apps were run again.

- **Functionally the same.** The Museum loads, scrolls, selects and installs, and the touch
  shim works.
- **Visually worse, from a single cause.**
  - Every control drawn with `-webkit-border-image` lost its frame: buttons, toolbar
    buttons, the toggle switch, and the Museum's update dialog.
  - Enyo's CSS gives these rules a border image and a border width, but no border style, or
    even an explicit `border-style: none`.
  - 2011 WebKit (and Chromium 37) drew the image anyway. Newer Chromium computes the width
    to 0, so the image disappears.
- **The fix is one serve-time CSS transform.**
  - The rule: in every CSS rule that sets a `-webkit-border-image` other than `none`, append
    `border-style: solid; border-color: transparent`.
  - With it, WebView 64 renders the Sampler the same as WebView 37.
  - Remaining difference: faint seams in some slices, which is the `fill` difference already
    noted in [lessons.md](lessons.md).
  - This is the first serve-time transform, and it validates the approach.

**Conclusion on the baseline question.**
- Chromium 37 is closest to what the apps were written for.
- Newer WebViews converge on 37 through global transforms.
- Lunacy supports the whole range, and the compatibility test suite runs on at least 37 and
  64.
- The border-image transform is needed on every platform newer than factory Lollipop, so it
  belongs in phase 0/1.

## TouchPad reference

A probe app, `spike/probe`, was installed on a real TouchPad running webOS CE 3.1.0. It
records the contract as the device presents it.

- **Location.**
  - `location.href` is `file:///media/cryptofs/apps/usr/palm/applications/<appid>/index.html`.
  - WebKit reports `location.hostname` (and `document.domain`) as
    `.media.cryptofs.apps.usr.palm.applications.<appid>`. That is a per-app file origin, and
    it is why the Museum's `.media.cryptofs.apps` check works.
  - Lunacy's `https://<appid>.media.cryptofs.apps` keeps the path identical and passes that
    check. It can't match `protocol: "file:"`; the corpus survey should look for apps that
    check it.
- **Cross-origin XHR is unrestricted.** `http://example.com/`, which sends no CORS
  headers, returns 200 with its body. That confirms the network shim is needed for such
  servers.
- **Local file access is restricted.** Sync XHR to `/usr/palm/command-resource-handlers.json`
  or `/etc/palm-build-info` throws `NETWORK_ERR` (101). So apps that ask for those files
  already fail on webOS, and Lunacy should fail the same way rather than serve them.
- **`palmGetResource(url, hint)`.**
  - It needs an absolute URL; relative paths return `null`.
  - With a `json` hint it returns the parsed object; without one, the text.
  - A missing or disallowed file returns `null`.
- **`PalmSystem`.** A host object whose members don't enumerate.
  - *Functions present:* `activate`, `addBannerMessage`, `addNewContentIndicator`,
    `allowResizeOnPositiveSpaceChange`, `cancelCrossAppScene`, `cancelSceneTransition`,
    `clearBannerMessages`, `crossAppSceneActive`, `deactivate`, `decrypt`, `editorFocused`,
    `enableDockMode`, `enableFullScreenMode`, `encrypt`, `hide`, `hideSpellingWidget`,
    `keepAlive`, `keyboardHide`, `keyboardShow`, `markFirstUseDone`, `paste`,
    `playSoundNotification`, `prepareSceneTransition`, `printFrame`,
    `receivePageUpDownInLandscape`, `removeBannerMessage`, `removeNewContentIndicator`,
    `runAnimationLoop`, `runCrossAppTransition`, `runSceneTransition`, `runTextIndexer`,
    `setAlertSound`, `setManualKeyboardEnabled`, `setWindowOrientation`,
    `setWindowProperties`, `show`, `shutdown`, `simulateMouseClick`, `stagePreparing`,
    `stageReady`, `useSimulatedMouseClicks`.
  - *Values:*
    - `identifier`: `"<appid> <pid>"`;
    - `launchParams`: `""` when there are none;
    - `locale`: `"en_us"`;
    - `localeRegion` and `phoneRegion`: `"us"`;
    - `timeFormat`: `"HH12"`;
    - `version`: `"Webkit4/V8; device"`;
    - `screenOrientation`: `"right"`;
    - `windowOrientation`: `"up"`;
    - `isActivated`, `isMinimal`, and a numeric `activityId`.
  - *Undefined:* `getResource`, `getIdentifier`, `TZ`, `windowIdentifier`, `simulated`.
- **`deviceInfo`** is a JSON string, not an object. The TouchPad's copy includes
  `screenWidth` 1024, `screenHeight` 768, `minimumCardWidth`/`Height`,
  `maximumCardWidth`/`Height`, `touchableRows`, `keyboardAvailable`, `keyboardType`,
  `wifiAvailable`, `bluetoothAvailable`, `carrierAvailable`, `coreNaviButton`,
  `swappableBattery`, `dockModeEnabled` and the platform version fields. Lunacy's copy
  should have the same keys.
- **Bus errors.** An unknown service replies
  `{"returnValue":false,"errorCode":-1,"errorText":"Service does not exist: <service>."}`.
  Lunacy uses exactly this.
- **Host to app calls.** LunaSysMgr calls functions on a global `Mojo` object, which Enyo
  defines: `handleGesture`, `hide`, `show`, `keyboardShown`, `lowMemoryNotification`,
  `positiveSpaceChanged`, `relaunch`, `screenOrientationChanged`, `stageActivated` and
  `stageDeactivated`. `stageActivated` fires at launch. The back gesture arrives as an ESC
  key event, which Enyo turns into `back`.
- **User agent.**
  `Mozilla/5.0 (hp-tablet; Linux; hpwOS/3.1.0; U; en-US) AppleWebKit/534.6 (KHTML, like Gecko) wOSSystem/234.83 Safari/534.6 TouchPad/1.0`.
- **The shipped Enyo is stock Enyo plus small changes.** On the TouchPad, `enyo/1.0` is a
  symlink to `enyo/0.10`. Compared with `enyojs/enyo-1.0`:
  - real code differences are small: 56 lines in `enyo-build.js`, and a few lines each in
    about 40 source files;
  - the accounts and contacts libraries differ more;
  - the TouchPad copy adds `resources/` localization folders and `lib/networkproxy`;
  - 487 images differ.

  `en_us.json` is missing on the TouchPad too, so that isn't a gap. The fork starts from
  stock, and imports the TouchPad differences where they matter.

## Not yet done

- **WebView about 95**, the last one for Android 5. It comes from Play (which needs a Google
  account) or a hand download.
- **A live touch sequence on the TouchPad.** The probe has a test square, but it needs a
  person to drag on it. The shipped framework's source already shows Enyo only handles mouse
  events on webOS too.
- **Phone form factor.**
- **Keyboard and text input.**

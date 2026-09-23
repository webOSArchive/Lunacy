# LunaCE against AndroidLuna: the deltas

A work list, written 2026-09-22 from a read of the LunaCE source (the tree vendored at
[Workbench/vendor/LunaCE](../Workbench/vendor/LunaCE), which is what the reference TouchPad
runs) against `AndroidLuna/`. Each entry says what LunaSysMgr does, what Lunacy does today,
what to change, and how to know it is right. It is written so that it can be worked through
one entry at a time without re-reading either tree.

**Status, 2026-09-23 (0.2.5, unchanged since 0.2.0):** A and B have been worked through; each entry starts with what
it came to, measured on the reference TouchPad. Open: A9 and A10, B1.5 (left as it is by
design), the rest of the system menu (B3) and of Just Type (B6), and C.

**Read first:** [luna-shell-reference.md](luna-shell-reference.md) is the measured spec of
the shell (the `§` numbers below are its sections), and [lessons.md](lessons.md) says why
every entry ends with a check on the device. The rules in [CLAUDE.md](../CLAUDE.md) apply to
all of this: no per-app fixes, the bus never fakes a success, every new asset copied from a
device gets a NOTICE, and nothing here is done until it has been compared with the reference
TouchPad at the same scale.

**How the pointers work.** LunaCE links are to `https://github.com/webOSArchive/LunaCE/blob/master/…`
with the line numbers of the vendored tree; if a line has drifted, search the file for the
symbol named next to it. Lunacy pointers are relative links into this repo, with the class or
function to look in. The three places Lunacy's side lives:

| Layer | Where |
|---|---|
| Shell (what LunaSysMgr drew) | [AndroidLuna/…/shell/](../AndroidLuna/src/main/java/org/webosarchive/lunacy/shell/): `ShellActivity.kt`, `CardLayer.kt`, `StatusBar.kt`, `Notifications.kt`, `Launcher.kt`, `QuickLaunch.kt`, `JustType.kt` |
| App-facing contract (`PalmSystem`, window properties, lifecycle) | [bridge.js](../AndroidLuna/src/main/assets/lunacy/bridge.js) (page side), [AppWindow.kt](../AndroidLuna/src/main/java/org/webosarchive/lunacy/card/AppWindow.kt) (`WindowHost` interface and `Native`), `ShellActivity.kt` (implements `WindowHost`) |
| Bus services LunaSysMgr itself provided (`com.palm.applicationManager`, `com.palm.systemmanager`) | `ShellActivity.registerServices()`, [Bus.kt](../AndroidLuna/src/main/java/org/webosarchive/lunacy/card/Bus.kt), [DockMode.kt](../AndroidLuna/src/main/java/org/webosarchive/lunacy/card/DockMode.kt) |

Priority follows the project's: what an app can notice comes before what only the eye can,
and within the shell the things the reference screenshots show come first. **A** is the
app-facing contract, **B** the shell, **C** the bus services LunaSysMgr owned, **D** the
things that look like deltas and aren't, so nobody spends a day on them.

---

## A. The app-facing contract

### A1. Full-screen cards (`PalmSystem.enableFullScreenMode`)

**Done 2026-09-22.** Measured with `Workbench/probe/org.webosarchive.lunacy.winprobe` on both machines, both orientations; the "Up orientation only" branch below is the phone-era path and does not apply to the tablet (full screen works in portrait too). Not yet checked with Palm's Video Player itself. See [fix-log.md](fix-log.md).

- **LunaCE:** the card's window grows by the status bar's 28 px and the status bar slides off
  the top, 400 ms OutCubic. Only in the Up orientation; in any other the card is always full
  screen. [CardWebApp.cpp#L1651](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWebApp.cpp#L1651)
  (`CardWebApp::enableFullScreenMode`),
  [SystemUiController.cpp#L1733](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L1733),
  [MenuWindowManager.cpp#L382](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/MenuWindowManager.cpp#L382);
  spec §1.2.
- **Who asks:** Enyo's `enyo.setFullScreen` ([system.js#L72](../Workbench/vendor/enyo-1.0/framework/source/palm/system/system.js)),
  Palm's Video Player, every Mojo video/game app; nine call sites in the tested apps.
- **Lunacy:** `bridge.js` line ~107 logs it and does nothing, so a video plays under a status
  bar. Listed in [fix-log.md](fix-log.md) "shell, full-screen cards".
- **Do:**
  1. `AppWindow.Native`: add `fullScreen(on: Boolean)`; `WindowHost`: add
     `fun fullScreen(window: AppWindow, on: Boolean)`. In `bridge.js` make
     `enableFullScreenMode` call it and remember the value on the window (webOS reports it
     back through `setWindowProperties`' `fullScreen` key, [WindowedWebApp.cpp#L1236](https://github.com/webOSArchive/LunaCE/blob/master/Src/webbase/WindowedWebApp.cpp#L1236)).
  2. `ShellActivity.fullScreen`: only acts when that window is the maximized card. Animate
     `cards`' `topMargin` from `luna.px(StatusBar.HEIGHT)` to 0 and `statusBar.translationY`
     to `-height`, 400 ms `Easing.OutCubic`, and back. The window's own resize event tells the
     page (Enyo re-reads `innerHeight`); nothing else needs sending.
  3. When a full-screen card minimizes or another card maximizes, put the bar back
     (LunaSysMgr does this from the card's window properties on focus change,
     [SystemUiController.cpp#L2161](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L2161)).
  4. Remember it per window so a card that comes back maximized comes back full screen.
- **Check:** Palm's Video Player on both machines: the bar goes, the video fills 768 px, and
  the home gesture still works from the bottom edge.

### A2. A card that asks for one orientation (`setWindowOrientation`, `windowOrientation =`)

**Done 2026-09-22, and the "easy" path is the faithful one:** held in landscape, the reference TouchPad turned the whole screen - status bar included - for a card that asked for "up", and the card then read `windowOrientation` "up". Left different: Android follows the sensor again as soon as the lock is released, where the TouchPad waited to be turned; an emulated card's request is ignored. "left"/"right" are mapped to Android's two landscapes by reasoning, not measurement.

- **LunaCE:** `"free"` lets the card turn with the screen. `"up"`, `"down"`, `"left"`,
  `"right"`, `"landscape"` or `"portrait"` fix it: the card is laid out at that
  orientation's size (`getFixedOrientationDimensions`: width and height swapped when the
  request disagrees with the screen, and the 28 px comes off the other axis) and *drawn
  rotated inside the screen* while the status bar stays where it is. An emulated card is
  always `"up"`. [CardWebApp.cpp#L95](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWebApp.cpp#L95)
  (constructor), [#L1329](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWebApp.cpp#L1329)
  (`getFixedOrientationDimensions`), [#L1399](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWebApp.cpp#L1399)
  (`setFixedOrientation`), [#L616](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWebApp.cpp#L616)
  (the rotated blit). The request also arrives from `appinfo.json`'s
  `requestedWindowOrientation`, read at line 97.
- **Who asks:** Mojo's `StageController.setWindowOrientation` (games, video), Enyo's
  `enyo.setWindowOrientation`; the bridge already records the request in
  `PalmSystem.specifiedWindowOrientation`.
- **Lunacy:** `bridge.js` `windowOrientation` setter (line ~196) records it and logs
  "rotation lock not implemented". Cards always follow the screen.
- **Do** (the easy path first, then the faithful one if it is not enough):
  1. *Easy:* `WindowHost.windowOrientationRequested(window, o)`. While that window is the
     maximized card and `o` is not `"free"`, set the activity's `requestedOrientation` to
     the matching Android value (map through `DeviceProfile.windowOrientationFor`'s inverse:
     the TouchPad's `"up"` is portrait, `"right"` is landscape); restore
     `SCREEN_ORIENTATION_UNSPECIFIED` on minimize or when another card maximizes. Also honour
     `rotationLockMaximized` from `setWindowProperties` the same way
     ([CardWindow.cpp#L1189](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1189)).
     The difference from the device: the *shell* turns with the card, where webOS turned
     only the card. Card view still works because the lock is released on minimize.
  2. *Faithful:* keep the screen free, give the `Card` a rotation of ±90/180 and lay the
     `AppWindow` out at the swapped size, as `getFixedOrientationDimensions` does; the
     status bar stays put. Only do this if a screenshot shows an app where the difference
     matters.
  3. Read `requestedWindowOrientation` from `appinfo.json` in `AppRegistry.AppInfo` and apply
     it at launch, before the page can ask.
- **Check:** a Mojo game that fixes landscape, held in portrait, on both machines.

### A3. Window properties beyond `blockScreenTimeout`

**Done 2026-09-22 for `statusBarColor` and `fullScreen`.** Measured: the colour is taken up at the *next* maximize, not while the card is already up, and `0` is black, not "default". `suppressBannerMessages`, `suppressGestures` and `rotationLockMaximized` turned out to be no-ops on the device - see D.

`PalmSystem.setWindowProperties` takes these keys
([JsSysObject.cpp#L2018](https://github.com/webOSArchive/LunaCE/blob/master/Src/js/JsSysObject.cpp#L2018)
to L2098); Lunacy's `bridge.js` `setWindowProperties` (line ~113) acts on one and logs the
rest. Do them in this order:

| Key | What LunaSysMgr does | Pointer | Lunacy step |
|---|---|---|---|
| `statusBarColor` (int RGB) | while that card is maximized the status bar's fill is the app's colour instead of `#515558`, cross-faded 300 ms | [SystemUiController.cpp#L1131](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L1131), [StatusBar.cpp#L1009](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L1009), §4.1 | `StatusBar.setMode(Mode.APP, color)`; store the colour on `AppWindow`; `ShellActivity.onMaximized` passes it |
| `suppressGestures` (bool) | the bottom-edge gesture is off while that card is maximized (games) | [SystemUiController.cpp#L666](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L666), [#L2110](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L2110) | `CardLayer.onInterceptTouchEvent`: skip the edge test when `maximized.window.suppressGestures` |
| `rotationLockMaximized` (bool) | the screen stops rotating while the card is maximized | [CardWindow.cpp#L1189](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1189) | same lever as A2 step 1 |
| `suppressBannerMessages` (bool) | banners are held while that card is up | [WindowedWebApp.cpp#L1264](https://github.com/webOSArchive/LunaCE/blob/master/Src/webbase/WindowedWebApp.cpp#L1264); consumer: grep `suppressBannerMessages` in `Src/base/SystemUiController.cpp` and `Src/lunaui/notifications` | `Notifications.addBanner` queues without showing while the maximized window has it set; shows on minimize |
| `fullScreen` | see A1 | | |
| `dashHeight` | see B4 | | |
| `subtleLightbar`, `activeTouchpanel`, `alsDisabled`, `enableCompass`, `enableGyro`, `fastAccelerometer`, `webosDragMode`, `hasAlphaHole` | hardware the tablet has no equivalent for, or PDK | | keep logged |

- **Check:** an app that sets `statusBarColor` (Enyo's `enyo.windows.setWindowParams` path,
  [agent.js#L29](../Workbench/vendor/enyo-1.0/framework/source/palm/system/windows/agent.js));
  the bar's colour in a screenshot beside the device's.

### A4. `PalmSystem.deactivate`

**Not a delta - moved to D.** Measured: `enyo.windows.deactivateWindow(window)` on a maximized card does nothing on the reference TouchPad (no minimize, no `windowDeactivated`). `MethodDeactivate` only sends `ViewHost_UnfocusWindow`.

- **LunaCE:** the page asks to lose focus; the card is deactivated (the maximized card is
  minimized to card view). [JsSysObject.cpp](https://github.com/webOSArchive/LunaCE/blob/master/Src/js/JsSysObject.cpp)
  `MethodDeactivate`, and the `PalmSystem.deactivate` string in `Src/webbase`.
- **Who asks:** Enyo's `enyo.windows.deactivate(window)`
  ([agent.js#L20](../Workbench/vendor/enyo-1.0/framework/source/palm/system/windows/agent.js)).
- **Lunacy:** logged no-op (`bridge.js` line ~332).
- **Do:** `Native.deactivate()` → `WindowHost.deactivate(window)` → if it is the maximized
  card, `cards.showCardView()`. Mirror of the existing `activate`.
- **Check:** an Enyo app with two windows that hands focus back (Email's compose does on a
  device).

### A5. Banner sounds and the rest of `addBannerMessage`'s arguments

**Done 2026-09-22.** The device's `notification.wav`, `alert.wav` and `ringtone.mp3` are byte-identical to LunaCE's Apache-2.0 `sounds/`, so those are what ship. Open choice for codepoet: with no file named, the default is webOS's sound rather than the tone chosen in Android's settings (which Sounds & Alerts opens). Step 5 is moot: `suppressBannerMessages` does nothing on the device.

- **LunaCE:** `addBannerMessage(message, launchParams, icon, soundClass, soundFile, duration,
  doNotSuppress)`. A sound class of `notifications` plays `soundFile`, or
  `/usr/palm/sounds/notification.wav` when none is given; `alerts` plays `alert.wav`;
  `duration` caps it at `notificationSoundDuration` 5000 ms. [BannerMessageHandler.cpp#L743](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L743)
  to L772; §5.2.
- **Lunacy:** `bridge.js` `addBannerMessage` (line ~124) takes the first three and drops the
  rest; `playSoundNotification` is a no-op.
- **Do:**
  1. Pass `soundClass`, `soundFile`, `duration`, `doNotSuppress` through `Native.addBanner`
     to `Banner`.
  2. `Notifications.showNext`: play the file (an app's own file resolves on its origin, like
     the icon does; a bare name resolves in the sounds folder) through `SoundPool` or
     `MediaPlayer` on `STREAM_NOTIFICATION`.
  3. Copy `notification.wav` and `alert.wav` from the reference device's `/usr/palm/sounds`
     into `assets/luna/sounds/` with a NOTICE (rule 7). LunaCE's repo has them in
     [sounds/](https://github.com/webOSArchive/LunaCE/tree/master/sounds) but the device's
     copies are the reference.
  4. `playSoundNotification(soundClass)` plays the class's default.
  5. `doNotSuppress` is what A3's `suppressBannerMessages` must respect.
- **Check:** the notifytest app's `{"do":"banner"}` on both machines, with the volume up.

### A6. Low memory

**Done 2026-09-22.** Android 5 has no `am send-trim-memory`; use `--ei trimMemory 10` (or 15) on the shell ([dev-workflow.md](dev-workflow.md)). Not measured on the device, which can't be pushed into low memory remotely.

- **LunaCE:** `MemoryWatcher` tells every page (`Palm::WebGlobal::notifyLowMemory`,
  [MemoryWatcher.cpp#L187](https://github.com/webOSArchive/LunaCE/blob/master/Src/webbase/MemoryWatcher.cpp#L187))
  and the frameworks call the app's `Mojo.lowMemoryNotification` / Enyo's
  `lowMemoryNotification` handler; `WebAppManager::performLowMemoryActions`
  ([#L698](https://github.com/webOSArchive/LunaCE/blob/master/Src/webbase/WebAppManager.cpp#L698))
  closes background cards when it gets bad.
- **Lunacy:** the shell snapshots older cards on its own (architecture doc, "Card memory and
  crashes") but never tells an app.
- **Do:** `ShellActivity.onTrimMemory(level)`: at `TRIM_MEMORY_RUNNING_LOW` call
  `callMojo("lowMemoryNotification", "{\"state\":\"low\"}")` on every running window, at
  `TRIM_MEMORY_RUNNING_CRITICAL` with `"critical"`, and `"normal"` once it eases - those are
  the three states LunaSysMgr names
  ([WebAppManager.cpp#L1570](https://github.com/webOSArchive/LunaCE/blob/master/Src/webbase/WebAppManager.cpp#L1570)).
  Enyo 1 defines `Mojo.lowMemoryNotification(params)` and dispatches `params.state` as its
  `lowMemory` event ([system.js#L191](../Workbench/vendor/enyo-1.0/framework/source/palm/system/system.js)),
  so the same call reaches both frameworks.
- **Check:** `adb shell am send-trim-memory org.webosarchive.lunacy RUNNING_LOW` and watch
  the card's console.

### A7. Launch timing and the "preparing" card

**Done 2026-09-22; Q7 answered.** A screenshot burst on the reference TouchPad with `…lunacy.slowprobe` (paints at once, calls `stageReady` after 4 s) showed the card waiting in the card view with its pulsing icon and no dock or pill, then maximizing already showing "stageReady". The signal is `stageReady`, or 3 s after load for a page that never calls it (`WindowedWebApp::kShowWindowTimeoutMs`) - not the first paint.

- **LunaCE:** a new card waits up to 150 ms off-screen for the app's first frame; if it is not
  there, the card is added with the loading splash and given 750 ms more; if that runs out,
  it slides to its *card-view* slot and pulses there until the app paints, then maximizes.
  §2.5, [CardWindow.cpp#L1331](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1331),
  [#L1419](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1419),
  [#L1432](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1432);
  `stagePreparing` marks the start ([JsSysObject.cpp](https://github.com/webOSArchive/LunaCE/blob/master/Src/js/JsSysObject.cpp) `MethodStagePreparing`).
- **Lunacy:** `ShellActivity.showAsCard` maximizes at once with the `CardSplash` over the
  page; `stagePreparing` is a logged no-op; `onStageReady` is empty.
- **Do:** only after recording a launch on the device (Q7 in the reference): if a slow app's
  card really does drop back to card view there, add the 750 ms timer to `CardLayer.maximize`
  and a "waiting" state that `appIsReady()` releases. Otherwise leave it.
- **Check:** `screenrecord` on both, an app that takes more than a second to draw (Kindle).

### A8. Clipboard: `paste`, `copiedToClipboard`, `pastedFromClipboard`

**Done 2026-09-22**, and not as low priority as it looked: Chromium 37 refuses `execCommand("copy"/"cut")` to web content, so Enyo's own Copy and Cut did nothing either. The bridge now falls back to Android's clipboard. Measured on the device: copy, then `paste()` into the focused field, and the "Selection Copied" banner.

- **LunaCE:** `paste` pastes the system clipboard into the focused editor; the other two are
  notifications from WebKit. [JsSysObject.cpp](https://github.com/webOSArchive/LunaCE/blob/master/Src/js/JsSysObject.cpp)
  `MethodPaste`.
- **Who asks:** Enyo, six places (`grep -rn "PalmSystem.paste" Workbench/vendor/enyo-1.0/framework/source`).
- **Lunacy:** logged no-ops.
- **Do:** `Native.paste()` → read `ClipboardManager` on the main thread and
  `evaluateJavascript(document.execCommand('insertText', false, <text>))` into the focused
  element. Low priority: Enyo's own copy/paste menu works without it on Chromium.

### A9. Open: what a *free* card reports as its orientation

Found while measuring A2 and not yet settled. On the reference TouchPad, a free card read
`screenOrientation` "up" and `windowOrientation` "right" when launched in landscape **and**
when launched in portrait. Lunacy's getters follow the screen (landscape: "right" and "up";
portrait: "up" and "right"), as the 2026-09-22 drPodder fix assumed from a portrait-only
measurement. If the device's values really are constant for a free card, every Mojo app's
orientation branch behaves differently here in landscape. **Do:** with codepoet turning the
device, run the window probe and read both values in each of the four positions, launched
fresh and turned while open; then decide.

---

### A10. Open: a page's first script sees a 980-px-wide layout

Found 2026-09-22 with `emuprobe`: its inline script, run while the page is parsed, reads `innerWidth` 980 in Lunacy (Chromium's default layout width) where the reference TouchPad reads 320; by the time the page has loaded it is 320. The compat layer's viewport meta arrives after the first script runs. Any app that measures the window in a script in its `<head>` or body, rather than on load, is told the wrong size. Worth a look in `compat.js`'s viewport code; not touched yet.

## B. The shell

### B1. Card view: stacks, reorder, dimming, the angry card

Lunacy's `CardLayer` is one row of single cards. The device's is groups of cards, and four
behaviours hang off that. Do them in this order; each is independent of the next.

1. **Dimming.** **Done 2026-09-22.** Only a card that *was* active is dimmed (`setActiveCardWindow` dims the old one and brightens the new), so a card that has never been active stays bright; in practice every launched card has been. Measured: (23, 61, 73) on the device, (23, 62, 74) on the tablet for #1d4d5c. Every non-active card is drawn at RGB × 0.8, animated 300 ms OutCubic when the
   active card changes. §2.3 "Dimming",
   [CardWindow.cpp#L248](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L248).
   *Lunacy:* `Card.fade` is only used for throw-away. *Do:* a `ColorMatrixColorFilter` (scale
   0.8 on RGB) on a `Card` whose index ≠ `position.roundToInt()`, animated in
   `CardLayer.applyTransforms`. *Check:* the reference card-view screenshot's side cards.
2. **The angry card.** **Done 2026-09-22.** Correction from the source: `closeWindow(win, true)` sends the card off the *top* like any other close (only the keep-alive and the sound differ), so the "animating `lift` to `+height`" below is wrong. A card released with its centre *below* the screen bottom closes, like
   one thrown off the top (with a bird sound when upside-down, which needs no doing).
   [CardWindowManager.cpp#L2047](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2047).
   *Lunacy:* `CardLayer.onTouchEvent` `Drag.THROW` springs back. *Do:* add
   `centreBelowBottom` beside `centreAboveTop`, animating `lift` to `+height`.
3. **Tap-and-hold to reorder**, **done 2026-09-22** (correction: it is a tap-*and-hold* on the empty space that goes to the previous or next group - a plain tap there does nothing, `handleTapAndHoldGestureMinimized`; there is no reorder sound, see B8). And tap on the empty space left or right of centre to go to
   the previous or next card. §2.6, §2.7,
   [CardWindowManager.cpp#L1511](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1511)
   (hold), [#L1777](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1777)
   (reorder: card at 80 % opacity, detached, follows the finger; zones at ±`W·activeScale/2`;
   shuffle 350 ms OutCubic, cross-group 500 ms OutCubic). *Lunacy:* neither. *Do:* a
   `Drag.REORDER` state entered from a long-press runnable in `ACTION_DOWN`, moving the card's
   index in `cards` when its `cx` crosses a neighbour's; `carddrag` sound on pick-up
   ([#L1702](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1702),
   see B8).
4. **Groups (stacks).** **Done 2026-09-22**, measured with `winprobe`'s `card` action on both machines (`Workbench/results/b1-tp-stack.png`, `b1-and-stack8.png`). Also from the source: a new group goes just right of the *active* group, not at the end. Side groups are laid out at their target offset, where LunaCE uses the offset they had when the slide began. A card an app opens while its own card is focused joins that card's
   group, to its right; the group fans as §2.3 (x offsets `((i−p)/3)·activeW·0.35`, a few
   pixels of y, ±1° of tilt); side groups collapse to a 10 px stagger at `nonActiveScale`;
   maximizing lays other groups out side by side. [CardGroup.cpp#L753](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L753),
   [CardWindowManager.cpp#L644](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L644)
   (auto-grouping), §2.5. *Lunacy:* `cards: ArrayList<Card>` and `cardViewTarget(i)`. *Do:*
   `cards` becomes a list of groups; `cardViewTarget` takes `(group, index)`;
   `ShellActivity.onWindowOpened` puts a child card into its parent's group. This is the
   largest shell item on the list; the roadmap already has "card stacks/groups".
5. **Scale computed once.** The device computes `activeScale` from the *first* layout and
   never again, so a TouchPad that booted landscape keeps 0.5143 in portrait (§2.2). *Lunacy:*
   `activeScale()` recomputes from the current height. This is Q10, a product decision, not a
   bug: leave it unless codepoet says otherwise.

### B2. The bottom-edge gesture: flick, not follow, and the dead zone

**Done 2026-09-22.** codepoet chose to keep the fluid mode (recorded in the architecture doc). The dead zone is in: measured in Lunacy, taps at x 5, x 795 and the bottom row never reach the page, x 20 does, and the swipe up still minimizes. The device's `sysUiEnableNextPrevGestures` reads **true** (`getPreferences`), which is what turns the dead zone on - and which contradicts D's "left/right edge app-switch gestures ... off on the reference device". Open for codepoet: does a swipe in from the left or right edge switch cards on the reference TouchPad?

- **LunaCE, on the reference device:** `sysUiGestureDetection = 0`, so the swipe up is a
  *flick*: nothing moves until the finger lifts with a qualifying flick (25–100 px per touch
  update), then `handleUpSwipe` runs. The fluid, card-shrinks-with-the-finger mode is setting
  2 and is off. §1.4, [BezelGestureRecognizer.cpp#L130](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/BezelGestureRecognizer.cpp#L130),
  [SystemUiController.cpp#L412](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L412).
  Also: with the dead zone on (it is), a pen-down in the 15 px left, right or bottom band
  never reaches the app ([#L344](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L344)).
- **Lunacy:** `CardLayer.minimizeFollow` is the fluid mode, and the app receives the
  `ACTION_DOWN` in the bottom band until the trigger distance is reached.
- **Do:** this is a choice, not a defect - the fluid mode is LunaCE's own and arguably
  nicer. Record which one Lunacy wants in `Docs/architecture.md`; if the device's, replace
  `minimizeFollow` with a flick test on `ACTION_UP` (per-move delta 25–100 px in the
  reference's units, Q6). The dead zone: intercept `ACTION_DOWN` in the three bands
  unconditionally, which also stops a bottom toolbar button firing on a swipe up.
- **Check:** a swipe up that starts on a Mojo command menu button, on both machines.

### B3. Status bar: the transitions, the icons, the empty search group

**Done 2026-09-22, the system menu only begun** (date, battery and brightness rows, as codepoet asked; Wi-Fi, VPN, Bluetooth, airplane, rotation lock and mute rows still to come). Corrections from the source and the device: info icons right to left are Bluetooth *then* Wi-Fi (the reference's "[BT][wifi]" had them reversed); dashboard icons are painted newest-*leftmost*; the title group's separator fades with its ▾, so the card view has none; and the invisible search group takes **no** space next to the notification group (measured: the notification group sits against the system separator on the device). Not yet compared: the system menu against the device's, which needs a tap on the TouchPad.

Lunacy's `StatusBar` draws the right things at the right sizes but changes instantly where the
device animates, and is missing a few states.

| What | LunaCE | Lunacy today | Do |
|---|---|---|---|
| Title change | text cross-fades 300 ms while the width interpolates; the ▾ fades in 500 ms only when the app is "actionable" | `title` setter invalidates | animate `alpha` of old/new text in `onDraw`; §4.2, [StatusBarTitle.cpp#L203](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarTitle.cpp#L203) |
| Icons appearing | width 0→100 % over 1000 ms InOutQuad; the notification group fades 300 ms when its first icon arrives and out when none remain | list replaced, redrawn | per-icon width animator; [StatusBarIcon.cpp#L81](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarIcon.cpp#L81), [StatusBarNotificationArea.cpp#L254](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarNotificationArea.cpp#L254) |
| Battery image | 13 states by threshold: ≤12,20,28,36,44,52,60,68,76,84,88,99,100 % → `battery-0…11`, 100 % reuses 11; `battery-error` when the battery service is gone | `level*11/scale` | the threshold table; [StatusBarBattery.cpp#L148](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarBattery.cpp#L148) |
| Wi-Fi connecting | `wifi-connecting.png` while associating | off or 0–3 | `NetworkInfo.DetailedState.CONNECTING`; [StatusBarInfo.cpp#L221](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L221) |
| Other info icons | right to left: Bluetooth, rotation lock, mute, airplane, VPN; each only when active | none | `icon-rotation-lock`, `icon-mute`, `icon-airplane`, `bluetooth-*`, `vpn-status-icon` (§7.2) from Android's `Settings.System.ACCELEROMETER_ROTATION`, `AudioManager.ringerMode`, `Settings.Global.AIRPLANE_MODE_ON`, `BluetoothAdapter`; [StatusBarInfo.cpp#L162](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L162) |
| The invisible search group | LunaCE's status-bar search is off on the device, but its group still takes **65 px** between the notification group and the system group (3 + 12 + 5 + 28 + 5 + 12) | notification group sits directly left of the system separator | *measure first:* crop the reference screenshot with a dashboard up and check the gap before adding it; §4.2 search-group row, [StatusBarItemGroup.cpp#L488](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L488) |
| System menu | QML drop-down: battery, date, brightness slider, Wi-Fi, VPN, Bluetooth, airplane, rotation lock, mute; 300 wide, rows 42 px, right-aligned 11 px in | none; the ▾ is drawn but does nothing | on the roadmap. Read the *device's* `/usr/palm/sysmgr/uiComponents/SystemMenu/*.qml`, not the repo's (lessons.md); §4.3, [SystemMenu.qml](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/SystemMenu/SystemMenu.qml). The open group gets the same `status-bar-menu-dropdown-tab.png` the notification group already draws |

### B4. Notifications: taller dashboards, the scrolling drop-down, the swipe backing

**Done 2026-09-22, except taller dashboards** (skipped by codepoet's choice: no app is known to have used `dashheight`). The scroller, masks, swipe backing and badge handle are in. From the source: the badge area only matters for `webosDragMode: "manual"` dashboards (all of Enyo's), which get every touch right of the badge; a Mojo dashboard is dragged from anywhere. Persistent dashboards and transient alerts moved to D. Row order: newest on top, read from `layoutAllWindowsInMenu` (Q8). Found on the way: the status-bar and banner fallback icon is the app's mini icon, grey (measured).

| What | LunaCE | Lunacy today | Do |
|---|---|---|---|
| Dashboard height | a `dashheight` stage argument up to 320 px sets the row's height; default 52 | `DashboardMenu.ROW_H` = 52 for all | `bridge.js` `window.open` wrapper already parses `height=`; pass `attributes.dashHeight`/`height` and size the `Row` with it, capped at 320; [DashboardWebApp.cpp#L110](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWebApp.cpp#L110). Check what Enyo's `enyo.Dashboard` and Mojo's `createStageWithCallback` actually put in the features string |
| More than 410 px of rows | the list scrolls inside the frame, with `menu-dropdown-scrollfade-top/bottom.png` and `menu-arrow-up/down.png` fading in over 70 ms when it can scroll | `DashboardMenu` caps its height; rows past 410 px are clipped and unreachable | wrap `rows` in a `ScrollView`; draw the fades and arrows in `dispatchDraw`; [MenuContainer.qml#L79](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/MenuContainer/MenuContainer.qml#L79) |
| Swipe backing | while a row is dragged, the strip behind it shows `menu-dropdown-swipe-bg.png` (3-slice, 5 px caps) and a `menu-dropdown-swipe-highlight.png` edge line | row slides over the frame | draw both under the row in `Row.onDraw`; [DashboardWindowContainer.cpp#L1377](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1377) |
| Persistent dashboards | can't be swiped away | every row can | find how a persistent one is declared (grep `persistent` in `DashboardWindowContainer.cpp` and in `mojo.js`); refuse the drag |
| Badge area | the left 50 px of a row (the icon) is the drag handle; touches there are not forwarded to the app | whole row drags after slop | [#L50](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L50), [#L216](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L216) |
| Transient alerts | 320 px, `transient-alart-bg.png`, centred in positive space, opacity-animated | none | first find who opens one: grep `transient` in `mojo.js` and `enyo-1.0/framework/source`. If nothing does, drop it; §5.1 |
| Row order | not traced (Q8) | newest first | check on the device with two dashboards from notifytest |

### B5. Launcher: the small things the reference lists

Everything here is §3; Lunacy's is `Launcher.kt`.

**Stock items done 2026-09-22** (empty page, bottom shadow, kinetic scroll, auto-scroll, side-edge flip at the source's 50 px / 1500 ms - the 600 ms was chosen, not measured - installing). Found on the way and fixed: a user-installed app belongs on DOWNLOADS (`installedAppsPageIndex`), which is where the reference TouchPad has every one, and the status bar reads "Launcher" while the launcher is up. Column spacing (Q14) settled: measured 5 icons 149 px apart on the reference TouchPad in portrait, which is exactly `calculateAndSetHorizontalSpaceParameters` with the whole page width and the adjustment still counting seven icons (`free = W − 128(n+1) + 84`, gap `⌊free/(n−1)⌋`); Lunacy's even spread gave 146.5 there, and now uses the formula (157 on this 800-px tablet). The LunaCE extras (tab editing, app groups) are below.

| What | LunaCE | Lunacy today | Do |
|---|---|---|---|
| Empty page | "Tap and hold any app to drag it to this page." 18 px `#AAAAAA` in a 350 px box centred +190 px, over `launcher-empty-page.png` (280×220) | nothing | draw in `onDraw` when `page.apps.isEmpty()`; §3.6, [reorderablepage.cpp#L65](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/reorderablepage.cpp#L65) |
| Page bottom shadow | `quicklaunch-shadow.png` (8×8) tiled along the page bottom, under the icons | only `tab-shadow.png` at the top | one more `luna.tile` at `pageBottom() - 8`; [page.cpp#L547](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/page.cpp#L547) |
| Kinetic scroll | `KineticScroller`: velocity ÷ 2225 capped at 100, friction 8e-4, overscroll ≤ 100 px, snap back 500 ms OutCubic | `fling()`: `v * 0.35`, duration heuristic | port the constants; [KineticScroller.cpp#L33](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/util/KineticScroller.cpp#L33). Roadmap has "tuning scroll inertia" |
| Auto-scroll while dragging | near the page's top/bottom 20 px the page scrolls 150 px steps (300 ms, 800 ms delay) | none | `dragTo`: a runnable like `edgeFlip` for the vertical edges; [reorderablepage.cpp#L474](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/reorderablepage.cpp#L474) |
| Side-edge page flip | 50 px band, 1500 ms | `EDGE` 30 px, `EDGE_MS` 600 | *verify on the device first* - the architecture doc says 600 ms was chosen; if that was measured, leave it and fix the reference |
| Installing | icon at 0.5 opacity with `loading-strip.png` (19 frames of 32×32) at (+50, −50) while a package installs; `warning-icon.png` on failure | the launcher only changes when the install finishes | `Launcher.installing: Set<String>` driven from `ShellActivity.install`; [iconheap.cpp#L44](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L44) |
| Column spacing | `free = rowWidth − 128·(n+1) + 12·n; spacing = ⌊free/(n−1)⌋` (Q14) | even spacing across `width − 2·27` | compare a 7-icon row against the reference screenshot before touching it |
| Tab rename / add / delete [LunaCE] **(done 2026-09-22)** | long-press a tab → rename dialog; hold the empty bar 650 ms → "+"; tabs ≥ 4 get a trash; `MaxTabs` 6 | fixed four | §3.5; only after the stock items. `luna-send … systemUi launchertitlechange` is the bus side (C2) |
| App groups (folders) [LunaCE] **(done 2026-09-22)** | hover the centre 60 % of an icon 300 ms → group; 68×68 tile with 2×2 thumbnails; overlay panel | none | §3.8; low priority, LunaCE-only |

### B6. Just Type

**Done 2026-09-22 for the scope codepoet set: the launch results and "Search DuckDuckGo".** Drawn natively to the device app's measurements and with its art (`JustTypePanel`), compared with the reference TouchPad (`Workbench/results/b6-tp-justtype.png`, `b6-and-justtype2.png`). The filter tabs, "Search using…" and the rest are out of scope.

- **LunaCE:** tapping the pill opens Just Type, a web app in a `Type_Launcher` window under
  the status bar, cross-faded in over 150 ms; it searches apps and contacts and hands the
  text to whichever app the user picks. §3.2, §3.3,
  [OverlayWindowManager.cpp#L1837](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1837).
- **Lunacy:** the pill is an `EditText`; Return is swallowed. On the roadmap.
- **Do:** the useful subset without the web app: as text is typed, a list under the pill of
  launch points whose title matches (`registry.launchPoints`), tap launches. Keep the
  `universalSearchCrossFadeDuration` 150 ms. Anything more (Google search, contacts) is a
  separate decision.

### B7. The emulated card's chrome

**Done 2026-09-22**, compared with `emuprobe` on both machines, maximized and in the card view (`Workbench/results/b7-*`). Corrections: the keyboard button is placed from the *card's* corner (the screen's bottom-right), not the phone's; the backdrop is #0F0F0F (`emucard-bg.png` doesn't exist on the device, so LunaSysMgr's fill shows); in the card view there is no phone - a black card with the page drawn about 1.5× (`2 − scale`); and the main status bar shows the carrier string with a ▾ while an emulated card is up. Not done: turning the chrome with the screen (the strip and bar move to the sides in landscape, `layoutWidgets`) - Lunacy's emulated card stays upright.

On the roadmap; the pointers so it can be done without re-reading the C++:

- **What the device draws around a 320×452 phone-sized page:** the tablet screen goes black
  and `emucard-device-frame.png` sits centred (Lunacy has these); *plus* its own status bar
  of `StatusBar::TypeEmulatedCard` across the top of the phone, with the title in the
  `appname-background.png` pill ([StatusBarTitle.cpp#L121](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarTitle.cpp#L121));
  a 66 px `VirtualCoreNavi` gesture strip below it whose tap is the back gesture
  (`Key_CoreNavi_Back`); and a 52×44 keyboard button (`emucard-kb-up_icon.png`, a two-state
  sprite at rows 0 and 44) at the phone's bottom-right, 17 px in and 18 px up.
  [EmulatedCardWindow.cpp#L105](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/emulation/EmulatedCardWindow.cpp#L105)
  to L126 (construction), [#L854](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/emulation/EmulatedCardWindow.cpp#L854)
  (keyboard button position), [#L903](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/emulation/EmulatedCardWindow.cpp#L903)
  to L937 (where each piece goes in each orientation - the strip and bar rotate with the
  card), [#L952](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/emulation/EmulatedCardWindow.cpp#L952)
  (all three hidden while minimized), and the strip's own code in
  [virtual-corenavi/](https://github.com/webOSArchive/LunaCE/tree/master/Src/lunaui/cards/emulation/virtual-corenavi).
  Assets: `emucard-bg.png`, `wm-corner-*.png`, `emucard-kb-up_icon.png`.
- **Lunacy:** `Card` with `emulatedSize` and `frame`; `ShellActivity.showAsCard`. Nothing
  else.
- **Do:** a `EmulatedChrome` view inside `Card` that draws the three pieces, only while the
  card is maximized; the strip's tap calls `window.sendBack()`; the keyboard button calls
  `host.keyboard(window, true)`. The card-view thumbnail should be the phone alone
  (`emulationBoundingRect`, [#L456](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/emulation/EmulatedCardWindow.cpp#L456)).
- **Check:** the emuprobe app (`Workbench/probe/org.webosarchive.lunacy.emuprobe`) beside the
  device, both orientations.

### B8. System sounds

**Done 2026-09-22.** Two corrections from the source: `carddrag` is the *angry card's* stretch sound (played once the card is pulled 30 % of half the screen down, and only in landscape turned upside down - the TouchPad's UI orientation "Down" is relative to its natively landscape panel), not a reorder pick-up sound - reordering is silent; and the launcher plays `LauncherOpenApp`/`LauncherCloseApp` as it shows and hides (`SystemUiController::setLauncherShown`). The samples are in `/usr/share/systemsounds` on the device as raw PCM (s16le, mono, 44.1 kHz). `lunaSystemSoundAppOpen` is empty (no default in `Settings.cpp`, not in the device's luna.conf). `shutter` is not used: Lunacy has no screen capture. Android's Touch sounds is deliberately not read (off out of the box on the tablet); the system volume and the ringer switch are.

- **LunaCE:** `appclose` when a card is thrown away, `carddrag` on reorder pick-up, `shutter`
  for screenshots, `battery_full.mp3` at 100 %, and the notification/alert sounds of A5.
  [Settings.cpp#L106](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L106)
  (names), [CardWindowManager.cpp#L3314](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3314)
  (close), [#L1702](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1702)
  (drag), [StatusBarBattery.cpp#L218](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarBattery.cpp#L218).
  `appclose`, `carddrag` and `shutter` are PulseAudio feedback samples, **not files in the
  repo's `sounds/`**: copy them from the reference device (`ls /usr/palm/sounds` and
  `/usr/share/sounds` over novaterm) and give them a NOTICE.
- **Lunacy:** silent.
- **Do:** a `Sounds` object on `Luna` with a `SoundPool`; play from `CardLayer.throwAway`
  and the reorder of B1. Respect Android's ringer mode.
- **Check:** by ear, and that `lunaSystemSoundAppOpen` really is empty on the device
  (`grep -i sound /etc/palm/luna.conf`).

### B9. Wallpaper smaller than the screen

**Done 2026-09-22.** Measured on the reference TouchPad with a 600 x 400 image: drawn at its own size, centred on the whole screen, and the surround is Qt's `darkGray` (#808080), not black (`WindowServerLuna.cpp#L219`, `setBackgroundBrush(Qt::darkGray)`). Screenshots `Workbench/results/b9-tp-small.png`, `b9-and-small.png`. One deliberate difference: "screen-sized" means at most 1024 x 768, so the shipped 1024 x 1024 TouchPad wallpapers still fill a larger screen.

- **LunaCE:** an image at least screen-sized in either orientation is centre-cropped; a
  smaller one is drawn unscaled and centred on black. §1.3,
  [WindowServerLuna.cpp#L931](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L931).
- **Lunacy:** `wallpaperView.scaleType = CENTER_CROP` always (`ShellActivity.onCreate`).
- **Do:** in `showWallpaper`, `CENTER` on a black background when
  `bmp.width < shortSide && bmp.height < shortSide` (in shell pixels).

### B10. The launching card at launch, not window-open

**Not a delta - moved to D.** Measured 2026-09-22 with `Workbench/probe/org.webosarchive.lunacy.headprobe` (a `noWindow` app that opens its card after `{"delay": ms}`): launched with a 15 s delay, the reference TouchPad showed no card at all until the app opened one (LunaSysMgr's log: `APP START ... type: headless` at 21:42:45, `type: card` at 21:43:01; screenshots in between show the card view unchanged). `prepareAddWindow` runs when a window is created, and a headless app has none until it opens it - which is when Lunacy shows it too.

On the roadmap; the pointer: LunaSysMgr creates the card in
`CardWindowManager::prepareAddWindow` when the *launch* is requested, before the app's page
exists ([CardWindowManager.cpp#L709](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L709));
a `noWindow` app's first `window.open` then fills it. *Lunacy:* `ShellActivity.launch` adds
the root window to `hidden` for a `noWindow` app and the card only appears in
`onWindowOpened`. *Do:* create the `Card` with its `CardSplash` in `launch` for every
non-`noWindow` app (already so) and, for `noWindow` apps, create a placeholder card whose
`AppWindow` is attached when the first `card` child opens; time out and remove it if the app
never opens one (the Clock's alarm relaunch doesn't).

---

## C. Bus services LunaSysMgr provided

Lunacy answers `com.palm.applicationManager` `launch`, `open`, `listApps`, `getAppBasePath`
and the three dock-mode launch-point methods, and nothing on `com.palm.systemmanager`. The
device's full tables: [ApplicationManagerService.cpp](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp)
(callbacks are `servicecallback_<name>`, lines noted below) and
[SystemService.cpp](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemService.cpp).
Every reply shape must be **measured on the reference device** before it is written
(`luna-send -n 1 palm://com.palm.applicationManager/<method> '{…}'` over `Workbench/tp.sh`);
the C++ says which keys exist, not their exact form. Unknown methods already get the right
error from `Bus`; these are the ones apps and frameworks call.

### C1. `com.palm.applicationManager`

| Method | What it does | Pointer | Who calls it | Lunacy step |
|---|---|---|---|---|
| `open` with `target` / `mime` | the resource-handler table: a URL or file is matched by scheme (redirect handlers), then by MIME (from the extension), to the app that handles it; remote files go through the download manager first; `params` become the launch parameters | [#L566](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L566); the table is `/usr/palm/command-resource-handlers.json` **on the device** (not in the repo: [Settings.cpp#L101](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L101)), parsed by [MimeSystem.cpp](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/MimeSystem.cpp), plus each app's `appinfo.json` `mimeTypes` | any app that opens a file or link; `WebosLinks.kt` already covers `http`, `mailto`, `tel`, maps and the four Palm apps | copy the device's `command-resource-handlers.json` (with a NOTICE), build the table from it and installed apps' `mimeTypes` (`AppRegistry`); route to `launch` with `{"target": …}`; fall back to `WebosLinks` for what no app handles |
| `listAllHandlersForUrl`, `getHandlerForUrl`, `getHandlerForMimeType`, `getHandlerForExtension`, `mimeTypeForExtension` | queries on the same table | [#L2463](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L2463), [#L2322](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L2322), [#L2389](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L2389), [#L2259](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L2259) | Enyo calls `listAllHandlersForUrl` in two places (`grep -rn listAllHandlersForUrl Workbench/vendor/enyo-1.0/framework/source`) | same table; measure the reply shapes |
| `running` | the running apps: `{"running":[{"id":…,"processid":…}]}` (shape from `WebAppManager`, measure it) | [#L89](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L89) | task-switcher and utility apps | from `ShellActivity.running` |
| `close` | closes by `processId` | [#L131](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L131) | apps closing themselves or a child | map the pid Lunacy already hands out (`AppWindow.pid`) back to its windows |
| `getAppInfo` | `{"appId":…,"appInfo":<the listApps entry>}` | [#L348](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L348) | | `AppInfo.listEntry()` already exists |
| `listLaunchPoints`, `launchPointChanges` (subscription) | launch points, and a subscription that fires on install/remove | [#L1338](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L1338), [#L1980](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L1980) | launcher replacements, Preware | `registry.launchPoints`; fire from `ShellActivity.install`/`remove`. Rule 2: the subscription must deliver |
| `launch` reply | `processId` is the real id string | [#L867](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/ApplicationManagerService.cpp#L867) | | Lunacy returns `"success"`; **measure** what the device returns for a fresh launch and a relaunch and match it |
| `listPackages`, `searchApps`, `getSizeOfApps`, `inspect`, `rescan`, `addLaunchPoint`, `removeLaunchPoint`, `updateLaunchPointIcon`, `install` | package management | | Preware, the App Museum's Preware path | only if an app is seen to call them; `install` already goes through the Preware launch shape |

### C2. `com.palm.systemmanager`

Nothing is registered, so every call gets "Service does not exist", which is honest. The
methods the C++ registers ([SystemService.cpp](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemService.cpp),
the `{ "name", cb… }` table near the top) and which are worth having:

| Method | What | Lunacy step |
|---|---|---|
| `getForegroundApplication` (subscription) | the maximized card's app id, updated as it changes | from `CardLayer.maximized`; fire from `onMaximized`/`onCardView` |
| `getDockModeStatus` (subscription) | whether the device is in Exhibition | from `ShellActivity.exhibitionOn`; Exhibition apps ask |
| `getLockStatus`, `getBootStatus`, `getSystemStatus` | locked/unlocked, boot finished, a summary | constants: never locked, boot `finished` |
| `systemUi` | the launcher and system-UI verbs (`launchertitlechange`, `launcheraction`, `showAppMenu`, `quicklaunch`, `virtualkeyboard`, `addcard`, `deletecard`…); the key list is in the file | only `launchertitlechange` (with B5's tab rename) is worth doing; the rest served luna-systemui |
| `subscribeToSystemUI` / `publishToSystemUI` | the channel luna-systemui listened on | not needed: Lunacy's own picker replaces luna-systemui |
| `takeScreenShot` | writes a PNG, plays `shutter` | easy (`PixelCopy` isn't on API 21; draw the root view to a bitmap); low priority |
| `getSecurityPolicy`, `getDeviceLockMode`, `setDevicePasscode`, `matchDevicePasscode` | the lock screen's | keep the honest error (fix-log: Lunacy has no lock screen, Android's is real) |
| `launchModalApp` / `dismissModalApp` | a 320×480 modal child card over the caller, with the `#0F0F0F` 60 % scrim | see D; only Palm's Accounts uses it |
| `runProgressAnimation`, `applicationHasBeenTerminated`, `setAnimationValues`, the debug ones | n/a | never |

Enyo calls `com.palm.systemmanager` in four places with the method built at runtime
(`grep -rn "com.palm.systemmanager" Workbench/vendor/enyo-1.0/framework/source`); read those
four before choosing which of the above to do first.

### C3. Services on the roadmap already, for completeness

`com.palm.power/timeout` with `activitymanager`'s scheduling (one want, `AlarmManager`),
`com.palm.audio` (volume, `lockVolumeKeys`, and `systemsounds/playFeedback`, which Enyo calls
for its click sounds - the same samples as B8), `com.palm.zeroconf`, `com.palm.accountservices`,
the media indexer's db8 kinds. All in [fix-log.md](fix-log.md) "Known gaps".

---

## D. Not deltas - so nobody chases them

- **`PalmSystem.deactivate`** (was A4). Measured 2026-09-22: a maximized card that calls it
  stays maximized and hears nothing.
- **`suppressBannerMessages`, `suppressGestures`, `rotationLockMaximized`** (were in A3).
  LunaSysMgr stores all three and nothing reads them - in LunaCE and in HP's luna-sysmgr alike:
  the banner signal has no receiver, `suppressGestures` only eats the phone's quick-launch key
  (`Key_CoreNavi_QuickLaunch`), not the tablet's swipe up, and `CardWindow::rotationLockMaximized`
  has no caller. Measured for the first: a banner added while it is set shows as usual. The
  bridge accepts all three and does nothing.
- **`PalmSystem.keepAlive`.** Only apps named in luna.conf's `appsToKeepAlive` are kept
  ([WebAppManager.cpp#L1119](https://github.com/webOSArchive/LunaCE/blob/master/Src/webbase/WebAppManager.cpp#L1119));
  an ordinary app's call is ignored. Lunacy's no-op is right.
- **`addNewContentIndicator` / `removeNewContentIndicator`.** Ends in
  `CoreNaviManager::addStandbyRequest`, the CoreNavi LED's standby pulse
  ([WebAppMgrProxy.cpp#L382](https://github.com/webOSArchive/LunaCE/blob/master/Src/remote/WebAppMgrProxy.cpp#L382));
  the TouchPad has no LED and nothing in `Src/lunaui` draws it. Enyo calls it from
  `DashboardContent`; the no-op is right.
- **`editorFocused`, `hasAlphaHole`, `printFrame`, `encrypt`/`decrypt`, `runAnimationLoop`,
  `hideSpellingWidget`, `getDeviceKeys`.** Plugins, PDK, printing, the phone's keyboard.
- **Scene and cross-app transitions** (`prepareSceneTransition`, `runSceneTransition`,
  `runCrossAppTransition`, child cards). The TouchPad's Mojo (submission 506) doesn't call
  them - `grep -c prepareSceneTransition Workbench/vendor/touchpad/mojo/mojo.js` is 0 - and
  Enyo's cross-app UI is an iframe, which works. Leave logged.
- **Modal child cards** (`launchModalApp`). Only Palm's Accounts app; when the settings apps
  reach it, the spec is `CardWebApp.cpp#L136` (320×480, no status bar) and
  [CardWindow.cpp#L1626](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1626)
  (the scrim).
- **Left/right edge app-switch gestures, mini cards, pinch zoom and spread, infinite card
  cycling, tabbed cards, the wave launcher, drag-to-open status bar menus, the status-bar
  search icon.** All LunaCE toggles that are *off* on the reference device (§0.4). Only the
  search group's 65 px of empty space (B3) survives being off.
- **Card corner radius.** The device uses the GLSL path (≈4 px at card-view scale), not the
  25 px software path; Lunacy's `CORNER` 9 px unscaled matches (§2.4, Q4).
- **The dock's `backgroundOpacity` fade, `quicklaunch-bg-solid.png`, `search-pill*.png`,
  the `[Launcher]` keys in luna.conf, `cardMinimizeDuration` and the other unread animation
  keys.** Dead code and dead config (§0.3, §3.2, §3.4, Q17).
- **The virtual keyboard** (`Src/ime`). Lunacy uses Android's IME and ships the TouchPad's
  keyboard as the separate `LunaKeyboard` APK; not a shell delta.
- **The lock screen** (`Src/lunaui/lockscreen`), **brick/USB screens, boot animation,
  first-use, emergency, Touchstone dock detection, PDK card hosting** (`CardHostWindow`).
  Android's, or later layers (architecture doc, "Later layers").
- **A card for a `noWindow` app at launch** (was B10). Measured: the reference TouchPad shows
  nothing until the headless app opens its first card, as Lunacy does.
- **Persistent dashboards** (was in B4). `DashboardWebApp::attach` honours the `persistent`
  stage argument only for `com.palm.systemui`; no app can make one.
- **Transient alerts** (was in B4). The only transient alert is LunaSysMgr's own volume display
  (`VolumeControlAlertWindow`); neither Mojo nor Enyo opens one.
- **The 300 ms rotation animation** (`rotationAnimationDuration`). Android animates its own
  rotation; matching webOS's would mean drawing the rotation by hand. Not worth it.

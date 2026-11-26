# Android TV (experimental)

Lunacy on a TV, driven by a remote. Started 2026-11-23 on a Chromecast with Google TV
(Android 14, 32-bit ARM, no touch screen; [android5-setup.md](android5-setup.md) has the
device). Merged into `main` for 1.2.0 (2026-11-26); still experimental.

webOS had no remote and no focus: every control was a finger's. So what is here has no
TouchPad to be measured against. codepoet's call (2026-11-23): the focused control is shown in
**webOS's own pressed states**, not a focus ring.

## What works

- **In the TV's launcher.** The shell is in `LEANBACK_LAUNCHER` beside `LAUNCHER`, with a
  320 x 180 banner (`drawable-xhdpi/tv_banner.png`, made by
  [make-icons.py](../AndroidLuna/tools/make-icons.py)). Touch and leanback are declared optional,
  so the same APK still installs on tablets and phones. Google TV's own home shows the round
  icon; other Android TV launchers show the banner.
- **The D-pad in a card.** Measured first: the remote's arrows reach the page as arrow
  keydowns, focus stays on the body, and OK (`DPAD_CENTER`) gives the page no event at all,
  because Enyo's and Mojo's buttons are plain `<div>`s the engine can't focus.
  [RemoteKeys.kt](../AndroidLuna/src/main/java/org/webosarchive/lunacy/card/RemoteKeys.kt)
  takes a remote's D-pad and OK in `AppWindow.dispatchKeyEvent`, and the compat layer's
  [remote.js](../AndroidLuna/src/main/assets/lunacy/remote.js) keeps one control focused:
  - **What is a control.** Enyo: a node whose control (`enyo.$[id]`, as Enyo's dispatcher
    finds it) names a click handler, or is a button, toggle, list selector or menu item, or an
    Item with its tap highlight (a settings row is an Item too, and isn't one). Mojo: anything
    with its tap highlight (`x-mojo-tap-highlight`, `x-mojo-touch-feedback`). Both: links,
    buttons, fields and `onclick`. Only what can be seen and isn't covered (a popup's scrim
    keeps the focus in the popup).
  - **How it looks.** Enyo's buttons: their down state (`setDown`). Other Enyo controls: the
    held gradient (`enyo-held`). Mojo: its `selected` class, its tapped-row look. A control on
    which that state draws nothing (Enyo's toggle and slider knob) gets Enyo's held gradient
    laid over its own image.
  - **Moving.** The nearest control in the arrow's direction. With none, the scroller under
    the focus moves on by 60% of its height, as `enyo.BasicScroller.scrollTo` animates (Mojo:
    its scroller's `scrollTo`), and the focus follows what comes into view. A focused control
    cut off at its scroller's edge is scrolled into view.
  - **OK is a finger's tap.** The page says where the focused control is, and the shell sends
    a touch down and up there, so it goes through `input.js` as any tap does. Holding OK holds
    the finger. After the tap, a control that went (a page or popup changed) hands the focus
    to what is now nearest where it was, once the frameworks' transitions have settled.
  - **Keyboards stay keyboards.** Only a non-alphabetic device (a remote, a game pad) or a key
    sent as a D-pad alone (`adb shell input dpad keyevent`) is taken; a keyboard's arrows and
    Enter reach the page as before.
- **The launcher.** While it is open the shell takes the remote's keys first
  (`ShellActivity.dispatchKeyEvent`, `ShellChrome.remoteKey`, `Launcher.remote`). The focused
  icon or group wears the touch feedback a finger leaves on it (launcher-touch-feedback.png);
  the arrows move through the page's grid, past a row's end onto the next tab's page (as a
  swipe), and up from the top row onto the tab bar, where the focused tab is drawn as a finger
  on it (tab-highlight.png) and left and right change tabs as a tap does. OK launches, as a
  tap; a group opens. A finger clears the focus. The remote's Back is the TouchPad's home
  button as before: from a card to the card view, then the launcher.
- **The card view.** What the remote picks is webOS's own current card, the one left
  undimmed (`CardLayer.remote`): left and right move through a stack's cards and on to the
  next group, which slides to the middle as after a swipe; OK maximizes, as a tap; up throws
  the card away, the gesture's own direction. Down goes to the dock.
- **The dock**, under the card view and the launcher (`QuickLaunch.remote`): the focused icon
  wears the launch glow, the launcher button its own pressed state; left and right move along
  it, OK launches (or opens the launcher) as a tap, and up goes back to the card view or to the
  launcher icon the focus came down from. Down from the launcher's last row comes here.
  `ShellChrome.remoteKey` keeps which of them has the remote.
- **PDK.** OK is SDL's Return, beside the arrows the D-pad already sent.

Checked on the Chromecast (build 809): the card view with Clock and Calculator (left and right,
Calculator thrown away with up, Clock maximized with OK, its own controls then taking the
keys), the dock from the card view (along to the launcher button) and from the launcher's last
row and back up. Before that (build 808): the launcher's icons, its tabs, past a row's end onto the
next tab, and Clock launched with OK. Before that (build 807): First Use from start to end with the remote alone
(Next, Android's own permission dialogs, Skip), Sounds & Alerts (Enyo: switches, the volume
knob, a button) and Location Services (Mojo: rows, a list selector's popup and back).

## Only on a TV

Every remote control above is taken only where Android reports its leanback feature
(`RemoteKeys.tv`; `RemoteKeysTest`). A phone or tablet with a keyboard, a D-pad or a game pad
keeps its keys exactly as before (codepoet, 2026-11-23): checked on the Fold3, where a D-pad's
keys still reach the page as an arrow and Enter and nothing is focused.

**Round for the merge (build 811, 2026-11-23;
[test-results/811](test-results/811)):** `test.sh` 13 passed, 1 skipped; device-test on the
Chromecast 48/0/0 and on the Fold3 (over 791, data kept) 55/0/0; a fresh install on the
Chromecast 48/0/0; the Fold3 folded and unfolded twice by codepoet (one process throughout,
cards kept, db8 answering). Not run: the HP 10 G2 (Android 5, Chromium 37), Nexus 5, Nexus 7
and A7 Lite were off the network; codepoet will test them another time.

**1.2.0 (build 823, 2026-11-26):** the Android apps' list is read off the main thread, which
was an ANR as the shell started on slow devices, the Chromecast among them. Smoke-tested on the
Chromecast: Lunacy started with no ANR, Back opened the launcher and the D-pad and OK opened
Calculator. To drive it from adb, send `input dpad keyevent ...`: a plain `input keyevent`
comes from a virtual keyboard, whose keys a TV leaves alone (49d7e3e).

**Play Protect on the TV.** Reinstalling after an uninstall, Play Protect blocked Lunacy as
"built for an older version of Android" (targetSdk 28, as on main); the first install that day
had passed. **Install anyway** let it through. Anyone sideloading onto a TV may see this.

## Not yet

- **The rest of the shell**: a launcher group's panel, the status bar menus, Just Type,
  dashboards, and arranging the launcher, the dock or the cards (reordering, stacking).
- **A long launcher page**: scrolling to a row out of view is written, but no tab on the TV
  has enough icons to need it.
- **Back to the opener**: after a popup closes, focus goes to the nearest control, not to the
  row that opened it.
- **Sliders**: the knob takes focus, but left and right don't move it.
- **Enyo lists that render lazily**: rows are reached by scrolling on, untested on a long list.
- A pointer mode (D-pad moves a cursor) as a fallback for anything focus can't reach.

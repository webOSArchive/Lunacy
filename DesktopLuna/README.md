# Luna DE (DesktopLuna)

**Luna Desktop Edition:** Lunacy on a Windows, macOS or Linux desktop, for webOS app
developers. It runs Lunacy's own code (LunaCore, shared with the Android shell) in a JVM host,
with an Electron window as each app's card. The plan, its goals and what is left are in
[Docs/luna-de.md](../Docs/luna-de.md).

## Run it (first light, Linux)

```sh
./gradlew :DesktopLuna:host:installDist      # the JVM host, from the top of the repository
DesktopLuna/fetch-node.sh                    # once: Node 12 for webOS JS services, into DesktopLuna/node/
cd DesktopLuna && npm install && npm start
```

It needs `AndroidLuna/local-assets` (`AndroidLuna/fetch-assets.sh`), which holds the frameworks
and the webOS root, as an Android build does.

One window is the device: a TouchPad's screen (1024 x 768) running the shell, with a bezel
below it holding the home button. The launcher button (bottom right) opens the launcher; an app
comes up as a card. In the card view a tap brings a card up, a flick up closes it, a sideways
drag or the wheel slides between them, and a right-click offers Luna DE's tools: **pop out** (the
card's live page moves into a window of its own, beside the device), DevTools, reload, close. A
tap on the status bar's title opens the app's menu; banners and dashboards show at its right.

| Key | What it does |
|---|---|
| End | Opens and closes the launcher |
| Home | The card down to the card view, and up again (Alt+Home and Alt+End stay a text field's) |
| Left, Right, Enter | In the card view: the next card, and bring it up |
| Esc | The back gesture |
| F6, F7, F8, F9 | Turn the device up, down, right, left (portrait is 768 x 1024) |
| The bezel's right corner | Turn the device between wide and tall |
| The bezel's keyboard button | A real keyboard attached (lit) or not: without one, webOS's on-screen keyboard comes up for a text field and the app is resized and told, as on a device |
| The bezel's left corner | Pop the card out into a window of its own (also in a card's right-click menu, in the card view) |
| F12 | DevTools for whatever has the keyboard |
| Ctrl/Cmd+R | Reload the card |
| F1 | A spare home button |

The first six are the old SDK emulator's keys.

**A popped-out card** starts at the device's size and the way it is held, and is then a window
like any other: resize it freely and the page follows, hearing each change as a resize and a
change between wide and tall as the device turning.

**Installing an app:** drop its `.ipk`, or its folder (the one holding `appinfo.json`), on the
window; or press Ctrl/Cmd+O for a package, Ctrl/Cmd+Shift+O for a folder; or start with
`LUNADE_INSTALL=<path>`. It appears on the launcher's Downloads tab. Drop a folder again after
changing it; right-click an installed app's tile to remove it.

**Wi-Fi** in the launcher's Settings is a developer's switch: it simulates the network connection
being lost (apps are told, and their requests fail) until it is tapped again.

- `LUNADE_DATA=<folder>`: where Luna DE keeps its data (default: Electron's userData/data).
  Installed apps go in `<folder>/cryptofs/apps/usr/palm/applications/<id>/` until `palm-install`
  works (milestone 5).
- `LUNADE_OPEN=<app id>`: open that app at start.

The first test app, the Enyo 1 Sampler: `DesktopLuna/install-sampler.sh <data folder>` (it fetches
webOS Archive's copy and points it at the system's Enyo), then
`LUNADE_DATA=<data folder> LUNADE_OPEN=com.palmdts.enyo.stylematters npm start`.

`npm start` passes `--no-sandbox`: on this Linux setup Chromium's setuid sandbox helper would
need root. **Ratchet item** for packaging (Docs/luna-de.md): the packaged app runs with the
sandbox.

## What is here

| Path | What it is |
|---|---|
| `host/` | The JVM host (Kotlin): LunaCore's AppServer, bus, db8 and services; the page's native interface (`DesktopWindow`); the shell as the services see it (`DesktopShell`); the desktop's platform (`DesktopPlatform`: scheduler, images, collation, links, network, timers), over HTTP on the loopback interface. |
| `app/main.js` | Electron's main process: starts the host, routes the app origins (`*.media.cryptofs.apps`, and only those) to it, follows the host's shell events (an app to start, a card to bring forward or close, a banner), and keeps the cards, dashboards and alerts: each a native view over the shell, so that an app's `window.open` (a headless app opening its cards) makes a real window with its opener. |
| `app/shell.html`, `shell.js`, `shell.css` | The shell, drawn from AndroidLuna's LunaCE images and measurements: status bar, launcher, quick-launch bar, card view (cards shown as snapshots of their pages), the card menu. |
| `app/app-preload.js` | `window.LunacyNative` in an app's page: synchronous requests to the page's own origin, and a long poll for the bus's replies. |

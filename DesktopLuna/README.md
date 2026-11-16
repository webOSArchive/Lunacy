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
| The device's picture, beside it | An HP TouchPad or an HP Pre3 (480 x 800, webOS 2.2.4); changing it closes the open apps |
| The bezel's magnifying glass (as a Pre3) | Phone Zoom for the app whose card is up: laid out 640 px wide and shown whole, for an app written for a TouchPad |
| The bezel's `</>` button | The log window: every app's console in one place, from when Luna DE started. Right-click it to inspect a card with Electron's developer tools (F12 does the same) |
| The bezel's keyboard button | A real keyboard attached (lit) or not: without one, webOS's on-screen keyboard comes up for a text field and the app is resized and told, as on a device. Right-click it for the on-screen keyboard's size (XS, S, M, L) |
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
`LUNA_DE_INSTALL=<path>`. It appears on the launcher's Downloads tab. Drop a folder again after
changing it; right-click an installed app's tile to remove it.

**Wi-Fi** in the launcher's Settings is a developer's switch: it simulates the network connection
being lost (apps are told, and their requests fail) until it is tapped again.

- `LUNA_DE_DATA=<folder>`: where Luna DE keeps its data (default: Electron's userData/data).
  Installed apps go in `<folder>/cryptofs/apps/usr/palm/applications/<id>/` until `palm-install`
  works (milestone 5).
- `LUNA_DE_OPEN=<app id>`: open that app at start.

The first test app, the Enyo 1 Sampler: `DesktopLuna/install-sampler.sh <data folder>` (it fetches
webOS Archive's copy and points it at the system's Enyo), then
`LUNA_DE_DATA=<data folder> LUNA_DE_OPEN=com.palmdts.enyo.stylematters npm start`.

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

## Packaging

One command builds the distributable for the machine it runs on, with nothing copied by hand:

```
cd DesktopLuna
npm install
npm run dist        # output in DesktopLuna/dist/
```

It needs a JDK 17 with `JAVA_HOME` set to it (17 to 22 will do; on a Mac `brew install --cask temurin@17`), Node, and `AndroidLuna/local-assets`: the webOS frameworks
and system files with Lunacy's patches applied. Those are HP's and Palm's, not Lunacy's to keep
in its repository, so a builder takes them from a TouchPad of their own, once:

```
AndroidLuna/get-assets-from-touchpad.sh    # a TouchPad running webOS CE 3.1.0, on the cable (novacom)
AndroidLuna/fetch-assets.sh --desktop      # applies Lunacy's patches: Luna DE's part of local-assets
```

The first reads the device (and the webOS CE project's public repository for CE's own files)
into `Workbench/vendor/`; the second needs GNU coreutils and GNU patch, which a Mac gets with
`brew install coreutils gpatch`. Run the second again whenever `LunaRuntimes/` changes; the
build refuses assets older than the checkout's patches. (Lunacy's development machine runs
`fetch-assets.sh` without `--desktop`, which makes the Android build's native parts too.)

`package.js` builds the
host, fetches Node 12 for the machine (checked against nodejs.org's checksum), cuts a Java
runtime with `jlink` so users need no Java, and hands electron-builder the app with Lunacy's
assets at the places they have in a checkout. A package is for the system and processor it was
built on.

| Built on | Output |
|---|---|
| Linux | `luna-de-<version>-linux-x86_64.AppImage` and a `.tar.gz` |
| macOS | a `.dmg` and a `.zip`; signed with the Developer ID in the keychain (or `CSC_LINK` and `CSC_KEY_PASSWORD`), and notarised when `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD` and `APPLE_TEAM_ID` are set |
| Windows | an installer and a `.zip`; signed when `CSC_LINK` and `CSC_KEY_PASSWORD` name a certificate |

**Windows from Linux or a Mac.** `npm run dist -- --win` makes the Windows package without a
Windows machine: `luna-de-<version>-win-x64.zip`, the whole app in a folder (unzip it and run
`Luna DE.exe`). Nothing is compiled for Windows; its native parts are fetched (`node.exe`, and
the Windows modules of this machine's JDK version, from Adoptium, both checked against published
checksums) or come out of a jar. With Wine installed it makes the installer too. It is unsigned:
Windows SmartScreen will warn until it is signed.

Only the Linux package has been run (2026-11-16: the AppImage on a fresh home folder, with no
Java or Node of the machine's, opened Calendar with its JS services). The Windows zip has been
built, from Linux, and never started: no Windows machine has run Luna DE yet. The Mac build is
written and not yet tried. See "Packaging" in [Docs/luna-de.md](../Docs/luna-de.md) for what to
expect on each.
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
| The device's picture, beside it | The screen mode, one of four: **HP TouchPad** (1024 x 768) and **HP Pre3** (480 x 800, webOS 2.2.4), each in a window of its size; **Resizable**, a TouchPad the size of the window, sized by hand; and **Full Screen**, a TouchPad the size of the display. A click goes to the next; changing between devices closes the open apps. Luna DE starts in the mode it was last |
| F11, or the bezel's right-click menu | F11 goes into Full Screen and back out to the mode before it; the menu lists all four |
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

**Arranging the launcher:** hold an icon to begin (then drag any): onto another cell, onto the
middle of another icon to make a group, onto another tab, or onto the quick-launch bar. Hold a
tab (or right-click it) to rename or delete it; hold the tab bar's empty space (or right-click
it) for "+", a new tab. Hold an icon on the bar to move it or drag it off. Done, Esc or the
home button ends it.

**Just Type:** click the pill in the card view, or just type there. An installed app's
`universalSearch` entries in its appinfo.json (a search, a quick action, a content search over
its db8 kind) show as they did on a device, and choosing one launches the app with the params
a device gave it; the bundled Just Type settings app turns each on and off.

**Installing an app:** drop its `.ipk`, or its folder (the one holding `appinfo.json`), on the
window; or press Ctrl/Cmd+O for a package, Ctrl/Cmd+Shift+O for a folder; or start with
`LUNA_DE_INSTALL=<path>`. It appears on the launcher's Downloads tab. Drop a folder again after
changing it; right-click an installed app's tile to remove it.

**The SDK's tools** (webos-sdk-redux's novacomd, from the version after 0.4.1, finds a running
Luna DE on the same machine): `novacom -l` lists it as `local luna-de`, and `palm-install`,
`palm-launch` and `palm-log` work against `-d luna-de`. Luna DE runs no program of the host's for
them, only its own few commands inside its webOS root. It is an app environment, not an operating
system: there is no `novaterm`, and packages' scripts don't run. See "The SDK's tools" in [Docs/luna-de.md](../Docs/luna-de.md).

**Wi-Fi** in the launcher's Settings is a developer's switch: it simulates the network connection
being lost (apps are told, and their requests fail) until it is tapped again.

- `LUNA_DE_DATA=<folder>`: where Luna DE keeps its data (default: Electron's userData/data).
  Installed apps go in `<folder>/cryptofs/apps/usr/palm/applications/<id>/`, and its nduid
  (how the SDK's tools tell copies apart) is `<folder>/nduid`.
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
| `host/` | The JVM host (Kotlin): LunaCore's AppServer, bus, db8 and services; the page's native interface (`DesktopWindow`); the shell as the services and LunaCore's AppLifecycle see it (`DesktopShell`, whose windows are `DesktopView`s: which apps run, with which windows, and where each goes is decided here, by the class Android's shell runs); the desktop's platform (`DesktopPlatform`: scheduler, images, collation, links, network, timers), over HTTP on the loopback interface. |
| `app/main.js` | Electron's main process: starts the host, routes the app origins (`*.media.cryptofs.apps`, and only those) to it, follows the host's shell events (a window to show as a card or a dashboard, to keep unseen, to load or to close, a card to bring forward, a banner), and keeps the views: each a native view over the shell, so that an app's `window.open` (a headless app opening its cards) makes a real window with its opener. It tells the host of each window a page opens and each view that goes, and decides nothing of the apps' lifecycle itself. |
| `app/shell.html`, `shell.js`, `shell.css` | The shell, drawn from AndroidLuna's LunaCE images and measurements: status bar, launcher, quick-launch bar, card view (cards shown as snapshots of their pages), the card menu. |
| `app/app-preload.js` | `window.LunacyNative` in an app's page: synchronous requests to the page's own origin, and a long poll for the bus's replies. |

## Packaging

One command builds the distributables, with nothing copied by hand: a Mac's for both processors,
Linux's for its own, and Windows' from either with `--win`:

```
cd DesktopLuna
npm install
npm run dist        # output in out/, at the top of the repository
```

It needs a JDK 17 installed (17 to 22 will do; it needn't be the machine's default Java, the build finds it, and `LUNA_DE_JDK` names one outright), Node, and `AndroidLuna/local-assets`: the webOS frameworks
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
host, fetches Node 12 (checked against nodejs.org's checksum), cuts a Java runtime with `jlink`
so users need no Java, and hands electron-builder the app with Lunacy's assets as one zip the host reads in place
(`assets.zip`; [Docs/luna-de.md](../Docs/luna-de.md), "Packaging"). Another system's or processor's Java runtime is cut from Eclipse Temurin's
modules for it (from Adoptium, checked against its checksum), so one machine builds several.

| Command | Built on | Output |
|---|---|---|
| `npm run dist` | macOS | `luna-de-<version>-mac-arm64.dmg` and `-mac-x64.dmg` (and a `.zip` of each), signed and notarised (below) |
| `npm run dist` | Linux | `luna-de-<version>-linux-x86_64.AppImage` and a `.tar.gz`, for the machine's processor |
| `npm run dist -- --win` | Linux or macOS | `luna-de-<version>-win-x64.exe`, the installer (for the user installing, no administrator needed; the folder may be chosen; Start menu and desktop shortcuts; Luna DE's data kept on uninstall); `-win-x64-portable.exe`, the app in one program that runs without installing; and `-win-x64.zip`, the whole app in a folder (unzip it and run `Luna DE.exe`). On Linux the installer needs Wine; a Mac needs none. Signed when a key is given (below) |

`--arch x64,arm64` names the processors outright (a Linux arm64 package, say). `--release`
refuses to finish a package that isn't signed (and, on a Mac, notarised): use it for anything
handed to people. Without it an unsigned package is built, and the build says what it lacks.
After building, every signature in the package is read back, and a gap fails the build.

An Apple Silicon Mac's package is native, except the JS services' Node: Node 12 was never built for
Apple Silicon, so it runs under Rosetta (open question 6 in [Docs/luna-de.md](../Docs/luna-de.md)).

### Signing and notarising for macOS

Needed once: a **Developer ID Application** certificate in the keychain (not "Mac Installer
Distribution"): Xcode → Settings → Accounts → your Apple account → Manage Certificates → `+` →
Developer ID Application. With it, everything in the app is signed under the hardened runtime: the
app, the Java runtime, Node and SQLite's library.

Notarising (so a downloaded copy opens without Gatekeeper's block, or Apple Silicon's "is
damaged"):

1. Make an app-specific password at [appleid.apple.com](https://appleid.apple.com) → Sign-In and
   Security → App-Specific Passwords.
2. Find the team ID at [developer.apple.com/account](https://developer.apple.com/account), top right
   (10 characters); it is the Developer ID certificate's team.
3. `cp signing/set-apple-vars.example signing/set-apple-vars.sh` (git-ignored) and fill in
   `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD` and `APPLE_TEAM_ID`. The environment's own values win
   over the file.
4. `npm run dist -- --release`. Each app is sent to Apple's notary service after it is signed (a
   few minutes each), its ticket is stapled to it, and then the dmg and zip are made of it. The
   build ends by checking each app as a downloader's Mac would: `codesign --verify --deep
   --strict`, every program and library signed by the app's team with the hardened runtime and a
   timestamp, the ticket stapled, and `spctl` accepting it as notarised.

Without the certificate the package is unsigned; without the three variables it is signed and not
notarised. The build says which, before it starts.

### Signing for Windows

Windows signing runs on the machine that builds the package (Linux or a Mac), with
[jsign](https://github.com/ebourg/jsign), which the build fetches (a pinned version, checked against
its checksum). Give it a key in `signing/set-windows-vars.sh` (git-ignored; copy
`signing/set-windows-vars.example`) or the environment, one of:

- **A code-signing certificate file:** `LUNA_DE_WIN_CERT` (a `.pfx` or `.p12`),
  `LUNA_DE_WIN_CERT_PASSWORD`, and optionally `LUNA_DE_WIN_TSA` for another timestamp server than
  DigiCert's.
- **Azure Trusted Signing:** `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`,
  `AZURE_TRUSTED_SIGNING_ENDPOINT`, `AZURE_TRUSTED_SIGNING_ACCOUNT` and
  `AZURE_TRUSTED_SIGNING_PROFILE`. The app registration needs the *Trusted Signing Certificate
  Profile Signer* role on the signing account. Only some of the six set stops the build.

Signing is deep. Windows 11's Smart App Control checks every program and library an app loads, and
electron-builder on its own signs only the app's exe. So, before anything is zipped or packed into
the installer, every `.exe` and `.dll` in the app is signed: the Java runtime's, `node.exe` and
SQLite's DLL among them. A file that already carries a valid, timestamped signature keeps its
maker's (Microsoft's, Eclipse's for the Java runtime, the OpenJS Foundation's for Node); the rest
get Luna DE's. The installer and uninstaller are signed too. Then every signature is read back:
each must be intact, verify and be timestamped, and the app's exe and the installer must carry
Luna DE's signer. Whether Windows trusts the certificate's issuer is Windows' to say; the build
checks everything short of that.

A signature names the publisher, but SmartScreen's warning only fades as a signing identity earns
reputation through downloads; a new identity's releases still warn for a while.

Only the Linux package has been run (2026-11-16: the AppImage on a fresh home folder, with no
Java or Node of the machine's, opened Calendar with its JS services). The Windows zip has been
built, from Linux and a Mac, and never started: no Windows machine has run Luna DE yet (its deep
signing is checked, with a throwaway certificate). The Mac packages, Apple Silicon's and Intel's,
are built, signed and notarised, and Gatekeeper accepts both; neither has yet been downloaded onto
another Mac and run. See "Packaging" in [Docs/luna-de.md](../Docs/luna-de.md) for what to
expect on each.
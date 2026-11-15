# Luna DE (DesktopLuna)

**Luna Desktop Edition:** Lunacy on a Windows, macOS or Linux desktop, for webOS app
developers. It runs Lunacy's own code (LunaCore, shared with the Android shell) in a JVM host,
with an Electron window as each app's card. The plan, its goals and what is left are in
[Docs/luna-de.md](../Docs/luna-de.md).

## Run it (first light, Linux)

```sh
./gradlew :DesktopLuna:host:installDist      # the JVM host, from the top of the repository
cd DesktopLuna && npm install && npm start
```

It needs `AndroidLuna/local-assets` (`AndroidLuna/fetch-assets.sh`), which holds the frameworks
and the webOS root, as an Android build does. The launcher lists the bundled apps and the ones
installed in Luna DE's data folder; click one to open it in a TouchPad-sized window (1024 x
768). F12 opens that window's DevTools, F5 reloads it.

- `LUNADE_DATA=<folder>`: where Luna DE keeps its data (default: Electron's userData/data).
  Installed apps go in `<folder>/cryptofs/apps/usr/palm/applications/<id>/` until `palm-install`
  works (milestone 5).
- `LUNADE_OPEN=<app id>`: open that app at start.

`npm start` passes `--no-sandbox`: on this Linux setup Chromium's setuid sandbox helper would
need root. **Ratchet item** for packaging (Docs/luna-de.md): the packaged app runs with the
sandbox.

## What is here

| Path | What it is |
|---|---|
| `host/` | The JVM host (Kotlin): LunaCore's AppServer and bus, the page's native interface (`DesktopWindow`), the desktop's `Scheduler` and `Images`, over HTTP on the loopback interface. |
| `app/main.js` | Electron's main process: starts the host, routes the app origins (`*.media.cryptofs.apps`, and only those) to it, opens the launcher and the app windows. |
| `app/app-preload.js` | `window.LunacyNative` in an app window: synchronous requests to the page's own origin, and a long poll for the bus's replies. |
| `app/launcher.html` | Luna DE's launcher, its own and not the TouchPad's (rule 0 exemption, codepoet 2026-11-15). |

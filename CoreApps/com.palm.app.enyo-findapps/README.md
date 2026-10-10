# webOS App Catalog

The webOS Archive's restoration of the TouchPad App Catalog client (`com.palm.app.enyo-findapps`) — HP's original Enyo source, patched and maintained independently of HP's dead backend.

## Magazine content (Pivot)

The magazine feature — branded **Pivot** — ships a tiny placeholder edition baked directly into this repo at `main/source/magazine/defaultEdition/{lang}/` (a single page: spinner + progress bar + "Fetching your first issue of Pivot..." + a "View Recent Apps" escape hatch, a few KB, no submodule involved). The real edition (currently ~4MB/88 files for `en`; other locales are placeholder-only for now) is too large to preload onto a system image, so it's hydrated from `https://appcatalog.webosarchive.org/pivot/{lang}/` after install instead, cached on-device at `/media/internal/.pivot`.

`main/source/pivot-hydration.js` (loaded after `build.js`, alongside `archive-patch.js`) does the work in two independent halves:
- **Write path:** 20s after every launch (tuned empirically — starting this earlier visibly stalled the app's own splash/launch rendering, since native download-manager bridge calls and UI paint share one JS thread), best-effort checks the server's `version.json` against whatever `magazineVersion` is (or isn't — e.g. the folder was deleted) cached on disk, and if the server is ahead, downloads the full edition into `/media/internal/.pivot/{lang}/` via `palm://com.palm.downloadmanager/download`, one file per second, finishing with the manifest itself — its presence on disk doubles as the completion marker. Progress is reported into the placeholder's `enyo.ProgressBar` via direct DOM writes (not Enyo's component API — see the comment on `updateProgress()`), blending byte-weighted and file-count-weighted progress so the bar doesn't sit near 0% through the ~40 tiny JSON files that precede the edition's few large images.
- **Read path:** the magazine engine's existing default-edition fallback now tries the hydrated cache first, and only falls back to the bundled placeholder if it's missing or stale-format. A 15s recheck loop (and an immediate check on tab-return) picks up hydration finishing while the placeholder is already on screen, so the magazine swaps over live without needing an app restart.

Two things in `pivot-hydration.js` look removable but aren't:
- The `?_=Date.now()` cache-busting suffix on the hydrated-cache manifest fetch. Without it, WebKit's XHR cache can keep serving the *first* (necessarily-failed, since hydration hasn't run yet) response to every later poll of the same URL, even long after the real file exists on disk — the magazine then never recovers without a full app restart.
- `markHydrating()`'s heartbeat call after every file (not just once at the start). A one-shot lock stays "fresh" for its whole window regardless of whether the app is still running, so killing the app mid-download used to leave the next launch seeing a stale-but-unexpired lock and silently skipping hydration entirely, with no retry until a *third* launch.

The magazine **engine** (`Magazine`, `MagazinePage`, `BindableLayout`, etc., under `main/source/magazine/app/` and `main/source/magazine/services/`) needed zero changes for any of this — every file reference resolves through a `physicalPath` string taken verbatim from whichever manifest was loaded, with no path-joining logic anywhere, so pointing it at `/media/internal/.pivot/...` instead of `source/magazine/defaultEdition/...` is purely a manifest-URL choice.

Full editions are authored independently in [webOSArchive/PivotMagazine-WOSA](https://github.com/webOSArchive/PivotMagazine-WOSA) (no longer a submodule of this repo, sibling directory to this one) and published as static files to `catalog-service`'s `pivot/{lang}/` via `PivotMagazine-WOSA/Tools/gen-device-manifest.py` — see that repo's `CLAUDE.md` for the full publish workflow, including a couple of non-obvious gotchas (the generator never deletes stale files, and `--version` must be bumped every time or devices silently never see the update).

## Installing apps (6.2)

HP's catalog installed through `com.palm.appInstallService` and followed each install through its `status` subscription, which drives the catalog's own progress pill ("Downloading", "Installing", "Launch"). That service still exists on webOS but checks every package against HP's signing servers, gone since 2015, so every install there ends `install failed` / `FAILED_VERIFY`. From 6.2 the catalog picks an install path by where it is running (`main/source/archive-install.js`):

- **Lunacy** (detected by its `org.webosarchive.lunacy` service being on the bus): Lunacy's `com.palm.appInstallService` installs packages as Preware did, install scripts included, so the catalog uses HP's install path unchanged and gets the full progress UI.
- **webOS**: the catalog installs the package itself (`main/source/direct-install.js`). The download manager fetches it; `ipk-inspect.js` reads its `control.tar.gz`; a package with no install scripts goes to the stock installer, `com.palm.appinstaller/installNoVerify` (a `com.palm.*` app may call it), and its progress is reported to the catalog exactly as the appInstallService's would be. The stock installer runs no `preinst`/`postinst`/`prerm`/`postrm` - only Preware's root helper did - so a package that has any (38 of the archive's 4,300+, mostly webOS Archive's own) is handed to the `.ipk` handler instead. The catalog never registers as an `.ipk` handler and doesn't get in Preware's way.
- **Anywhere else** (LuneOS), and for packages with scripts: the **`.ipk` handler**. The catalog asks `listAllHandlersForMime` for `application/vnd.webos.ipk`, opens the active handler, then the alternates, then the original Preware by id, each with `{type: "install", file, target}`, until one opens. On LuneOS it launches through SAM (`com.webos.service.applicationmanager/launch`), because LunaAppManager answers `"<id>" was not found` even for an app it launched. From a patch by a Preware 2 developer.

Everything the device does here was measured on a webOS CE 3.1.0 TouchPad: the appInstallService's parameters and states, `installNoVerify`'s replies to a `com.palm.*` app, and the handler list. `ipk-inspect.js` carries a small inflate (webOS 3's browser has none); its script detection was checked against every package in the archive that has scripts, and its inflate against zlib.

Two catalog quirks the install path needed:

- The appInstallService refuses an install with any empty string or without an `authToken` ("Bad parameter"); the catalog's session comes from `DummyConfig`, so its values fill in.
- A list row is found by its app's id to be redrawn, and fetching an app's details changes that id from the catalog's number to the package id, so a row stayed at "Downloading..." after the app had installed. Rows are now also matched by their `AppDownload`.

## Removed (6.2.2925)

- **Google Analytics.** HP's catalog shipped Google's `ga.js` and tracked launches, scenes and taps against a property ID it fetched from HP's own servers. That lookup now always fails, and it was retried every time the catalog came to the front. The library, the tracker, the lookup (`getGoogleAnalyticsWebPropertyID`) and every tracking call are gone, from `main/build.js` (the bundle the app runs) and from the sources in `main/lib` and `main/source` alike.
- **The carrier data connection.** On a device reporting AT&T's network codes (the catalog's own dummy session does), it asked the connection manager for a cellular "proxy" connection, used only by HP's payment server; on every device today that fails ("APN Not found in CarrierDB"). `getDataService` and its callbacks are gone the same way.

## Self-update

The app checks `http://appcatalog.webosarchive.org/appcatalog-touchpad.json` (a static manifest at the domain root, served over plain HTTP so it works on a freshly-Doctored device before Preware/the community OTA are installed) a few seconds after launch, compares `version` against its own `appinfo.json` version, and prompts to install through the `.ipk` handler (above) if the manifest is newer - not directly, so the catalog never replaces itself while it runs. See `main/source/archive-patch.js`.

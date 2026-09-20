# Related projects

A map of the projects around legacy Palm/HP webOS preservation and its successors, grouped
by the layer they work at. Most live under the
[webOSArchive](https://github.com/webOSArchive) and [codepoet80](https://github.com/codepoet80)
GitHub accounts; the public front door is [webosarchive.org](https://www.webosarchive.org).

## Background in one paragraph

webOS (Palm, 2009; HP, 2010–2011) was a Linux phone/tablet OS whose apps were HTML/JS
(the Mojo and Enyo frameworks) hosted by the system UI process (LunaSysMgr) and talking to
system services over the JSON "Luna" service bus (`palm://` URIs), with a native C/C++ escape
hatch (the PDK: SDL 1.2 + OpenGL ES). HP ended the hardware in 2011, open-sourced much of the
stack as Open webOS (2012), and sold it to LG (2013), where it became the TV OS and later
webOS OSE. The devices kept working; the cloud services behind them did not. Everything below
is about keeping those devices useful, preserving their software, or carrying the platform's
ideas forward.

## Knowledge and archives

| Project | What it is |
|---|---|
| [webos-mcp](https://github.com/webOSArchive/webos-mcp) | MCP server giving Claude persistent knowledge of legacy webOS development (2009–2012 webOS, not LG TV). |
| [webos-docs](https://github.com/webOSArchive/webos-docs) | Activation, reset and usage guides for legacy devices — [docs.webosarchive.org](https://docs.webosarchive.org). |
| [webos-sdk-archive](https://github.com/webOSArchive/webos-sdk-archive) | Restored clone of Palm's SDK/PDK download and developer pages — [sdk.webosarchive.org](https://sdk.webosarchive.org). |
| [webos-sdk-redux](https://github.com/webOSArchive/webos-sdk-redux) | The Palm SDK tooling (`palm-package`, `palm-install`, novacom) kept installable. |
| [help.palm.com](https://github.com/webOSArchive/help.palm.com) | Restored on-device Help content (TouchPad: EN/FR/DE/IT). |
| [pivotce.com](https://github.com/webOSArchive/pivotce.com) | pivotCE news archive (2013–2024), rescued — [webosarchive.org/pivot](https://www.webosarchive.org/pivot/). |
| [webosarchive-dot-org](https://github.com/webOSArchive/webosarchive-dot-org) | The webosarchive.org site itself. |

## Restored cloud services

The devices depend on servers that no longer exist. These replace them.

| Project | Replaces / provides |
|---|---|
| [webos-catalog-backend](https://github.com/webOSArchive/webos-catalog-backend), [webos-catalog-service](https://github.com/webOSArchive/webos-catalog-service), [webos-catalog-metadata](https://github.com/webOSArchive/webos-catalog-metadata) | The App Catalog → the **App Museum** ([appcatalog.webosarchive.org](https://appcatalog.webosarchive.org)), usable on-device and in a browser. |
| [webos-appscanner](https://github.com/webOSArchive/webos-appscanner) | App Museum support tooling. |
| [webos-community-account](https://github.com/webOSArchive/webos-community-account) | On-device pieces linking a TouchPad to an App Museum account (developer management, reviews). |
| [webos-update-exploration](https://github.com/codepoet80/webos-update-exploration) | Local OMA DM server replacing Palm's update servers (`ps.palmws.com`). |
| [woce-backup](https://github.com/codepoet80/woce-backup) | Backup & Restore rebuilt to store on-device instead of Palm's servers. |
| [app-services](https://github.com/codepoet80/app-services) | Account templates and the accounts service. |
| [oauth-broker-for-webos](https://github.com/webOSArchive/oauth-broker-for-webos) | Lets legacy apps sign in to modern OAuth services. |
| [podcast-service](https://github.com/webOSArchive/podcast-service) | Podcast directory + HTTPS-downgrading proxy (PodcastIndex) — [podcasts.webosarchive.org](https://podcasts.webosarchive.org). |
| [accuweatherxml-proxy](https://github.com/webOSArchive/accuweatherxml-proxy) | Emulates AccuWeather's retired XML API over the new JSON API. |
| [sharing-service](https://github.com/webOSArchive/sharing-service) | Backend for the Share Space app. |
| [squid-sslbump-for-webos](https://github.com/webOSArchive/squid-sslbump-for-webos), [squid-for-webos](https://github.com/webOSArchive/squid-for-webos) | TLS-bridging proxies for devices that can't negotiate modern HTTPS. |

## Modernizing the device (without reflashing)

| Project | What it does |
|---|---|
| [preware](https://github.com/webOSArchive/preware) | The homebrew package manager. |
| [preware-modernize-feed](https://github.com/webOSArchive/preware-modernize-feed) | Preware feed bringing TLS 1.2/1.3, current root certs and a working clock to browser, mail, curl and apps. |
| [OpenSSL-legacyWebOS](https://github.com/webOSArchive/OpenSSL-legacyWebOS) | Modern OpenSSL built for the legacy toolchain. |
| [webos-vpn-profiles](https://github.com/webOSArchive/webos-vpn-profiles) | OpenVPN as a native agent plugin in the built-in Settings → VPN. |
| [webos-touchpad-accessories](https://github.com/webOSArchive/webos-touchpad-accessories) | Making modern USB/Bluetooth accessories work on a stock TouchPad. |
| [acl-manager](https://github.com/webOSArchive/acl-manager) | Suspend/resume the Android Compatibility Layer when it conflicts with native apps. |
| [LuneCast](https://github.com/webOSArchive/LuneCast) | Share the TouchPad screen to a computer over USB. |

## Rebuilding the OS

| Project | What it is |
|---|---|
| [LunaCE](https://github.com/webOSArchive/LunaCE) | Community-maintained **LunaSysMgr** — the process that *is* the webOS UI (cards, launcher, status bar, keyboard). Builds on [Herrie82/LunaSysMgr](https://github.com/Herrie82/LunaSysMgr). |
| [webOS-Community-Edition](https://github.com/webOSArchive/webOS-Community-Edition) | webOS CE 3.1.0 — a doctor (full restore image) that rolls the preservation work into a TouchPad release after 3.0.5. |
| [BrowserServer](https://github.com/codepoet80/BrowserServer) | Fork of the isis-browser rendering process; with [webos-webkit-update-research](https://github.com/codepoet80/webos-webkit-update-research), part of updating the web engine. |
| [qupzilla-webos](https://github.com/codepoet80/qupzilla-webos) | QupZilla 2.3.1 on Qt 5.9 for TouchPad / Pre3 — a newer browser engine on-device. |
| [atlas-browser-app](https://github.com/codepoet80/atlas-browser-app) | Enyo 1 browser app: one source tree, two platforms, two engines (with a WPE-based backend). |
| [Qt6-webos](https://github.com/webOSArchive/Qt6-webos) | Attempt to run Qt 6 on the TouchPad; documents the compatibility wall it hit. |
| Upstream sources | [openwebos](https://github.com/openwebos) (Open webOS, 2012), [isis-project](https://github.com/isis-project) (browser stack), [woce](https://github.com/woce) (earlier community OS builds). |

## Bringing software *to* webOS

| Project | What it is |
|---|---|
| [Android-to-webOS-Ports](https://github.com/webOSArchive/Android-to-webOS-Ports) | **apkenv**-based shim running Android NDK games natively as PDK apps — bionic linker as a library + fake JNI + SDL/PDL backend + per-engine modules, no Dalvik. Ten games shipped. Built on [thp/apkenv](https://github.com/thp/apkenv). |
| [Pre-PDK-to-TouchPad-Ports](https://github.com/webOSArchive/Pre-PDK-to-TouchPad-Ports) | Patching Pre-era PDK game `.ipk`s to run (and look better) on the TouchPad. |
| [webos-sdlquake-hd](https://github.com/webOSArchive/webos-sdlquake-hd), [webos-half-life](https://github.com/webOSArchive/webos-half-life), [webOS-CommanderKeen-SDL](https://github.com/codepoet80/webOS-CommanderKeen-SDL), [webOS-Vanilla-Conquer](https://github.com/codepoet80/webOS-Vanilla-Conquer), [scummvm](https://github.com/codepoet80/scummvm) | Native SDL game/engine ports. |

## Taking webOS software *elsewhere*

| Project | What it is |
|---|---|
| **Lunacy** (this project) | A simulated Luna shell and service bus on Android, with modernized Enyo/Mojo frameworks swapped in under unmodified apps. See [../README.md](../README.md). |
| [wcl](https://github.com/webOSArchive/wcl) | webOS Compatibility Layer: an earlier Android WebView approach that patched apps one at a time. Superseded by Lunacy; see [lessons.md](lessons.md). |
| android-touchpad-emulator | Ran the real webOS userland (x86 SDK image under QEMU; native ARM rootfs planned). Booted, but too slow to use. Superseded by Lunacy; see [lessons.md](lessons.md). Unpublished. |

## Apps that run on webOS *and* modern devices

Built with Enyo 2 from one source tree, shipped as a **Progressive Web App**, an Android
(Cordova) app, and a webOS `.ipk` in the App Museum. The template is
[enyo2-bootplate](https://github.com/webOSArchive/enyo2-bootplate) (`./build.sh www | webos | android`).
Enyo 2 was the step that moved the framework out of the OS and into the app — the same
model PWAs later standardized (manifest ≈ `appinfo.json`, service worker ≈ installed package,
web APIs ≈ `palm://` services).

| App | Source | Live |
|---|---|---|
| Check Mate (to-do) | [enyo2-checkmate](https://github.com/codepoet80/enyo2-checkmate), [checkmate-service](https://github.com/codepoet80/checkmate-service) | [checkmate.wosa.link](https://checkmate.wosa.link) |
| FeedSpider (RSS) | [FeedSpider2](https://github.com/codepoet80/FeedSpider2) | [feedspider.wosa.link](https://feedspider.wosa.link) |
| Papyrus (ePub reader, Enyo 1 + `webos-compat.js` shim) | [webos-papyrus-ereader](https://github.com/codepoet80/webos-papyrus-ereader) | [papyrus.wosa.link](https://papyrus.wosa.link) |
| Hacker Mystery 95 (game) | [HackerMystery-JS](https://github.com/codepoet80/HackerMystery-JS) | [hackermystery95.wosa.link](https://hackermystery95.wosa.link) |

Going the other way, [webos-pwa-installer](https://github.com/webOSArchive/webos-pwa-installer)
puts launcher shortcuts for modern sites and PWAs onto a webOS device.

## New and revived apps for the devices

| Project | What it is |
|---|---|
| [webos-synergy-revival](https://github.com/codepoet80/webos-synergy-revival) | Modernized stock Synergy account connectors (with [Herrie82's fork](https://github.com/Herrie82/webos-synergy-revival)). |
| [webos-imessage-synergy](https://github.com/webOSArchive/webos-imessage-synergy), [webos-bluebubbles-synergy](https://github.com/webOSArchive/webos-bluebubbles-synergy) | iMessage into the native Messages app via Message Bridge / BlueBubbles. |
| [webos-webcal-synergy](https://github.com/webOSArchive/webos-webcal-synergy) | Public iCal feeds into the native Calendar. |
| [org.webosports.service.contacts.carddav](https://github.com/codepoet80/org.webosports.service.contacts.carddav) | CardDAV contacts sync service (fork of the webOS-ports connector). |
| [webos-core-apps](https://github.com/webOSArchive/webos-core-apps) | The Enyo 1 core apps (accounts, calculator, …). |
| [webos-metube](https://github.com/codepoet80/webos-metube), [webos-drpodder](https://github.com/codepoet80/webos-drpodder), [webos-videochat](https://github.com/codepoet80/webos-videochat), [enyo2-claudechat](https://github.com/webOSArchive/enyo2-claudechat) | YouTube/Reddit video, podcasts (Mojo), P2P video chat replacing Skype, a Claude chat client. |
| [enyo1-bootplate](https://github.com/webOSArchive/enyo1-bootplate), [mochi-sampler](https://github.com/webOSArchive/mochi-sampler) | Starter templates and UI samplers for classic development. |

## LuneOS (webOS-ports)

[LuneOS](https://webos-ports.org) rebuilds the webOS experience on modern Linux — Yocto,
webOS OSE core, a Qt 6 Wayland compositor with the card shell, Chromium-based web runtime —
and runs on Android hardware via Halium (Android kernel and vendor blobs in an LXC container,
reached through libhybris). It's the mirror image of apkenv: Android's lower layers under a
webOS-style userspace.

| Project | Role |
|---|---|
| [webOS-ports](https://github.com/webOS-ports) | The organization: [meta-webos-ports](https://github.com/webOS-ports/meta-webos-ports) (distro layer), [meta-smartphone](https://github.com/webOS-ports/meta-smartphone) (device layers), [luna-next-cardshell](https://github.com/webOS-ports/luna-next-cardshell), [luna-surfacemanager](https://github.com/webOS-ports/luna-surfacemanager). |
| [luneos-porting-mcp](https://github.com/webOS-ports/luneos-porting-mcp) | MCP knowledge server for LuneOS device porting. |
| Pixel Tablet (`tangorpro`) port | Halium bring-up on Tensor G2 — kernel/KMI, pKVM, Trusty, compositor, WiFi and landscape fixes, sent upstream as PRs to webOS-ports and Herrie82's forks (local workspace, not yet published). |

## Other upstreams worth knowing

- [webOS OSE](https://github.com/webosose) — LG's open-source edition; the core LuneOS builds on.
- [thp/apkenv](https://github.com/thp/apkenv) — original apkenv.
- [JayCanuck/legacy-webos](https://github.com/JayCanuck/legacy-webos) — community legacy webOS resources.
- Community: [webOS Archive Discord](http://www.webosarchive.org/discord), [webOSLives forum](https://forums.weboslives.eu/), [webOSNation archive](http://forums.webosarchive.org).

# The reference Pre3

Measured on a connected HP Pre3 on 2026-09-21. Lunacy answers as a Pre3 on a phone-sized
screen (see "Decided" in [roadmap.md](roadmap.md)), and until now the Pre3's values in
`DeviceProfile.kt` came from the community's record rather than hardware. This is that
measurement.

`DeviceProfile.PRE3` has been corrected from these measurements; "What was corrected" below
lists what changed. The TouchPad profile is untouched and its output is unchanged.

## The unit

| | |
|---|---|
| novacom name | `mantaray-linux` |
| Kernel | `Linux webos-device 2.6.32.9-palm-rib #1 PREEMPT 156 armv7l` |
| OS | HP webOS 2.2.4 |
| Build | `Nova-ATT-Mantaray`, build 2211, `20111129175821`, mark 525914 |
| Customization | `att` |
| Product SKU | `FB389AA#ABA` (`/dev/tokens/ProductSKU`) |
| Display | 480x800 (`/sys/class/graphics/fb0/modes`: `U:480x800p-0`) |

**This is an AT&T unit.** The build name, `DMCARRIER`, the SKU and the carrier code are
carrier-specific; an unlocked or EU Pre3 will differ. Everything else below should not.

**It is patched but not upgraded** (codepoet): community patches through Preware, no Luna or
OS upgrade. That shows in `/etc/palm`, and it matters — see "Stock or patched" below.

Its serial and device id were deliberately not recorded. Lunacy generates those per install
rather than copying a real device's, so the shape is all that matters: an nduid is 40 hex
digits and a serial is a prefix plus seven characters.

## System properties

Read with `luna-send palm://com.palm.preferences/systemProperties/Get`, one key per call.

| Key | Value |
|---|---|
| `com.palm.properties.DMMODEL` | `HSTNH-F30CN` |
| `com.palm.properties.PRODoID` | `HSTNH-F30CN` |
| `com.palm.properties.boardType` | `mantaray-pvt` (no trailing newline; the TouchPad's `topaz-Wifi-pvt\n` has one) |
| `com.palm.properties.version` | `HP webOS 2.2.4` |
| `com.palm.properties.deviceNameShort` | `Pre3` |
| `com.palm.properties.productLineName` | `Pre` |
| `com.palm.properties.DMCARRIER` | `ATT` (the TouchPad's is empty) |
| `com.palm.properties.deviceName` | `HP Pre3` |
| `com.palm.properties.productLineVersion` | `3.0` — the device version the user agent ends with, so a TouchPad's is `1.0` |
| `com.palm.properties.buildName` | `Nova-ATT-Mantaray` |
| `com.palm.properties.buildNumber` | `2211` |
| `com.palm.properties.browserOsName` | **no such key** — a 3.x property the Pre3 hasn't got |

`CARRIER`, `carrierCode`, `ROMVERSION`, `prodSKU` and `DMNETWORK` all answer
`{"returnValue": false, "errorText": "no such key"}` — the same error text Lunacy returns, so
that shape holds on 2.2.4 as well as on the TouchPad.

**One call-shape difference.** webOS 2.2.4 takes a single `{"key": "..."}`. A TouchPad-style
`{"keys": [...]}` gets `{"returnValue": false, "errorText": "missing parameter key"}`. Lunacy
takes `key` and answers `no such key` when it is absent, where the device distinguishes a
missing parameter from an unknown one. A small honest-error fidelity gap, not a functional one.

## The user agent

There is no user agent in any config file. `/usr/lib/libWebKitLuna.so` carries the template:

```
Mozilla/5.0 (Linux; webOS/%s; U; %s) AppleWebKit/534.6 (KHTML, like Gecko) %s/221.56 Safari/534.6 %s/%s
```

The fields are the webOS version, the locale, a product token, the device name and the device
version. So the 2.2.4 shape is `(Linux; webOS/2.2.4; U; en-US)` — note the `Linux; ` prefix,
which the value in `DeviceProfile.kt` is missing — and `221.56` is confirmed.

Compare the TouchPad's own template, read the same way from a stock 3.0.5 unit:

```
Mozilla/5.0 (%s; Linux; %s/%s; U; %s) AppleWebKit/534.6 (KHTML, like Gecko) %s/234.83 Safari/534.6 %s/%s
```

The 3.x template has an extra leading field for the device class (`hp-tablet`) and names the
OS `hpwOS`; 2.2.4 has neither — it is `Linux` first and plain `webOS`. That reconstructs the
TouchPad's measured agent exactly, so the reading is sound.

**Measured on the wire**, from an app context, and identical to `navigator.userAgent` in the
page:

```
Mozilla/5.0 (Linux; webOS/2.2.4; U; en-US) AppleWebKit/534.6 (KHTML, like Gecko) webOSSystem/221.56 Safari/534.6 Pre/3.0
```

The product token is **`webOSSystem`**, not the TouchPad's `wOSSystem`. That is worth being
exact about: the TouchPad's measured agent uses `wOSSystem/234.83`, and reasoning from it to
the Pre3 gives the wrong string. 2.2.4 spells the token out.

The same request's other device headers:

| Header | Value |
|---|---|
| `X-Palm-Carrier` | `c001-01` (the TouchPad's is `c090-01`) |
| `X-Att-Deviceid` | `HP Pre3/2.1.2` |

`X-Att-Deviceid` is AT&T's own and will not be on an unlocked unit. Its `2.1.2` is stale — the
device runs 2.2.4 — so it is a fixed carrier string, not a live version.

`X-Palm-Carrier` is a device-identity header, not a live network one: this unit has no carrier
service at all (the status bar reads "Check SIM") and still sends `c001-01` over Wi-Fi.

Also in `/etc/palm/browser-app.conf`: `NetworkTimeout=60`, `EnableEnhancedViewport=true`,
`EnableFrameFlattening=true`, `FontScaleFactor=1.0`, `ClickSearchRadius=6`.

## PalmSystem.deviceInfo

Read in an app on the device. This is the authoritative shape for the phone profile:

```json
{"modelName":"Prē3","modelNameAscii":"Pre3","platformVersion":"2.2.4",
 "platformVersionMajor":2,"platformVersionMinor":2,"platformVersionDot":4,
 "carrierName":"ATT","serialNumber":"<this unit's>","screenWidth":480,"screenHeight":800,
 "minimumCardWidth":480,"minimumCardHeight":318,"maximumCardWidth":480,
 "maximumCardHeight":758,"touchableRows":14,"keyboardAvailable":true,"keyboardSlider":true,
 "keyboardType":"QWERTY","wifiAvailable":true,"bluetoothAvailable":true,
 "coreNaviButton":false,"swappableBattery":true,"dockModeEnabled":true}
```

Five things in it are not what you would guess:

- **`modelName` is `Prē3`** — Palm's branding, with a macron, and not ASCII. `modelNameAscii`
  carries `Pre3` separately. The TouchPad has no such split (both say `TouchPad`), so Lunacy's
  profile had been treating the two as one field.
- **`screenWidth` is the short side.** A TouchPad names its screen `1024 x 768` whichever way
  up it is; the Pre3 names its own `480 x 800`. Each reports its natural orientation, and
  neither changes when the screen turns.
- **`minimumCardHeight` is 318 and `touchableRows` 14** — the same as the TouchPad's, not the
  smaller phone numbers the community's record suggested.
- **`coreNaviButton` is false**, although the Pre3 has a gesture area and `luna.conf` enables
  its light bar.
- **There is no `carrierAvailable` member at all.** The TouchPad has one (`false`). A page that
  enumerates `deviceInfo` could tell the two apart by it.

`maximumCardHeight` is the screen height less the platform's `PositiveSpaceTopPadding`:
800 − 42 here, and 768 − 28 on the TouchPad. That is the same rule on both, and it is what
Lunacy had been computing from its own status-bar height.

`PalmSystem.version` is `"Webkit4/V8; device"` — the same string the TouchPad reports, so it
does not vary by webOS version.

## Reading an app's log on 2.2.4

`console.log` from an app **never reaches `palm-log`** on webOS 2.2.4, at any
`--system-log-level` (`error`, `warning` or `info`), locked or unlocked. Only errors arrive.
The reference probe reports through `console.log`, so it comes back empty here; the small
`Workbench/probe/org.webosarchive.lunacy.netprobe` app reports the same way through
`console.error`, which does arrive.

Two things that cost time before that was clear: the device sits on its **lock screen** with a
USB dialog when novacom is attached, and an app launched behind it renders but does not get on
with its work. Unlock first:
`luna-send -n 1 palm://com.palm.display/control/setState '{"state":"unlock"}'`. And
`palm-launch` on an app that is already open only re-focuses its card — close it first with
`palm-launch -c` or the page never re-runs.

## Stock or patched

The lesson from the TouchPad — the device is the reference, but read what has been changed —
applies here too. Modification time separates them:

| File | Date | |
|---|---|---|
| `/etc/palm/luna-platform.conf` | Nov 29 2011 | **stock** (the build date) |
| `/etc/palm/luna.conf` | Aug 14 2016 | patched; `luna.conf.webosinternals.orig` is stock |
| `/etc/palm/lunaAnimations.conf` | Aug 14 2016 | patched; `.webosinternals.orig` is stock |

The patches change responsiveness only: `DisplayNumBuffers` 3→8, `MoveMinX` 16→4, `MoveMinY`
12→3, `TapRadiusMax` 25→15, and the `VTrackBall` rates. Every value quoted below is the stock
one.

## The phone's shell geometry

**The Pre3 runs the tablet UI path.** `luna-platform.conf` is stock and says so:

```
[UI]
TabletUi=true
PositiveSpaceTopPadding=42
PositiveSpaceBottomPadding=42
ScaleFactor=1.5
AtlasEnabled=false
[VirtualKeyboard]
VirtualKeyboardEnabled=false
```

That is worth dwelling on. The Pre3 is not the old 320x480 phone shell scaled up: it runs the
same LunaSysMgr code path the TouchPad does, at `ScaleFactor=1.5` on a 480x800 screen, with no
virtual keyboard (it has a hardware one). The platform file overrides `luna.conf`, exactly as
on the TouchPad.

Card ratios and spaces, from stock `luna.conf` `[UI]`:

| | Pre3 | TouchPad (for comparison) |
|---|---|---|
| `ActiveCardWindowRatio` | 0.659 | 0.55 |
| `NonActiveCardWindowRatio` | 0.61 | 0.50 |
| `MaximumNegativeSpaceHeightRatio` | 0.55 | |
| `PositiveSpace*Padding` | 28, overridden to 42 | |

Other stock `luna.conf` values worth having:

- `WifiInterfaceName=eth0`, `WanInterfaceName=ppp0`. **A second device confirming `eth0`** —
  the interface name the fix log already corrected `connectionmanager` to.
- `[CoreNavi] EnableLightBar=true`, `CoreNaviBrightnessScaler=40`,
  `GestureAnimationSpeedInMs=1000`, `ThrobberBrightnessInLight=100`,
  `ThrobberBrightnessInDark=50`.
- `[Fonts]` Prelude for `Banner`, `LockWindow`, `DockMode` and `Quicklaunch`, as on the TouchPad.
- `[Memory] CardLimit=-1`, `AppsToAllowInLowMemory=com.palm.app.phone;com.palm.app.contacts;com.palm.app.messaging`.
- `[Launcher] LauncherIconReorderPositionThreshold=20`, `LauncherLabelWidthAdjust=0`,
  `LauncherLabelXPadding=0`.

The full stock `lunaAnimations.conf` was captured: card launch 400/curve 40, slide 300/10,
maximize 300/10, minimize 350/6, delete 300/6, scoot-away 400/30, transition 300/20, ghost
750/10, loading pulse 1000 after 900 ms; `positiveSpaceChange` 400/6; launcher 350/15,
quick launch 350/6; `normalFPS=35`, `slowFPS=20`.

### The trackball rates, for LunaKeyboard

Stock `luna.conf` `[VTrackBall]` — the Pre3's own tuning for the scroll ball LunaKeyboard
draws:

```
PixelsPerMoveH=14   PixelsPerMoveV=26
AccelRateH1=350     AccelRateV1=200    AccelConstH1=1   AccelConstV1=2
AccelRateH2=700     AccelRateV2=375    AccelConstH2=2   AccelConstV2=3
```

## Frameworks on 2.2.4

- **Enyo 0.10 and 1.0 both ship** (`/usr/palm/frameworks/enyo/`). Enyo 1.0's `appinfo.json`
  is `com.palm.enyo` version `1.0.0` — so Enyo 1 apps ran on the phone, which is what phase 1's
  phone form factor depends on.
- **Mojo submission 383**, where the TouchPad ships 506. Lunacy serves 506.
- Also present: `mojo2`, `mojo.core`, `mojoservice` (and `.transport`, `.transport.sync`),
  `mojodbshim`, `foundations`, `prototype`, `underscore`, `webview`, `globalization`, `media`,
  `mediacapture`, `mediaextension`, `mediastream`, `imagethumbnail`, `messaging.library`,
  `notes`, `photos`, `tasks`, `sync.ui`, `network.alerts`, `unittest`.

## What was corrected

`DeviceProfile.PRE3`'s values came from the community's record. Measured against this unit:

| Field | Was | Now |
|---|---|---|
| `model` (DMMODEL/PRODoID) | `P160UNA` | `HSTNH-F30CN` |
| `modelName` | `Pre3` | `Prē3`, with `modelNameAscii` = `Pre3` as its own field |
| `boardType` | `mantaray-pvt\n` | `mantaray-pvt`, no trailing newline |
| `userAgent` | `(webOS/2.2.4…) wOSBrowser/221.56` | `(Linux; webOS/2.2.4…) webOSSystem/221.56` |
| `carrierCode` | `c000-01` | `c001-01` |
| `buildName` | `Nova-Palm-Mantaray` | `Nova-ATT-Mantaray` |
| `buildNumber` | `1` | `2211` |
| `versionString` | `webOS 2.2.4` | `HP webOS 2.2.4` |
| `serialPrefix` | `PRE` + 7 | `MTRE` + 10 |
| `coreNaviButton` | `true` | `false` |
| `minimumCardHeight` | `0` | `318` |
| `touchableRows` | `8` | `14` |
| `carrierName` (DMCARRIER, deviceInfo) | hardcoded `""` for both devices | `ATT` |
| `productLineVersion` | hardcoded `1.0` for both devices | `3.0` |
| `browserOsName` | hardcoded `hpwOS` for both devices | absent on a Pre3 |

`model` was the important one: `PRODoID` is what apps key off to tell webOS devices apart —
the fix log records Palm's Help app picking the wrong device's content when it was missing —
and `P160UNA` is a retail SKU, not what the device reports.

Three of these needed the profile to be able to say something it could not before, so
`DeviceProfile` gained fields for them: `modelNameAscii` (the TouchPad has no macron, so the
two were one field), `naturalLandscape` (which side `deviceInfo` calls the width),
`positiveSpaceTopPadding` (what `maximumCardHeight` is short of the screen, previously taken
from Lunacy's own status-bar height), `reportsCarrierAvailable` (the Pre3 omits the member)
and `serialBodyLength`. The TouchPad's values for all of them reproduce its previous output
exactly.

**`buildName`, `carrierName` and `carrierCode` are this AT&T unit's.** An unlocked Pre3 reports
its own, and none was available; the code says so where those values sit.

## Two corrections to the *TouchPad* profile

Both turned up while checking the Pre3's `deviceInfo` against the reference TouchPad's own,
recorded in `Workbench/results/`. Neither had been decided: both values arrived with the
commit that laid the profile out, unverified.

| Field | Was | Reference device | Now |
|---|---|---|---|
| `bluetoothAvailable` | `false` | `true` | `true` |
| `dockModeEnabled` | hardcoded `false` | `true` | `true` |

`bluetoothAvailable` follows the rule the profile exists for: report what the device reported.
Lunacy does nothing with Bluetooth, but an app that goes on to call a Bluetooth service gets an
honest error from the bus, as it would for any service Lunacy hasn't got. `dockModeEnabled` was
simply stale — Lunacy has dock mode now, through Palm's Exhibition app and Android's screen
saver — and both reference devices report `true`, so it is a constant beside `wifiAvailable`
rather than a profile field.

With these, **the TouchPad profile reproduces the reference device's `deviceInfo` exactly**.
Checked on the HP 10 G2 on 2026-09-21 by reading `PalmSystem.deviceInfo` out of a running card
(`Workbench/cdp.sh eval`) and comparing it to the reference device's own, recorded earlier:
the member names and their order are identical, and the only differing values are the six that
are meant to differ — the per-install serial and the five screen sizes, which are this
screen's real ones (1280 x 800 rather than 1024 x 768). `maximumCardHeight` is
`screenHeight - 28` on both.

The `systemProperties` rework was checked on the same device: `browserOsName` still answers
`hpwOS`, `DMCARRIER` an empty string, `productLineVersion` `1.0`, and an unknown key still gets
`no such key`.

## Still to measure

- An unlocked (non-carrier) Pre3's `buildName`, `carrierName` and carrier code, and whether
  `X-Att-Deviceid` is replaced or simply absent.
- A second Pre3's serial, to confirm that `MTRE` is a model prefix and not this unit's alone.

## Changes made to this device

Per the rule that every change to a test device is recorded:

- `palm-log --system-log-level=info` (it was at the production default, `error`). This turned
  out not to matter — 2.2.4 does not carry `console.log` at any level — and can be set back.
- Installed `org.webosarchive.lunacy.probe` 0.0.9 and
  `org.webosarchive.lunacy.netprobe` 0.0.2, both webOS Archive's own. Remove with
  `palm-install -r <id>`.
- Unlocked the screen and set `onWhenConnected` with a 1800 s timeout, which does not survive
  a reboot.

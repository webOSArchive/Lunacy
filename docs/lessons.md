# Lessons from earlier attempts

Lunacy is the third attempt at running classic webOS apps on modern Android hardware. Both
earlier attempts taught something, and each lesson below is a rule Lunacy follows.

## wcl: patching apps in a WebView

[wcl](https://github.com/webOSArchive/wcl) hosted apps in an Android WebView with JS shims
for `PalmSystem` and `PalmServiceBridge` and a few Kotlin service stubs. Enyo samples ran, and
a Mojo app rendered. But each new app brought new conflicts between modern Chromium and its
2011 JavaScript and CSS, and the fixes piled up as special cases for individual apps.

**Rules Lunacy takes from it:**

- **Fix the layer apps trust, not the apps.** Apps load their framework from the OS, so fix
  the framework. Per-app CSS and JS fixes never converge.
- **Don't fake success.** wcl answered unknown services with `returnValue:true`, which hid
  every gap. Lunacy returns real errors and logs them.
- **Subscriptions must deliver updates.** A subscription that only ever returns one reply
  breaks apps quietly.
- **Keep the bus asynchronous.** wcl made bus calls synchronous over `@JavascriptInterface`,
  which blocks the page. Whatever the transport, a bus call queues and returns at once, and
  the reply arrives later. Only true `PalmSystem` synchronous needs (properties,
  `palmGetResource`) use a synchronous path.
- **Don't force the viewport.** wcl hardcoded `width=1024`, which defeated Enyo's responsive
  panels. Render at the real width by default; fixed-viewport is a per-app fallback.
- **Serve apps from real origins**, not `file://`. Storage and permissions then stay per app,
  and Android's scoped-storage limits stop mattering.
- **Keep db8 and WebSQL real.** Apps assume their data survives.
- **Keep the webOS paths.** Apps reach their framework through relative paths that only make
  sense in the webOS filesystem layout, so each origin mirrors that layout.
- **`file://` was a privilege.** Apps called any web API with no same-origin limits. A real
  origin takes that away, so the compat layer gives it back through a global network shim.
- **Measure convergence.** wcl had no way to tell that its fixes weren't converging. Lunacy
  logs where every fix lands, and a falling share of general fixes is the alarm.
- **Touch is not mouse.** Old webOS content expects touches as mouse events. In spike 1,
  one global script fixed every scroller, with no framework change.
- **Test on the target early.** Desktop Safari with a mouse is not Android Chromium with
  touch. A device spike comes before any architecture is built on the assumption.

**What carries over:** `.ipk` extraction (entries in `data.tar.gz` have `./` prefixes), the
connectivity and locale/time service code, the `palmGetResource` approach, and the catalog of
Chromium quirks:
- `-webkit-border-image` in modern Chromium needs `border-style` and an explicit width, and
  its `fill` behavior differs from 2011 WebKit.
- Relative image URLs inside border-image can break.

## Building Lunacy (2026-09-19)

Things that cost time and are easy to get wrong again:

- **Enyo on webOS gets inertia from the host.** With `PalmSystem` present, Enyo's scrollers
  coast only after a flick that LunaSysMgr reports through
  `Mojo.handleGesture('flick', …)`. Without it, scrolling feels sticky.
- **The host talks to pages through `window.Mojo`.** Lifecycle, gestures and flicks all
  arrive that way, for Enyo apps too.
- **Window type is in `window.open`'s features** (`attributes={"window":"dashboard"}`).
  Android's `onCreateWindow` never sees them, so the page has to hand them over first.
- **WebView transparency is reset by the `window.open` transport.** Set it again after
  each page load.
- **Whole-number scale, or seams.** At fractional device-px-per-CSS-px, `-webkit-border-image`
  and nine-patch slices show hairline seams. Round the TouchPad-px scale.
- **Nine-slice images need a middle.** When a bitmap is scaled to an even width, insets of
  half the width leave no stretchable middle, and only the corners draw.
- **Match the reference device, not the source alone.** LunaCE's code and repo config
  disagreed with the device in places: its `lunaAnimations.conf`, the card ratios from
  `luna-platform.conf`, and the dock icon position. Pull the device's `/etc/palm` and
  measure its screenshots.
- **App assets have conventions.** Status-bar notification icons are white glyphs on a
  24 or 32 px canvas. Dashboard layer icons are 48 px. A wrong-sized test icon looks wrong
  on the TouchPad too, so check test assets there before blaming Lunacy.
- **webOS's default font was Prelude, installed system-wide.** Pages name it in several
  spellings, and Android has none of them, so a stylesheet has to supply them.
- **Android's back button is the TouchPad's home button.** webOS tablets had no back.

## android-touchpad-emulator: running the real thing

This attempt ran the real webOS userland: first Palm's x86 SDK emulator image under QEMU,
with a native 32-bit ARM root filesystem planned next. It booted to the launcher, but it took
over two minutes to boot on a tablet, and it was too slow to be useful.

**Rules Lunacy takes from it:**

- **Simulate the contract, not the machine.** The value is in the apps and the experience,
  not in running LunaSysMgr. A modern shell that looks like webOS beats a genuine one that
  can't keep up.
- **Don't depend on Palm binaries or images.** Lunacy runs on any Android device without a
  user-supplied system image, and without CPUs that can run 32-bit ARM code.
- **Borrowing is fine.** LunaCE code and knowledge still inform what the shell should *do*,
  even though none of it runs.

## LunaNext (LuneOS)

LuneOS's LunaNext shell is also a reimplementation. It shows the approach works, and also
that function alone isn't enough: it doesn't look or feel like webOS. **For Lunacy, the look
and feel is a requirement, not polish.**

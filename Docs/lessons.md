# Lessons

Lunacy is the third attempt at running classic webOS apps on modern Android hardware. The
earlier two taught most of the rules below; the rest were paid for in this one. Each is a
rule Lunacy follows.

## The device is the reference

The strongest lesson of all, and the one that keeps coming back: **where a source tree and the
reference TouchPad disagree, the device is right.** Every time this has been tested, the device
won.

- **Its configuration differs from the repo's.** LunaCE's `lunaAnimations.conf`, the card
  ratios in `luna-platform.conf` and the dock icon position are all overridden on the device.
  Pull `/etc/palm` and use those values.
- **Its *source* differs from the repo's.** LunaCE's tree has three dock-mode clock faces;
  the device's `/usr/palm/sysmgr/uiComponents` has four, and opens on a face the repo doesn't
  contain at all. Anything the shell draws should be read from the device's own QML.
- **What a toolkit asks for is not what it draws.** The dock clock's QML asks for
  `font.weight: Font.Light`; the family it names has no Light face registered, so the device
  renders the regular weight - about twice the ink. Reading the source gave exactly the wrong
  answer.
- **What the shell *sends* is part of the contract too.** Exhibition's launch parameters were
  written from memory: `dockMode`, plus a `touchstoneMode` that exists nowhere in webOS.
  LunaSysMgr sends `{"windowType":"dockModeWindow","dockMode":true}` and apps test both keys,
  so they all quietly showed their ordinary view instead. The replies had been measured; the
  messages going out had not.
- **The right font can still look wrong.** Enyo asks for Prelude, the compat layer serves the
  TouchPad's own Prelude-Medium, and the page confirms it is loaded and in use - and the text
  still comes out thinner. Measured against the device on the same app at the same scale, cap
  heights match exactly, advances are 2-5% wider, and a short word gets about 15% less ink.
  webOS's WebKit hinted its advances and snapped stems to whole pixels; Chromium positions
  subpixel and leaves them unhinted. Nothing in the font stack is wrong, so nothing in the font
  stack will fix it.
- **A screenshot beats reading, every time.** One screenshot of the real dock-mode app menu
  corrected three things at once: it is flush to the screen edge with no frame, the whole panel
  is a single gradient (what looked like a highlighted row for the current app is not - nothing
  marks it), and the title reads "Choose an App" rather than the app's name.

So: before drawing anything, get a screenshot of the real thing and measure pixels from it.
Where this repo's docs disagree with the device, the device wins and the doc is wrong.

## Fix the layer apps trust

From [wcl](https://github.com/webOSArchive/wcl), which hosted apps in a WebView with JS shims
and Kotlin service stubs. Enyo samples ran and a Mojo app rendered, but every new app brought
new conflicts with modern Chromium and the fixes piled up as per-app special cases.

- **Fix the framework or the compat layer, never the app.** Apps load their framework from the
  OS, so fix the framework: one fix covers every app built on it. Per-app fixes never converge.
- **Measure convergence.** wcl had no way to tell its fixes weren't converging.
  [fix-log.md](fix-log.md) records where every fix lands; a falling share of general fixes is
  the alarm.
- **Don't fake success.** wcl answered unknown services with `returnValue:true`, which hid
  every gap. Return webOS's own errors and log them.
- **Subscriptions must deliver updates.** One that only ever replies once breaks apps quietly.
- **Keep the bus asynchronous.** A bus call queues and returns at once; only true synchronous
  `PalmSystem` needs (properties, `palmGetResource`) use a synchronous path.
- **Don't force the viewport.** wcl hardcoded `width=1024`, defeating Enyo's responsive panels.
- **Serve apps from real origins**, not `file://`, and **keep the webOS paths**: apps reach
  their framework through relative paths that only make sense in that layout.
- **`file://` was a privilege.** Apps called any web API with no same-origin limit; a real
  origin takes that away, so the compat layer gives it back through a global network shim.
- **Keep db8 and WebSQL real.** Apps assume their data survives.
- **Touch is not mouse.** Old webOS content expects touches as mouse events; one global script
  fixed every scroller.
- **Test on the target early.** Desktop Safari with a mouse is not Android Chromium with touch.

What carries over from wcl: `.ipk` extraction (`data.tar.gz` entries have `./` prefixes), the
connectivity and locale/time service code, the `palmGetResource` approach, and its catalog of
Chromium quirks - chiefly that `-webkit-border-image` needs an explicit `border-style` and
width, and that relative URLs inside it can break.

## Simulate the contract, not the machine

From android-touchpad-emulator, which ran the real webOS userland under QEMU. It booted to the
launcher and was far too slow to use.

- **The value is the apps and the experience**, not running LunaSysMgr. A modern shell that
  looks like webOS beats a genuine one that can't keep up.
- **Don't depend on Palm binaries or system images.** Lunacy runs on any Android device.
- **Borrowing is still fine.** LunaCE's code and configuration inform what the shell should
  *do*, even though none of it runs. (See the first section for the limits of that.)

From LuneOS's LunaNext, also a reimplementation: it shows the approach works, and that
function alone isn't enough - it doesn't look or feel like webOS. **For Lunacy the look and
feel is a requirement, not polish.**

## What the host has to provide

- **The host talks to pages through `window.Mojo`** - lifecycle, gestures, flicks, rotation -
  for Enyo apps as well as Mojo ones.
- **Inertia comes from the host.** With `PalmSystem` present, Enyo's scrollers only coast
  after a flick reported through `Mojo.handleGesture('flick', …)`. Without it, scrolling feels
  sticky.
- **Window type is in `window.open`'s features** (`attributes={"window":"dashboard"}`), which
  Android's `onCreateWindow` never sees, so the page has to hand it over first.
- **A framework's code may not be in the framework folder.** Mojo's was compiled into webOS's
  browser; what is on disk is assets plus V8 *native* scripts. See [mojo.md](mojo.md).
- **Anything a device provided, a page will assume.** Prelude was installed system-wide;
  `/media/internal` was the user's own storage; a file picker lived at a known path. Each has
  to exist here or apps fail in ways that look like their own bugs.

## Drawing the shell

- **Whole-number scale, or seams.** At fractional device-px-per-CSS-px, `-webkit-border-image`
  and nine-patch slices show hairline seams. Round the TouchPad-px scale.
- **Better still, don't scale art that has an ideal size.** LunaCE's keyboard art is drawn for
  one height, and its own source says so: "assets give us 'ideal' non-scaled sizes". Drawn at
  any other height the glyphs are resampled and the bevels smear - LunaCE shrinks its nine-tile
  corners to hide it. Draw at the ideal size and stretch only what was designed to stretch.
- **Nine-slice images need a middle.** Insets of half the width leave nothing stretchable, and
  only the corners draw.
- **Sprites are not halves.** LunaCE's two-state images keep each state at a documented rect
  inside a larger canvas; splitting the bitmap in half is off by a few pixels.
- **App assets have conventions.** Status-bar notification icons are white glyphs on a 24 or
  32 px canvas; dashboard layer icons are 48 px. A wrong-sized test icon looks wrong on the
  TouchPad too, so check test assets there before blaming Lunacy.
- **Android's back button is the TouchPad's home button.** webOS tablets had no back.
- **WebView transparency is reset by the `window.open` transport.** Set it again after each
  page load.

## Things that cost a day

- **An empty catch turns a missing global into a lie.** webOS's Prototype parses JSON with
  V8's `builtinEval`; undefined, it threw into a bare `catch` and every `evalJSON` reported
  "Badly formed JSON string" about perfectly good JSON. When an error makes no sense, look for
  a swallowed exception before doubting the data.
- **Enforcing a measured contract can still break things, if the environment differs in
  shape.** `palmGetResource` was tightened to refuse paths that aren't absolute, which is what
  a TouchPad does - but Enyo builds root-relative paths here, because pages are served from an
  origin rather than `file://`. Every app lost date and number formatting. Measure the
  contract, then ask what the difference in environment implies.
- **Bundled is not installed.** Anything that walks installed packages has to look at the
  bundled apps too, or a bundled app quietly loses its db8 kinds, its configuration, its icons.
- **A 180° turn is not a configuration change.** Android reports no configuration change when
  a tablet is turned end for end - the orientation and the size are unchanged - so anything
  tracking rotation needs a display listener, not `onConfigurationChanged`.
- **A patch in a series is a diff against the patches before it**, not against the pristine
  tree, or applying them in order fails. `AndroidLuna/tools/make-enyo-patch.sh` builds that base.
- **An app loads either the built framework or its source tree.** A change to a fork has to be
  in both, or it works in some apps and not others.

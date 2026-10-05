# Lunacy's changes to Mojo

Upstream: Palm's Mojo as it is on the reference TouchPad (webOS CE 3.1.0),
`/usr/palm/frameworks/mojo`, with `mojocommon` beside it. Palm's code, shipped as abandonware
like the Prelude fonts; see the NOTICE `fetch-assets.sh` writes next to it.

Mojo is not packaged the way Enyo is. **webOS's browser had the framework compiled in**: a
page loads `mojo.js`, which looks for a global `palmInitFramework<submission>` that WebKit
provided and calls it. The submission on disk carries only assets - stylesheets, images,
templates, formats - and `builtins/` carries the code, as the V8 *native* scripts the browser
was built from. Without the global, mojo.js falls back to fetching
`submissions/506/javascripts/loader.js`, which webOS doesn't ship: that is the "The load of
framework submission 506 failed" message.

So Lunacy serves the builtins in front of the app's own `mojo.js` tag (a serve-time transform,
see "Mojo" in [architecture.md](../../Docs/architecture.md)), and the patches below make
those files loadable as ordinary scripts. Everything else is Palm's, unchanged.

**Adding a patch:** edit `AndroidLuna/local-assets/fw/mojo/`, then diff it against
`Workbench/vendor/touchpad/mojo/` into `patches/`, the same way the Enyo fork works
(`AndroidLuna/tools/make-enyo-patch.sh` does it there); `fetch-assets.sh` applies them in order
and fails the build if one no longer applies.

**Then the SDK.** Every patch here goes into the SDK too (codepoet, 2026-10-05):
[webos-sdk-redux](https://github.com/webOSArchive/webos-sdk-redux) ships Mojo for desktop
previews, and its `update-frameworks.sh` rebuilds it from this folder; run it and commit the
result there. A desktop page doesn't load the builtins a device's browser had compiled in: it
loads the SDK's `javascripts/`, HP's own desktop build of the same framework. So a patch to the
builtins needs a desktop form as well, the same change made in `javascripts/`, kept in
`sdk-patches/` under the same name (a diff with `a/` and `b/` at the submission folder);
`update-frameworks.sh` applies those. A patch to the submission's stylesheets or other assets
applies to the SDK as it is. Each entry below says which it is.

## Patches

### 0001-run-outside-webos-browser.patch

Palm's Mojo expects webOS's browser. Two things about that browser are not true of Chromium,
and both are in this patch because both are in the same two files.

**The builtins are V8 native scripts.** `builtins/InstallPrototypeBuiltIn.js` (webOS's
Prototype 1.6) and `builtins/palmInitFramework506.js` (the framework itself) are written in
V8's internal syntax, which only V8's own compiler accepts. Four things are given their
ordinary JavaScript meanings so a browser can load them:

- `global`, V8's global object, becomes `this` - the window.
- `%SetProperty(global, "name", $name, flags)`, which registers a native on the global,
  becomes an assignment. `%ToFastProperties` and `%FunctionSetPrototype` likewise; they are
  optimisation hints with obvious equivalents.
- `$Object`, `$Function`, `$Array` and the rest of V8's aliases for the built-in
  constructors become the constructors.
- `builtinEval`, V8's own eval, becomes an indirect `(0, eval)` - the same thing in a page.
  webOS's Prototype parses JSON with it, so without this every `evalJSON` threw "Badly formed
  JSON string", and an app's launch parameters never reached it. That is what kept Flying
  Toasters showing its settings scene instead of its exhibition one.

**A page is neither file:// nor http://.** Mojo knows two kinds of host: a device, where a
page is `file:///…`, and a desktop browser on `http://`. Lunacy serves every app from its own
`https://` origin, which is neither: `Mojo._calculateAppRootPath` matched nothing and threw
before the framework started, and `Mojo.hostingPrefix` fell back to `file://`. Both accept
`https` now.

Nothing in the framework's own logic changes. Mojo's device test is separate and already
right: `Mojo.Host.current` is "palm-sys-mgr" when `window.palmGetResource` exists, which
Lunacy's bridge provides, so the framework takes the device path and keeps the launch
parameters the shell passes.

Files: `builtins/InstallPrototypeBuiltIn.js`, `builtins/palmInitFramework506.js`.

SDK: none needed. It makes the builtins load as ordinary scripts, which `javascripts/` already
are, and a desktop page is on `file://` or `http://`, which Mojo knew.

### 0002-mojo2-builtins.patch

The same treatment for **Mojo 2**, which Palm's own Video Player loads and which nothing had
run before. Mojo 2 is packaged differently again: its submission (205) *does* carry
`javascripts/` on disk, but `mojo2/mojo.js` still prefers the builtin when one is on the
window, and MojoLoader hands out the libraries the framework asks for (`underscore`,
`foundations`, `globalization`, `mojo.core`) from builtins of their own. All of them are V8
native scripts, so all of them get the same four substitutions as submission 506, plus one
more the 506 files didn't need: `$Object`, V8's alias for the constructor.

Two host assumptions are corrected as well, the same ones patch 0001 fixed in 506:

- `palmInitFramework2205.js` works out `Mojo.hostingPrefix` from `/http:\/\/(.*:[0-9]+)/`,
  which matches nothing on an https origin.
- `palmmojo_coreVersion1_0.js` works out the app's own folder from the document URI and knows
  only `file:///` (a device) and `http://` (a desktop browser); the `http` branch is
  unguarded, so on Lunacy it threw before `Mojo.Core.App.path` was ever set.

Files: `builtins/palmInitFramework2205.js`, `builtins/palmmojo_coreVersion1_0.js`,
`builtins/palmunderscoreVersion1_0.js`, `builtins/palmfoundationsVersion1_0.js`,
`builtins/palmglobalizationVersion1_0.js`, `builtins/palmcontactsVersion1_0.js`.

`palmcontactsVersion1_0.js` is patched with the others for consistency; nothing has loaded it
yet.

SDK: none needed. The SDK ships Mojo 1's submission 506 only, and these are the same loading
fixes as 0001.

### 0003-scene-fills-its-scroller.patch

A scene element is the content of Mojo's scene scroller, and the scroller is given the card's
height; the scene itself is left at whatever its own content comes to.

On a device that is enough, because the scroller is `overflow: -webkit-palm-overflow` -
webOS's own scrolling model, which has no equivalent here - and the scene fills it. Chromium
doesn't know that value, so `Mojo.Widget.Scroller` takes its own fallback
(`scrollContainer.style.overflow = "hidden"`) and a scene whose content is short collapses to
nothing. Every `height: 100%` child of a scene then collapses with it: drPodder's splash
paints its background with a `position: absolute; height: 100%` div inside the scene, so it
came out as a grey card with the logo adrift in it where the reference device shows a
full-bleed gradient (codepoet's screenshot, 2026-09-22).

`min-height: 100%` rather than `height`, so a scene taller than the card still scrolls, and it
only bites where the scroller has a definite height - which is exactly the case the fallback
broke. A scene that isn't in a scroller (webOS IAmA reddit's is not) is untouched.

File: `submissions/506/stylesheets/global-base.css`, which on the device is a symlink to
`mojocommon/stylesheets/global-base.css`; the patch is applied through the link
(`patch --follow-symlinks`), so it is mojocommon's file that changes, as it would on a device.

SDK: applies as it is (`update-frameworks.sh` applies it to the SDK's copy of the stylesheet).

### 0004-whole-pixel-dimensions.patch

`Mojo.View.getDimensions` is `offsetWidth` and `offsetHeight`. The TouchPad's WebKit laid out
in whole pixels, so those were an element's size. Chromium lays out in fractions and rounds
them to the nearest pixel, which can be a fraction *more* than the element has, and Mojo
sizes things with what it measures. A TextField measures its text area at its natural width
beside the row's floated label and fixes it there; in Keyring SD 0.0.6 the room beside USER
was 1201.875 px, the measurement came back 1202, and a 1202 px box no longer fits beside the
label, so the text dropped onto a second line and the row came out double height (codepoet,
on the Pixel Tablet, 2026-10-05). Measured on the reference TouchPad with
`Workbench/probe`'s mojoprobe 0.0.3: the same rows are 52 px with the text beside the label,
and its engine also moves a box that doesn't fit below a float, so the difference is the
rounding alone.

A size that rounded up by less than a pixel now comes back as the whole pixels the element
has (`Math.floor` of its bounding box); anything else is `offsetWidth`/`offsetHeight` as
before, so a transformed element, whose box differs by more than that, is untouched.

Files: `builtins/palmInitFramework506.js` (Mojo 1), `builtins/palmInitFramework2205.js`
(Mojo 2).

SDK: `sdk-patches/0004-whole-pixel-dimensions.patch`, the same change to `javascripts/view.js`,
which is where a desktop page gets `Mojo.View.getDimensions`.

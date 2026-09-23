# Lunacy's changes to Enyo 1.0

Upstream: [enyojs/enyo-1.0](https://github.com/enyojs/enyo-1.0), tag `r1` (Enyo 1.0 submission
128, as shipped on HP webOS 3.0.5). Apache 2.0.

Lunacy serves this framework at `/usr/palm/frameworks/enyo/…`, where apps load it from the OS,
so a fix here reaches every app built on it - which is the point (rule 1 in
[CLAUDE.md](../../CLAUDE.md)). The fork is **stock Enyo plus the patches in `patches/`**:
`fetch-assets.sh` copies the upstream clone and applies them in order, and fails if one
doesn't apply. That keeps every change to the framework readable as a diff against upstream,
which is what rule 4 asks for.

Enyo is served from `framework/build/enyo-build.js` (its non-debug path), so a change has to
be made in the built file as well as in `framework/source/`. Each patch does both.

**Adding one:** edit `AndroidLuna/local-assets/fw/enyo/1.0/`, then

```sh
cd AndroidLuna
tools/make-enyo-patch.sh 000N-name framework/source/<file> framework/build/enyo-build.js
```

and add an entry below. A patch in a series has to be a diff against the tree with the
*earlier* patches applied, not against stock, or applying them in order fails; the script
builds that base itself so it can't be got wrong by hand.

**Both copies.** An app loads either the built file or the source tree - the Enyo Sampler
takes `framework/build/enyo-build.js`, Glimpse takes `framework/source/` - so a change has to
be in both or it will work in some apps and not others. The built file is minified, so its
half of a patch is written as a readable override appended to the end rather than as a
minified diff.

## Patches

### 0001-screen-orientation-changed.patch

`Mojo.screenOrientationChanged` raises Enyo's own orientation change.

Enyo watches `window.resize` to decide the window has rotated, and leaves the host's
`Mojo.screenOrientationChanged` an empty stub - its own note says why: the callback wasn't
made on one of Palm's devices, and the stub exists only so sysmgr doesn't error on the ones
where it is. Turning a tablet end for end changes the orientation while the window keeps its
size, so no resize is fired and an app never hears about it.

Lunacy's shell does make that call, so the stub now feeds `enyo.sendOrientationChange()`, the
same function the resize path uses. It only dispatches `windowRotated` when the orientation
really changed, so the two paths can't double up.

Files: `framework/source/compatibility/webosGesture.js`,
`framework/build/enyo-build.js`.

### 0002-webview-over-iframe.patch

`enyo.WebView` shows web pages, over an iframe.

On webOS this control was the BrowserAdapter: an NPAPI plugin (`<object
type="application/x-palm-browser">`) talking to browserserver, another process. Lunacy has no
such plugin, so the control rendered an object that answered nothing, and an app calling it
got a TypeError - that is what put the "DIAG ERROR" overlay on USA Today (see fix-log.md).

Everything above the plugin is Enyo's own code and still runs; only the bottom layer changes.
The node is an iframe, `adapterReady` is "the node exists", connecting completes at once, and
`_callBrowserAdapter` - the single point every command goes through - maps the plugin's calls
onto what an iframe can do: `openURL`, `reloadPage`, `stopLoad`, `goBack`, `goForward`,
`setHTML`. The load event gives `onLoadStarted`, `onLoadComplete` and `onPageTitleChanged`.

What an iframe can't do is logged once per call, so an app's use of it shows up rather than
vanishing: the filesystem calls (`saveViewToFile`, `resizeImage`, `generateIconFromFile`), the
plugin's own dialogs, printing and find-in-page. A page on another origin keeps its window to
itself, so history and title only work for pages Lunacy serves.

Two things to know if you change this:
- `urlTitleChanged` is the callback for a page's title; there is no `pageTitleChanged` on this
  kind, and calling one silently stopped the load ever reaching the app.
- The load listener is bound when a page is first opened, not in `rendered()`: something in
  the kind machinery replaces `rendered` after the override runs.

Files: `framework/source/palm/controls/BasicWebView.js`, `framework/build/enyo-build.js`.

### 0003-flex-share-flex-basis.patch

A flexed child's share of a horizontal box is said with `flex-basis: 0` instead of `width: 0`
on engines where the two differ.

`FlexLayout` makes a flexed child "exactly its share of the leftover space" by giving it
`width: 0px` next to its `-webkit-box-flex`. The WebKit of 2011 skipped a fixed width of 0
when working out how wide a box's content wanted to be (`RenderBlock` only used a fixed width
above 0 for its preferred width), so a box sized to its content still came out as wide as
its children's content, then split evenly. Newer Chromium counts the 0, so the same box comes
out only as wide as its children's borders. Palm's Clock draws its clock/alarm switch with a
`RadioGroup` between two `Spacer`s in a `Toolbar`. On WebView 131 the group came out 63 px
wide and its two icons spilled out of 32 px buttons.

`enyo.FlexLayout.zeroWidthCounts()` checks the engine once, on a hidden box: does a 0 width
count, and does `flex-basis` share a fixed box out evenly? Only when both are true is the
width said with `flex-basis`, which gives the same even split in a fixed box and counts the
content in a box sized to it. Everywhere else, including the older engines this was written
for, Enyo's `width: 0px` stays as it was. Heights are left alone: the old engine worked out a
box's content height by laying it out, and a 0 height stayed 0 there too.

Files: `framework/source/base/layout/FlexLayout.js`, `framework/build/enyo-build.js`.

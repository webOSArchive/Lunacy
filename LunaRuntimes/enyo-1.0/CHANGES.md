# Lunacy's changes to Enyo 1.0

Base: Enyo as it is on the reference TouchPad (webOS CE 3.1.0),
`/usr/palm/frameworks/enyo/0.10`, copied off the device into `Workbench/vendor/touchpad/enyo-0.10`.
Enyo is Apache 2.0 ([enyojs/enyo-1.0](https://github.com/enyojs/enyo-1.0)); the libraries and
localized resources HP shipped only on the device are Palm/HP's, shipped as abandonware.

**Rebased 2026-10-05.** Until then the base was the GitHub release (`submissions/128.2`), which
this file called the build HP shipped. It isn't: its built `enyo-build.js` differs from the
device's in about a hundred files' worth of code (clipboard paste and the `webosEvent` setup among
them), it lacks the device's `resources/` and some of its libraries, and its `BasicWebView` has
none of the device's call logging. The TouchPad's built file is byte-identical to the one in HP's
3.0.5 SDK. Every app in Lunacy now runs the framework code the TouchPad runs, plus the patches
below; webos-sdk-redux 0.4 ships the same tree, built from this series, for desktop previews.

Lunacy serves this framework at `/usr/palm/frameworks/enyo/…`, where apps load it from the OS,
so a fix here reaches every app built on it - which is the point (rule 1 in
[CLAUDE.md](../../CLAUDE.md)). The fork is **stock Enyo plus the patches in `patches/`**:
`fetch-assets.sh` copies the device's tree and applies them in order (with no fuzz), and fails
if one doesn't apply. That keeps every change to the framework readable as a diff against what
the device had, which is what rule 4 asks for.

Enyo is served from `framework/build/enyo-build.js` (its non-debug path), so a change has to
be made in the built file as well as in `framework/source/`. Each patch does both.

**Adding one:** edit `AndroidLuna/local-assets/fw/enyo/1.0/`, then

```sh
cd AndroidLuna
tools/make-enyo-patch.sh 000N-name framework/source/<file> framework/build/enyo-build.js
```

and add an entry below. Then update the SDK, which ships the same Enyo for desktop previews:
run [webos-sdk-redux](https://github.com/webOSArchive/webos-sdk-redux)'s
`update-frameworks.sh` (it applies this series to the TouchPad's tree) and commit the result
there. Every patch here goes into the SDK too (codepoet, 2026-10-05).

A patch in a series has to be a diff against the tree with the
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

The device's `BasicWebView` logs every call it makes to the plugin (`setHTML` without its
arguments, for privacy, as Palm's comment says), so its `palm-log` shows them. The replacement
`_callBrowserAdapter` keeps that logging, in the source and in the built file's override.

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

### 0004-flex-share-content-sized.patch

A box sized to its content keeps its flexed children at their natural widths on an engine
that takes `flex-basis` literally inside a `-webkit-box`.

Patch 0003 says a child's share with `flex-basis: 0` on engines that count a 0 width. Chromium
37 to 131 laid `-webkit-box` out with the old flexible-box code, which never read `flex-basis`,
so in a box sized to its content each child kept its natural width and the box grew to fit
them, as it did on the TouchPad. WebView 149 builds `-webkit-box` on flexbox and honours the
basis: the same box is split evenly, and the widest child's caption is cut off. App Catalog's
category lists sort with a `RadioGroup` of three buttons centred in a `VFlexBox`; on the Pixel
Tablet "Recommended" lost its last letters.

`enyo.FlexLayout.basisSplitsContent()` checks the engine once, on a hidden box: two children
with `flex-basis: 0` and very different text that come out the same width. On such an engine,
and only for a container Enyo can see is sized to its content (`enyo.FlexLayout.contentSized`:
no width of its own, not flexed, and a child of a horizontal box or of a vertical box that
doesn't stretch its children), the share is not said at all, and the children take their
natural widths. A container with a definite width keeps the even split every engine agrees
on; and the engines patch 0003 was written for answer no and are unchanged.

Measured on 2026-10-01: the three buttons are 142, 110 and 109 px on the reference TouchPad
and 144, 110 and 110 on the Pixel Tablet with this patch, against 121, 121 and 121 without it.
On the Nexus 7's WebView 39 both probes answer no, Enyo's own `width: 0px` stays, and the
buttons measure the same 144, 110 and 110.
`flow()` now notes the container it lays out, which `flowExtent()` reads.

Files: `framework/source/base/layout/FlexLayout.js`, `framework/build/enyo-build.js`.

### 0005-webview-native.patch

In a Lunacy card, `enyo.WebView` shows pages in a native Android WebView rather than an iframe.

Patch 0002's iframe can't show a page that refuses to be framed (most large sites send
`X-Frame-Options` or a `frame-ancestors` policy), and can't see into a page on another origin,
so it had no title, history or load progress. That was enough for an app showing its own
pages, not for a browser: Palm's Web app is `enyo.WebView` and little else.

The override is the same text appended to both copies, and it only takes over where the card
offers `LunacyNative.webViewCreate`; elsewhere (a desktop browser) 0002's iframe stays.

- **The node** is an empty `div`. Connecting makes a native view ([BrowserViews.kt](../../AndroidLuna/src/main/java/org/webosarchive/lunacy/card/BrowserViews.kt)),
  a child of the card's own WebView, so it moves, scales and clips with the card in card view.
- **Its place.** The box's rectangle, the page's width (from which the card's scale follows),
  whether it is shown and whether a popup is over it, sent when any of them changes; they are
  looked at five times a second, since a pane sliding or the keyboard moves the box without an
  event.
- **The plugin's calls** (`_callBrowserAdapter`) go across by name with their arguments as
  JSON; a function argument (`saveImageAtPoint`'s callback) waits on the page under a token.
- **The plugin's callbacks** come back by name onto the BasicWebView (`window.__lunacyWebView`):
  `loadStarted`, `loadProgressChanged`, `loadStopped`, `documentLoadFinished`,
  `urlTitleChanged` (with back and forward), `mainDocumentLoadFailed` (with webOS's error codes
  where the app knows them), `dialogAlert`/`Confirm`/`Prompt`, `dialogSSLConfirm`,
  `dialogUserPassword`, `mimeNotSupported`, `urlRedirected`, `createPage`, and `eventFired`
  with a `mousehold` on a link or image, which is how the app's context menu opens.
- **Popups.** The plugin drew into the page, so Enyo's popups came out over the web content.
  The native view is over the whole page. While an `enyo.BasicPopup` is open, or is still on
  screen over the box (a toaster sliding away), the view is *covered*: Android draws it into a
  picture, the page shows the picture as the box's background, and the view steps aside once
  the picture is up; it comes back when the popup has gone. `BasicPopup.prepareOpen` and
  `close` are wrapped so that this happens at once rather than at the next look.

`destroy` is replaced rather than wrapped: the original sets a property on `this.node`, which
throws when the control is destroyed before it was ever rendered.

Its `_callBrowserAdapter` logs each call first, as the device's does (see 0002).

Files: `framework/source/palm/controls/BasicWebView.js`, `framework/build/enyo-build.js`.

The native view also does what the plugin did for a page set with `setHTML`, which Email's
message view relies on (BrowserViews.kt): an `<object type="application/x-palm-email">` in the
HTML is drawn as the message it names in the file cache; `setHeaderHeight` keeps that much room
at the top of the document and clips the view away over it, so the app's own header shows
through and takes touches there; and `scrolledTo` is sent as the view scrolls, which the app
follows with its header. That is Android's side; this patch carries the calls as before.

### 0006-flex-percent-height.patch

A percentage height inside a child Enyo flexes vertically comes out 0 px, as it did on the
TouchPad.

Enyo gives a vertically flexed child `height: 0px` and lets `-webkit-box` stretch it. The
TouchPad's WebKit worked a percentage height inside that child out against the 0 px, before the
box stretched it, and never again: `height: 100%` there was 0 and the content overflowed.
Email's message view is laid out on it: its `WebView` is `height: 100%` inside a flexed pane,
and the header placed after it "floats over the webview" because the WebView takes no room.
Today's Chromium resolves the percentage against the stretched height, so the WebView took the
whole pane and pushed the header out of sight below it.

`enyo.FlexLayout.percentOfFlexed()` checks the engine once, on a hidden box: a child with
`height: 100%` inside a flexed child of a 100 px vertical box. Where it comes out taller than
0, `flowExtent` gives an in-flow child (not `absolute` or `fixed`) with a percentage height,
inside a child it has flexed vertically, `height: 0px`, which is what the TouchPad computed.
Engines that answer 0 are untouched.

Measured on 2026-10-07 with `Workbench/probe`'s webviewprobe (Email's pane, reduced): on the
reference TouchPad the WebView is 1024 x 0 and its view 1024 x 700 at the pane's top, with the
header at the pane's top; in Lunacy on the Galaxy Tab A7 Lite (Android 14) the WebView was 800 x
1272 and the header at 1312, and with this patch the WebView is 800 x 0, the view 800 x 1272 and
the header at the pane's top.

Files: `framework/source/base/layout/FlexLayout.js`, `framework/build/enyo-build.js`.

## Added

Nothing, since the rebase. `lib/networkproxy` (HP's network-proxy settings library, which the
Wi-Fi library loads, and without which the webOS Community Account Manager's app stopped while
starting) used to be copied in from the TouchPad beside the GitHub release; it is part of the
device's tree, and so are the device's newer `accounts`, `authlib` and `addressing` libraries and
their localized `resources/`, which the earlier base lacked.

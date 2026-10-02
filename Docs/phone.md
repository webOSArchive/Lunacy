# The phone layout

Written 2026-10-02, the first day Lunacy had one. A phone is not a TouchPad: a 360 px shell
holds a third of a TouchPad's width, and the tablet layout on it gave two launcher columns,
tab titles squeezed into each other and a dock of overlapping icons. Palm meant to bring the
webOS 3 shell to its phones and never got to it; this is Lunacy's reading of what that would
have been, the TouchPad's own parts tightened rather than another shell (codepoet: neither the
Pre3's launcher nor LuneOS's is the model; the TouchPad's is).

Everything here is in TouchPad px, the shell's unit (one of them is `Luna.density` device
px: 3 on a Nexus 5). Nothing here changes a tablet.

## 1. Phone or tablet: `FormFactor`

One decision, made once at start, that everything else follows: the shell's layout, and the
device apps are told they are on (`DeviceProfile.forScreen`: a Pre3 on a phone, a TouchPad on
a tablet), so the two never disagree. In `card/FormFactor.kt`.

**From the screen**, three clues:

| Clue | Phone when | Why |
|---|---|---|
| Short side in density-independent px | under 600 dp | Android's own line (`sw600dp`), which every Android app's tablet resources sit behind |
| Diagonal in inches, from the panel's physical dpi | under 7 in | A screen that size is held in one hand whatever density it reports |
| Shape, long side over short | 1.7:1 or taller | 16:9 and the taller screens since are phones; a TouchPad is 4:3, most tablets 16:10 |

The two sizes decide when they agree; when they don't (a small tablet set to a dense
configuration, a big phone reporting a loose one) the shape breaks the tie. A panel that
reports an implausible physical dpi (outside 60 to 1000) is measured by its logical density
instead.

**Or the owner's word.** The `layout` preference, `auto` (default), `phone` or `tablet`:

- Device Info's Display group has a Layout row saying what was decided and from what
  ("Phone (from the screen)"); tapping it opens the choice, and the app layout width below.
- `palm://org.webosarchive.lunacy/system/setLayout {"layout": "phone", "appLayoutWidth": 640}`
  on the bus, Lunacy's own apps only; `system/getEnvironment` reports `layout` with the
  clues. The shell takes a change up when it next starts (the choice fixes `Luna.density`
  and every view's sizes at construction).
- Over adb: `am start -n org.webosarchive.lunacy/.shell.ShellActivity --es layout phone`
  (the shell restarts itself).

Measured: the Nexus 5 (1080 × 1920 at 480 dpi) reads short side 360 dp, 5.0 in, 1.78:1,
a phone by all three. The Pixel Tablet (2560 × 1600 at 320 dpi) and the HP 10 G2 read as
tablets on all three.

## 2. The launcher (`Launcher.Phone`)

| | TouchPad | Phone | Note |
|---|---|---|---|
| Tab bar height | 50 | **40** | `tab-bg.png`'s slices are 20 top and 20 bottom, so 40 is as short as the art goes |
| Tab width | min(bar / tabs, 150) | **150**, always | The TouchPad's width; the bar **scrolls sideways** instead of squeezing, so a phone holds as many tabs as a tablet (6) |
| Tab bar scrolling | – | a sideways drag on the bar scrolls it; tapping a tab or landing on a page scrolls that tab fully into view (250 ms InQuad, with the page snap) | The "+" for a new tab sits after the last tab in the scrolling content, held as on a tablet |
| Permanent tabs | 4 | **2** | LunaCE won't delete its four built-in tabs; a phone can go down to two. Deleting a built-in page sends its icons to the first page, as any deletion does, and apps that would have landed on it land on the first page |
| Cell | 128 | **84** | Four columns across a 360 px portrait, seven across its 640 px landscape |
| Icon | 64 | **56** | Seven-eighths; a group's 68 px composite is drawn at the same size |
| Label width | 100 | 80 | Still 14 px bold, two lines |
| Columns | LunaCE's rule from `MaxIconsPerRow` | as many cells as fit between 6 px margins, the rest shared as gaps | The TouchPad rule gives two columns at 360 |
| Row gap, top margin | 10, 20 | 6, 12 | |
| Launch glow, delete badge offset | 90; −50, −50 | 80; −36, −36 | Scaled with the cell |

The group panel caps its columns at what the width holds (a phone's 360 holds two 118 px
cells), and the Rename/New Tab dialog shrinks from 520 px to the width less its padding.

## 3. The dock (`QuickLaunch`)

| | TouchPad | Phone |
|---|---|---|
| Icons | up to 5 | **up to 4** |
| Launcher button | its own place, 64 px in from the right edge, the icons sharing the space to its left | **the fifth of five equal slots**: the same size as the icons, no room of its own on the right |
| Icon size | 64 | 64, or 85 % of a slot where that is narrower (61 px on a Nexus 5 portrait) |

The dock stays 100 px tall. A dock saved with five icons on a tablet shows four on a phone;
the fifth is kept in the saved list.

## 4. Fixed viewport, per app

Two things, each with one owner (codepoet, 2026-10-02):

- **What an app is told** is the global decision above: on a phone every app hears Pre3,
  webOS 2.2.4 and the Pre3's user agent, so any per-device logic in it sees a phone.
- **Whether its page is scaled** is per app, the fixed-viewport fallback the rules already
  allow (architecture.md, "No per-app hacks"), on or off, in `card/FixedViewport.kt`.

A TouchPad app is written for a 1024 × 768 screen: Palm's Clock is a 514 px face, its
Calculator a 500 × 690 panel, Memos a 943 px grid. On a 360 px card they are simply
clipped. With the switch **on** the page is laid out at the **fixed viewport width**
(`FormFactor.appLayoutWidth`, default **640** px, set in Device Info's Layout dialog) and
the card shows it whole, scaled by card width over that width wherever the card is narrower:
on a Nexus 5 in portrait 360 / 640 = 0.5625, 1.6875 device px per CSS px; its 640 px
landscape card, and every tablet card, are at 1, so the switch is harmless there. With the
switch **off**, the default, the page is laid out at the card's own width, which is what
every app written for the Pre3 wants: the SDK had it declare its viewport
(`height=device-height`) and size itself with the framework.

**Defaults.** Off, except the bundled Palm apps known to draw a fixed screen: Clock,
Calculator and Memos (`FixedViewport.DEFAULT_ON`, a shipped default for the user's switch;
the apps themselves are untouched). An app's switch is forgotten when the app is removed.

**Where.** Device Info's "Software — Use Phone Zoom" group (the switch is called Phone Zoom there) lists every app as the TouchPad's Device Info did
(title on the left, "v" and the version on the right), with the switch at the right of each
row; `system/setFixedViewport {"id", "on"}` on Lunacy's own service, and `system/getEnvironment`
reports `software`. A change takes effect when the app's window next loads a page.

**How.** The viewport, as the compat layer already writes it (compat.js, "The viewport"):
`AppWindow.cardSize` reports the card in layout px at the scaled density, and the meta
that follows (`width=640, height=1088, minimum-scale=maximum-scale=0.5625` on the Nexus 5)
does the rest. The keyboard's `positiveSpaceChanged` is sent in the same px
(`AppWindow.pageScale`). The mismatch between the device's numbers and the page's px is the
Pre3's own: it laid pages out at 320 × 533 under a `ScaleFactor` of 1.5.

Seen on the Nexus 5 at 640: Clock, Calculator and Device Info fit whole and read well;
Memos fits and leaves a grey band under its grid, because the grid is a fixed 943 px, the
TouchPad portrait's height less its bars, and the 1088 px page is taller than a TouchPad
ever was (the architecture doc's "1024 × 768, scaled to fit" would need the card's aspect
too; 640 is the width that reads best, and it is a setting).

## 5. Not yet measured

- Nothing here has been put beside a Pre3 or a TouchPad in the same state; the TouchPad in
  portrait was at its lock screen when the first screenshots were taken.
- Rotation of the phone with the launcher open: the tab bar's scroll is clamped, the grid
  re-lays out at seven columns, not yet watched.
- The keyboard on a phone with the app scale on: `positiveSpaceChanged` is scaled, Enyo's
  own resize path is the window's and needs no scaling, not yet watched with a field.

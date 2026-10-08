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

- **Screen & Lock's Layout group**, a Screen Layout selector (Automatic, Phone, Tablet) on
  its main view between the screen settings and Wallpaper, with a note saying what the
  screen reads as. Choosing a layout that differs from the one showing restarts the shell.
  This is codepoet's exception to rules 0 and 5 (2026-10-03): a TouchPad's Screen & Lock
  had no such group, and the app is otherwise Palm's, unchanged (its NOTICE says so; the
  addition is marked `[Lunacy]` in its source). It moved here from Device Info because a
  screen's layout is a setting of the screen, and it should be in plain sight, not behind
  a tap on an information row. The app's stylesheet also gained a media query so its 500 px
  column fits a card under 540 px wide (the Nexus 5's 360), the way Device Info's
  `max-width` column does; every footnote under a group, Palm's two included, has 6 px
  above and 9 px below (codepoet, 2026-10-03). Lunacy's own Sounds & Alerts has the same
  media query for its column, so it fits a phone too; Device Info already did.
- Device Info's Display group still has a Layout row saying what was decided and from
  what ("Phone (from the screen)"), read-only, and a Phone Zoom Width row below it that
  opens the width (§4).
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
| Cell | 128 square | **84 wide, 112 tall** | Four columns across a 360 px portrait, seven across its 640 px landscape. The height is the TouchPad's at seven-eighths, which a 56 px icon over a two-line label needs; an 84 px square held 92 px of icon and label and each row's labels ran into the next row's icons (codepoet, 2026-10-02) |
| Icon | 64 | **56** | Seven-eighths; a group's 68 px composite is drawn at the same size |
| Label | 100 wide, 14 px bold | 80 wide, **13 px** bold | Two lines; the TouchPad's 14 read heavy under a 56 px icon (codepoet, 2026-10-03) |
| Columns | LunaCE's rule from `MaxIconsPerRow` | as many cells as fit between 6 px margins, the rest shared as gaps | The TouchPad rule gives two columns at 360 |
| Row gap, top margin, icon lift above the cell centre | 10, 20, 11 | 9, 18, 10 | Seven-eighths |
| Launch glow, delete badge offset | 90; −50, −50 | 80; −30, −44 | The glow scaled with the icon; the badge 12 px in from the cell's corner, the TouchPad's 14 at seven-eighths. Edit mode's frame, a 128 px square on a tablet, is drawn as a nine-slice (16 px corners) to fill the taller cell |

The group panel caps its columns at what the width holds (a phone's 360 holds two 118 px
cells), and the Rename/New Tab dialog shrinks from 520 px to the width less its padding.

## 3. The dock (`QuickLaunch`)

| | TouchPad | Phone |
|---|---|---|
| Icons | up to 5 | **up to 4** |
| Launcher button | its own place, 64 px in from the right edge, the icons sharing the space to its left | **the fifth of five equal slots**: the same size as the icons, no room of its own on the right |
| Icon size | 64 | **59**, or 78 % of a slot where that is narrower (56 px on a Nexus 5 portrait); the launcher button the same |
| Height, icon centre from the top | 100, 54 | **95, 51** |

The dock is 5 % shorter and its icons 8 % smaller than the tablet's (codepoet, 2026-10-03; both
measured against the TouchPad's dock on the Nexus 5). A dock saved with five icons on a tablet shows four on a phone;
the fifth is kept in the saved list.

## 4. Fixed viewport, per app

Two things, each with one owner (codepoet, 2026-10-02):

- **What an app is told** is the global decision above: on a phone every app hears Pre3,
  webOS 2.2.4 and the Pre3's user agent, so any per-device logic in it sees a phone.
- **Whether its page is scaled** is per app, the fixed-viewport fallback the rules already
  allow (architecture.md, "No per-app hacks"), on or off, in `card/FixedViewport.kt`.

A TouchPad app is written for a 1024 × 768 screen: Palm's Clock is a 514 px face, its
Calculator a 500 × 690 panel, Memos a 943 px grid. On a 360 px card they are simply
clipped. With the switch **on** the page is laid out so that the card's **shorter side** is
the **fixed viewport width** (`FormFactor.appLayoutWidth`, default **640** px, set in Device
Info's Phone Zoom Width row), and the card shows it whole, scaled down to fit: on a Nexus 5 in
portrait 360 / 640 = 0.5625, 1.6875 device px per CSS px, a 640 × 1088 page; in landscape the
640 × 332 card shows a 1234 × 640 page at 0.519. Until 2026-10-04 only the width counted, and
a phone in landscape showed these apps at 1, clipped below about 330 px; Calculator 3.2.0's
landscape scientific layout (it wants a window 900 px wide) is what showed it. The switch only
acts in the phone layout: a tablet's card is a TouchPad's size already, and the Pixel
Tablet's landscape card (1024 × 612) would otherwise be shrunk. With the
switch **off**, the default, the page is laid out at the card's own width, which is what
every app written for the Pre3 wants: the SDK had it declare its viewport
(`height=device-height`) and size itself with the framework.

**Defaults.** Off, except the bundled Palm apps known to draw a fixed screen - Clock,
Calculator and Memos - and the two whose chrome is a TouchPad's width, the Web app (its
action bar left no room for the address) and App Catalog (codepoet, 2026-10-04)
(`FixedViewport.DEFAULT_ON`, a shipped default for the user's switch; the apps themselves are
untouched). An app's switch is forgotten when the app is removed.

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

## 5. Just Type (`JustTypePanel`)

| | TouchPad | Phone |
|---|---|---|
| Field width | 570 | the width less 12 px margins |
| Group width | 736 | the width less 12 px margins |
| Tiles | 85 × 113 at a 138 px pitch from 40 px in | 85 × 113, as many to a row as fit inside the group's 14 px borders with equal gaps of at least 10 px: three across a Nexus 5 portrait (13 px gaps), six across its landscape (11 px) |

The header, field height, row pitch and the "Search DuckDuckGo" row are the TouchPad's.
Done 2026-10-03 (codepoet: "it's too wide"); nothing changes on a tablet (the launcher,
its edit mode, the dock and Just Type checked unchanged on the Pixel Tablet the same day).

**Typing opens it.** A keyboard's printable key in the card view or the launcher opens Just
Type with that character, as on a TouchPad with a keyboard paired: `ShellActivity`'s
`dispatchKeyEvent`, not while a card is up, a text field (a tab's or a group's name) has the
focus, or Ctrl, Alt or Meta is held. Both form factors; checked on the Nexus 5 with
`adb shell input keyevent KEYCODE_T` from the launcher (codepoet, 2026-10-02; done
2026-10-03).

## 6. Palm's apps on a phone's card

Two general fixes, nothing per app; neither changes a tablet.

- **The centred column.** Palm's settings and Welcome screens sit in one `.box-center` column
  500 px wide (the accounts library's first-launch view, the Accounts app, Exhibition, Email's
  settings). Under 540 px it takes the card's width less 12 px each side, Lunacy-wide, from
  `compat.js` (codepoet, 2026-10-08) - the rule Lunacy's own Screen & Lock and Sounds & Alerts
  carried in their stylesheets.
- **Enyo's phone layout.** A `SlidingPane` under its `multiViewMinWidth` stacks its views at the
  card's width and slides the selected one over the rest; Enyo patch 0010 makes that layout
  work (it never had on a modern engine). Photos & Videos uses it for albums: tap one and its
  pictures slide over the libraries, drag them right to go back. A library (All Photos &
  Videos) doesn't slide over, because HP's phone code covers only albums.
- **Email** finishes HP's phone layout in the app itself (codepoet's exception to rule 5,
  2026-10-08; its NOTICE lists the changes): a tapped folder or message brings its panel over,
  and a swipe in from the right edge (the right 64 px; the outer 15 are the gesture dead zone)
  brings the next panel over; the grab handle in the message's toolbar takes it back.
  **Kept in mind:** the swipe from the edge and "a tap that changes the next panel brings it
  over" could be made Enyo's own for every stacked `SlidingPane` (a version was written as
  an Enyo patch and withdrawn the same day). codepoet expects most apps already bring their
  panels on themselves; Email and Photos were HP's unfinished ones.

## 7. Not yet measured

- Nothing here has been put beside a Pre3 or a TouchPad in the same state; the TouchPad in
  portrait was at its lock screen when the first screenshots were taken.
- Rotation of the phone with the launcher open: the tab bar's scroll is clamped, the grid
  re-lays out at seven columns, not yet watched.
- The keyboard on a phone with the app scale on: `positiveSpaceChanged` is scaled, Enyo's
  own resize path is the window's and needs no scaling, not yet watched with a field.

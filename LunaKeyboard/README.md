# LunaKeyboard

An Android input method that looks and behaves like the HP TouchPad's keyboard.

This is a **companion app, not part of Lunacy**. It is a separate APK - built by the same
Gradle root for convenience, but sharing no code with the shell - and it works anywhere in Android - in Chrome, in
Settings, in any app with a text field. Lunacy neither needs it nor knows about it. Install
it because Android's own keyboard is enormous, or don't install it at all.

## What it is

webOS's tablet keyboard, rebuilt as an `InputMethodService`:

- Palm's **US QWERTY tablet layout**, key for key.
- LunaCE's **key art**, drawn at the sizes webOS drew it.
- **Press and hold** a key for its accents and alternates, in Palm's own order.
- The **symbol page** behind `+ = [ ]`, and shift / shift-lock on a double tap. Which of the
  two the keys reach is webOS's rule and not the obvious one (`TabletKeymap::map`): a letter
  takes its alternate from the symbol key, and shift only capitalises it; the number row and
  the punctuation take theirs from **shift**, so shift on `1` gives `!` and the symbol page
  leaves them alone. **Shift-lock is shift for this, and survives a letter but not a symbol:**
  locked, the letters keep coming out capital, and the first key that takes its alternate -
  anything off the number row - ends the lock. Typed on the tablet, locked: `q w 1 q e` gives
  `QW!qe`.
- The **scroll ball**: the little ball in the top right corner that drags the insertion
  point around. Hold shift while you drag it and it selects instead.

## The scroll ball

The ball is LunaCE's, not Palm's: the community LunaSysMgr added `cKey_Trackball` to the
tablet keymap, two units wide at the end of the number row. Dragging it steps the cursor one
character for every 15 px of travel, and the four arrows around it light up in the direction
it is being pushed, fading back over the next second once it is let go. Here it moves the
cursor with arrow-key events, so it works in any app's text field.

## The press-and-hold balloon

The balloon has to reach above the keyboard: two lines of accents are 150 px tall and a
second-row key has only 60 px above it. webOS had that freedom for nothing - LunaSysMgr lays
its keymap out inside the whole available screen rather than inside the keys, so a popup could
float anywhere.

On Android the obvious answer is a `PopupWindow`, and it is the wrong one. It draws in the
right place, but **Android clamps a touch to the frame of the window that caught it**: drag a
finger above the keyboard and every move arrives with `y` pinned to `0`. The balloon's upper
row could be seen and never touched.

So the input view is simply taller than the keyboard - by `popup-bg-2.png`'s height less the
10 px it overlaps its key - and the extra is transparent. `onComputeInsets` then tells Android
two things:

- `contentTopInsets` / `visibleTopInsets` are the top of the **keyboard**, so the app is
  pushed up by the keys and not by the room above them.
- `touchableInsets = TOUCHABLE_INSETS_REGION` with the region set to the keyboard, so a tap in
  that room goes to the app. The balloon still gets the finger that opened it, because a
  gesture stays with the window that caught it.

## One size, and why

The keyboard has a single height, and it is the art's own. Nothing is ever scaled.

LunaCE offered four heights - 243, 291, 340 and 393 px - and scaled its pictures to fit
them. Scaling costs you the glyphs and the key bevels: at anything but the tallest, the
backspace and hide-keyboard icons are resampled and go soft, and the rounded corners smear.
LunaCE knew it, and shrank its own nine-tile corners to compensate as the keys got smaller
(`m_9tileCorner.m_trimV`, set from how far below the ideal height you are).

So this keyboard draws at the ideal height and stops there. LunaCE names it:

```c
// assets give us "ideal" non-scaled sizes.
int fullKeymapHeight = (m_short_gray_key.height() + (cKeymapRows - 1) * m_white_key.height()) / 2;
```

That is **335 px** of keys - 55 for the number row, 70 for each of the other four - because a
key plate image holds two states stacked, the released key above the pressed one, so a
plate's own row height is half its height. Add the 5 px of background webOS left above the
keys and the keyboard is 340 px, the height of `keyboard-bg.png`.

A key is only ever stretched **sideways**, through the flat middle of its nine-slice, to
share out however wide the screen is. Its bevels, its icons and its lettering are drawn at
1:1 and land on whole pixels. On the reference tablet that makes a key about 20 mm across,
against a TouchPad's 18 mm.

## Where the numbers came from

Nothing here was written from memory. The layout, the alternate characters, the extended
lists, the colours and the timings are read out of LunaCE's source, and the geometry was
measured against screenshots of a real TouchPad:

| What | Where |
|---|---|
| Layout, weights, alternates, extended lists | `Src/ime/tabletkeymaps/en.h`, `common_keys.h` |
| Key art, plate choice, cap layout, colours | `Src/ime/TabletKeyboard.cpp` |
| Row heights, key zones, page switching | `Src/ime/TabletKeymap.cpp` |
| The ideal, unscaled height | `TabletKeyboard::setKeyboardHeight` |

A TouchPad screenshot of the keyboard measures 291 px tall, which is exactly LunaCE's Small
preset - that screenshot had been resized down - and its keys measure 93 px across, the
width of the art. The art is 1:1, which is how it is drawn here.

One visible consequence: at the ideal height the `/ ,` and `? .` keys stack their two
characters, where a resized-down keyboard lays them side by side. That is webOS's own rule -
a key too short to stack them turns them sideways (`height / 3 < fontSize - 2`) - and at the
ideal height they fit.

## Building and installing

```sh
./gradlew :LunaKeyboard:assembleDebug        # from the top of the repo
adb install -r out/LunaKeyboard-debug.apk
```

A sideloaded keyboard has to be switched on and then chosen, neither of which Android makes
obvious. The app's launcher entry does both, and has a field to try it in. Over `adb`:

```sh
adb shell ime enable org.webosarchive.keyboard/.KeyboardService
adb shell ime set org.webosarchive.keyboard/.KeyboardService
```

`adb shell ime set <the old one>` puts the previous keyboard back.

Two things to know when reinstalling. Android **won't uninstall the keyboard while it is
the selected one** - `adb uninstall` answers `DELETE_FAILED_INTERNAL_ERROR` - so switch
away first. And replacing the APK deselects it, so `ime set` it again afterwards.

## Not yet

- **Only US QWERTY.** LunaCE ships fourteen keymaps; the rest are a port away. While there
  is one layout the symbol key is two units wide, which is what webOS did - the second unit
  is the language key's, and it only appears when there is a language to switch to.
- **One height.** Resizing is deliberately gone, for the reasons above. Restoring it means
  accepting resampled glyphs, or a second set of art.
- **No suggestion bar**, so no auto-correct and no learned words.
- **No phone layout.** The tablet layout scales down, but webOS had a separate one
  (`PhoneKeyboard.cpp`) for narrow screens.
- **No key sounds or haptics.**

## Licence and provenance

See [NOTICE](NOTICE). The art and the ported layout are LunaCE's, Apache 2.0; the Prelude
fonts ship as abandonware.

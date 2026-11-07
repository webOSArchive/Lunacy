#!/usr/bin/env python3
"""
Makes Lunacy's launcher icon and notification icon from codepoet's artwork in Meta/.

  python3 AndroidLuna/tools/make-icons.py

- The launcher icon is Meta/LunacyRobot.png (the Android robot with a door open on the night
  sky and the moon) in front of a patch of Meta/LunaOnly.png's starry sky.
  - Android 8 and later: an adaptive icon (mipmap-anydpi-v26/ic_launcher.xml) with those two
    layers, 108 dp each, the robot as large as fits inside a round mask's 72 dp circle uncut,
    and a white silhouette of the robot as the monochrome layer for Android 13's themed icons.
  - Android 5 to 7: ic_launcher.png, the same two layers in a rounded rectangle, 48 dp.
- The splash logo is the robot alone (drawable-*/lunacy_logo.png), on splash_background.
- The notification small icon is the crescent from Meta/LunaRound.png as a white silhouette
  (drawable-*/ic_stat_lunacy.png, 24 dp), as Android draws a small icon.

Needs Pillow. Writes into AndroidLuna/src/main/res.
"""
import os
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
META = os.path.join(ROOT, "Meta")
RES = os.path.join(ROOT, "AndroidLuna", "src", "main", "res")
DENSITIES = [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0)]

# The robot's height in the 108 dp adaptive layer: the largest that keeps every pixel inside
# the round mask's 72 dp circle (measured: 68 fits, 70 crosses it by a fraction of a pixel).
ROBOT_DP = 68
# The legacy icon's corner radius, as a share of its side (the old icon's tile was about this).
LEGACY_RADIUS = 0.18
# The legacy icon's tile within its 48 dp: Android's guideline keylines leave a 2 dp margin.
LEGACY_TILE_DP = 44
SPLASH_PX = 480


def robot():
    r = Image.open(os.path.join(META, "LunacyRobot.png")).convert("RGBA")
    return r.crop(r.getbbox())


def sky():
    # A moon-free corner of LunaOnly's sky (its bottom left, 420 px square).
    return Image.open(os.path.join(META, "LunaOnly.png")).convert("RGBA").crop((0, 808, 420, 1228))


def robot_layer(px, robot_dp=ROBOT_DP, layer_dp=108):
    """The robot centred in a transparent square px wide, robot_dp of layer_dp high."""
    r = robot()
    h = round(px * robot_dp / layer_dp)
    w = round(r.width * h / r.height)
    layer = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    small = r.resize((w, h), Image.LANCZOS)
    layer.paste(small, ((px - w) // 2, (px - h) // 2), small)
    return layer


def rounded_mask(px, radius):
    m = Image.new("L", (px * 4, px * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, px * 4 - 1, px * 4 - 1), radius=radius * 4, fill=255)
    return m.resize((px, px), Image.LANCZOS)


def crescent():
    """The moon from LunaRound as a white silhouette, trimmed to itself: stars and glow left out."""
    im = Image.open(os.path.join(META, "LunaRound.png")).convert("RGBA")
    lum = im.convert("L")
    # The moon is far brighter than the sky; its glow and the stars are smaller or dimmer.
    m = lum.point(lambda v: 255 if v > 110 else 0)
    # A closing (dilate, then erode) fills the darker craters; an opening (erode, then dilate)
    # removes the stars, which are a few pixels across.
    m = m.filter(ImageFilter.MaxFilter(15)).filter(ImageFilter.MinFilter(15))
    m = m.filter(ImageFilter.MinFilter(21)).filter(ImageFilter.MaxFilter(21))
    m = m.crop(m.getbbox())
    side = max(m.size)
    sq = Image.new("L", (side, side), 0)
    sq.paste(m, ((side - m.width) // 2, (side - m.height) // 2))
    white = Image.new("RGBA", sq.size, (255, 255, 255, 255))
    white.putalpha(sq)
    return white


def save(img, *path):
    out = os.path.join(RES, *path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out, optimize=True)


def main():
    moon = crescent()
    for name, scale in DENSITIES:
        layer = round(108 * scale)
        save(sky().resize((layer, layer), Image.LANCZOS), "mipmap-" + name, "ic_launcher_background.png")
        fg = robot_layer(layer)
        save(fg, "mipmap-" + name, "ic_launcher_foreground.png")
        # The robot as one colour, its window on the night sky cut out (the moon kept), so the
        # door still reads in a themed icon.
        mono = Image.new("RGBA", fg.size, (255, 255, 255, 0))
        lit = fg.convert("L").point(lambda v: 255 if v > 45 else 0)
        mono.putalpha(ImageChops.multiply(fg.split()[3], lit))
        save(mono, "mipmap-" + name, "ic_launcher_monochrome.png")

        # Android 5 to 7: the same picture in a rounded rectangle, the robot a little larger.
        legacy = round(48 * scale)
        tile = round(LEGACY_TILE_DP * scale)
        t = sky().resize((tile, tile), Image.LANCZOS)
        rb = robot_layer(tile, robot_dp=40, layer_dp=48)
        t.alpha_composite(rb)
        t.putalpha(ImageChops.multiply(t.split()[3], rounded_mask(tile, round(tile * LEGACY_RADIUS))))
        icon = Image.new("RGBA", (legacy, legacy), (0, 0, 0, 0))
        icon.paste(t, ((legacy - tile) // 2, (legacy - tile) // 2), t)
        save(icon, "mipmap-" + name, "ic_launcher.png")

        stat = round(24 * scale)
        inner = round(22 * scale)
        m = moon.resize((inner, inner), Image.LANCZOS)
        s = Image.new("RGBA", (stat, stat), (255, 255, 255, 0))
        s.paste(m, ((stat - inner) // 2, (stat - inner) // 2), m)
        save(s, "drawable-" + name, "ic_stat_lunacy.png")

    for name, scale in DENSITIES[:4]:
        px = round(SPLASH_PX * scale / 3.0)
        r = robot()
        h = px
        w = round(r.width * h / r.height)
        logo = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        small = r.resize((w, h), Image.LANCZOS)
        logo.paste(small, ((px - w) // 2, 0), small)
        save(logo, "drawable-" + name, "lunacy_logo.png")

    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<!-- Made by AndroidLuna/tools/make-icons.py from Meta/LunacyRobot.png and Meta/LunaOnly.png. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""
    os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
    with open(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), "w") as f:
        f.write(adaptive)


if __name__ == "__main__":
    main()

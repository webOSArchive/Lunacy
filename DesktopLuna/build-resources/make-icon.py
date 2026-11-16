#!/usr/bin/env python3
"""Luna DE's icon, from Lunacy's moon (Meta/LunaRound.png; its NOTICE says where that came from).

  python3 DesktopLuna/build-resources/make-icon.py

Writes, beside this file: icon.png (1024, the whole square with rounded corners; Linux and
Windows, which electron-builder turns into an .ico), icon-mac.png (the same art at macOS's
proportions: an 824 px rounded square in a 1024 px canvas, as its icon grid has it), and
../app/images/icon.png (256, the window's own icon on Linux). Needs Pillow.
"""
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "..", "Meta", "LunaRound.png")
S = 4  # the corner mask is drawn large and scaled down, for a smooth edge


def rounded(art, size, radius):
    im = art.resize((size, size), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (size * S, size * S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size * S - 1, size * S - 1], radius * S, fill=255)
    im.putalpha(mask.resize((size, size), Image.LANCZOS))
    return im


art = Image.open(SRC).convert("RGB")
rounded(art, 1024, 180).save(os.path.join(HERE, "icon.png"))
mac = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
mac.paste(rounded(art, 824, 185), (100, 100))
mac.save(os.path.join(HERE, "icon-mac.png"))
rounded(art, 256, 45).save(os.path.join(HERE, "..", "app", "images", "icon.png"))
print("icon.png, icon-mac.png, app/images/icon.png")

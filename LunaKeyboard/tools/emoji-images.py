#!/usr/bin/env python3
"""Make the keyboard's emoji images from webOS CE's emoji list.

    emoji-images.py <ce-emoji dir> <work dir>

<ce-emoji dir> is webos-doctor-ce's packages/ce-emoji: its keyboard-emoji.txt is the emoji
layer's list, in key order, and its fetch-openmoji.py fetches OpenMoji at the release CE pins.
The 618 px originals go into <work dir> (kept, so a second run fetches nothing), and each is
written to src/main/assets/emoji/<HEX>.png at 144 px: the art CE draws from its 72 px set,
sharp at the keyboard's scale 2 as well as 1. keyboard-emoji.txt is copied beside them, which
is what the keyboard reads. OpenMoji is CC BY-SA 4.0: see the NOTICE there.
"""
import os, shutil, subprocess, sys
from PIL import Image

SIZE = 144


def main():
    ce, work = sys.argv[1], sys.argv[2]
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'assets', 'emoji')
    listing = os.path.join(ce, 'keyboard-emoji.txt')
    subprocess.check_call([sys.executable, '-I', os.path.join(ce, 'fetch-openmoji.py'), listing, work, '618x618'])
    os.makedirs(out, exist_ok=True)
    for name in sorted(os.listdir(work)):
        if not name.endswith('.png'):
            continue
        img = Image.open(os.path.join(work, name)).convert('RGBA').resize((SIZE, SIZE), Image.LANCZOS)
        # A palette, as OpenMoji's own files are: the art is flat colour, and it is a third the size.
        img.quantize(colors=256, method=Image.FASTOCTREE).save(os.path.join(out, name), optimize=True)
    shutil.copyfile(listing, os.path.join(out, 'keyboard-emoji.txt'))


if __name__ == '__main__':
    main()

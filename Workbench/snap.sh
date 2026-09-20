#!/bin/sh
adb exec-out screencap -p > results/$1.png
python3 -c "
from PIL import Image; im=Image.open('results/$1.png'); im.rotate(-90,expand=True).save('results/$1.png')"

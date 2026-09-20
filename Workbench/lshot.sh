#!/bin/sh
# Screenshot the Android tablet into results/<name>.png, upright.
adb exec-out screencap -p > results/$1.png
python3 -c "
from PIL import Image; im=Image.open('results/$1.png')
w,h=im.size
if w<h and '$2'!='portrait': im=im.rotate(-90,expand=True)
im.save('results/$1.png')"

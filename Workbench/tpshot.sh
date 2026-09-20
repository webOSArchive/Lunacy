#!/bin/sh
# Screenshot the connected TouchPad into results/<name>.png
./tp.sh "luna-send -n 1 palm://com.palm.systemmanager/takeScreenShot '{\"file\":\"/media/internal/lunacy-shot.png\"}'" 4 >/dev/null
novacom get file:///media/internal/lunacy-shot.png > results/$1.png
python3 -c "from PIL import Image; im=Image.open('results/$1.png'); im.rotate(${2:--90},expand=True).save('results/$1.png')"

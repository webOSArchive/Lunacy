#!/bin/sh
# usage: shot.sh name url bridge(true|false) [wait]
name=$1; url=$2; bridge=${3:-true}; wait=${4:-12}
adb shell am force-stop org.webosarchive.lunacy.spike
adb logcat -c
adb logcat -v brief LunacySpike:V chromium:E AndroidRuntime:E '*:S' > results/$name.log &
LP=$!
adb shell am start -n org.webosarchive.lunacy.Workbench/.CardActivity --es url "$url" --ez bridge $bridge >/dev/null
sleep $wait
adb exec-out screencap -p > results/$name.png
kill $LP
python3 -c "
from PIL import Image; im=Image.open('results/$name.png'); im.rotate(-90,expand=True).save('results/$name.png')"

#!/bin/sh
# Launch an app on the TouchPad and take a burst of screenshots on the device itself, to see
# what the shell does in the first seconds: tpburst.sh <appid> <name> [count]
n=${3:-10}
./tp.sh "luna-send -n 1 palm://com.palm.applicationManager/launch '{\"id\":\"$1\"}' >/dev/null; for i in \$(seq 1 $n); do date +%s.%N | cut -c7-14 > /media/internal/burst-\$i.t; luna-send -n 1 palm://com.palm.systemmanager/takeScreenShot '{\"file\":\"/media/internal/burst-'\$i'.png\"}' >/dev/null; done" $((n/2+4)) >/dev/null
for i in $(seq 1 $n); do novacom get file:///media/internal/burst-$i.png > results/$2-$i.png; t=$(novacom get file:///media/internal/burst-$i.t); echo "$i $t"; done

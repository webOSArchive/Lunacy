#!/bin/sh
# Launch (or relaunch) an app on the TouchPad over the bus, as an app would: tplaunch.sh <appid> '<json params>'
./tp.sh "luna-send -n 1 palm://com.palm.applicationManager/launch '{\"id\":\"$1\",\"params\":${2:-{\}}}'" ${3:-2} | grep -o '{.*returnValue.*}'

#!/bin/sh
# Launch (or relaunch) an app in Lunacy on the Android tablet: alaunch.sh <appid> '<json params>' [wait]
adb shell "am start -n org.webosarchive.lunacy/.shell.ShellActivity --es launch $1 --es params '${2:-{\}}'" >/dev/null
sleep ${3:-2}

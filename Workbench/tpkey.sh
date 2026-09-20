#!/bin/sh
# Inject a key press+release on the TouchPad's gpio-keys device. $1 = keycode (octal, e.g. 146 = KEY_HOME 102)
Z='\000\000\000\000\000\000\000\000'
./tp.sh "printf '$Z\001\000\\$1\000\001\000\000\000$Z\000\000\000\000\000\000\000\000' > /dev/input/event0; printf '$Z\001\000\\$1\000\000\000\000\000$Z\000\000\000\000\000\000\000\000' > /dev/input/event0" 2 >/dev/null

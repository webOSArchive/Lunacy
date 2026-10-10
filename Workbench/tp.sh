#!/bin/sh
# Run one shell command on the connected TouchPad through a pty (luna-send needs one).
# The TouchPad by name: with Lunacy's novacom relay attached too, novaterm's default device
# may be Lunacy (TP_DEVICE overrides it).
(printf "%s\n" "$1"; sleep ${2:-3}; echo exit) | timeout 60 novaterm -d ${TP_DEVICE:-topaz-linux} 2>&1 | tr -d '\r'

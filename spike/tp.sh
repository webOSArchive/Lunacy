#!/bin/sh
# Run one shell command on the connected TouchPad through a pty (luna-send needs one).
(printf "%s\n" "$1"; sleep ${2:-3}; echo exit) | timeout 60 novaterm 2>&1 | tr -d '\r'

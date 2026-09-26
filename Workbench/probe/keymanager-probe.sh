#!/bin/sh
# Records com.palm.keymanager's replies on a TouchPad: novacom put, then sh (as root). See KeyManager.kt.
A="-a org.webosarchive.lunacy.probe"; K=palm://com.palm.keymanager
c() { echo "== $1"; shift; luna-send -n 1 "$@" 2>&1; }
c "fetch missing" $A $K/fetchKey '{"keyname":"lunacyprobe"}'
c "keyInfo missing" $A $K/keyInfo '{"keyname":"lunacyprobe"}'
c "remove missing" $A $K/remove '{"keyname":"lunacyprobe"}'
c "store" $A $K/store '{"keyname":"lunacyprobe","keydata":"{\"a\":1}","type":"ASCIIBLOB","nohide":true}'
c "store again" $A $K/store '{"keyname":"lunacyprobe","keydata":"x","type":"ASCIIBLOB","nohide":true}'
c "fetch" $A $K/fetchKey '{"keyname":"lunacyprobe"}'
c "keyInfo" $A $K/keyInfo '{"keyname":"lunacyprobe"}'
c "fetch other app" -a com.other.app $K/fetchKey '{"keyname":"lunacyprobe"}'
c "remove" $A $K/remove '{"keyname":"lunacyprobe"}'
c "fetch no name" $A $K/fetchKey '{}'
c "store hidden" $A $K/store '{"keyname":"lunacyprobe2","keydata":"x","type":"ASCIIBLOB"}'
c "fetch hidden" $A $K/fetchKey '{"keyname":"lunacyprobe2"}'
c "keyInfo hidden" $A $K/keyInfo '{"keyname":"lunacyprobe2"}'
c "remove hidden" $A $K/remove '{"keyname":"lunacyprobe2"}'

#!/bin/sh
# Lays out what a ported app's bundle needs beside the app itself, in <dest>:
#   lunacy/     Lunacy's compatibility layer, shared with AndroidLuna as it is
#   fonts/      the Prelude family, which webOS had installed
#   fw/enyo/1.0 the TouchPad's Enyo with Lunacy's patches, as webos-sdk-redux ships it
# An app's Xcode project runs this as a build phase. Nothing here is edited on the way: a fix
# to any of it belongs upstream (Lunacy's assets, or LunaRuntimes/enyo-1.0/patches and the SDK).
#
# ENYO_FRAMEWORK overrides where Enyo comes from; by default a webos-sdk-redux clone beside
# this one.
set -e
[ -n "$1" ] || { echo "usage: $0 <dest>" >&2; exit 1; }
HERE=$(cd "$(dirname "$0")" && pwd)
ASSETS=$HERE/../AndroidLuna/src/main/assets
ENYO=${ENYO_FRAMEWORK:-$HERE/../../webos-sdk-redux/0.4.1/share/framework/enyo/1.0}
[ -f "$ENYO/framework/build/enyo-build.js" ] || { echo "assemble-assets: no Enyo at $ENYO (set ENYO_FRAMEWORK)" >&2; exit 1; }
DEST=$1
mkdir -p "$DEST/lunacy" "$DEST/fonts" "$DEST/fw/enyo"
# The page-side scripts only: services/ and the Mojo boot files are for things iOSLuna doesn't run.
for f in compat.js bridge.js crypt.js textedit.js net.js websql.js fonts.css; do
    cp "$ASSETS/lunacy/$f" "$DEST/lunacy/$f"
done
rsync -a --delete "$ASSETS/luna/fonts/" "$DEST/fonts/"
rsync -a --delete "$ENYO/" "$DEST/fw/enyo/1.0/"

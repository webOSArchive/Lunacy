#!/bin/sh
# Builds the font probe with the Prelude files (and the device's core fonts, when a TouchPad
# is connected) inside it, so it can load each one by a name of its own. The fonts stay out
# of the repository: they are copied into a scratch build tree.
set -e
here=$(cd "$(dirname "$0")" && pwd)
out=${1:-/tmp/fontprobe-build}
rm -rf "$out"; mkdir -p "$out"
cp -a "$here/org.webosarchive.lunacy.fontprobe" "$out/"
mkdir -p "$out/org.webosarchive.lunacy.fontprobe/fonts"
cp "$here"/../../AndroidLuna/src/main/assets/luna/fonts/Prelude*.ttf "$out/org.webosarchive.lunacy.fontprobe/fonts/"
for f in arial arialbd verdana verdanab georgia times cour; do
	novacom get file:///usr/share/fonts/$f.ttf > "$out/org.webosarchive.lunacy.fontprobe/fonts/$f.ttf" 2>/dev/null || rm -f "$out/org.webosarchive.lunacy.fontprobe/fonts/$f.ttf"
done
(cd "$out" && palm-package org.webosarchive.lunacy.fontprobe >/dev/null)
ls "$out"/*.ipk

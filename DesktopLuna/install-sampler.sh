#!/bin/sh
# Installs webOS Archive's Enyo 1 Sampler (Style Matters, from the webOS SDK's samples) into Luna
# DE's data folder, as the first test app: https://github.com/webOSArchive/enyo1-sampler.
#
#   DesktopLuna/install-sampler.sh <Luna DE data folder>
#
# The sampler carries its own Enyo as a git submodule; on a device an Enyo app loads the system's,
# so its index.html is pointed at /usr/palm/frameworks/enyo/1.0/framework/enyo.js, which Lunacy
# serves with its patches (the same tree the SDK ships). codepoet, 2026-11-15: test apps load
# Lunacy's own Enyo (and Mojo), patched. Nothing else of the app is changed.
set -e
DATA=${1:?usage: $0 <Luna DE data folder>}
COMMIT=306aa2f
ID=com.palmdts.enyo.stylematters
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
git clone -q https://github.com/webOSArchive/enyo1-sampler.git "$WORK/src"
git -C "$WORK/src" checkout -q "$COMMIT"
rm -rf "$WORK/src/.git" "$WORK/src/enyo" "$WORK/src/.gitmodules"
sed -i 's#<script src="enyo/enyo.js" launch="debug" type="text/javascript"></script>#<script src="/usr/palm/frameworks/enyo/1.0/framework/enyo.js" type="text/javascript"></script>#' "$WORK/src/index.html"
grep -q '/usr/palm/frameworks/enyo/1.0/framework/enyo.js' "$WORK/src/index.html" || { echo "index.html didn't take the system Enyo" >&2; exit 1; }
DEST="$DATA/cryptofs/apps/usr/palm/applications/$ID"
rm -rf "$DEST"; mkdir -p "$(dirname "$DEST")"; cp -r "$WORK/src" "$DEST"
echo "installed $ID in $DEST"

#!/bin/sh
# Hands out/'s release APKs to the webOS Archive's F-Droid repository, which adds them and
# re-signs its index.
#
#   Meta/fdroid/update-repo.sh [repository project]
#
# The repository is a project of its own (default ~/Projects/webosarchive-fdroid), since it
# carries other apps too; Lunacy's descriptions are its metadata/org.webosarchive.*.yml. See
# README.md here.
set -eu
cd "$(dirname "$0")/../.."
FEED=${1:-$HOME/Projects/webosarchive-fdroid}
[ -x "$FEED/add-apks.sh" ] || { echo "update-repo: no repository project at $FEED" >&2; exit 1; }

# Only release builds go in: the debug APKs carry another signature and can't update them.
set -- out/AndroidLuna-arm32-release.apk out/AndroidLuna-arm64-release.apk out/LunaKeyboard-release.apk
for apk; do [ -f "$apk" ] || { echo "update-repo: $apk is missing; run ./build.sh publish first" >&2; exit 1; }; done
(cd out && grep -E ' (AndroidLuna-arm(32|64)|LunaKeyboard)-release\.apk$' SHA256SUMS | sha256sum -c --quiet) ||
    { echo "update-repo: out/ doesn't match its SHA256SUMS" >&2; exit 1; }

exec "$FEED/add-apks.sh" "$(pwd)/$1" "$(pwd)/$2" "$(pwd)/$3"

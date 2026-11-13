#!/bin/sh
# Adds out/'s release APKs to Lunacy's own F-Droid repository and re-signs its index.
#
#   Meta/fdroid/update-repo.sh [workspace]
#
# The workspace (default ~/Projects/LunacyFDroid) holds config.yml and keystore.p12, the
# repository's own signing key: never commit either, and keep a backup of the key - a lost key
# means every user has to remove the repository and add it again. After this, upload the
# workspace's repo/ and archive/ folders as they are to https://www.webosarchive.org/fdroid/.
# README.md says how the workspace and fdroidserver were set up.
set -eu
cd "$(dirname "$0")/../.."
LUNACY=$(pwd)
WORK=${1:-$HOME/Projects/LunacyFDroid}
FDROID=${FDROID:-$HOME/.local/share/fdroidserver/bin/fdroid}
SDK=${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties)}
AAPT2=$(ls -d "$SDK"/build-tools/*/aapt2 | sort -V | tail -1)

die() { echo "update-repo: $*" >&2; exit 1; }
[ -f "$WORK/config.yml" ] && [ -f "$WORK/keystore.p12" ] || die "no repository workspace at $WORK"
[ -x "$FDROID" ] || die "no fdroid at $FDROID (README.md)"

# Only release builds go in: the debug APKs carry another signature and can't update them.
set -- out/AndroidLuna-arm32-release.apk out/AndroidLuna-arm64-release.apk out/LunaKeyboard-release.apk
for apk; do [ -f "$apk" ] || die "$apk is missing; run ./build.sh publish first"; done
(cd out && grep -E ' (AndroidLuna-arm(32|64)|LunaKeyboard)-release\.apk$' SHA256SUMS | sha256sum -c --quiet) ||
    die "out/ doesn't match its SHA256SUMS"

# Each APK under a name of its own, so a release never replaces an earlier one.
for apk; do
    badge=$("$AAPT2" dump badging "$apk" | head -1)
    id=$(echo "$badge" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
    code=$(echo "$badge" | sed -n "s/.* versionCode='\([^']*\)'.*/\1/p")
    case $apk in *arm32*) abi=_arm32 ;; *arm64*) abi=_arm64 ;; *) abi= ;; esac
    dest="$WORK/repo/${id}_${code}${abi}.apk"
    if [ -f "$dest" ]; then cmp -s "$apk" "$dest" || die "$dest exists and differs; a build number is published once"
    else cp "$apk" "$dest"; echo "added $(basename "$dest")"; fi
done

# The descriptions are kept here, in the source; the repository's icon is Lunacy's.
rm -rf "$WORK/metadata" && cp -r Meta/fdroid/metadata "$WORK/metadata"
cp Meta/LunacyRobot.png "$WORK/icon.png"

# Capped and niced: on 2026-11-13 an unpinned pip install of fdroidserver ran away with this
# machine's CPU and memory. fdroid update itself takes seconds and well under a gigabyte.
cd "$WORK"
ANDROID_HOME=$SDK systemd-run --user --scope -q -p MemoryMax=6G timeout 900 nice "$FDROID" update
echo "Ready to upload: $WORK/repo/ and $WORK/archive/ to https://www.webosarchive.org/fdroid/"

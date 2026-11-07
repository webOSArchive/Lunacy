#!/bin/sh
# Makes (or remakes) a patch in one of the framework forks' series from the working copy in
# local-assets: Enyo, Mojo or webOS CE's calendar framework (LunaRuntimes/<framework>).
#
# A series patch has to be a diff against the tree with the *earlier* patches already applied,
# not against stock, or applying them in order fails. This builds that base in a temp tree, so
# it can't be got wrong by hand, and writes plain a/ b/ headers with no timestamps.
#
#   tools/make-patch.sh enyo-1.0 0002-webview-over-iframe framework/source/palm/.../X.js
#   tools/make-patch.sh mojo 0005-name javascripts/widgets/x.js
#   tools/make-patch.sh calendar 0003-name utils/x.js
#
set -e
cd "$(dirname "$0")/.."
HERE=$(pwd)
fw=$1; name=$2
[ -n "$fw" ] && [ -n "$name" ] && [ $# -gt 2 ] || { echo "usage: $0 enyo-1.0|mojo|calendar|photos-service <patch-name> <file> [file...]" >&2; exit 1; }
shift 2
# Each framework's stock tree, as fetch-assets.sh lays it down, and where it lays it.
case "$fw" in
    enyo-1.0) stock=../Workbench/vendor/touchpad/enyo-0.10; work=local-assets/fw/enyo/1.0 ;;
    mojo) stock=../Workbench/vendor/touchpad/mojo; work=local-assets/fw/mojo ;;
    calendar) stock=../Workbench/vendor/webos-ce/system/usr/palm/frameworks/calendar; work=local-assets/fw/frameworks/calendar ;;
    photos-service) stock=../Workbench/vendor/touchpad/os-services/com.palm.service.photos; work=local-assets/rootfs/usr/palm/services/com.palm.service.photos ;;
    *) echo "make-patch: no framework '$fw' (enyo-1.0, mojo, calendar or photos-service)" >&2; exit 1 ;;
esac
out=../LunaRuntimes/$fw/patches/$name.patch
base=$(mktemp -d)
trap 'rm -rf "$base"' EXIT
cp -r "$stock/." "$base/"
chmod -R u+w "$base"
for p in ../LunaRuntimes/$fw/patches/*.patch; do
    [ -e "$p" ] || continue
    case "$(basename "$p" .patch)" in
        "$name") continue ;;                      # the one being made
    esac
    # Only the patches that come before this one in the series.
    [ "$(basename "$p" .patch)" \< "$name" ] || continue
    (cd "$base" && patch -p1 --forward --silent --fuzz=0 < "$HERE/$p")
done
: > "$out"
for f in "$@"; do
    diff -u "$base/$f" "$work/$f" |
        sed "s|^--- .*|--- a/$f|; s|^+++ .*|+++ b/$f|" >> "$out" || true
done
echo "wrote $out ($(wc -l < "$out") lines)"

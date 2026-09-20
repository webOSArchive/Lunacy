#!/bin/sh
# Makes (or remakes) a patch in the Enyo fork's series from the working copy in local-assets.
#
# A series patch has to be a diff against the tree with the *earlier* patches already applied,
# not against stock, or applying them in order fails. This builds that base in a temp tree, so
# it can't be got wrong by hand.
#
#   tools/make-enyo-patch.sh 0002-webview-over-iframe framework/source/palm/.../X.js framework/build/enyo-build.js
#
set -e
cd "$(dirname "$0")/.."
HERE=$(pwd)
name=$1; shift
[ -n "$name" ] && [ $# -gt 0 ] || { echo "usage: $0 <patch-name> <file> [file...]" >&2; exit 1; }
out=../LunaRuntimes/enyo-1.0/patches/$name.patch
base=$(mktemp -d)
trap 'rm -rf "$base"' EXIT
cp -r ../Workbench/vendor/enyo-1.0/. "$base/"
for p in ../LunaRuntimes/enyo-1.0/patches/*.patch; do
    [ -e "$p" ] || continue
    case "$(basename "$p" .patch)" in
        "$name") continue ;;                      # the one being made
    esac
    # Only the patches that come before this one in the series.
    [ "$(basename "$p" .patch)" \< "$name" ] || continue
    (cd "$base" && patch -p1 --forward --silent < "$HERE/$p")
done
: > "$out"
for f in "$@"; do
    diff -u "$base/$f" "local-assets/fw/enyo/1.0/$f" |
        sed "s|^--- .*|--- a/$f|; s|^+++ .*|+++ b/$f|" >> "$out" || true
done
echo "wrote $out ($(wc -l < "$out") lines)"

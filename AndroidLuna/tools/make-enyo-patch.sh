#!/bin/sh
# The Enyo fork's patches: tools/make-patch.sh enyo-1.0, which does the same for Mojo and the
# calendar framework. Kept for the docs and habits that name it.
#   tools/make-enyo-patch.sh 0002-webview-over-iframe framework/source/palm/.../X.js
exec "$(dirname "$0")/make-patch.sh" enyo-1.0 "$@"

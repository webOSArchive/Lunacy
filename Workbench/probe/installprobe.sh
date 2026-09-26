#!/bin/sh
# Packages com.palm.lunacy.installprobe with App Catalog 6.2's direct-install files (from the
# catalog repo beside Lunacy's), for running on the reference TouchPad.
cd "$(dirname "$0")"
C=${CATALOG:-$HOME/Projects/webos-appcatalog-touchpad}/main/source
cp $C/ipk-inspect.js $C/direct-install.js com.palm.lunacy.installprobe/
palm-package com.palm.lunacy.installprobe -o . >/dev/null && echo com.palm.lunacy.installprobe_0.0.1_all.ipk

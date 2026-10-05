#!/bin/sh
# Populates AndroidLuna/local-assets/ from the workbench's local clones: the frameworks and the
# apps that aren't bundled. Glimpse goes to AndroidLuna/local-test-apps/ instead, which only a
# build that asks for it includes (./gradlew assembleDebug -PtestApps): it is a test app that
# users shouldn't get (codepoet). Nothing here is committed.
# See Docs/android5-setup.md and Docs/roadmap.md.
set -e
cd "$(dirname "$0")"
HERE=$(pwd)
V=../Workbench/vendor
L=local-assets
T=local-test-apps
rm -rf $L $T && mkdir -p $L/fw/enyo $L/apps $T/apps
# Enyo as the reference TouchPad has it: /usr/palm/frameworks/enyo/0.10 off the device
# (webOS CE 3.1.0), the build HP shipped, with the libraries (networkproxy among them) and the
# localized resources the Apache 2.0 release on GitHub lacks. That release is a slightly
# different build; Lunacy patched it until 2026-10-05 (LunaRuntimes/enyo-1.0/CHANGES.md).
cp -r $V/touchpad/enyo-0.10 $L/fw/enyo/1.0
# Lunacy's changes to Enyo, as diffs against the device's: LunaRuntimes/enyo-1.0/CHANGES.md says
# what each one is and why. A patch that no longer applies is a build failure, not a silent skip.
for patch in ../LunaRuntimes/enyo-1.0/patches/*.patch; do
    [ -e "$patch" ] || continue
    (cd $L/fw/enyo/1.0 && patch -p1 --forward --silent --fuzz=0 --follow-symlinks < "$HERE/$patch") ||
        { echo "fetch-assets: $patch does not apply to the TouchPad's Enyo" >&2; exit 1; }
    echo "enyo: applied $(basename "$patch")"
done
cat > $L/fw/enyo/1.0/NOTICE <<'NOTICE'
Enyo 1.0 as it is on the reference TouchPad (webOS CE 3.1.0), /usr/palm/frameworks/enyo/0.10,
with Lunacy's patches (LunaRuntimes/enyo-1.0/patches in the Lunacy repository). Enyo itself is
Apache 2.0 (Hewlett-Packard; github.com/enyojs/enyo-1.0). The libraries and localized
resources HP shipped on the device and never released with the source (lib/networkproxy among
them) are Palm/HP's, distributed by Lunacy as abandonware, like Mojo.
NOTICE
cp -r ../Workbench/apps-src/com.ingloriousapps.glimpse/usr/palm/applications/com.ingloriousapps.glimpse $T/apps/
# Palm's own settings apps that Lunacy ships (Screen & Lock, Help) are committed under
# assets/apps/ with their NOTICE, like Mojo and the fonts. Any others under
# Workbench/vendor/settings-apps/ are copied here for testing and stay out of the repo.
for a in $V/settings-apps/*; do
    id=$(basename "$a")
    [ -d "$a" ] && [ ! -d "src/main/assets/apps/$id" ] && cp -r "$a" $L/apps/
done
# Records a tree's symlinks as a manifest - "link target", both relative to the tree, the
# target followed to a real file - and takes the links out. Gradle's asset merger refuses a
# symlink, and copying every link's target in its place (cp -L, as before) shipped the
# frameworks twice over: on the device version/1.0 is a link to submission/N, and Mojo's
# images and templates link into mojocommon file by file. The app server follows the manifest
# as it serves (AppServer.followLinks), and the ROM is laid down with real links
# (WebosRoot.syncRom), as the device had them.
links() {  # links <tree> <manifest>
    tree=$1; out=$2; : > "$out"
    find "$tree" -type l | sort | while read -r l; do
        rel=${l#"$tree"/}
        t=$(realpath -m --relative-to="$tree" "$(dirname "$l")/$(readlink "$l")")
        case $t in ../*|/*) echo "fetch-assets: link $rel -> $t leaves the tree; dropped" >&2; continue;; esac
        [ -e "$tree/$t" ] || { echo "fetch-assets: link $rel -> $t is dangling; dropped" >&2; continue; }
        echo "$rel $t" >> "$out"
    done
    find "$tree" -type l -delete
    echo "links: $(wc -l < "$out") in $tree"
}

# Mojo, from the reference TouchPad. webOS's browser had the framework compiled in, so the
# submission on disk carries only its assets and builtins/ carries the code; Lunacy serves
# both. Palm's code, shipped as abandonware like the fonts (see the NOTICE it gets).
mkdir -p $L/fw/mojo $L/fw/mojocommon
# The submission symlinks into mojocommon; links() below records those.
cp -r $V/touchpad/mojo/. $L/fw/mojo/
# mojocommon is a framework of its own beside Mojo, and the submission symlinks into it for
# shared resources and images; Lunacy serves it at its own path so those links resolve.
cp -r $V/touchpad/mojocommon/. $L/fw/mojocommon/
chmod -R u+w $L/fw/mojo $L/fw/mojocommon
# Lunacy's changes to Mojo, as diffs against the device's own copy (LunaRuntimes/mojo/CHANGES.md).
for patch in ../LunaRuntimes/mojo/patches/*.patch; do
    [ -e "$patch" ] || continue
    (cd $L/fw/mojo && patch -p1 --forward --silent --follow-symlinks < "$HERE/$patch") ||
        { echo "fetch-assets: $patch does not apply to the TouchPad's Mojo" >&2; exit 1; }
    echo "mojo: applied $(basename "$patch")"
done
cat > $L/fw/mojo/NOTICE <<'NOTICE'
Palm's Mojo framework, copied from /usr/palm/frameworks/mojo on the reference TouchPad
(webOS CE 3.1.0): mojo.js, submission 506's assets, and the builtins webOS's own browser
provided (Prototype 1.6, palmInitFramework506 and palmInitFramework2205 - the Mojo 1 and
Mojo 2 frameworks - and the libraries MojoLoader hands out).
Copyright Palm, Inc. / Hewlett-Packard. Never released under an open licence; distributed by
Lunacy as abandonware: no owner has asserted rights since webOS was discontinued.
NOTICE

# The rest of /usr/palm/frameworks, at their own paths. Mojo 2 apps - Palm's own Video
# Player is one - load mojo2/mojo.js, which pulls in mojoloader.js and asks MojoLoader for
# mojo.core; Prototype comes from its own framework there rather than from a builtin. Only
# the frameworks something has needed are copied.
mkdir -p $L/fw/frameworks
for f in mojo2 prototype mojo.core underscore foundations globalization mojoloader.js \
         metascene.base metascene.videos metascene.videos.share \
         mediastream mediaextension mediacapture imagethumbnail mojodbshim media; do
    cp -r $V/touchpad/$f $L/fw/frameworks/
done
chmod -R u+w $L/fw/frameworks
# mojo2's images and templates link to ../../../../mojocommon, which on the device is
# /usr/palm/frameworks/mojocommon, beside it; here mojocommon is served from fw/mojocommon, so
# a stand-in link lets those links resolve (and goes with the rest, below).
ln -s ../mojocommon $L/fw/frameworks/mojocommon
links $L/fw $L/fw.links
cat > $L/fw/frameworks/NOTICE <<'NOTICE'
Palm's frameworks, copied from /usr/palm/frameworks on the reference TouchPad
(webOS CE 3.1.0): mojo2 (submission 205), prototype, mojo.core, foundations, globalization,
mojoloader.js, the metascene frameworks the Video Player's scenes come from, and the media
frameworks they and other apps ask MojoLoader for. `media` is /usr/lib/luna/luna-media-shim,
which the frameworks folder symlinks to.
Copyright Palm, Inc. / Hewlett-Packard, except underscore, which is MIT (Jeremy Ashkenas),
and prototype, which is MIT (Sam Stephenson). Palm's own is never released under an open
licence; distributed by Lunacy as abandonware, like Mojo itself.
NOTICE

# Enyo samples as installable apps: test apps, in the APK only with -PtestApps.
# The SDK samples load Enyo by an SDK-tree relative path; packaging them as apps points them
# at the framework path installed apps use (what palm-package'd samples needed on a device too).
for s in Sampler HelloWorld; do
    id=com.palmdts.enyo.$(echo $s | tr A-Z a-z)
    cp -r $V/enyo-1.0/support/examples/$s $T/apps/$id
    sed -i 's#"../../../../1.0/framework/enyo.js"#"/usr/palm/frameworks/enyo/1.0/framework/enyo.js"#' $T/apps/$id/index.html
    sed -i "s#\"id\": *\"[^\"]*\"#\"id\": \"$id\"#" $T/apps/$id/appinfo.json
done

# JS services. Node is nodejs-mobile 0.3.3 (Node 12.19): the last release whose libnode.so loads
# on Android 5 (later ones need API 24). tools/node-launcher.cpp makes it an executable. Both
# ARM ABIs are built: 32-bit for the Android 5 test devices (webOS's own CPU class), 64-bit for
# the SoCs that can no longer run 32-bit code at all (Pixel 7 and later, and most phones
# since). Android installs the one the device runs.
#
# 16 KB memory pages: a device with them loads only libraries whose segments are 16 KB
# aligned, and Android 17 names any that aren't in a dialog on every launch of a debuggable
# build. nodejs-mobile's prebuilt libnode.so is 4 KB aligned, so for 64-bit tools/build-node.sh
# rebuilds it from the same source with the alignment, into v0.3.3-16k/; that copy is used
# when it exists, and the prebuilt otherwise, with a warning. 32-bit stays on the prebuilt
# (codepoet, 2026-10-01): it is the one proven on the Android 5 devices, and no 32-bit device
# has 16 KB pages. libc++_shared.so and the launcher come from NDK r28, which aligns 64-bit
# libraries to 16 KB by itself. Palm's service frameworks are the reference TouchPad's
# (Workbench/vendor/touchpad/services-fw), with their version symlinks resolved.
NDK=${ANDROID_NDK:-$HOME/Android/Sdk/ndk/android-ndk-r28c}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64
[ -x $TC/bin/clang++ ] || { echo "fetch-assets: no NDK at $NDK (set ANDROID_NDK; BUILDING.md)" >&2; exit 1; }
rm -rf local-jni
for ABI in armeabi-v7a arm64-v8a; do
    case $ABI in
        armeabi-v7a) CXX=armv7a-linux-androideabi21-clang++; SYS=arm-linux-androideabi ;;
        arm64-v8a)   CXX=aarch64-linux-android21-clang++;    SYS=aarch64-linux-android ;;
    esac
    NM=$V/nodejs-mobile/v0.3.3/bin/$ABI
    if [ $ABI = arm64-v8a ]; then
        NM16=$V/nodejs-mobile/v0.3.3-16k/bin/$ABI
        if [ -f $NM16/libnode.so ]; then NM=$NM16; else echo "fetch-assets: $ABI: no 16 KB libnode.so (tools/build-node.sh); using nodejs-mobile's 4 KB prebuilt" >&2; fi
    fi
    J=local-jni/$ABI
    mkdir -p $J
    # Both stripped: nodejs-mobile ships libnode.so with its symbol tables and DWARF (10 MB of
    # the 44), and the NDK's libc++_shared.so is its unstripped copy.
    cp $NM/libnode.so $TC/sysroot/usr/lib/$SYS/libc++_shared.so $J/
    $TC/bin/llvm-strip --strip-all $J/libnode.so $J/libc++_shared.so
    # max-page-size aligns the launcher's LOAD segments and common-page-size ends its RELRO
    # region on a 16 KB boundary; NDK r28 does both by default for 64-bit, and the options
    # keep the 32-bit build the same.
    $TC/bin/$CXX -pie -fPIE -O2 -s -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -o $J/liblunacynode.so tools/node-launcher.cpp -L$NM -lnode -llog
    echo "node: $ABI from $NM"
done
# The webOS root filesystem's ROM (WebosRoot.kt): what the TouchPad's rootfs held for JS
# services and package scripts, laid into Lunacy's files/webos. Palm's service frameworks and
# jsservicelauncher, the system's own JS services and the db8 kinds they own, from the
# reference TouchPad (Workbench/vendor/touchpad/services-fw, os-services and etc-db).
R=$L/rootfs
mkdir -p $R/usr/palm/frameworks $R/usr/palm/services $R/usr/palm/public/accounts $R/etc/palm/db/kinds $R/etc/palm/db/permissions $R/etc/palm/tempdb
for f in foundations foundations.crypto foundations.io foundations.json mojoservice mojoservice.transport underscore globalization mojoloader.js; do
    cp -r $V/touchpad/services-fw/$f $R/usr/palm/frameworks/ 2>/dev/null || cp -r $V/touchpad/$f $R/usr/palm/frameworks/
done
cp -r $V/touchpad/services-fw/jsservicelauncher $R/usr/palm/services/
cat > $R/usr/palm/frameworks/NOTICE <<'NOTICE'
Palm's JS service frameworks (foundations*, mojoservice*, globalization, underscore,
mojoloader.js) and jsservicelauncher, copied from /usr/palm on the reference TouchPad
(webOS CE 3.1.0). Status: treated as abandonware, like Mojo: no owner has asserted rights
since webOS was discontinued. underscore is MIT (Jeremy Ashkenas).
NOTICE
# The accounts service: LG's Apache 2.0 release, as the TouchPad runs it (its LICENSE comes along).
cp -r $V/touchpad/os-services/com.palm.service.accounts $R/usr/palm/services/
# The palmprofile service (com.palm.accountservices), as HP shipped it. The reference TouchPad
# runs it patched by the webOS Community Account Manager, whose postinst kept HP's originals as
# <file>.stock; those go back in place and the package's own additions come out, so that the
# package patches Lunacy's copy exactly as it patches a device's.
P=$R/usr/palm/services/com.palm.service.palmprofile
cp -r $V/touchpad/os-services/com.palm.service.palmprofile $P
find $P -name '*.stock' | while read f; do mv "$f" "${f%.stock}"; done
rm -f $P/handlers/UpdateUsernameCommandAssistant.js $P/handlers/SyncDeviceNameCommandAssistant.js $P/handlers/SignOutCommandAssistant.js
cat > $P/NOTICE <<'NOTICE'
HP's palmprofile service (bus name com.palm.accountservices), copied from
/usr/palm/services/com.palm.service.palmprofile on the reference TouchPad (webOS CE 3.1.0),
with the files the webOS Community Account Manager patches restored from the stock copies it
keeps. Copyright Palm, Inc. / Hewlett-Packard. Never released under an open licence;
distributed by Lunacy as abandonware, like Mojo.
NOTICE
# The system's own account template, which the accounts service reads.
cp -r $V/touchpad/os-services/public/accounts/com.palm.palmprofile $R/usr/palm/public/accounts/
cat > $R/usr/palm/public/accounts/NOTICE <<'NOTICE'
The palmprofile account template and its images, from /usr/palm/public/accounts on the
reference TouchPad (webOS CE 3.1.0). Copyright Palm, Inc. / Hewlett-Packard; abandonware.
NOTICE
# db8 kinds and permissions those services own, from /etc/palm on the reference TouchPad.
for k in com.palm.palmprofile com.palm.account.credentials com.palm.service.accounts; do
    cp -r $V/touchpad/etc-db/db/kinds/$k $R/etc/palm/db/kinds/
done
cp -r $V/touchpad/etc-db/db/permissions/com.palm.service.accounts $R/etc/palm/db/permissions/
# App Catalog's magazine kinds, owned on the device by com.palm.service.appcatalog: the catalog
# looks its edition up in them as it starts, and an unknown kind was an error where the device
# answers an empty list.
for k in com.palm.appcatalog com.palm.appcatalog.editionfile com.palm.appcatalog.editioninfo; do
    cp $V/touchpad/etc-db/db/kinds/$k $R/etc/palm/db/kinds/
    cp $V/touchpad/etc-db/db/permissions/$k $R/etc/palm/db/permissions/
done
# The Web app's bookmark, history and preference kinds, owned on the device by the system
# rather than the app (Lunacy ships Palm's app; see its NOTICE).
for k in com.palm.browserbookmarks com.palm.browserhistory com.palm.browserpreferences; do
    cp $V/touchpad/etc-db/db/kinds/$k $R/etc/palm/db/kinds/
    cp $V/touchpad/etc-db/db/permissions/$k $R/etc/palm/db/permissions/
done
# /usr/palm/ipkgs/manifest.json lists the packages a device's ROM ships, which App Catalog offers
# to revert to (the reference TouchPad lists 14, the catalog among them). Lunacy's ROM ships no
# packages, so it lists none.
mkdir -p $R/usr/palm/ipkgs && echo '[]' > $R/usr/palm/ipkgs/manifest.json
cp -r $V/touchpad/etc-db/tempdb/kinds $V/touchpad/etc-db/tempdb/permissions $R/etc/palm/tempdb/
rm -rf $R/etc/palm/tempdb/permissions/com.palm.imbuddystatus
cat > $R/etc/palm/NOTICE <<'NOTICE'
db8 kind and permission files for the accounts and palmprofile services, App Catalog and the
Web app, from /etc/palm on the reference TouchPad (webOS CE 3.1.0). Copyright Palm, Inc. /
LG Electronics.
NOTICE

# OpenAL's configuration, as the TouchPad had it: its OpenAL Soft 1.11 (the PDK runtime's
# libopenal.so.1) plays through SDL with these settings. PDK apps read /etc/openal through
# the preload (LunaRuntimes/pdk/libpreload), which looks it up here.
mkdir -p $R/etc/openal
cp $V/touchpad/etc-openal/alsoft.conf $R/etc/openal/
cat > $R/etc/openal/NOTICE <<'NOTICE'
alsoft.conf from /etc/openal on the reference TouchPad (webOS CE 3.1.0): OpenAL Soft's sample
configuration (LGPL 2, https://github.com/kcat/openal-soft) with Palm's settings.
NOTICE

# busybox: webOS's /bin and /usr/bin were busybox, and package scripts are written for it.
# Packaged as a library so that Android installs it where it may be run, one per ABI. It is
# built from source against bionic by tools/build-busybox.sh, which says why: the prebuilt
# static busyboxes are killed by Android 10 and later's seccomp policy (exit 159). The build
# lives in local-build/, which this script doesn't empty, and is made here the first time.
BBO=local-build/busybox/out
[ -f $BBO/BUILT ] || NDK=$NDK sh tools/build-busybox.sh ||
    { echo "fetch-assets: tools/build-busybox.sh failed" >&2; exit 1; }
for ABI in armeabi-v7a arm64-v8a; do cp $BBO/$ABI/libbusybox.so local-jni/$ABI/; done
echo "fetch-assets: busybox: $(cat $BBO/BUILT)"
cat > $L/rootfs/NOTICE.busybox <<'NOTICE'
busybox 1.37.0 (libbusybox.so in the APK), GPL-2.0, built from the unmodified source
https://busybox.net/downloads/busybox-1.37.0.tar.bz2 by AndroidLuna/tools/build-busybox.sh
(https://github.com/webOSArchive/Lunacy/blob/main/AndroidLuna/tools/build-busybox.sh), which
holds the configuration: defconfig with FEATURE_SUID, STATIC and the applets Android's libc
can't build turned off, compiled against bionic with -DBB_GLOBAL_CONST= .
NOTICE
# The ROM's symlinks, made on the device as links (WebosRoot.syncRom), and its file list, so
# that Lunacy needn't walk it with AssetManager.list(), which reads the APK's whole asset
# index on every call.
chmod -R u+w $R
links $R $L/rootfs.links
(cd $L/rootfs && find . -type f | sed 's#^\./##' | sort) > $L/rootfs.index
# The PDK runtime goes into local-assets and local-jni, both emptied above, so it is built
# again on every run (its downloads and builds are kept in local-build/pdk).
NDK=$NDK sh tools/build-pdk.sh > /dev/null || { echo "fetch-assets: tools/build-pdk.sh failed" >&2; exit 1; }
echo "local-assets ready: $(du -sh $L | cut -f1); local-jni: $(du -sh local-jni | cut -f1)"

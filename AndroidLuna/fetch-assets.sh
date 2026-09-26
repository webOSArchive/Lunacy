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
cp -r $V/enyo-1.0 $L/fw/enyo/1.0 && rm -rf $L/fw/enyo/1.0/.git $L/fw/enyo/1.0/support/docs
# Lunacy's changes to Enyo, as diffs against upstream: LunaRuntimes/enyo-1.0/CHANGES.md says what
# each one is and why. A patch that no longer applies is a build failure, not a silent skip.
for patch in ../LunaRuntimes/enyo-1.0/patches/*.patch; do
    [ -e "$patch" ] || continue
    (cd $L/fw/enyo/1.0 && patch -p1 --forward --silent < "$HERE/$patch") ||
        { echo "fetch-assets: $patch does not apply to stock Enyo" >&2; exit 1; }
    echo "enyo: applied $(basename "$patch")"
done
# Enyo libraries HP shipped in the TouchPad's framework folder and never released with the
# source: added whole from the reference TouchPad (LunaRuntimes/enyo-1.0/CHANGES.md, "Added").
for lib in networkproxy; do
    [ -d $L/fw/enyo/1.0/framework/lib/$lib ] && { echo "fetch-assets: upstream Enyo has lib/$lib now" >&2; exit 1; }
    cp -r $V/touchpad/enyo-0.10/framework/lib/$lib $L/fw/enyo/1.0/framework/lib/
    cat > $L/fw/enyo/1.0/framework/lib/$lib/NOTICE <<'NOTICE'
From /usr/palm/frameworks/enyo/0.10/framework/lib on the reference TouchPad (webOS CE 3.1.0).
Copyright Palm, Inc. / Hewlett-Packard; not part of the Apache 2.0 Enyo release. Distributed by
Lunacy as abandonware, like Mojo.
NOTICE
    echo "enyo: added lib/$lib"
done
cp -r ../Workbench/apps-src/com.ingloriousapps.glimpse/usr/palm/applications/com.ingloriousapps.glimpse $T/apps/
# Palm's own settings apps that Lunacy ships (Screen & Lock, Help) are committed under
# assets/apps/ with their NOTICE, like Mojo and the fonts. Any others under
# Workbench/vendor/settings-apps/ are copied here for testing and stay out of the repo.
for a in $V/settings-apps/*; do
    id=$(basename "$a")
    [ -d "$a" ] && [ ! -d "src/main/assets/apps/$id" ] && cp -r "$a" $L/apps/
done
# Mojo, from the reference TouchPad. webOS's browser had the framework compiled in, so the
# submission on disk carries only its assets and builtins/ carries the code; Lunacy serves
# both. Palm's code, shipped as abandonware like the fonts (see the NOTICE it gets).
mkdir -p $L/fw/mojo $L/fw/mojocommon
# -L: the submission symlinks into mojocommon, and Gradle's asset merger chokes on symlinks.
cp -rL $V/touchpad/mojo/. $L/fw/mojo/
# mojocommon is a framework of its own beside Mojo, and the submission symlinks into it for
# shared resources and images; Lunacy serves it at its own path so those links resolve.
cp -rL $V/touchpad/mojocommon/. $L/fw/mojocommon/
chmod -R u+w $L/fw/mojo $L/fw/mojocommon
# Lunacy's changes to Mojo, as diffs against the device's own copy (LunaRuntimes/mojo/CHANGES.md).
for patch in ../LunaRuntimes/mojo/patches/*.patch; do
    [ -e "$patch" ] || continue
    (cd $L/fw/mojo && patch -p1 --forward --silent < "$HERE/$patch") ||
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
    cp -rL $V/touchpad/$f $L/fw/frameworks/
done
chmod -R u+w $L/fw/frameworks
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

# Enyo samples as installable apps.
# The SDK samples load Enyo by an SDK-tree relative path; packaging them as apps points them
# at the framework path installed apps use (what palm-package'd samples needed on a device too).
for s in Sampler HelloWorld; do
    id=com.palmdts.enyo.$(echo $s | tr A-Z a-z)
    cp -r $V/enyo-1.0/support/examples/$s $L/apps/$id
    sed -i 's#"../../../../1.0/framework/enyo.js"#"/usr/palm/frameworks/enyo/1.0/framework/enyo.js"#' $L/apps/$id/index.html
    sed -i "s#\"id\": *\"[^\"]*\"#\"id\": \"$id\"#" $L/apps/$id/appinfo.json
done

# JS services. Node is nodejs-mobile 0.3.3 (Node 12.19): the last release whose libnode.so loads
# on Android 5 (later ones need API 24). tools/node-launcher.cpp makes it an executable, built
# with NDK r21, whose libc++_shared.so matches. Palm's service frameworks are the reference
# TouchPad's (Workbench/vendor/touchpad/services-fw), with their version symlinks resolved.
NDK=${ANDROID_NDK:-$HOME/Android/Sdk/ndk/21.4.7075529}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64
NM=$V/nodejs-mobile/v0.3.3/bin/armeabi-v7a
J=local-jni/armeabi-v7a
rm -rf local-jni && mkdir -p $J
cp $NM/libnode.so $J/
cp $TC/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so $J/
$TC/bin/armv7a-linux-androideabi21-clang++ -pie -fPIE -O2 -s -o $J/liblunacynode.so tools/node-launcher.cpp -L$NM -lnode
# The webOS root filesystem's ROM (WebosRoot.kt): what the TouchPad's rootfs held for JS
# services and package scripts, laid into Lunacy's files/webos. Palm's service frameworks and
# jsservicelauncher, the system's own JS services and the db8 kinds they own, from the
# reference TouchPad (Workbench/vendor/touchpad/services-fw, os-services and etc-db).
R=$L/rootfs
mkdir -p $R/usr/palm/frameworks $R/usr/palm/services $R/usr/palm/public/accounts $R/etc/palm/db/kinds $R/etc/palm/db/permissions $R/etc/palm/tempdb
for f in foundations foundations.crypto foundations.io foundations.json mojoservice mojoservice.transport underscore globalization mojoloader.js; do
    cp -rL $V/touchpad/services-fw/$f $R/usr/palm/frameworks/ 2>/dev/null || cp -rL $V/touchpad/$f $R/usr/palm/frameworks/
done
cp -rL $V/touchpad/services-fw/jsservicelauncher $R/usr/palm/services/
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
# /usr/palm/ipkgs/manifest.json lists the packages a device's ROM ships, which App Catalog offers
# to revert to (the reference TouchPad lists 14, the catalog among them). Lunacy's ROM ships no
# packages, so it lists none.
mkdir -p $R/usr/palm/ipkgs && echo '[]' > $R/usr/palm/ipkgs/manifest.json
cp -r $V/touchpad/etc-db/tempdb/kinds $V/touchpad/etc-db/tempdb/permissions $R/etc/palm/tempdb/
rm -rf $R/etc/palm/tempdb/permissions/com.palm.imbuddystatus
cat > $R/etc/palm/NOTICE <<'NOTICE'
db8 kind and permission files for the accounts and palmprofile services, from /etc/palm on the
reference TouchPad (webOS CE 3.1.0). Copyright Palm, Inc. / LG Electronics.
NOTICE

# busybox: webOS's /bin and /usr/bin were busybox, and package scripts are written for it.
# The static ARM build busybox.net publishes, checked against the hash it was first fetched
# with; packaged as a library so that Android installs it where it may be run.
BB=$V/busybox/busybox-armv7l
BB_URL=https://busybox.net/downloads/binaries/1.31.0-defconfig-multiarch-musl/busybox-armv7l
BB_SHA=cd04052b8b6885f75f50b2a280bfcbf849d8710c8e61d369c533acf307eda064
[ -f $BB ] || { mkdir -p $(dirname $BB) && curl -sfL -o $BB $BB_URL; }
echo "$BB_SHA  $BB" | sha256sum -c --quiet || { echo "fetch-assets: $BB isn't the busybox it should be" >&2; exit 1; }
cp $BB $J/libbusybox.so
cat > $L/rootfs/NOTICE.busybox <<'NOTICE'
busybox 1.31.0 (libbusybox.so in the APK): the static armv7l build from
https://busybox.net/downloads/binaries/1.31.0-defconfig-multiarch-musl/ , unmodified.
GPL-2.0. Source: https://busybox.net/downloads/busybox-1.31.0.tar.bz2
NOTICE
# The ROM's file list, so that Lunacy needn't walk it with AssetManager.list(), which reads the
# APK's whole asset index on every call (WebosRoot.syncRom).
(cd $L/rootfs && find . -type f | sed 's#^\./##' | sort) > $L/rootfs.index
echo "local-assets ready: $(du -sh $L | cut -f1); local-jni: $(du -sh local-jni | cut -f1)"

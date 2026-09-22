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
mkdir -p $L/services-fw/frameworks
for f in foundations foundations.crypto foundations.io foundations.json mojoservice mojoservice.transport underscore mojoloader.js; do
    cp -rL $V/touchpad/services-fw/$f $L/services-fw/frameworks/
done
cp -rL $V/touchpad/services-fw/jsservicelauncher $L/services-fw/
cat > $L/services-fw/NOTICE <<'NOTICE'
Palm's JS service frameworks (foundations*, mojoservice*, underscore, mojoloader.js) and
jsservicelauncher, copied from /usr/palm on the reference TouchPad (webOS CE 3.1.0).
Status: treated as abandonware, like Mojo: no owner has asserted rights since webOS was
discontinued. underscore is MIT (Jeremy Ashkenas).
NOTICE
echo "local-assets ready: $(du -sh $L | cut -f1); local-jni: $(du -sh local-jni | cut -f1)"

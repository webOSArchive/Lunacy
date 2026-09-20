#!/bin/sh
# Populates local-assets/ from the spike's local clones: the Enyo framework and the bundled apps.
# Nothing here is committed. See docs/android5-setup.md and docs/roadmap.md.
set -e
cd "$(dirname "$0")"
V=../spike/vendor
L=local-assets
rm -rf $L && mkdir -p $L/fw/enyo $L/apps
cp -r $V/enyo-1.0 $L/fw/enyo/1.0 && rm -rf $L/fw/enyo/1.0/.git $L/fw/enyo/1.0/support/docs
# Lunacy's changes to Enyo, as diffs against upstream: framework/enyo-1.0/CHANGES.md says what
# each one is and why. A patch that no longer applies is a build failure, not a silent skip.
for patch in framework/enyo-1.0/patches/*.patch; do
    [ -e "$patch" ] || continue
    (cd $L/fw/enyo/1.0 && patch -p1 --forward --silent < "../../../../$patch") ||
        { echo "fetch-assets: $patch does not apply to stock Enyo" >&2; exit 1; }
    echo "enyo: applied $(basename "$patch")"
done
cp -r ../spike/apps-src/com.palm.app-museum2/usr/palm/applications/com.palm.app-museum2 $L/apps/
cp -r ../spike/apps-src/com.ingloriousapps.glimpse/usr/palm/applications/com.ingloriousapps.glimpse $L/apps/
# Palm's own settings apps that Lunacy ships (Screen & Lock, Help) are committed under
# assets/apps/ with their NOTICE, like Mojo and the fonts. Any others under
# spike/vendor/settings-apps/ are copied here for testing and stay out of the repo.
for a in $V/settings-apps/*; do
    id=$(basename "$a")
    [ -d "$a" ] && [ ! -d "app/src/main/assets/apps/$id" ] && cp -r "$a" $L/apps/
done
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
# TouchPad's (spike/vendor/touchpad/services-fw), with their version symlinks resolved.
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

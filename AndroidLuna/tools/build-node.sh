#!/bin/sh
# Builds libnode.so for Lunacy's JS services from the nodejs-mobile source, with its segments
# aligned to 16 KB pages. nodejs-mobile's own prebuilt (0.3.3, Node 12.19) is linked at 4 KB,
# and a device with 16 KB memory pages runs a 4 KB library only in Android's backcompat mode,
# while Android 17 names it in an "Android App Compatibility" dialog on every launch of a
# debuggable build (Docs/android5-setup.md, the Pixel Tablet). The build is nodejs-mobile's
# (android-configure, NDK r21's standalone toolchain, API 21, the same release) plus two
# linker options: -z max-page-size=16384 aligns every LOAD segment and -z common-page-size=16384
# ends the RELRO region on a 16 KB boundary, which NDK r21's linker doesn't do by itself.
#
#   AndroidLuna/tools/build-node.sh arm64      # or arm
#
# Needs: the nodejs-mobile source at Workbench/vendor/nodejs-mobile/src, checked out at tag
# nodejs-mobile-v0.3.3 (git clone --depth 1 --branch nodejs-mobile-v0.3.3
# https://github.com/nodejs-mobile/nodejs-mobile.git src); NDK r21e; python2.7 (Node 12's
# configure); about 40 minutes and 6 GB. The result goes to
# Workbench/vendor/nodejs-mobile/v0.3.3-16k/bin/<abi>/libnode.so, where fetch-assets.sh looks
# for it before falling back to the 4 KB prebuilt.
set -e
cd "$(dirname "$0")/../.."
ARCH=${1:?usage: build-node.sh arm|arm64}
case $ARCH in
    arm)   ABI=armeabi-v7a ;;
    arm64) ABI=arm64-v8a ;;
    *) echo "build-node: arch is arm or arm64" >&2; exit 1 ;;
esac
NDK=${ANDROID_NDK:-$HOME/Android/Sdk/ndk/21.4.7075529}
SRC=Workbench/vendor/nodejs-mobile/src
OUT=Workbench/vendor/nodejs-mobile/v0.3.3-16k/bin/$ABI
[ -f $SRC/android-configure ] || { echo "build-node: no nodejs-mobile source at $SRC" >&2; exit 1; }
tag=$(git -C $SRC describe --tags --exact-match 2>/dev/null || git -C $SRC rev-parse --short HEAD)
[ "$tag" = "nodejs-mobile-v0.3.3" ] || echo "build-node: warning: source is at $tag, not nodejs-mobile-v0.3.3" >&2
export LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
# The host-side V8 tools (mksnapshot and friends) build with this machine's own compiler, and
# GCC 13 no longer pulls <cstdint> in through <string>, which Node 12's V8 relied on.
export CXXFLAGS_host="-include cstdint"
cd $SRC
make clean >/dev/null 2>&1 || true
rm -rf android-toolchain out
# android-configure makes the standalone toolchain, exports the cross compilers and runs
# ./configure; sourced so its exports reach make. Its toolchain prompt is skipped by /dev/null.
. ./android-configure "$NDK" $ARCH </dev/null
make -j"$(getconf _NPROCESSORS_ONLN)"
cd - >/dev/null
mkdir -p $OUT
cp $SRC/out/Release/lib.target/libnode.so $OUT/libnode.so
RE=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf
$RE -l $OUT/libnode.so | awk '/LOAD/ && $NF != "0x4000" { bad = 1 } END { exit bad }' ||
    { echo "build-node: $OUT/libnode.so is not 16 KB aligned" >&2; exit 1; }
echo "build-node: $OUT/libnode.so ($(du -h $OUT/libnode.so | cut -f1), 16 KB aligned)"

#!/bin/sh
# Builds Lunacy's busybox for both ABIs, static, from busybox.net's source, into
# local-jni/<abi>/libbusybox.so (gitignored; fetch-assets.sh keeps a built one).
#
# Why not the prebuilt ones: busybox.net's armv7 binary and Alpine's arm64 one are built with
# FEATURE_SUID, which makes every applet call setuid() and setgid() as it starts, to drop a
# setuid root it never has. Android's seccomp policy for apps kills a process on either call
# (bionic's SECCOMP_BLOCKLIST_APP), so on Android 10 and later a package's postinst died
# with SIGSYS (exit 159) before its first line (found on the Pixel Tablet, 2026-10-02, with
# qemu-user's -strace against the published blocklist). This build turns the feature off
# and makes no such call; everything else is defconfig, less tc (its kernel headers are
# gone) and the x86-only hash acceleration.
#
# Needs: the NDK's clang (r28c), curl, dpkg-deb; the Debian armel sysroot build-pdk.sh
# makes, for the 32-bit build.
set -e
HERE=$(cd "$(dirname "$0")/.." && pwd)
NDK=${NDK:-$HOME/Android/Sdk/ndk/android-ndk-r28c}
BIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
WORK=$HERE/local-build/busybox
DEBIAN=https://deb.debian.org/debian
BB_VER=1.37.0
mkdir -p "$WORK/deb" "$WORK/sysroot64"
cd "$WORK"

[ -f busybox-$BB_VER.tar.bz2 ] || curl -sL https://busybox.net/downloads/busybox-$BB_VER.tar.bz2 -o busybox-$BB_VER.tar.bz2

# The arm64 sysroot: trixie's glibc and gcc runtime, to link statically against.
if [ ! -f deb/Packages-arm64 ]; then
  curl -sL $DEBIAN/dists/trixie/main/binary-arm64/Packages.gz | zcat > deb/Packages-arm64
fi
for p in libc6 libc6-dev linux-libc-dev libgcc-s1 libgcc-14-dev; do
  f=$(awk -v p="$p" '$1=="Package:"{cur=$2} $1=="Filename:" && cur==p {print $2; exit}' deb/Packages-arm64)
  b=$(basename "$f")
  if [ ! -f "deb/$b" ]; then echo "fetching $b"; curl -sL "$DEBIAN/$f" -o "deb/$b"; dpkg-deb -x "deb/$b" sysroot64; fi
done

configure() {  # $1 = build dir
  rm -rf "$1"; mkdir -p "$1"; tar xjf busybox-$BB_VER.tar.bz2 -C "$1" --strip-components=1
  ( cd "$1" && make defconfig > /dev/null 2>&1 &&
    sed -i 's/^CONFIG_FEATURE_SUID=y/# CONFIG_FEATURE_SUID is not set/; s/^CONFIG_FEATURE_SUID_CONFIG=y/# CONFIG_FEATURE_SUID_CONFIG is not set/; s/^# CONFIG_STATIC is not set/CONFIG_STATIC=y/; s/^CONFIG_TC=y/# CONFIG_TC is not set/; s/^CONFIG_SHA1_HWACCEL=y/# CONFIG_SHA1_HWACCEL is not set/; s/^CONFIG_SHA256_HWACCEL=y/# CONFIG_SHA256_HWACCEL is not set/' .config )
}

build() {  # $1 = build dir, $2 = clang target flags, $3 = output
  ( cd "$1" && make -j8 CC="$BIN/clang $2 -fuse-ld=lld -Wno-error" HOSTCC=cc CROSS_COMPILE= busybox_unstripped > make.log 2>&1 ) || { echo "busybox failed in $1; see $1/make.log"; exit 1; }
  "$BIN/llvm-strip" -o "$3" "$1/busybox_unstripped"
  echo "built $3"
}

G64=$WORK/sysroot64/usr/lib/gcc/aarch64-linux-gnu/14
configure build64
mkdir -p "$HERE/local-jni/arm64-v8a"
build build64 "--target=aarch64-linux-gnu --sysroot=$WORK/sysroot64 -B$G64 -L$G64" "$HERE/local-jni/arm64-v8a/libbusybox.so"

S32=$HERE/local-build/pdk/sysroot
if [ -f "$S32/usr/lib/arm-linux-gnueabi/libc.a" ]; then
  G32=$S32/usr/lib/gcc/arm-linux-gnueabi/12
  configure build32
  mkdir -p "$HERE/local-jni/armeabi-v7a"
  build build32 "--target=armv7-linux-gnueabi -mfloat-abi=softfp -mfpu=neon --sysroot=$S32 -B$G32 -L$G32" "$HERE/local-jni/armeabi-v7a/libbusybox.so"
else
  echo "no armel sysroot (run build-pdk.sh first); the 32-bit busybox was not rebuilt"
fi
# fetch-assets.sh leaves a built busybox alone.
echo "busybox $BB_VER, static, FEATURE_SUID off; built $(date -u +%Y-%m-%d)" > "$HERE/local-jni/.busybox-built"

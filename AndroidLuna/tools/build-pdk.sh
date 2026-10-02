#!/bin/sh
# Builds the PDK runtime (Docs/pdk.md): the glibc a PDK app's binary was linked against,
# SDL 1.2 with Lunacy's drivers, and Lunacy's libpdl, for 32-bit ARM. The runtime lives in
# local-assets/pdk/ (the libraries, extracted to the app's files at first use) and
# local-jni/armeabi-v7a/libld-linux.so (the dynamic loader, which has to be exec'd and so
# lives with the native libraries). Both are gitignored, like fetch-assets.sh's output.
#
# Needs: the NDK's clang (r28c), qemu-arm-static (optional, for the host test), curl, dpkg-deb.
# The glibc, libgcc, libstdc++ and zlib come from Debian bookworm's armel packages: soft-float
# ARM, the ABI every PDK binary was built with (readelf: "soft-float ABI"), each shipped with
# its NOTICE and version.
set -e
HERE=$(cd "$(dirname "$0")/.." && pwd)
NDK=${NDK:-$HOME/Android/Sdk/ndk/android-ndk-r28c}
BIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
WORK=$HERE/local-build/pdk
OUT=$HERE/local-assets/pdk
JNI=$HERE/local-jni/armeabi-v7a
JNI64=$HERE/local-jni/arm64-v8a
SRC=$HERE/../LunaRuntimes/pdk
DEBIAN=https://deb.debian.org/debian
SDL_VER=1.2.15

mkdir -p "$WORK/deb" "$WORK/sysroot" "$OUT/lib" "$JNI" "$JNI64"
cd "$WORK"

# ---- the Debian armel sysroot: runtime libraries and the headers to build against ----
if [ ! -f deb/Packages ]; then
  curl -sL $DEBIAN/dists/bookworm/main/binary-armel/Packages.gz | zcat > deb/Packages
fi
for p in libc6 libc6-dev linux-libc-dev libgcc-s1 libgcc-12-dev libstdc++6 zlib1g zlib1g-dev libogg0 libogg-dev libvorbis0a libvorbisfile3 libvorbis-dev; do
  f=$(awk -v p="$p" '$1=="Package:"{cur=$2} $1=="Filename:" && cur==p {print $2; exit}' deb/Packages)
  b=$(basename "$f")
  if [ ! -f "deb/$b" ]; then echo "fetching $b"; curl -sL "$DEBIAN/$f" -o "deb/$b"; dpkg-deb -x "deb/$b" sysroot; fi
done
G=$WORK/sysroot/usr/lib/gcc/arm-linux-gnueabi/12
# glibc's libm.so is a linker script naming /lib/arm-linux-gnueabi/libm.so.6 by its absolute
# path, which lld doesn't take under --sysroot: it fell back to libm.a, and SDL_mixer came
# out with glibc's private _dl_hwcap unresolved. A symlink to the real library instead.
ln -sf ../../../lib/arm-linux-gnueabi/libm.so.6 $WORK/sysroot/usr/lib/arm-linux-gnueabi/libm.so
CC="$BIN/clang --target=armv7-linux-gnueabi -mfloat-abi=softfp -mfpu=neon --sysroot=$WORK/sysroot -fuse-ld=lld -B$G -L$G"
export CC AR=$BIN/llvm-ar RANLIB=$BIN/llvm-ranlib

# ---- SDL 1.2 with Lunacy's drivers in place of the dummy ones ----
if [ ! -d SDL-$SDL_VER ]; then
  [ -f SDL-$SDL_VER.tar.gz ] || curl -sL https://www.libsdl.org/release/SDL-$SDL_VER.tar.gz -o SDL-$SDL_VER.tar.gz
  tar xzf SDL-$SDL_VER.tar.gz
fi
S=$WORK/SDL-$SDL_VER
rm -f $S/src/video/dummy/*.c $S/src/video/dummy/*.h $S/src/audio/dummy/*.c $S/src/audio/dummy/*.h
cp $SRC/sdl-lunacy/SDL_lunacyvideo.c $SRC/sdl-lunacy/SDL_lunacyvideo.h $SRC/sdl-lunacy/SDL_lunacyevents.c \
   $SRC/sdl-lunacy/SDL_lunacyevents_c.h $SRC/sdl-lunacy/lunacy_protocol.h $S/src/video/dummy/
cp $SRC/sdl-lunacy/SDL_lunacyaudio.c $SRC/sdl-lunacy/SDL_lunacyaudio.h $S/src/audio/dummy/
# The bootstrap tables name the drivers they link; ours replace the dummy entries.
sed -i 's/DUMMY_bootstrap/LUNACY_bootstrap/g' $S/src/video/SDL_video.c $S/src/video/SDL_sysvideo.h
# Palm's SDL_GLattr additions (the PDK's SDL_video.h): SDL_GL_RETAINED_BACKING 16,
# SDL_GL_CONTEXT_MAJOR_VERSION 17, SDL_GL_CONTEXT_MINOR_VERSION 18, SDL_GL_SWAP_CONTROL 19.
# Stock SDL refuses an attribute it doesn't know, and a game asking for a GLES 1 context
# (Transformers G1) quits on the refusal. Accepted here; the driver answers GetAttribute.
if ! grep -q 'Palm attributes' $S/src/video/SDL_video.c; then
  sed -i '/^int SDL_GL_SetAttribute( SDL_GLattr attr, int value )/,/^}/ s/^\t\tdefault:/\t\tcase 17: case 18: case 19: \/* Palm attributes (16, SDL_GL_RETAINED_BACKING, is stock SDL_GL_SWAP_CONTROL here) *\/ retval = 0; break;\n\t\tdefault:/' $S/src/video/SDL_video.c
  grep -q 'Palm attributes' $S/src/video/SDL_video.c || { echo "the SDL_GL_SetAttribute patch didn't apply"; exit 1; }
fi
sed -i 's/DUMMYAUD_bootstrap/LUNACYAUD_bootstrap/g' $S/src/audio/SDL_audio.c $S/src/audio/SDL_sysaudio.h
mkdir -p sdl-build && cd sdl-build
if [ ! -f Makefile ]; then
  $S/configure --host=arm-linux-gnueabi --prefix=$WORK/sdl-out --disable-static --enable-shared \
    --disable-video-x11 --disable-video-directfb --disable-video-fbcon --enable-video-dummy \
    --disable-oss --disable-alsa --disable-pulseaudio --disable-esd --disable-arts --disable-nas \
    --disable-diskaudio --enable-dummyaudio --enable-joystick --enable-cdrom --disable-assembly > configure.log 2>&1
fi
make -j8 > make.log 2>&1
make install > install.log 2>&1
cd "$WORK"
cp sdl-out/lib/libSDL-1.2.so.0.11.4 "$OUT/lib/libSDL-1.2.so.0"

# ---- libpdl, against Palm's own headers ----
PDK_INC=${PDK_INC:-/opt/PalmPDK/include}
$CC -O2 -shared -fPIC -Wl,-soname,libpdl.so -I"$PDK_INC" -I"$PDK_INC/SDL" -I$WORK/sdl-out/include/SDL -I$SRC/sdl-lunacy \
  $SRC/libpdl/pdl.c -o "$OUT/lib/libpdl.so" -L"$OUT/lib" -l:libSDL-1.2.so.0
# ---- SDL_mixer 1.2, with Ogg Vorbis (the format webOS games shipped their music in) ----
MIX_VER=1.2.12
if [ ! -d SDL_mixer-$MIX_VER ]; then
  [ -f SDL_mixer-$MIX_VER.tar.gz ] || curl -sL https://www.libsdl.org/projects/SDL_mixer/release/SDL_mixer-$MIX_VER.tar.gz -o SDL_mixer-$MIX_VER.tar.gz
  tar xzf SDL_mixer-$MIX_VER.tar.gz
fi
mkdir -p mixer-build && cd mixer-build
if [ ! -f Makefile ]; then
  # 2012 code under a 2025 clang: the vorbis callback cast is an error by default now.
  CFLAGS="-O2 -Wno-incompatible-function-pointer-types" SDL_CONFIG=$WORK/sdl-out/bin/sdl-config $WORK/SDL_mixer-$MIX_VER/configure --host=arm-linux-gnueabi --prefix=$WORK/mixer-out \
    --disable-static --enable-shared --disable-music-mod --disable-music-midi --disable-music-flac --disable-music-mp3 \
    --disable-music-mp3-mad-gpl --enable-music-ogg --disable-music-ogg-shared --disable-music-ogg-tremor \
    --with-sdl-prefix=$WORK/sdl-out > configure.log 2>&1
fi
# The library only: the example players don't link here and aren't shipped.
make -j8 -k > make.log 2>&1 || true
[ -f build/.libs/libSDL_mixer-1.2.so.0.12.0 ] || { echo "SDL_mixer failed to build; see $WORK/mixer-build/make.log"; exit 1; }
make install-lib install-hdrs > install.log 2>&1
cd "$WORK"
cp mixer-out/lib/libSDL_mixer-1.2.so.0.12.0 "$OUT/lib/libSDL_mixer-1.2.so.0"
for f in libogg.so.0 libvorbis.so.0 libvorbisfile.so.3; do cp "$WORK/sysroot/usr/lib/arm-linux-gnueabi/$f" "$OUT/lib/"; done
# SDL_image and SDL_ttf: not built yet.

# ---- libGLES_CM: generated from the PDK's own GLES 1.1 headers (Docs/pdk.md, "Transformers G1") ----
# The app's end serialises every call into a batch for the shell (LPDK_GL); the shell's end,
# liblunacygl.so, replays it on a GLES 1.1 context of its own. Both are generated from the
# same headers so the opcodes agree. gen_gles1_log.py is the stage-one logging library.
python3 "$SRC/libgles/gen_gles1.py" client "$PDK_INC/GLES/gl.h" "$PDK_INC/GLES/glext.h" > libgles_cm.c
$CC -O2 -shared -fPIC -Wl,-soname,libGLES_CM.so -I"$PDK_INC" libgles_cm.c -o "$OUT/lib/libGLES_CM.so"
python3 "$SRC/libgles/gen_gles1.py" server "$PDK_INC/GLES/gl.h" "$PDK_INC/GLES/glext.h" > gles_replay.inc
for abi in armeabi-v7a:armv7a-linux-androideabi21 arm64-v8a:aarch64-linux-android21; do
  mkdir -p "$HERE/local-jni/${abi%%:*}"
  "$BIN/clang" --target="${abi##*:}" -O2 -g -shared -fPIC -Wl,-soname,liblunacygl.so -I. "$SRC/libgles/gl_server.c" \
    -lEGL -lGLESv1_CM -landroid -llog -o "$HERE/local-jni/${abi%%:*}/liblunacygl.so"
done
# liblunacy-preload: /proc/self/exe as the app's binary, not the loader's (LD_PRELOAD).
$CC -O2 -shared -fPIC -Wl,-soname,liblunacy-preload.so "$SRC/libpreload/preload.c" -o "$OUT/lib/liblunacy-preload.so" -ldl
# libSDL_cinema: Palm's video player for a game's movies. A stub that has no movie to play.
$CC -O2 -shared -fPIC -Wl,-soname,libSDL_cinema.so "$SRC/libcinema/cinema.c" -o "$OUT/lib/libSDL_cinema.so"

# ---- the glibc runtime ----
L=$WORK/sysroot/lib/arm-linux-gnueabi
for f in libc.so.6 libm.so.6 libdl.so.2 libpthread.so.0 librt.so.1 libgcc_s.so.1 libz.so.1; do cp "$L/$f" "$OUT/lib/"; done
cp $WORK/sysroot/usr/lib/arm-linux-gnueabi/libstdc++.so.6 "$OUT/lib/"
# A build id, so that an installed Lunacy refreshes the extracted runtime when it changes.
date -u +%Y-%m-%dT%H:%M:%SZ > "$OUT/BUILD"
cp $WORK/sysroot/lib/ld-linux.so.3 "$JNI/libld-linux.so"
# The loader again, by its own name, for the emulated path: qemu loads the program and finds
# its interpreter at /lib/ld-linux.so.3 under the runtime folder it is given as a sysroot.
cp $WORK/sysroot/lib/ld-linux.so.3 "$OUT/lib/ld-linux.so.3"

# ---- the emulator, for 64-bit-only devices: Termux's qemu-arm, built for Android ----
# Debian's static qemu (glibc) dies under Android's app seccomp - it makes calls the policy
# doesn't know - where Termux's, linked against bionic, runs (measured on the Pixel Tablet,
# 2026-10-02). It is exec'd, so it lives with the native libraries; the libraries it needs
# (glib, pixman, gnutls and theirs) are assets, extracted with the runtime and found through
# LD_LIBRARY_PATH. qemu is GPL 2, glib and gnutls LGPL; sources are at packages.termux.dev.
TERMUX=https://packages.termux.dev/apt/termux-main
mkdir -p termux "$OUT/qemu-lib"
if [ ! -f termux/Packages ]; then curl -sL $TERMUX/dists/stable/main/binary-aarch64/Packages -o termux/Packages; fi
python3 - "$WORK/termux" "$TERMUX" "$OUT/qemu-lib" "$JNI64/libqemu-arm.so" <<'PY'
import sys, os, re, subprocess, shutil
work, base, outlib, outqemu = sys.argv[1:5]
pk = {}; cur = None
for line in open(os.path.join(work, 'Packages')):
    line = line.rstrip('\n')
    if line.startswith('Package: '): cur = line[9:].strip(); pk[cur] = {}
    elif cur and ': ' in line and not line.startswith(' '):
        k, v = line.split(': ', 1); pk[cur][k] = v
# The package and every package it depends on, by name; then only the libraries the
# binary actually loads, by DT_NEEDED, so python and the rest of gnutls's tail stay out.
want = ['qemu-user-arm']; seen = set()
while want:
    p = want.pop()
    if p in seen or p not in pk: continue
    seen.add(p)
    for d in pk[p].get('Depends', '').split(','):
        d = d.strip().split(' ')[0].split('|')[0].strip()
        if d: want.append(d)
x = os.path.join(work, 'x'); os.makedirs(os.path.join(work, 'deb'), exist_ok=True); os.makedirs(x, exist_ok=True)
for p in sorted(seen):
    f = pk[p]['Filename']; b = os.path.join(work, 'deb', os.path.basename(f))
    if not os.path.exists(b):
        print('fetching', os.path.basename(f)); subprocess.run(['curl', '-sL', base + '/' + f, '-o', b], check=True)
        subprocess.run(['dpkg-deb', '-x', b, x], check=True)
libdir = os.path.join(x, 'data/data/com.termux/files/usr/lib')
qemu = os.path.join(x, 'data/data/com.termux/files/usr/bin/qemu-arm')
shutil.copy(qemu, outqemu); os.chmod(outqemu, 0o755)
system = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so', 'libz.so'}
def needed(p):
    r = subprocess.run(['readelf', '-d', p], capture_output=True, text=True).stdout
    return [l.split('[')[1].rstrip(']') for l in r.splitlines() if 'NEEDED' in l]
todo = [outqemu]; have = set()
while todo:
    for n in needed(todo.pop()):
        if n in system or n in have: continue
        src = os.path.join(libdir, n)
        if not os.path.exists(src):
            alt = [f for f in os.listdir(libdir) if f.startswith(n)]
            if not alt: print('missing', n); continue
            src = os.path.join(libdir, alt[0])
        dst = os.path.join(outlib, n); shutil.copy(src, dst); have.add(n); todo.append(dst)
open(os.path.join(outlib, 'VERSION'), 'w').write(pk['qemu-user-arm']['Version'] + '\n')
print('qemu', pk['qemu-user-arm']['Version'], 'with', len(have), 'libraries')
PY
QEMU_VER=$(cat "$OUT/qemu-lib/VERSION")
cat > "$OUT/NOTICE" <<EOF
The PDK runtime (Docs/pdk.md), built by tools/build-pdk.sh:
- ld-linux.so.3 (as libld-linux.so), libc, libm, libdl, libpthread, librt: GNU C Library
  $(awk '$1=="Package:"{cur=$2} $1=="Version:" && cur=="libc6" {print $2; exit}' deb/Packages) (LGPL 2.1), Debian bookworm armel.
- libgcc_s, libstdc++: GCC 12 runtime (GPL 3 with the GCC Runtime Library Exception), Debian bookworm armel.
- libz: zlib (zlib licence), Debian bookworm armel.
- libSDL-1.2.so.0: SDL $SDL_VER (LGPL 2.1), with Lunacy's video, event and audio drivers
  (LunaRuntimes/pdk/sdl-lunacy, LGPL 2.1).
- libpdl.so: Lunacy's own (LunaRuntimes/pdk/libpdl), built against Palm's PDK headers.
- libSDL_mixer-1.2.so.0: SDL_mixer $MIX_VER (zlib licence), with Ogg Vorbis.
- libGLES_CM.so: Lunacy's own (LunaRuntimes/pdk/libgles), generated from the PDK's Khronos
  GLES 1.1 headers (SGI Free Software License B); its shell-side half is liblunacygl.so in
  the APK's own libraries.
- libSDL_cinema.so: Lunacy's own stub (LunaRuntimes/pdk/libcinema).
- liblunacy-preload.so: Lunacy's own (LunaRuntimes/pdk/libpreload).
- libogg, libvorbis, libvorbisfile: Xiph.Org (BSD), Debian bookworm armel.
- libqemu-arm.so (64-bit builds only): QEMU $QEMU_VER user-mode emulator for 32-bit ARM
  (GPL 2), Termux's build for Android, with the libraries it loads in qemu-lib/ (glib
  LGPL 2.1, pixman MIT, gnutls LGPL 2.1, nettle LGPL, gmp LGPL, libffi MIT, pcre2 BSD,
  libiconv LGPL, libidn2 LGPL, libunistring LGPL, libtasn1 LGPL, p11-kit BSD, elfutils
  LGPL, xz LGPL, zstd BSD, bzip2 BSD, Termux's libandroid-* MIT), from packages.termux.dev.
Sources: https://deb.debian.org/debian/pool/main/ and https://www.libsdl.org/release/.
EOF
ls -la "$OUT/lib" "$JNI/libld-linux.so" "$JNI64/libqemu-arm.so"
echo "pdk runtime built"

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
for p in libc6 libc6-dev linux-libc-dev libgcc-s1 libgcc-12-dev libstdc++6 zlib1g zlib1g-dev libogg0 libogg-dev libvorbis0a libvorbisfile3 libvorbis-dev libjpeg62-turbo libjpeg62-turbo-dev; do
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
# The accelerometer as joystick 0, as webOS had it, in place of the Linux driver.
cp $SRC/sdl-lunacy/SDL_lunacyjoystick.c $S/src/joystick/linux/SDL_sysjoystick.c
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
# The major version is kept for the driver (LUNACY_gl_major): it picks the GL library and
# answers SDL_GL_GetAttribute, and libEGL reports it.
if ! grep -q 'LUNACY_gl_major' $S/src/video/SDL_video.c; then
  sed -i 's|^\t\tcase 17: case 18: case 19: /\* Palm attributes|\t\tcase 17: { extern int LUNACY_gl_major; LUNACY_gl_major = value; }   /* fall through */\n\t\tcase 18: case 19: /* Palm attributes|' $S/src/video/SDL_video.c
  grep -q 'LUNACY_gl_major' $S/src/video/SDL_video.c || { echo "the GLES major version patch didn't apply"; exit 1; }
fi
sed -i 's/DUMMYAUD_bootstrap/LUNACYAUD_bootstrap/g' $S/src/audio/SDL_audio.c $S/src/audio/SDL_sysaudio.h
# No pointer: webOS's SDL drew none, and stock SDL paints a software arrow into a 2D app's
# screen until the app hides it (Drum Machine never does). Hidden from the start; an app
# that calls SDL_ShowCursor(SDL_ENABLE) still gets it.
sed -i 's/^volatile int SDL_cursorstate = CURSOR_VISIBLE;/volatile int SDL_cursorstate = 0;   \/* Lunacy: hidden, as on webOS *\//; s/^\tSDL_cursorstate = CURSOR_VISIBLE;$/\tSDL_cursorstate = 0;   \/* Lunacy: hidden, as on webOS *\//' $S/src/video/SDL_cursor.c
grep -q 'Lunacy: hidden' $S/src/video/SDL_cursor.c || { echo "the SDL cursor patch didn't apply"; exit 1; }
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
  $SRC/libpdl/pdl.c -o "$OUT/lib/libpdl.so" -L"$OUT/lib" -l:libSDL-1.2.so.0 -lpthread
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

# ---- the image and font libraries apps link by name (survey of the mirror: libpng12.so.0,
# libpng.so.3, libjpeg.so.62, libfreetype.so.6), and SDL_image, SDL_ttf and SDL_net ----
SYSLIB=$WORK/sysroot/usr/lib/arm-linux-gnueabi
DEPS=$WORK/deps-out
mkdir -p $DEPS
fetch() { [ -f "$2" ] || curl -sL "$1" -o "$2"; [ -d "${2%.tar.*}" ] || tar xf "$2"; }
# libpng 1.2, the series webOS shipped: libpng12.so.0, and libpng.so.3 for the older name.
PNG_VER=1.2.59
fetch https://download.sourceforge.net/libpng/libpng-$PNG_VER.tar.gz libpng-$PNG_VER.tar.gz
if [ ! -f $DEPS/lib/libpng12.so.0 ]; then
  mkdir -p png-build && cd png-build
  CPPFLAGS="-I$WORK/sysroot/usr/include" LDFLAGS="-L$SYSLIB" $WORK/libpng-$PNG_VER/configure --host=arm-linux-gnueabi \
    --prefix=$DEPS --enable-shared --disable-static > configure.log 2>&1
  make -j8 > make.log 2>&1 && make install > install.log 2>&1
  cd "$WORK"
fi
# FreeType 2, plain: its soname has been libfreetype.so.6 since 2.0. No PNG, bzip2, brotli
# or HarfBuzz, so it needs nothing beyond zlib and libc.
FT_VER=2.13.2
fetch https://download.savannah.gnu.org/releases/freetype/freetype-$FT_VER.tar.gz freetype-$FT_VER.tar.gz
if [ ! -f $DEPS/lib/libfreetype.so.6 ]; then
  mkdir -p ft-build && cd ft-build
  CPPFLAGS="-I$WORK/sysroot/usr/include" LDFLAGS="-L$SYSLIB" $WORK/freetype-$FT_VER/configure --host=arm-linux-gnueabi \
    --prefix=$DEPS --enable-shared --disable-static --with-zlib=yes --with-png=no --with-bzip2=no \
    --with-brotli=no --with-harfbuzz=no > configure.log 2>&1
  make -j8 > make.log 2>&1 && make install > install.log 2>&1
  cd "$WORK"
fi
ADDON_ENV="CPPFLAGS=-I$DEPS/include -I$DEPS/include/freetype2 -I$WORK/sysroot/usr/include LDFLAGS=-L$DEPS/lib -L$SYSLIB"
# SDL_image 1.2: PNG and JPEG linked, not dlopen'd, so the loader finds them by soname.
IMG_VER=1.2.12
fetch https://www.libsdl.org/projects/SDL_image/release/SDL_image-$IMG_VER.tar.gz SDL_image-$IMG_VER.tar.gz
mkdir -p image-build && cd image-build
if [ ! -f Makefile ]; then
  env "CPPFLAGS=-I$DEPS/include -I$WORK/sysroot/usr/include" "LDFLAGS=-L$DEPS/lib -L$SYSLIB" LIBPNG_CFLAGS=-I$DEPS/include LIBPNG_LIBS="-L$DEPS/lib -lpng12" \
    $WORK/SDL_image-$IMG_VER/configure --host=arm-linux-gnueabi --prefix=$WORK/addon-out --disable-static --enable-shared \
    --with-sdl-prefix=$WORK/sdl-out --enable-png --disable-png-shared --enable-jpg --disable-jpg-shared \
    --disable-tif --disable-webp --disable-sdltest > configure.log 2>&1
fi
make -j8 -k > make.log 2>&1 || true
[ -f .libs/libSDL_image-1.2.so.0.8.4 ] || { echo "SDL_image failed; see $WORK/image-build/make.log"; exit 1; }
make install-libLTLIBRARIES install-libSDL_imageincludeHEADERS > install.log 2>&1
cd "$WORK"
# SDL_ttf 2.0, against the FreeType above.
TTF_VER=2.0.11
fetch https://www.libsdl.org/projects/SDL_ttf/release/SDL_ttf-$TTF_VER.tar.gz SDL_ttf-$TTF_VER.tar.gz
mkdir -p ttf-build && cd ttf-build
if [ ! -f Makefile ]; then
  # LIBS: configure found FreeType's headers but left its link line empty: every FT_ symbol
  # came out unresolved and libfreetype.so.6 unnamed.
  env "CPPFLAGS=-I$DEPS/include -I$DEPS/include/freetype2" "LDFLAGS=-L$DEPS/lib -L$SYSLIB" LIBS=-lfreetype FT2_CONFIG=$DEPS/bin/freetype-config \
    FREETYPE_CFLAGS="-I$DEPS/include/freetype2" FREETYPE_LIBS="-L$DEPS/lib -lfreetype" \
    $WORK/SDL_ttf-$TTF_VER/configure --host=arm-linux-gnueabi --prefix=$WORK/addon-out --disable-static --enable-shared \
    --with-sdl-prefix=$WORK/sdl-out --with-freetype-prefix=$DEPS --without-x --disable-sdltest > configure.log 2>&1
fi
make -j8 -k > make.log 2>&1 || true
[ -f .libs/libSDL_ttf-2.0.so.0.10.1 ] || { echo "SDL_ttf failed; see $WORK/ttf-build/make.log"; exit 1; }
readelf -d .libs/libSDL_ttf-2.0.so.0.10.1 | grep -q libfreetype.so.6 || { echo "SDL_ttf does not name libfreetype.so.6"; exit 1; }
make install-libLTLIBRARIES > install.log 2>&1
cd "$WORK"
# SDL_net 1.2.
NET_VER=1.2.8
fetch https://www.libsdl.org/projects/SDL_net/release/SDL_net-$NET_VER.tar.gz SDL_net-$NET_VER.tar.gz
mkdir -p net-build && cd net-build
if [ ! -f Makefile ]; then
  $WORK/SDL_net-$NET_VER/configure --host=arm-linux-gnueabi --prefix=$WORK/addon-out --disable-static --enable-shared \
    --with-sdl-prefix=$WORK/sdl-out --disable-gui --disable-sdltest > configure.log 2>&1
fi
make -j8 -k > make.log 2>&1 || true
[ -f .libs/libSDL_net-1.2.so.0.8.0 ] || { echo "SDL_net failed; see $WORK/net-build/make.log"; exit 1; }
make install-libLTLIBRARIES > install.log 2>&1
cd "$WORK"
cp addon-out/lib/libSDL_image-1.2.so.0.8.4 "$OUT/lib/libSDL_image-1.2.so.0"
cp addon-out/lib/libSDL_ttf-2.0.so.0.10.1 "$OUT/lib/libSDL_ttf-2.0.so.0"
cp addon-out/lib/libSDL_net-1.2.so.0.8.0 "$OUT/lib/libSDL_net-1.2.so.0"
cp -L $DEPS/lib/libpng12.so.0 $DEPS/lib/libpng.so.3 $DEPS/lib/libfreetype.so.6 "$OUT/lib/"
cp -L $SYSLIB/libjpeg.so.62 "$OUT/lib/"
# ---- the TouchPad's other system fonts ----
# Besides Prelude, /usr/share/fonts on the TouchPad holds Microsoft's core web fonts (arial,
# cour, georgia, times, verdana, lucon), which apps open by name with SDL_ttf (Plasma Clock
# quits without arial.ttf). Those can't be shipped; the metric-compatible Liberation fonts
# (SIL OFL) stand in under the TouchPad's file names, so text keeps its widths, and DejaVu
# Sans for Verdana. The CJK fonts aren't stood in for.
mkdir -p fonts-x "$OUT/fonts"
for p in fonts-liberation2 fonts-dejavu-core fonts-dejavu-extra; do
  f=$(awk -v p="$p" '$1=="Package:"{cur=$2} $1=="Filename:" && cur==p {print $2; exit}' deb/Packages)
  b=$(basename "$f")
  if [ ! -f "deb/$b" ]; then echo "fetching $b"; curl -sL "$DEBIAN/$f" -o "deb/$b"; fi
  dpkg-deb -x "deb/$b" fonts-x
done
LIB=fonts-x/usr/share/fonts/truetype/liberation2; DJ=fonts-x/usr/share/fonts/truetype/dejavu
# Each source once; a TouchPad name whose source is already there becomes an alias,
# made as a link at extraction (fonts/aliases).
rm -f "$OUT/fonts/"*; : > "$OUT/fonts/aliases"
while read tp src; do
  first=$(grep -l "^$src\$" "$OUT/fonts/".src-* 2>/dev/null | head -1)
  if [ -n "$first" ]; then echo "$tp ${first##*/.src-}" >> "$OUT/fonts/aliases"
  else cp "$src" "$OUT/fonts/$tp"; echo "$src" > "$OUT/fonts/.src-$tp"; fi
done <<EOF2
arial.ttf $LIB/LiberationSans-Regular.ttf
arialbd.ttf $LIB/LiberationSans-Bold.ttf
ariali.ttf $LIB/LiberationSans-Italic.ttf
arialbi.ttf $LIB/LiberationSans-BoldItalic.ttf
cour.ttf $LIB/LiberationMono-Regular.ttf
courbd.ttf $LIB/LiberationMono-Bold.ttf
couri.ttf $LIB/LiberationMono-Italic.ttf
courbi.ttf $LIB/LiberationMono-BoldItalic.ttf
times.ttf $LIB/LiberationSerif-Regular.ttf
timesbd.ttf $LIB/LiberationSerif-Bold.ttf
timesi.ttf $LIB/LiberationSerif-Italic.ttf
timesbi.ttf $LIB/LiberationSerif-BoldItalic.ttf
georgia.ttf $LIB/LiberationSerif-Regular.ttf
georgiab.ttf $LIB/LiberationSerif-Bold.ttf
georgiai.ttf $LIB/LiberationSerif-Italic.ttf
georgiaz.ttf $LIB/LiberationSerif-BoldItalic.ttf
verdana.ttf $DJ/DejaVuSans.ttf
verdanab.ttf $DJ/DejaVuSans-Bold.ttf
verdanai.ttf $DJ/DejaVuSans-Oblique.ttf
verdanabi.ttf $DJ/DejaVuSans-BoldOblique.ttf
lucon.ttf $LIB/LiberationMono-Regular.ttf
EOF2
rm -f "$OUT/fonts/".src-*
cat > "$OUT/fonts/NOTICE" <<'EOF2'
Stand-ins for the Microsoft core fonts the HP TouchPad carries in /usr/share/fonts, which
can't be redistributed here. Each file has the TouchPad's name and holds a free font of the
same metrics (except Verdana and Georgia, which have none):
  arial*, cour*, lucon, times*, georgia*: Liberation Sans, Mono and Serif 2.1.5, SIL Open
    Font License 1.1 (Debian bookworm fonts-liberation2).
  verdana*: DejaVu Sans 2.37, Bitstream Vera / DejaVu licence (Debian bookworm
    fonts-dejavu-core and fonts-dejavu-extra).
Sources: https://github.com/liberationfonts/liberation-fonts and https://dejavu-fonts.github.io/
EOF2

# Names apps link by that are another library's: made as symlinks when the runtime is
# extracted (PdkRuntime), since assets can't hold links.
cat > "$OUT/aliases" <<EOA
libSDL.so libSDL-1.2.so.0
libSDL-1.2.so libSDL-1.2.so.0
libSDL_mixer.so libSDL_mixer-1.2.so.0
libSDL_image.so libSDL_image-1.2.so.0
libSDL_ttf.so libSDL_ttf-2.0.so.0
libSDL_net.so libSDL_net-1.2.so.0
libdl.so libdl.so.2
libgcc_s.so libgcc_s.so.1
libstdc++.so libstdc++.so.6
libpng12.so libpng12.so.0
libjpeg.so libjpeg.so.62
libfreetype.so libfreetype.so.6
libz.so libz.so.1
libopenal.so libopenal.so.1
libGLES_CM.so.1 libGLES_CM.so
libGLESv2.so.2 libGLESv2.so
EOA

# ---- libGLES_CM: generated from the PDK's own GLES 1.1 headers (Docs/pdk.md, "Transformers G1") ----
# The app's end serialises every call into a batch for the shell (LPDK_GL); the shell's end,
# liblunacygl.so, replays it on a GLES 1.1 context of its own. Both are generated from the
# same headers so the opcodes agree. gen_gles1_log.py is the stage-one logging library.
python3 "$SRC/libgles/gen_gles1.py" client "$PDK_INC/GLES/gl.h" "$PDK_INC/GLES/glext.h" > libgles_cm.c
$CC -O2 -shared -fPIC -Wl,-soname,libGLES_CM.so -Wl,-Bsymbolic -I"$PDK_INC" -I"$SRC/libgles" libgles_cm.c -o "$OUT/lib/libGLES_CM.so"
python3 "$SRC/libgles/gen_gles1.py" server "$PDK_INC/GLES/gl.h" "$PDK_INC/GLES/glext.h" > gles_replay.inc
# GLES 2 the same way (gen_gles2.py): libGLESv2.so for the app, liblunacygl2.so for the shell.
python3 "$SRC/libgles/gen_gles2.py" client "$PDK_INC/GLES2/gl2.h" "$PDK_INC/GLES2/gl2ext.h" > libglesv2.c
$CC -O2 -shared -fPIC -Wl,-soname,libGLESv2.so -Wl,-Bsymbolic -I"$PDK_INC" -I"$SRC/libgles" libglesv2.c -o "$OUT/lib/libGLESv2.so"
python3 "$SRC/libgles/gen_gles2.py" server "$PDK_INC/GLES2/gl2.h" "$PDK_INC/GLES2/gl2ext.h" > gles2_replay.inc
# Each client library binds its own lunacy_gl_* (-Bsymbolic above): an apkenv port loads both.
# libEGL: the TouchPad's EGL as a PDK app saw it, over SDL's one context (Docs/pdk.md, "EGL").
$CC -O2 -shared -fPIC -Wl,-soname,libEGL.so "$SRC/libegl/egl.c" -o "$OUT/lib/libEGL.so" -ldl
for abi in armeabi-v7a:armv7a-linux-androideabi21 arm64-v8a:aarch64-linux-android21; do
  mkdir -p "$HERE/local-jni/${abi%%:*}"
  "$BIN/clang" --target="${abi##*:}" -O2 -g -shared -fPIC -Wl,-soname,liblunacygl.so -I. -I"$SRC/libgles" "$SRC/libgles/gl_server.c" \
    -lEGL -lGLESv1_CM -landroid -llog -lm -o "$HERE/local-jni/${abi%%:*}/liblunacygl.so"
  "$BIN/clang" --target="${abi##*:}" -O2 -g -shared -fPIC -DGLES_VERSION=2 -Wl,-soname,liblunacygl2.so -I. -I"$SRC/libgles" "$SRC/libgles/gl_server.c" \
    -lEGL -lGLESv2 -landroid -llog -lm -o "$HERE/local-jni/${abi%%:*}/liblunacygl2.so"
done
# libenosys: the native path's guard on Android 10 and later, a bionic program that runs the
# app as its ptrace child and answers the calls seccomp refuses with -ENOSYS (32-bit build only;
# named lib*.so so the installer puts it with the native libraries, where it can be exec'd).
"$BIN/clang" --target=armv7a-linux-androideabi21 -O2 "$SRC/enosys/enosys.c" -o "$HERE/local-jni/armeabi-v7a/libenosys.so"
# liblunacy-preload: /proc/self/exe as the app's binary, not the loader's (LD_PRELOAD).
$CC -O2 -shared -fPIC -Wl,-soname,liblunacy-preload.so "$SRC/libpreload/preload.c" -o "$OUT/lib/liblunacy-preload.so" -ldl -lpthread
# liblunaservice: the Luna bus client library native system services link (the TouchPad's
# mail services and file cache), carrying the bus over the link the shell gives them. Its
# glib calls resolve against the TouchPad's glib the services load with it.
$CC -O2 -shared -fPIC -Wl,-soname,liblunaservice.so "$SRC/liblunaservice/lunaservice.c" -o "$OUT/lib/liblunaservice.so" -lpthread
# ---- OpenAL Soft 1.11.753, the version the TouchPad shipped as libopenal.so.1 ----
# Palm built it with two backends, an SDL one of its own (Alc/sdl.c, which webOS games
# played through) and the wave writer; Lunacy's sdl.c is written again from what the
# device's library shows of Palm's (LunaRuntimes/pdk/openal). Built straight from the
# sources with the flags its CMake gives a release build, with config.h written here.
AL_VER=1.11.753
if [ ! -d openal-soft-openal-soft-$AL_VER ]; then
  [ -f openal-soft-$AL_VER.tar.gz ] || curl -sL https://github.com/kcat/openal-soft/archive/refs/tags/openal-soft-$AL_VER.tar.gz -o openal-soft-$AL_VER.tar.gz
  echo "1c16af245dc5721a899d49c883f76393c1f9732681d6881894dfcc77de1ddeeb  openal-soft-$AL_VER.tar.gz" | sha256sum -c --quiet || { echo "openal-soft-$AL_VER.tar.gz isn't the source it should be"; exit 1; }
  tar xzf openal-soft-$AL_VER.tar.gz
fi
A=$WORK/openal-soft-openal-soft-$AL_VER
cp $SRC/openal/sdl.c $A/Alc/sdl.c
# The SDL backend ahead of the wave writer, which only opens when a config names a file.
if ! grep -q alc_sdl_init $A/Alc/ALc.c; then
  sed -i 's|^    { "wave", alc_wave_init, alc_wave_deinit, alc_wave_probe, EmptyFuncs },|    { "sdl", alc_sdl_init, alc_sdl_deinit, alc_sdl_probe, EmptyFuncs },\n&|' $A/Alc/ALc.c
  sed -i 's|^void alc_wave_init(BackendFuncs \*func_list);|void alc_sdl_init(BackendFuncs *func_list);\nvoid alc_sdl_deinit(void);\nvoid alc_sdl_probe(int type);\n&|' $A/OpenAL32/Include/alMain.h
  grep -q alc_sdl_init $A/Alc/ALc.c && grep -q alc_sdl_init $A/OpenAL32/Include/alMain.h || { echo "the OpenAL backend patch didn't apply"; exit 1; }
fi
mkdir -p openal-build
cat > openal-build/config.h <<CFG
#ifndef CONFIG_H
#define CONFIG_H
#define ALSOFT_VERSION "$AL_VER"
#define HAVE_DLFCN_H
#define HAVE_STAT
#define HAVE_SQRTF
#define HAVE_ACOSF
#define HAVE_ATANF
#define HAVE_FABSF
#define HAVE_STRTOF
#define HAVE_STDINT_H
#define SIZEOF_LONG 4
#define SIZEOF_LONG_LONG 8
#define SIZEOF_UINT 4
#define SIZEOF_VOIDP 4
#define HAVE_GCC_DESTRUCTOR
#define HAVE_GCC_FORMAT
#define HAVE_FLOAT_H
#define HAVE_FENV_H
#define HAVE_FESETROUND
#define HAVE_PTHREAD_SETSCHEDPARAM
#endif
CFG
$CC -O2 -funroll-loops -fomit-frame-pointer -DNDEBUG -D_GNU_SOURCE=1 -DAL_BUILD_LIBRARY -pthread \
  -fvisibility=hidden -DHAVE_GCC_VISIBILITY -Wno-everything -shared -fPIC -Wl,-soname,libopenal.so.1 \
  -I$WORK/openal-build -I$A/OpenAL32/Include -I$A/include \
  $A/OpenAL32/alAuxEffectSlot.c $A/OpenAL32/alBuffer.c $A/OpenAL32/alDatabuffer.c $A/OpenAL32/alEffect.c \
  $A/OpenAL32/alError.c $A/OpenAL32/alExtension.c $A/OpenAL32/alFilter.c $A/OpenAL32/alListener.c \
  $A/OpenAL32/alSource.c $A/OpenAL32/alState.c $A/OpenAL32/alThunk.c \
  $A/Alc/ALc.c $A/Alc/ALu.c $A/Alc/alcConfig.c $A/Alc/alcEcho.c $A/Alc/alcReverb.c $A/Alc/alcRing.c \
  $A/Alc/alcThread.c $A/Alc/bs2b.c $A/Alc/wave.c $A/Alc/sdl.c \
  -o "$OUT/lib/libopenal.so.1" -lrt -ldl -lm

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
  the APK's own libraries. libGLESv2.so likewise from the GLES 2 headers (liblunacygl2.so).
- libEGL.so: Lunacy's own (LunaRuntimes/pdk/libegl).
- libSDL_cinema.so: Lunacy's own stub (LunaRuntimes/pdk/libcinema).
- libopenal.so.1: OpenAL Soft $AL_VER (LGPL 2), the TouchPad's version, from
  https://github.com/kcat/openal-soft (tag openal-soft-$AL_VER), with Lunacy's SDL backend
  (LunaRuntimes/pdk/openal/sdl.c) in place of Palm's.
- liblunacy-preload.so: Lunacy's own (LunaRuntimes/pdk/libpreload).
- liblunaservice.so: Lunacy's own (LunaRuntimes/pdk/liblunaservice), for webOS's Luna
  service bus API; its structures follow Open webOS's luna-service2 header (Apache 2.0).
- libogg, libvorbis, libvorbisfile: Xiph.Org (BSD), Debian bookworm armel.
- libSDL_image-1.2.so.0: SDL_image $IMG_VER (zlib licence); libSDL_ttf-2.0.so.0: SDL_ttf $TTF_VER
  (zlib licence); libSDL_net-1.2.so.0: SDL_net $NET_VER (zlib licence). From libsdl.org.
- libpng12.so.0, libpng.so.3: libpng $PNG_VER (libpng licence), from libpng.org.
- libfreetype.so.6: FreeType $FT_VER (FreeType Project License), from savannah.gnu.org.
- libjpeg.so.62: libjpeg-turbo (IJG and BSD licences), Debian bookworm armel libjpeg62-turbo.
- libqemu-arm.so (64-bit builds only): QEMU $QEMU_VER user-mode emulator for 32-bit ARM
  (GPL 2), Termux's build for Android, with the libraries it loads in qemu-lib/ (glib
  LGPL 2.1, pixman MIT, gnutls LGPL 2.1, nettle LGPL, gmp LGPL, libffi MIT, pcre2 BSD,
  libiconv LGPL, libidn2 LGPL, libunistring LGPL, libtasn1 LGPL, p11-kit BSD, elfutils
  LGPL, xz LGPL, zstd BSD, bzip2 BSD, Termux's libandroid-* MIT), from packages.termux.dev.
Sources: https://deb.debian.org/debian/pool/main/ and https://www.libsdl.org/release/.
EOF
ls -la "$OUT/lib" "$JNI/libld-linux.so" "$JNI64/libqemu-arm.so"
echo "pdk runtime built"

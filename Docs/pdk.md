# PDK apps

Started 2026-10-02 with Commander Keen (`com.cmdrkeen.game` 1.6.0, CloneKeen for the
TouchPad), which codepoet picked as a light first case. What was learnt, what was built, and
how the rest of the PDK could follow. Fidelity matters less here than on the shell
(codepoet): the goal is that the apps run.

## 1. What a PDK app is

A webOS PDK app is a Linux ELF executable: 32-bit ARMv7, Thumb-2, VFPv3 and NEON, the
**soft-float ABI** (`readelf`: "soft-float ABI", `Tag_ABI_HardFP_use: Deprecated`),
dynamically linked against **glibc** (`/lib/ld-linux.so.3`, the TouchPad's is glibc 2.8 from
CodeSourcery's 2010 toolchain; the PDK's device libs are 2.5), **SDL 1.2** (Palm's build,
`libSDL-1.2.so.0.11.2`, with `SDL_image`, `SDL_mixer`, `SDL_ttf`, `SDL_net`), **libpdl** (Palm's
PDL API: 84 functions, listed in `/opt/PalmPDK/include/PDL*.h`), and for 3D games `libGLESv2`
or `libGLES_CM`. Palm's SDL has no video driver of its own in the usual sense: it rendered
through `libnapp` and `libPiranha`, LunaSysMgr's native-window and 2D libraries, which is
the one part that cannot come across.

Keen's own surface, measured with `nm -D`: 85 undefined symbols in all. 22 are `SDL_`
(a 2D software renderer: `SDL_SetVideoMode`, `SDL_UpperBlit`, `SDL_Flip`, `SDL_SoftStretch`,
`SDL_OpenAudio`, `SDL_PollEvent`, …), 3 are `PDL_` (`PDL_Init`, `PDL_GesturesEnable`,
`PDL_SetTouchAggression`), none are `Mix_` although it links SDL_mixer, none are C++, and
the rest are glibc 2.4 and the compiler's `__aeabi_*` helpers. It asks for a 1024 × 768
full-screen mode and draws a 320 × 240 game surface into it.

## 1a. The corpus

Every package in the mirror of appcatalog.webosarchive.org (4,318 ipks, scanned 2026-10-02 for
`"type": "pdk"` or `"game"` and the main binary's `NEEDED` list):

| | apps |
|---|---|
| PDK and game apps | **729** (111 of them typed `game`) |
| 2D only, no GLES | **151**: EA's Worms and Game of Life, the explusalpha emulators (SNES9x, NES, GBA, Genesis, …), Grave Defense, Keen and more |
| OpenGL ES 1.x (`libGLES_CM`) | **473** |
| OpenGL ES 2 (`libGLESv2`) | **122** |
| link `SDL_mixer` / `SDL_image` / `SDL_ttf` / `SDL_cinema` / `SDL_net` | 461 / 306 / 141 / 143 / 113 |
| no SDL at all | 101 (plugins and odd ones) |
| C++ runtime | 574 |

So the 2D path reaches about a fifth of the catalogue, and the GLES 1.1 stream two thirds more
(SDL_image, SDL_ttf and SDL_net are built since, section 5b). GLES 2 is the last fifth.

## 2. What runs where

| | Nexus 5 (Snapdragon 800) | HP 10 G2, Nexus 7 2012 | Pixel Tablet, most phones since 2023 |
|---|---|---|---|
| CPU | ARMv7, NEON, VFPv3/4 | 32-bit ARMv7 | ARMv8, **64-bit only**: no 32-bit mode at all |
| The binary as shipped | runs | should run (not yet tried) | can't execute |

The Android 5 test devices are the same CPU class as webOS hardware and run the binaries
as they are; the 64-bit-only SoCs need the emulation path below.

**Proof, 2026-10-02.** Keen's binary, unchanged, ran on the Nexus 5's Android 6 kernel
through Debian's glibc 2.36 for armel with SDL 1.2.15 built with its dummy video and audio
drivers: the loader loaded it, every symbol resolved, it read its data files and ran its
game loop until killed. The same runs on this machine under `qemu-arm-static`, which is the
desk-side test bed.

The one Android rule that matters: the glibc **loader has to be exec'd**, and Android lets an
app exec files from its native-library directory on every version, and from its own data
directory only while it targets API 28 or lower. So `ld-linux.so.3` ships as
`libld-linux.so` in `jniLibs`, and everything else (the libraries, the app's binary) is only
mmap'd by it, which is allowed. The 32-bit flavour targets 24; this is a ratchet item for
later targets (architecture.md, "Platform target").

## 3. How Lunacy runs one

**The process.** `PdkHost` starts `libld-linux.so --library-path <runtime> <app>/<main>` in
the app's folder, with the SDL and PDL drivers pointed at it by environment variables.
The runtime is Debian bookworm's armel glibc, libgcc, libstdc++ and zlib (each with its
NOTICE), SDL 1.2.15 rebuilt with Lunacy's drivers, and Lunacy's own libpdl; all built by
`AndroidLuna/tools/build-pdk.sh` with the NDK's clang targeting `armv7-linux-gnueabi`
against the Debian sysroot, into `local-assets/pdk` and `local-jni/armeabi-v7a`. The
libraries are extracted from the assets to `files/pdk/lib` at first use.

**The screen.** SDL's video driver (`LunaRuntimes/pdk/sdl-lunacy`) makes the screen surface
a file both sides mmap (`files/pdk/fb/<id>`), 32-bit R G B X in memory, which is what an
Android `ARGB_8888` bitmap holds, so a frame is `copyPixelsFromBuffer` and a draw. `SDL_Flip`
and `SDL_UpdateRects` send one message; the host copies the file into the bitmap the view
isn't showing and swaps. The view keeps the app's aspect in the card, letterboxed.

**Input.** The host sends each finger down the socket in the app's screen px; the driver's
event pump posts them as SDL mouse events, the first finger as SDL's own mouse and every
finger's events carrying its index in `which`, as Palm's SDL did for multi-touch. The back
gesture is an Escape key, as Palm's SDL gave it. Keys arrive as SDL keysyms.

**Sound.** The audio driver sends each mixed buffer down a second connection; the host plays
it through an `AudioTrack`. The socket's buffering paces the app.

**PDL.** Lunacy's libpdl (`LunaRuntimes/pdk/libpdl/pdl.c`, built against Palm's headers)
answers from the environment the shell set (screen metrics, OS version, device name,
nduid, language, the app's paths) and sends the rest as JSON to the host: orientation
(`PDL_SetOrientation` turns the card), vibration, gestures, the screen timeout. Not yet
carried: `PDL_ServiceCall` (the bus), sensors, the JS side of hybrid apps, purchases.

**The card.** `PdkWindow` is an `AppWindow` that never loads a page, with the frame view as
its child, so the card layer sizes, scales, thumbnails and closes it as any card. The
loading card goes at the first frame. The process ending closes the card.

## 4. The wire protocol

In `LunaRuntimes/pdk/sdl-lunacy/lunacy_protocol.h`, mirrored in `PdkHost`. Every message is
three little-endian uint32s, type, a, length, then the payload. Connections on one
abstract Unix socket, told apart by a first byte: `V` for video, input and PDL, `A` for
audio, `P` for libpdl's own requests, `G` for the GL stream (blocking, with the shell's
answers coming back on it: swap acknowledgements, read pixels, GLES 2 query results). The
shell writes to the app from a sender thread, never the main thread.

## 5. Approaches for the rest, none ruled out

1. **Run the binary** (built): every 32-bit ARM device. The cheapest path and the exact
   original code. What it needs per library: an SDL 1.2 built against the shell (done),
   `SDL_image`/`SDL_mixer`/`SDL_ttf` built the same way (not yet; a Keen-era game links
   `SDL_mixer` without calling it, so a placeholder stands in), and for 3D games an
   `libGLESv2` that reaches the GPU. GLES across processes is the hard one: either render
   in the app's process into a buffer the shell reads (a software GLES, slow), or give the
   process a real EGL surface, which means the Android `Surface` crossing a process
   boundary, which binder can do but a plain process can't. Worth measuring how many PDK
   apps are 2D first.
2. **Emulate** on 64-bit-only and Intel devices: `qemu-user` for 32-bit ARM runs the same
   runtime and binary, as it does on this machine, at a cost in speed that a 2011 game may
   well afford. codepoet's thought of a **separate compatibility APK** fits here: the
   emulator and the runtime are big, most users on 32-bit devices won't need them, and a
   companion APK (like the keyboard) could carry them and be invoked by the shell over an
   intent and the same socket.
3. **Recompile** where source exists: CloneKeen is GPL and its source is on the net, as is
   much of what the PDK community ported (ScummVM, DOSBox, Quake). An NDK build of SDL 1.2
   against the Lunacy drivers would let those run natively on every ABI, without glibc.
4. **A loader in-process** (apkenv in reverse): resolve the binary's imports against bionic
   plus shims. Keen's 85 symbols say it's smaller than it sounds for simple apps, but glibc
   ABI details (FILE, pthread types, TLS) make it a long tail; the running-glibc route
   avoids all of it, so this is the fallback if exec is ever closed off.

**Android 10 and later: the native path under `libenosys.so`** (2026-10-05). Android's app
seccomp policy refuses a 32-bit process `set_robust_list` (338) and `rseq` (398), and kills it
for asking (SIGSYS, exit 159). Debian's glibc 2.36 makes both as its loader sets up the first
thread, before the program's first line, so every PDK app died at once on Android 12 and 14.
glibc goes on without either when the kernel says it hasn't the call; the trouble is only
that Android kills instead of saying so. A SIGSYS handler in the preload can't answer for it:
the loader's calls come before any preload is loaded, and glibc makes `set_robust_list` again
as each new thread starts, with every signal blocked, where a SIGSYS kills whatever the
handler (Fieldrunners died that way). Patching the two calls out of the loader got it through
startup and no further.

So from Android 10 the 32-bit build starts a native app through `libenosys.so`
(`LunaRuntimes/pdk/enosys/`), a small bionic program that runs it as its ptrace child and
answers each refused call with -ENOSYS, logging each number once. The kernel stops the app
only for a refused call, a signal, a new thread or the exec; its other calls run untraced, so
games keep the TouchPad's 60 frames/s. Measured natively, where qemu had been the only way:
Tiger Woods, Fieldrunners and Where's My Water at 60 frames/s on a Kyocera tablet (Android 12,
Adreno 620); Where's My Water on a Galaxy Tab A7 Lite (Android 14) at 60 frames/s, where under
qemu it played at about 6 and took 20 to 65 s to load each level. Older Android starts the
loader directly, as before. A debugger can't attach to an app the helper is tracing.

## 5a. 64-bit-only devices: the emulated path, measured on the Pixel Tablet

Three things were learnt the hard way, each with `qemu-user`'s `-strace` or by elimination:

- **Debian's static qemu (glibc) can't run inside an app.** It starts from `adb shell`, and
  runs Keen to the point of asking for the shell's socket, but started by Lunacy it dies with
  SIGSYS: glibc's startup makes calls Android's app seccomp policy doesn't list (`rseq`, and
  more that the nested trace couldn't show). Termux's qemu, built against bionic, runs under
  the app's sandbox, so that is the one shipped (`libqemu-arm.so` with its 24 libraries in
  `qemu-lib/`, from packages.termux.dev).
- **The same seccomp killed every package script on Android 10 and later.** The prebuilt
  busybox binaries (busybox.net's armv7, Alpine's arm64) call `setuid` and `setgid` as every
  applet starts, and both are on bionic's blocklist: exit 159, no output, before the first
  line. `tools/build-busybox.sh` builds busybox for both ABIs with `FEATURE_SUID` off, and
  `fetch-assets.sh` keeps that build. Keen's postinst then ran clean on the tablet - under
  Android's own `/system/bin/sh`, it turned out: the static glibc build still died of
  SIGSYS in glibc's own startup, so the tablet's root had no busybox commands and a postinst
  that used one (webOS Account's `mount`) failed. Since 2026-10-03 the script builds against
  bionic ([BUILDING.md](../BUILDING.md)), and every system call is one Android makes itself.
- **And then the prebuilts came back.** `fetch-assets.sh` empties `local-jni/` before it
  looked there for the marker saying busybox had been built, so every run after 0.5.0 quietly
  put the prebuilt busyboxes back - and emptied the PDK runtime with them. Found 2026-10-05
  on the Pixel Tablet, where every novacom `run` died with exit 159. The build now lives in
  `local-build/busybox/out/`, which `fetch-assets.sh` builds when it is missing and copies
  from on every run, and `fetch-assets.sh` runs `build-pdk.sh` at its end.
- **qemu checks the execute bit**, and the installer keeps a package's files as they came,
  with none; the binary was "Exec format error" until `PdkHost` sets it before launch.
- qemu needs the program run as the program (`qemu-arm -L <runtime> <binary>`), not the
  loader as the program: run that way its fixed mappings land on the loader's and it crashes.

The runtime folder doubles as qemu's sysroot: `files/pdk/lib` holds `ld-linux.so.3` by its
own name, so `-L files/pdk` finds `/lib/ld-linux.so.3` and the libraries beside it.

**Result, 2026-10-02:** Keen plays on the Pixel Tablet in a Lunacy card, the same binary
as on the Nexus 5, through Termux's qemu: the title screen, then its demo level running.
Speed not yet measured; the demo looked smooth in screenshots. Apps of this size are well
within what a Tensor G2 emulates; a GLES game under emulation would still have the GL path
to cross.

## 5b. Transformers G1: Awakening, the second app

codepoet's pick for generalising: `com.glu.app.transg1` 1.2.0, a Glu game typed `game`, PIE,
linking **OpenGL ES 1.1** (85 `gl*` calls, `libGLES_CM`), SDL (15 calls, among them
`SDL_SetVideoMode` with `SDL_OPENGL`, `SDL_GL_SwapBuffers`, timers and mutexes), SDL_mixer (8
`Mix_*` calls: music and channels), `libSDL_cinema` (Palm's video player, 6 `CIN_*` calls, for
its intro movie) and 5 PDL calls (`SetOrientation`, `ScreenTimeoutEnable`, `NotifyMusicPlaying`,
`LaunchBrowser`, `Quit`). SDL_mixer is built now. What it still needs is the GL path:

- Our SDL driver takes an `SDL_OPENGL` mode once it has `GL_LoadLibrary`, `GL_GetProcAddress`,
  `GL_MakeCurrent` and `GL_SwapBuffers`; SDL otherwise refuses with "OpenGL not available".
- A `libGLES_CM.so` in the app's process that **serialises** the 85 calls down the socket,
  copying the client-side arrays each draw references (the pointer semantics are known), and
  a GLES 1.1 context in the shell, on a `TextureView` in the card, that replays them. Indirect
  rendering, as GLX did it; 150-odd functions in the full API, 85 for this game. Readbacks
  (`glReadPixels`, `glGetError`, `glGetIntegerv`) are round trips.
- `libSDL_cinema` as a stub that reports the movie as finished, until a player exists.

**Stage one, done 2026-10-02 (evening):** Transformers runs to its render loop on the Nexus 5
and under qemu on the desk, against a *logging* `libGLES_CM.so` generated from the PDK's
own GLES 1.1 headers (`LunaRuntimes/pdk/libgles/gen_gles1.py`, 283 functions, queries
answered plausibly). Getting there, in order, each found with `LD_DEBUG=bindings` or
gdb through qemu's stub:

1. `SDL_Init` refused a subsystem flag because the build had `--disable-joystick`; enabled,
   with no devices behind it (codepoet: joystick support came late to webOS and few games
   use it - this is only so the flag is accepted).
2. `SDL_GL_SetAttribute(17, 1)` was refused: Palm's SDL added `SDL_GL_RETAINED_BACKING`
   (16), `SDL_GL_CONTEXT_MAJOR_VERSION` (17), `_MINOR_VERSION` (18) and moved
   `SDL_GL_SWAP_CONTROL` to 19; and a `SDL_OPENGLES` mode flag (0x40). Accepted now, and
   `GetAttribute` answers a GLES 1.1 context.
3. The binary asked for an executable stack (`PT_GNU_STACK` RWE), which Android refuses an
   app outright: the host runs a copy beside it with the flag cleared (`.lunacy.<name>`),
   the one change `execstack -c` makes; the package's file is untouched.
4. `/proc/self/exe` named the loader, so the game built its data path from the wrong place
   and aborted: `liblunacy-preload.so` (LD_PRELOAD) answers that link with the binary.
5. SDL clears the screen surface it is handed whatever the mode, so the GL surface has
   pixels of its own, unused by GL.
6. A fresh runtime wasn't being extracted on the device: the stamp is now the build's id.

The game is a Pre-era 320 × 480 title; on a TouchPad it ran in the emulated card. What it
uses of GLES 1.1, from the log: fixed-point everything (`glOrthox`, `glRotatex`,
`glColor4x`, `glTexEnvx`), one texture unit, vertex and texcoord arrays, `glDrawArrays`,
`glBindFramebufferOES(0)`, no VBOs so far.

**Stage two, done 2026-10-02 (night): the GL stream.** Transformers plays, on the Nexus 5
natively and on the Pixel Tablet under qemu. Both halves are generated from the PDK's own
GLES 1.1 headers by `LunaRuntimes/pdk/libgles/gen_gles1.py`, so the opcodes agree by
construction:

- *The app's end*, `libGLES_CM.so` (`gen_gles1.py client` + `client_prelude.c`): every
  `glXxx` the headers declare writes its call into a batch - scalars as 4-byte units, a
  copied pointer argument as a length and its bytes, padded to 4 - sent on a connection of
  the library's own (greeting `G`) when it is full (512 KB), at `glFlush`/`glFinish`, and at
  the swap, which goes in the stream after the frame's commands. Client-side vertex arrays
  are copied when drawn, not when set: a draw is preceded by one `ARRAY` command per enabled
  array with exactly the elements it covers (from the index range, read from the app's
  indices or from the copy kept of an element VBO). Most queries are answered in the app's
  process from state the library keeps: `glGetString`, `glGetIntegerv`, `glGetError`,
  `glGen*` (a counter), `glIs*`, with a TouchPad's limits. `glReadPixels` (199 apps) is a
  round trip: the shell reads and sends the bytes back.
- *The shell's end*, `liblunacygl.so` (`gl_server.c` + `gen_gles1.py server`, NDK, both
  ABIs, driven by `PdkGl.kt` on a thread per app).

**Stage three, the same night: the app's own framebuffer.** The first replay scaled and
turned every viewport into the card, which is wrong for anything that addresses the
framebuffer in its own pixels: framebuffer objects (134 apps), `glCopyTexSubImage2D` (113),
`glReadPixels` (199), draw-texture (12). Now the app gets what it had on a device:

- A framebuffer of its own screen's size (320 x 480 for a Pre game), an offscreen
  framebuffer object in the app's GLES context, which is current on a 1 x 1 pbuffer and
  never on a window. Every call passes through untouched; binding framebuffer 0 binds this
  one. So viewports, scissors, copies, reads and draw-texture mean what they meant.
- At each swap a second context, sharing the colour texture, draws it into the card's
  `TextureView`, scaled to fit with black bars and turned (below), as the TouchPad's
  compositor put an app's buffer on its screen; then the swap is acknowledged.
- Pacing: the app waits at a swap while two are unacknowledged, so it runs one frame ahead
  of the screen and no further. Without a window (the card not laid out, a dozing tablet)
  the app keeps drawing into its framebuffer and each swap is acknowledged after a frame's
  time, so a hidden game doesn't spin and nothing queues.
- Names: the client makes up framebuffer and renderbuffer names, and Adreno refuses to
  bind a name it didn't generate (the app's draws went into the pbuffer). The shell maps
  each app name to one it generated; textures may be bound by any name in GLES 1.1 and
  pass through.
- The shell checks that its framebuffer is complete and is the one bound: a bind that
  doesn't take leaves the pbuffer's, which reports complete too. Tegra 3's GLES 1.1 (the
  Nexus 7) answers `GL_FRAMEBUFFER_BINDING_OES` with `GL_INVALID_ENUM`, and every game
  went with no GL context (Tiger Woods PGA Tour); there the bound framebuffer's colour
  attachment, which the pbuffer's hasn't, says which it is.
- Adreno 620's GLES 1 (a Kyocera tablet on Android 12, both ABIs) loads its GLES 1 layer as
  the first GLES 1 context is made and asks its GLES 2 library for a string on the way; in
  Lunacy's process, with no context current on the GL thread, that went through a null
  pointer and took the shell down (a standalone program on the device didn't need the help).
  A throwaway GLES 2 context is current while the server's are made.
- Uploads are checked against the bytes that arrived before the driver reads them, and
  BGRA (`GL_EXT_texture_format_BGRA8888`, Mandelbrot) is swizzled to RGBA where the
  device's GLES 1.1 hasn't it.
- PVRTC (`GL_IMG_texture_compression_pvrtc`, the TouchPad's PowerVR format) is decoded to
  RGBA in the shell where the driver hasn't it (`pvrtc.h`, both GLES versions). Mali,
  Adreno and Tegra refuse the formats with `GL_INVALID_ENUM`, and the texture drew white:
  Tiger Woods PGA Tour's backgrounds and golfer (4 and 2 bpp) on the HP 10 G2's Mali-450.
  The decoded textures take 8 to 16 times the memory the compressed ones did.
- The GL card shows in the card view as any card does: a `TextureView` is part of the
  view tree, so it scales with the card; there is no separate thumbnail to make.

Two generator lessons: an extension function is called through a pointer fetched with
`eglGetProcAddress`, and the generator must keep the function's own name for its hooks
(it renamed it, and every extension hook silently never matched); a parameter named `w`
clashed with the writer, now `wr_`.

**How a PDK app is shown, measured on the TouchPad.** A probe built with the PDK's own
toolchain (`/opt/PalmPDK/arm-gcc`), run from `/tmp` over novacom, reported and held:

| | the TouchPad |
|---|---|
| `PDL_GetScreenMetrics` | 1024 x 768, 132 dpi |
| `SDL_GetVideoInfo` current mode | 1024 x 768 |
| `SDL_SetVideoMode(0, 0)` | 1024 x 768 |
| a 1024 x 768 buffer | shown as it is |
| a 768 x 1024 buffer | turned a quarter counter-clockwise, filling the screen |
| a 320 x 480 buffer | turned counter-clockwise, scaled to fit, black above and below |
| `PDL_SetOrientation(3)` (Mandelbrot) | turns the system's banners; the app's picture doesn't move |

**The same on a Pre3 (2026-10-03),** with `Workbench/probe/turnprobe` installed as a PDK app
(an app launched by the system, not run from a shell: on webOS 2.2.4 a PDK process started
over novacom gets no window and no accelerometer):

| | the Pre3 |
|---|---|
| `PDL_GetScreenMetrics` | 480 x 800, 260 dpi |
| `SDL_SetVideoMode(800, 480)` | an 800 x 480 surface |
| an 800 x 480 buffer | turned a quarter **clockwise**, filling the screen: its top edge on the screen's right (`results/pre3-turn.png`) |
| the accelerometer | SDL joystick 0 "webOS accelerometer", three axes; **axis 0 positive toward the right edge** (right edge down: `pre3-right.png`; left edge down: `pre3-left.png`), axis 1 positive toward the top, axis 2 about -32700 lying face up |

So the two devices turn an other-shape buffer opposite ways, and `PdkWindow.orient` turns
clockwise on a portrait device and counter-clockwise on a landscape one. The accelerometer's
x sign that was assumed is confirmed.

So the screen stays the device's way up and a buffer of the other shape is turned; a game
that wants to be held otherwise draws itself turned (codepoet: PDK games start from
landscape and turn themselves). Lunacy does the same: the app is told its device's screen
(1024 x 768 on the TouchPad profile; the Pre3's 480 x 800 on the phone one), the card holds
the screen the device's way up while it is maximized, and a buffer of the other shape is
turned counter-clockwise, in the card's blit (GL) or on the canvas (2D), with touches
mapped back. The Pre3's portrait screen is assumed to turn the same way; there is no Pre3
to measure. PDL orientation requests are noted and nothing more. A PDK card is full screen
from the start, no status bar, as on webOS (codepoet).

**The libraries apps link by name.** A scan of every ARM binary in the mirror (871 apps
carry one) for libraries the runtime didn't provide: `libSDL_image-1.2.so.0` 310,
`libGLESv2.so` 160, `libSDL_ttf-2.0.so.0` 137, `libSDL_net-1.2.so.0` 115, unversioned names
(`libSDL.so` 65, `libSDL_mixer.so` 41, `libdl.so` 12, ...), `libcrypto.so.0.9.8` 50,
`libcurl.so.4` 39, `libjpeg.so.62` 33, `libpng12.so.0` 27, `libfreetype.so.6` 26. Built now:
SDL_image 1.2.12 (PNG and JPEG linked in), SDL_ttf 2.0.11, SDL_net 1.2.8, libpng 1.2.59
(`libpng12.so.0`, `libpng.so.3`), FreeType 2.13.2 (`libfreetype.so.6`), libjpeg62-turbo from
Debian armel; the unversioned names are symlinks made at extraction from `pdk/aliases`.
Checked on the Nexus 5 with Drum Machine (2D, SDL_ttf), Mandelbrot (GLES 1.1, SDL_ttf) and
Falling Sand (GLES 1.1, SDL_image, SDL_ttf, SDL_net).

**webOS's paths.** Apps open the system fonts by path
(`/usr/share/fonts/PreludeCondensed-Medium.ttf`) and save into `/media/internal`. The
preload library (`LunaRuntimes/pdk/libpreload`, now loaded under qemu too) looks up any
path under `/usr`, `/media`, `/var`, `/etc/palm` or `/home/root`, none of which Android has,
in Lunacy's webOS root, for open, stat (and the `__xstat` forms 2010 binaries call),
access, opendir, mkdir, unlink, rename and the rest. The runtime extraction lays the Prelude
fonts into the root's `usr/share/fonts`. Stock SDL's software pointer is hidden from the
start, as webOS's SDL drew none.

Dev tools: `LUNACY_GL_TRACE=1` in `files/pdk/env` logs every call's name from the app's
side; `touch files/pdk/gldump` (as the app, `run-as`) logs one frame's commands with
their first arguments from the shell's side; the first 20 GL errors are logged with the
command that made them. `gen_gles1_log.py` is the stage-one logging library.

**GLES 2, the same night.** `gen_gles2.py` does for the PDK's GLES 2 headers what
`gen_gles1.py` does for GLES 1.1: `libGLESv2.so` for the app (`client2_prelude.c`), and a second
build of `gl_server.c` (`-DGLES_VERSION=2`, `liblunacygl2.so`) for the shell, with the same
offscreen framebuffer and a shader blit to the card. Both client libraries share
`client_transport.h`; each batch carries its GLES version, and the card starts the stream
when it has both the mode and the first batch. What GLES 2 adds:

- *Queries are round trips.* Any call with an output (shader and program status, info
  logs, uniform and attribute locations, `glGet*v`) is sent and answered: the shell calls the
  real function and replies with the return value and each output, sized by the spec's
  tables. A failed compile or link is logged with the driver's message and the source.
- *Shaders and programs* are named by the client and mapped to the driver's, as framebuffers
  and renderbuffers are; creating one needs no round trip.
- *Vertex attribute arrays* in the app's memory travel with each draw, like GLES 1.1's.
- Extensions answer in the app's process: only Android ports linking the whole header
  (apkenv) import them.

Of the 160 apps that link `libGLESv2.so`, about 90 use shaders; the rest are 2D apps whose
template linked it (Plasma Clock, Fireworks). Dice (Karge Software) and ThermalPad (TouchPad
edition) render on the Nexus 5, and Dice under qemu on the Pixel Tablet at 57 frames/s.

Two bugs found on the way, both general: the client sent no flag for an output buffer, so
every query answered zeros (a compile status of 0 made apps delete good shaders); and the
array, index and attribute commands declared their blobs unpadded while writing them padded
to 4, so an odd index count shifted the rest of the batch two bytes and the draws after it
were lost (Dice drew nothing). Both fixed; the shell logs a batch that doesn't parse.

**The accelerometer**, as webOS gave it to PDK apps: SDL joystick 0. Measured on the
TouchPad with a probe: one joystick named "webOS accelerometer", three axes, no buttons; at
rest a magnitude of about 32768 (1 g); axis 1 positive toward the top of the screen, axis 2
negative with the screen tilted back. Axis 0's sign wasn't measurable in the pose the
TouchPad was in; a tilt test on the Pre3 (above, 2026-10-03) found it positive to the right.
The card reads Android's accelerometer while it is the active card, turns it into the
device's frame and those units, and sends it (`LPDK_ACCEL`); `SDL_lunacyjoystick.c` replaces
SDL's Linux joystick driver. Readings are coalesced, latest wins.

Everything the shell sends the app now goes from a thread of its own: Android's
`LocalSocket.flush()` waits until the other end has read every byte, and a game reads its
events only when it polls, so the accelerometer's 50 writes a second from the main thread
made Android call the shell unresponsive. Touches had the same latent risk.

**The TouchPad's other fonts.** Besides Prelude, its `/usr/share/fonts` holds Microsoft's
core web fonts, which apps open by name (Plasma Clock quits without `arial.ttf`). They can't
be shipped; the runtime lays down metric-compatible free stand-ins under the TouchPad's file
names: Liberation Sans, Serif and Mono (for Arial, Times New Roman and Georgia, Courier New
and Lucida Console) and DejaVu Sans (for Verdana), with a NOTICE (`pdk/fonts`). The CJK fonts
aren't stood in for.

Measured against the TouchPad, running the app's unpacked folder from `/tmp` over novacom
(no install): Mandelbrot shows a black screen there too; Fireworks shows its title only as a
splash between about 3 and 8 seconds, then waits for taps on a black sky. Neither is a
Lunacy fault. On the phone profile, a TouchPad-only app sees a Pre3's screen, as it would have
on a Pre3.

Open: hybrid apps, web apps that embed a PDK plugin (the chess app tried here is one);
OpenSSL 0.9.8 and curl (about 50 apps); the accelerometer's x sign; PDL_ServiceCall to the bus.

## 6. Seen on the Nexus 5, 2026-10-02

Keen installed from its package (its postinst, a game-controller jailbreak for the TouchPad,
runs clean in the webOS root once `/etc/udev/rules.d` exists there, as it does on a device)
and launched from the shell: the process started through `libld-linux.so`, the runtime
extracted from the assets, the video mode 1024 × 768 at 32 bpp, the audio device 44100 Hz
mono. The loading card gave way to the title screen with the game's own touch controls,
the shell turned landscape for it, two taps on its ENTER button started a one-player game,
and the card view showed the live game in its card. Not yet checked by ear: the sound.

## 6a. Fieldrunners: OpenAL, 2026-10-05

codepoet's Fieldrunners port (`com.subatomicstudios.fieldrunners` 1.1.0, an engine of his
own over the iOS game's data) links `libopenal.so.1`, which every webOS device carried and
the runtime didn't: the loader stopped at it, exit 127.

**What the device had.** The PDK's copy of the TouchPad's library
(`/opt/PalmPDK/device/lib/libopenal.so.1`) is **OpenAL Soft 1.11.753** ("1.1 ALSOFT
1.11.753"), built by Palm with two backends: the wave writer and an `Alc/sdl.c` of Palm's
own that loads `libSDL.so` with `dlopen` and plays through `SDL_OpenAudio` (its symbols:
`sdl_load`, `sdl_open_playback`, `sdl_reset_playback`, `sdl_callback`, `sdl_stop_playback`,
`sdl_close_playback`, no capture; its device "Simple Directmedia Layer"). The TouchPad's
`/etc/openal/alsoft.conf` (read over novacom) chooses it - `drivers = sdl` - with
`format = AL_FORMAT_STEREO16`, 44100 Hz, `period_size = 1024`, `periods = 4`, 256 sources.

**What Lunacy does.** The same: OpenAL Soft 1.11.753 from its tag, built by `build-pdk.sh`
with an SDL backend written again from those symbols and messages
(`LunaRuntimes/pdk/openal/sdl.c`), so OpenAL's mix goes out through SDL's audio, which is
Lunacy's SDL driver; the exported API is the device library's, function for function. The
TouchPad's `alsoft.conf` is in the webOS root at `/etc/openal`, and the preload maps that
path there, so OpenAL reads its settings where it did on a device. An app that opens SDL's
audio itself as well meets the one SDL device it met on webOS.

**Result.** Fieldrunners plays, with its sound, on the HP 10 G2 (61 frames/s, natively),
the Nexus 5 (62, natively) and the Pixel Tablet (58-60 under qemu): title screen, the map
picker and a game, audio at 44100 Hz stereo in 1024-sample buffers.

**The native mode is landscape.** Fieldrunners sizes itself with `SDL_SetVideoMode(0, 0)`.
On the phone profile that was the Pre3's portrait 480 x 800, so the game laid itself out
portrait and its controls were out of reach (codepoet, on the Nexus 5). PDK games start
from landscape (codepoet), and the TouchPad's own native mode was 1024 x 768, so the SDL
driver's current mode is now the screen held landscape: 800 x 480 on the phone profile,
which the card turns as it turns any landscape buffer on a portrait phone (Keen sized itself
1024 x 768 and was already turned). `PDL_GetScreenMetrics` still reports the screen as
measured on the Pre3.

## 6b. EGL and the Android ports: Where's My Water, 2026-10-05

The Android games codepoet ported to webOS with apkenv
([Android-to-webOS-Ports](https://github.com/webOSArchive/Android-to-webOS-Ports); eleven `com.apkenv.*` packages
in the catalog) are PDK apps: an ordinary glibc binary, `apkenv`, which loads the game's
own Android libraries with a bionic linker of its own and drives them through faked JNI.
Lunacy runs them as it runs any PDK app, so nothing is undone on the webOS side. Three things
stood in the way, each general.

**No libEGL.** The binary links `libEGL.so`, Qualcomm's library, which the TouchPad had
beside the PDK's but the PDK never published headers for (exit 127 at the loader). apkenv
looks GL extensions up through `eglGetProcAddress`. What the device's EGL answers a PDK app
whose context SDL made was measured with `Workbench/probe/eglprobe`, built with the PDK's
toolchain and run from `/tmp`, in GLES 1 and GLES 2:

| | the TouchPad |
|---|---|
| current display, context, surfaces before `SDL_SetVideoMode` | none |
| after it | display 0x2, context 0x1, draw and read 0x1; `eglGetDisplay(EGL_DEFAULT_DISPLAY)` 0x1 |
| `eglInitialize` | 1.4 |
| `eglQueryString` | vendor "Qualcomm, Inc", 1.4, seven extensions (`EGL_KHR_image`, `EGL_KHR_lock_surface`, ...), client APIs the string "NULL"; no display: NULL and `EGL_BAD_DISPLAY`; an unknown name: NULL and `EGL_BAD_PARAMETER` |
| `eglQuerySurface`, `eglQueryContext` | 1024 x 768; client version as SDL was asked; config 5 |
| `eglGetProcAddress` | NULL for core GL (`glClear`; `glCreateShader` in GLES 1); the extension functions of the context's GLES only (`glGenFramebuffersOES`, `glDrawTexiOES` in GLES 1; nothing of GLES 1's in GLES 2); GLES 1's before any context; NULL for unknown names |
| `eglSwapBuffers` | the current surface: true; `EGL_NO_SURFACE`: false, `EGL_BAD_SURFACE` |
| `eglSwapInterval(1)` | ended the probe's process |

Lunacy's `libEGL.so` (`LunaRuntimes/pdk/libegl`) answers the same over SDL's one context and
surface, with Lunacy's own vendor and only the EGL extensions it has (none), as its
`glGetString` does. It makes no other context or surface (`EGL_BAD_ALLOC`); the interval is
accepted, the shell paces the stream. The survey of the catalog found every EGL import of
the apkenv ports among these (Kindle and Movie Store's player link the library too).

apkenv links both `libGLES_CM.so` and `libGLESv2.so`. The SDL driver now keeps the GLES
version asked for with Palm's `SDL_GL_CONTEXT_MAJOR_VERSION` (`SDL_video.c` patched by
`build-pdk.sh`), takes the mode, swap and `SDL_GL_GetProcAddress` from that version's client
library, and answers `SDL_GL_GetAttribute` with it; each client library is linked
`-Bsymbolic`, so its own `lunacy_gl_*` calls never reach the other's.

**Android's own libraries.** The bionic linker searches `/vendor/lib` and `/system/lib`
before its own bundled bionic. A TouchPad had neither folder; Android has its own current
bionic there, which apkenv loaded and quit on (`Unimplemented but required:
pthread_gettid_np`, exit 4). The preload now looks `/system` and `/vendor` up in the webOS
root, where, as on a device, they don't exist.

**State an app reads back.** The GLES 1 client sends calls and gets nothing back, and
answered `glIsEnabled`, `glGetIntegerv` and `glGetTexEnviv` with 0 for anything it didn't
keep. apkenv saves that state around its own blit of the game's portrait framebuffer and
puts it back, so after the first frame texturing was off and every quad drew in its vertex
colour: white, with blocks for text (on the Nexus 5 also `GL_INVALID_ENUM`, for the active
texture put back as 0). The client now keeps what an app can read back, starting from
GLES 1.1's initial values: the enables (`GL_TEXTURE_2D` and the point sprite per texture
unit), the active and client-active unit, the bound texture per unit and the bound
framebuffer and renderbuffer, the texture environment per unit, and the matrix mode, blend,
depth, cull, alpha test, clear values, current colour and masks. GLES 2's queries are round
trips already. Found with the dev tools above: the shell's `gldump` showed draws with
textures bound and nothing wrong, `LUNACY_GL_TRACE` counted 48 uploads and 16,000 `glIsEnabled`.

**The TouchPad's pace.** The game ran at 200 frames/s on both tablets: a TextureView's
`eglSwapBuffers` doesn't wait for the display, so the shell acknowledged each swap as soon as
it had replayed it. On the TouchPad a PDK app's swaps went at 60 a second, each waiting up to
20 ms (`Workbench/probe/swapprobe`, a clear and a swap in a loop: 60.3 swaps/s). The shell
now acknowledges at most 60 a second, shown or not; a late frame restarts the count rather
than letting the next ones hurry.

**How far ahead the sound is mixed.** The sound stuttered on every device, natively too:
recorded in the shell, it came as bursts of exactly 16 buffers (apkenv's 32 KB ring) with
300 to 700 ms of digital silence between, while Android's track never ran short. On the
TouchPad SDL's audio thread ran at most 6 buffers (128 ms) ahead of real time, refilled two or
three at a time (`Workbench/probe/audioprobe`, 24000 Hz and 512 samples as the game asks);
under Lunacy the socket to the shell, some 200 KB, let it run seconds ahead, and apkenv's
pump and FMOD fell into those bursts. The SDL driver now gives its audio socket a send buffer
of one mixed buffer, which with the shell's track of four keeps it near the TouchPad's. The
same menu went from 80 silent buffers in 236 to none, on the Nexus 5 and the Pixel Tablet.

**Result.** Where's My Water 1.0.2 is playable on the HP 10 G2 and on the Pixel Tablet
under qemu (codepoet), at 60 frames/s with its sound. The game draws portrait into the
TouchPad's landscape buffer, so on a tablet held landscape it is sideways, as on a TouchPad
held so. On the Nexus 5 1.0.2 drew into a corner: its apkenv
shows the whole 768 x 1024 portrait canvas it made for the TouchPad, and the game, given the
phone profile's 800 x 480, drew 480 x 800 of it. codepoet's 1.0.3, built with the current
apkenv (which shows only the region drawn), fills the screen; it plays on the HP 10 G2,
the Nexus 5 and the Pixel Tablet (codepoet). It also asks
`eglGetProcAddress` before `SDL_Init`, as the TouchPad allowed; Lunacy's libEGL had asked SDL
for the GLES version there, and stock SDL dereferences its video device before one exists
(the game quit at once, exit 1). libEGL now reads the version the SDL driver keeps. Open: `eglSwapInterval` is not measured beyond killing the probe; the other ten ports are
untried.

## 7. Still to do on the first path

- The bus for `PDL_ServiceCall` through the host, as JS services have it.
- A keyboard for apps that want one (`PDL_SetKeyboardState`).
- The launcher's icon for a `pdk` app shows it as runnable only where the runtime is
  present; the banner says so where it isn't.
- More of the TouchPad's SDL behaviours a game may depend on (which events `which`
  carried), with the probe approach above (`/tmp`, no install).

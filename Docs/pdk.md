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

So the 2D path built today reaches about a fifth of the catalogue outright once SDL_image and
SDL_ttf are built, and a GLES 1.1 path would reach two thirds more. GLES 2 is the last fifth.

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
three little-endian uint32s, type, a, length, then the payload. Two connections on one
abstract Unix socket, told apart by a first byte: `V` for video, input and PDL, `A` for
audio.

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
  `fetch-assets.sh` keeps that build. Keen's postinst then ran clean on the tablet.
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
`glBindFramebufferOES(0)`, no VBOs so far. That is the serialiser's first target.

## 6. Seen on the Nexus 5, 2026-10-02

Keen installed from its package (its postinst, a game-controller jailbreak for the TouchPad,
runs clean in the webOS root once `/etc/udev/rules.d` exists there, as it does on a device)
and launched from the shell: the process started through `libld-linux.so`, the runtime
extracted from the assets, the video mode 1024 × 768 at 32 bpp, the audio device 44100 Hz
mono. The loading card gave way to the title screen with the game's own touch controls,
the shell turned landscape for it, two taps on its ENTER button started a one-player game,
and the card view showed the live game in its card. Not yet checked by ear: the sound.

## 7. Still to do on the first path

- `SDL_mixer`, `SDL_image`, `SDL_ttf` real builds (their sources are LGPL/zlib).
- The bus for `PDL_ServiceCall` through the host, as JS services have it.
- A keyboard for apps that want one (`PDL_SetKeyboardState`).
- The launcher's icon for a `pdk` app shows it as runnable only where the runtime is
  present; the banner says so where it isn't.
- Record the TouchPad's SDL behaviours a game may depend on (which events `which` carried,
  what `SDL_GetVideoInfo` said) with a probe app built with the PDK's own toolchain, which
  is on this machine.

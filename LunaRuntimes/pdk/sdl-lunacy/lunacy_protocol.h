/*
    The wire protocol between a PDK app's process and the shell (Docs/pdk.md). Shared by
    the SDL drivers, libpdl, and PdkHost.kt, which must agree with it byte for byte.

    Every message: uint32 type, uint32 a, uint32 len, then len bytes, all little-endian.
    Connections are told apart by their first byte: 'V' carries video, input and PDL;
    'A' carries audio; 'P' is libpdl's own, for PDL requests made before SDL has opened
    'V' (or from an app that never does). Each is a stream in both directions.
*/
#ifndef _lunacy_protocol_h
#define _lunacy_protocol_h

/* app -> shell, on the 'V' connection */
#define LPDK_VIDEO_MODE   1   /* a = 0: 2D, payload int32 w, h, bpp, the framebuffer file sized w*h*(bpp/8); a = 1: OpenGL ES, payload w, h, 0 */
#define LPDK_FRAME        2   /* a = 0: the whole screen changed (2D); a = 1: SDL_GL_SwapBuffers; payload none */
#define LPDK_CAPTION      3   /* payload: the title, UTF-8 */
#define LPDK_PDL          4   /* a = request id; payload: a JSON request (libpdl) */

/* app -> shell, on the 'A' connection */
#define LPDK_AUDIO_OPEN   10  /* payload: int32 freq, format (SDL AUDIO_*), channels, samples */
#define LPDK_AUDIO_DATA   11  /* payload: PCM, one SDL buffer's worth */
#define LPDK_AUDIO_CLOSE  12

/* shell -> app, on the 'V' connection */
#define LPDK_TOUCH        20  /* a = finger; payload: int32 action (0 down, 1 move, 2 up), x, y in screen px */
#define LPDK_KEY          21  /* a = 1 down, 0 up; payload: int32 sdl keysym, unicode */
#define LPDK_QUIT         22  /* SDL_QUIT */
#define LPDK_ACTIVE       23  /* a = 1 gained, 0 lost (SDL_ACTIVEEVENT, app + input focus) */
#define LPDK_PDL_REPLY    24  /* a = request id; payload: JSON */

#define LPDK_HEADER 12

#endif

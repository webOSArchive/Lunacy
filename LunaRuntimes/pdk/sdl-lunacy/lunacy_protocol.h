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
/* On the GL connection (greeting 'G', libGLES_CM.so's own): */
#define LPDK_GL           6   /* to the shell: a batch of GLES 1.1 commands (gen_gles1.py), the swap among them */
#define LPDK_GL_PIXELS    7   /* to the app: a glReadPixels' bytes */
#define LPDK_GL_ACK       8   /* to the app: a swap was shown */

/* app -> shell, on the 'A' connection */
#define LPDK_AUDIO_OPEN   10  /* payload: int32 freq, format (SDL AUDIO_*), channels, samples */
#define LPDK_AUDIO_DATA   11  /* payload: PCM, one SDL buffer's worth */
#define LPDK_AUDIO_CLOSE  12

/* shell -> app, on the 'V' connection */
#define LPDK_TOUCH        20  /* a = finger; payload: int32 action (0 down, 1 move, 2 up), x, y in screen px */
#define LPDK_KEY          21  /* a = 1 down, 0 up; payload: int32 sdl keysym, unicode */
#define LPDK_QUIT         22  /* SDL_QUIT */
#define LPDK_ACTIVE       23  /* a = 1 gained, 0 lost (SDL_ACTIVEEVENT, app + input focus) */
#define LPDK_ACCEL        25  /* payload: int32 x, y, z, the accelerometer as webOS's joystick axes (1 g = 32768, the device's frame) */
#define LPDK_PDL_REPLY    24  /* a = request id; payload: JSON */

/* A hybrid app's plugin (Docs/pdk.md, "Hybrid apps"): libpdl's JS link, greeting 'J'.
   Strings in a payload are each NUL-terminated, one after another. */
#define LPDK_JS_READY     30  /* to the shell: PDL_JSRegistrationComplete; payload: the handlers' names */
#define LPDK_JS_CALL      31  /* to the app: a = call id; payload: the method's name, then each argument */
#define LPDK_JS_REPLY     32  /* to the shell: a = call id; payload: one byte (0 a reply, 1 an exception, 2 none), then the string */
#define LPDK_CALL_JS      33  /* to the shell: PDL_CallJS; payload: the function's name, then each argument */

#define LPDK_HEADER 12

#endif

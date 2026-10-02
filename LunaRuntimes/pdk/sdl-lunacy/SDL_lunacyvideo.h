/*
    The Lunacy video, event and audio drivers for SDL 1.2 (Docs/pdk.md).

    A PDK app runs as its own process, a glibc ARM binary exactly as it shipped, and this
    is how its SDL reaches the shell: the screen surface lives in a file both sides mmap,
    and one Unix socket carries frames, touches, keys and audio between them. Nothing in
    the app's binary is changed; SDL is rebuilt with these in place of the "dummy" drivers.

    Environment, set by the shell before exec:
      LUNACY_PDK_SOCKET  the abstract Unix socket name the shell listens on
      LUNACY_PDK_FB      the framebuffer file (created by the shell, sized here)

    Wire format, both directions: a 12-byte header of three little-endian uint32s
    (type, a, b) followed by `b` payload bytes. See lunacy_protocol.h.

    This file is part of SDL (LGPL 2.1 or later, see SDL's COPYING).
*/
#ifndef _SDL_lunacyvideo_h
#define _SDL_lunacyvideo_h

#include "../SDL_sysvideo.h"

#define _THIS	SDL_VideoDevice *this

struct SDL_PrivateVideoData {
    int w, h, bpp;
    void *buffer;      /* the mmap'd framebuffer file */
    size_t buffer_len;
    int fb_fd;
    int sock;          /* the control socket, non-blocking; -1 until connected */
    int finger_down[16];
};

/* Shared by the event pump and the audio driver. */
int LUNACY_Connect(const char *hello);
int LUNACY_Send(int sock, uint32_t type, uint32_t a, const void *payload, uint32_t len);
extern int LUNACY_control_sock;

#endif /* _SDL_lunacyvideo_h */

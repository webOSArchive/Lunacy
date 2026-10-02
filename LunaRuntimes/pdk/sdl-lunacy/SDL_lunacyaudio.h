/*
    SDL - Simple DirectMedia Layer
    Copyright (C) 1997-2012 Sam Lantinga
    LGPL 2.1 or later; see SDL's COPYING.

    The Lunacy audio driver's private data (Docs/pdk.md).
*/
#include "SDL_config.h"

#ifndef _SDL_lunacyaudio_h
#define _SDL_lunacyaudio_h

#include "../SDL_sysaudio.h"

#define _THIS	SDL_AudioDevice *this

struct SDL_PrivateAudioData {
	int sock;          /* the 'A' connection to the shell, or -1 */
	Uint8 *mixbuf;
	Uint32 mixlen;
	Uint32 write_delay;
};

#endif /* _SDL_lunacyaudio_h */

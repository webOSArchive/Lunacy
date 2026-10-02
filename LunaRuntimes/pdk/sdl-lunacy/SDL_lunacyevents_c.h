/*
    SDL - Simple DirectMedia Layer
    Copyright (C) 1997-2012 Sam Lantinga
    LGPL 2.1 or later; see SDL's COPYING.

    The Lunacy event pump: touches and keys from the shell's socket (Docs/pdk.md).
*/
#include "SDL_config.h"
#include "SDL_lunacyvideo.h"

extern void LUNACY_InitOSKeymap(_THIS);
extern void LUNACY_PumpEvents(_THIS);

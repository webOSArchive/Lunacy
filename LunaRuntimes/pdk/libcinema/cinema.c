/*
 * A stand-in for Palm's libSDL_cinema, the TouchPad's video player for a PDK game's movies
 * (Docs/pdk.md). Nothing plays, but the API keeps its contract (the PDK's SDL_cinema.h, and
 * the TouchPad's /usr/lib/libSDL_cinema.so read 2026-11-03): CIN_Init and CIN_LoadCIN answer
 * 1 for success, and CIN_Play's movie ends at once with the event the real one sends at the
 * end of a stream - SDL_USEREVENT, code SDL_CINEMAEVENT (0xE000), data1 SDL_CINEMA_EOS (0).
 *
 * Answering failure (0) broke the games that play an intro: Hawx said "can't int" and spun
 * waiting for the movie, Oregon Trail used the movie it was refused and crashed.
 */
#include <stdio.h>
#include <string.h>
#include "SDL.h"

#define SDL_CINEMAEVENT 0xE000
#define SDL_CINEMA_EOS 0

static void note(const char *what, const char *url)
{
	static int n;
	if (n++ < 6) fprintf(stderr, "[cinema] %s%s%s: no player, the movie ends at once\n", what, url ? " " : "", url ? url : "");
}

int CIN_Init(void) { return 1; }
void CIN_DeInit(void) { }
int CIN_LoadCIN(const char *url) { note("CIN_LoadCIN", url); return 1; }

void CIN_Play(void)
{
	SDL_Event e;
	memset(&e, 0, sizeof e);
	e.type = SDL_USEREVENT;
	e.user.code = SDL_CINEMAEVENT;
	e.user.data1 = (void *)SDL_CINEMA_EOS;
	SDL_PushEvent(&e);
}

void CIN_Pause(void) { }
void CIN_Stop(void) { }

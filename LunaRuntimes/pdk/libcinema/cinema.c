/*
 * Palm's libSDL_cinema, the video player a PDK game plays its movies through (Docs/pdk.md),
 * with Android's player behind it.
 *
 * The API is the PDK's SDL_cinema.h, and its behaviour the TouchPad's /usr/lib/libSDL_cinema.so
 * (read 2026-11-03, and its disassembly 2026-11-07): CIN_Init and CIN_LoadCIN answer 1 for
 * success; CIN_Play, CIN_Pause and CIN_Stop are void; the end of the movie is an event the
 * library pushes from the media player's end-of-stream or error callback: SDL_USEREVENT, code
 * SDL_CINEMAEVENT (0xE000), data1 SDL_CINEMA_EOS (0) or SDL_CINEMA_ERR (1). Nothing else pushes
 * it, so CIN_Stop ends a movie silently. Palm's played it over the app at the display's size
 * (SDL_WebOsHookGetDisplayRect) and paused it while the app was in the background.
 *
 * Here each call is a request to the shell on the app's connection (lunacy_protocol.h's
 * LPDK_PDL, {"call":"cinema",...}), which plays the movie with Android's MediaPlayer over the
 * game's card (PdkWindow), and the end comes back as LPDK_CINEMA, which the SDL event reader
 * pushes as the TouchPad's library did (SDL_lunacyevents.c). Until 2026-11-07 this was a
 * stand-in that played nothing and ended every movie at once.
 */
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include "SDL.h"
#include "lunacy_protocol.h"

/* From the SDL video driver (SDL_lunacyvideo.c), in the same process, as libpdl uses them. */
extern int LUNACY_control_sock;
extern int LUNACY_Send(int sock, uint32_t type, uint32_t a, const void *payload, uint32_t len);

static int tell(const char *json)
{
	if (LUNACY_control_sock < 0) {
		fprintf(stderr, "[cinema] no connection to the shell (no video mode yet): %s\n", json);
		return 0;
	}
	return LUNACY_Send(LUNACY_control_sock, LPDK_PDL, 0, json, strlen(json)) >= 0;
}

static int op(const char *name)
{
	char json[64];
	snprintf(json, sizeof json, "{\"call\":\"cinema\",\"op\":\"%s\"}", name);
	return tell(json);
}

int CIN_Init(void) { return 1; }
void CIN_DeInit(void) { op("stop"); }

/* A file:// URL or a path; the shell finds the file and says if it can't play it (an error event at play). */
int CIN_LoadCIN(const char *url)
{
	char json[4200];
	size_t n = 0;
	const char *p;
	if (!url || !*url) return 0;
	n += snprintf(json, sizeof json, "{\"call\":\"cinema\",\"op\":\"load\",\"url\":\"");
	for (p = url; *p && n < sizeof json - 8; p++) {
		unsigned char c = (unsigned char)*p;
		if (c == '"' || c == '\\') { json[n++] = '\\'; json[n++] = c; }
		else if (c < 0x20) n += snprintf(json + n, sizeof json - n, "\\u%04x", c);
		else json[n++] = c;
	}
	if (*p) return 0;   /* longer than any path a game builds */
	n += snprintf(json + n, sizeof json - n, "\"}");
	return tell(json);
}

void CIN_Play(void) { op("play"); }
void CIN_Pause(void) { op("pause"); }
void CIN_Stop(void) { op("stop"); }

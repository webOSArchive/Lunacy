/*
 * A stand-in for Palm's libSDL_cinema, the TouchPad's video player for a PDK game's movies
 * (Docs/pdk.md). Nothing plays: every call says it is done, so a game that opens with a
 * movie goes straight to its first screen. The real signatures are not in the PDK; these
 * take what the callers pass and return 0 (not playing, no handle).
 */
#include <stdio.h>
static void note(const char *f) { static int n; if (n++ < 6) fprintf(stderr, "[cinema] %s: no player\n", f); }
int CIN_Init(void) { note("CIN_Init"); return 0; }
void CIN_DeInit(void) { note("CIN_DeInit"); }
void *CIN_LoadCIN(const char *path, int a, int b) { note("CIN_LoadCIN"); return 0; }
int CIN_Play(void *cin, int a, int b) { note("CIN_Play"); return 0; }
int CIN_Pause(void *cin) { note("CIN_Pause"); return 0; }
int CIN_Stop(void *cin) { note("CIN_Stop"); return 0; }
int CIN_IsPlaying(void *cin) { return 0; }
int CIN_GetStatus(void *cin) { return 0; }
void CIN_Free(void *cin) { }

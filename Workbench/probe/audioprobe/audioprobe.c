/* Lunacy's audio probe: how far ahead of playback the TouchPad's SDL audio thread runs.
   Opens SDL audio as Where's My Water's apkenv does (argv: rate, samples; default 24000,
   512, stereo S16) and timestamps each callback for 3 s: a burst at the start is the
   buffering below SDL; the steady interval is the pace. Run from /tmp over novacom. */
#include <SDL.h>
#include <PDL.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/time.h>
static double now(void) { struct timeval t; gettimeofday(&t, NULL); return t.tv_sec + t.tv_usec / 1e6; }
static double stamps[4000]; static volatile int n;
static void cb(void *u, Uint8 *s, int len) { if (n < 4000) stamps[n++] = now(); memset(s, 0, len); }
int main(int argc, char **argv) {
    FILE *lf = fopen("/media/internal/audioprobe.log", "w"); if (lf) { dup2(fileno(lf), 1); dup2(fileno(lf), 2); setvbuf(stdout, NULL, _IONBF, 0); }
    int rate = argc > 1 ? atoi(argv[1]) : 24000, samples = argc > 2 ? atoi(argv[2]) : 512;
    SDL_Init(SDL_INIT_AUDIO); PDL_Init(0);
    SDL_AudioSpec want, got; memset(&want, 0, sizeof want);
    want.freq = rate; want.format = AUDIO_S16SYS; want.channels = 2; want.samples = samples; want.callback = cb;
    if (SDL_OpenAudio(&want, &got) < 0) { printf("open failed: %s\n", SDL_GetError()); return 1; }
    printf("asked %d Hz %d samples; got %d Hz %d ch %d samples size %d\n", rate, samples, got.freq, got.channels, got.samples, got.size);
    double t0 = now(); SDL_PauseAudio(0); sleep(3); SDL_PauseAudio(1);
    double per = (double)got.samples / got.freq;
    printf("buffer time %.1f ms; %d callbacks in 3 s (real time needs %.0f)\n", per * 1000, n, 3 / per);
    for (int i = 0; i < n && i < 40; i++) printf("cb %2d at %7.1f ms\n", i, (stamps[i] - t0) * 1000);
    int ahead = 0; for (int i = 0; i < n; i++) { int a = i + 1 - (int)((stamps[i] - t0) / per); if (a > ahead) ahead = a; }
    printf("most buffers ahead of real time: %d (%.0f ms)\n", ahead, ahead * per * 1000);
    SDL_CloseAudio(); SDL_Quit();
    return 0;
}

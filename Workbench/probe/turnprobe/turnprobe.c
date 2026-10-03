/* Lunacy's Pre3 probe: which way webOS turns a buffer of the other shape, and the
   accelerometer's axes. Run from /tmp over novacom; no install. */
#include <SDL.h>
#include <PDL.h>
#include <stdio.h>
#include <unistd.h>
int main(int argc, char **argv) {
    FILE *lf = fopen("/media/internal/turnprobe.log", "w"); if (lf) { dup2(fileno(lf), 1); dup2(fileno(lf), 2); setvbuf(stdout, NULL, _IONBF, 0); }
    int w = argc > 1 ? atoi(argv[1]) : 800, h = argc > 2 ? atoi(argv[2]) : 480, secs = argc > 3 ? atoi(argv[3]) : 600;
    SDL_Init(SDL_INIT_VIDEO | SDL_INIT_JOYSTICK);
    PDL_Init(0);
    PDL_ScreenMetrics m; PDL_GetScreenMetrics(&m);
    printf("screen %d x %d, %d dpi\n", m.horizontalPixels, m.verticalPixels, m.horizontalDPI);
    const SDL_VideoInfo *vi = SDL_GetVideoInfo();
    printf("current mode %d x %d\n", vi->current_w, vi->current_h);
    SDL_Surface *s = SDL_SetVideoMode(w, h, 32, SDL_SWSURFACE);
    if (!s) { printf("no mode: %s\n", SDL_GetError()); return 1; }
    printf("got surface %d x %d\n", s->w, s->h);
    SDL_FillRect(s, NULL, SDL_MapRGB(s->format, 255, 255, 255));
    SDL_Rect top = {0, 0, s->w, 40}; SDL_FillRect(s, &top, SDL_MapRGB(s->format, 255, 0, 0));      /* red along the buffer's top */
    SDL_Rect left = {0, 0, 40, s->h}; SDL_FillRect(s, &left, SDL_MapRGB(s->format, 0, 160, 0));    /* green along its left */
    SDL_Rect corner = {0, 0, 80, 80}; SDL_FillRect(s, &corner, SDL_MapRGB(s->format, 0, 0, 255));  /* blue at its top-left */
    SDL_Flip(s);
    int n = SDL_NumJoysticks(); printf("joysticks %d\n", n);
    SDL_Joystick *j = n > 0 ? SDL_JoystickOpen(0) : NULL;
    if (j) printf("joystick 0 '%s' axes %d\n", SDL_JoystickName(0), SDL_JoystickNumAxes(j));
    for (int t = 0; t < secs * 2; t++) {
        SDL_Event e; while (SDL_PollEvent(&e)) {}
        if (j) {
            SDL_JoystickUpdate();
            int ax = SDL_JoystickGetAxis(j, 0), ay = SDL_JoystickGetAxis(j, 1), az = SDL_JoystickGetAxis(j, 2);
            printf("t=%4.1f axes %6d %6d %6d\n", t / 2.0, ax, ay, az); fflush(stdout);
            /* The axes drawn: a black marker from the buffer's centre, axis 0 along the buffer's x, axis 1 along its y. */
            SDL_FillRect(s, NULL, SDL_MapRGB(s->format, 255, 255, 255));
            SDL_FillRect(s, &top, SDL_MapRGB(s->format, 255, 0, 0)); SDL_FillRect(s, &left, SDL_MapRGB(s->format, 0, 160, 0)); SDL_FillRect(s, &corner, SDL_MapRGB(s->format, 0, 0, 255));
            SDL_Rect mid = {s->w / 2 - 4, 0, 8, s->h}; SDL_FillRect(s, &mid, SDL_MapRGB(s->format, 200, 200, 200));
            SDL_Rect midy = {0, s->h / 2 - 4, s->w, 8}; SDL_FillRect(s, &midy, SDL_MapRGB(s->format, 200, 200, 200));
            int mx = s->w / 2 + ax * (s->w / 2 - 60) / 32768, my = s->h / 2 + ay * (s->h / 2 - 60) / 32768;
            SDL_Rect mk = {mx - 20, my - 20, 40, 40}; SDL_FillRect(s, &mk, SDL_MapRGB(s->format, 0, 0, 0));
            SDL_Flip(s);
        }
        usleep(500000);
    }
    PDL_Quit(); SDL_Quit();
    return 0;
}

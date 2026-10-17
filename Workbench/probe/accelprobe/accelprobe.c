/* Lunacy's TouchPad probe: PDL's accelerometer (PDL_Sensors.h) beside SDL's joystick 0, read
   at the same moment, so the one's axes can be written in the other's. Installed as a PDK app
   (sensors need the system's launch); the log goes to /media/internal/accelprobe.log.
   The screen shows the PDL reading: a black marker moved by PDL x along the screen's x and
   PDL y along its y, and a red one by the joystick's axes 0 and 1. */
#include <SDL.h>
#include <PDL.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
int main(int argc, char **argv) {
    FILE *lf = fopen("/media/internal/accelprobe.log", "w"); if (lf) { dup2(fileno(lf), 1); dup2(fileno(lf), 2); setvbuf(stdout, NULL, _IONBF, 0); }
    PDL_Init(0);
    SDL_Init(SDL_INIT_VIDEO | SDL_INIT_JOYSTICK);
    SDL_Surface *s = SDL_SetVideoMode(0, 0, 32, SDL_SWSURFACE);
    if (!s) { printf("no mode: %s\n", SDL_GetError()); return 1; }
    printf("surface %d x %d\n", s->w, s->h);
    int n = SDL_NumJoysticks(); SDL_Joystick *j = n > 0 ? SDL_JoystickOpen(0) : NULL;
    printf("joysticks %d%s%s\n", n, j ? ", 0 is " : "", j ? SDL_JoystickName(0) : "");
    printf("PDL_SensorExists(ACCELEROMETER) %d\n", PDL_SensorExists(PDL_SENSOR_ACCELEROMETER));
    PDL_Err e = PDL_EnableSensor(PDL_SENSOR_ACCELEROMETER, PDL_TRUE);
    printf("PDL_EnableSensor %d %s\n", e, e ? PDL_GetError() : "");
    float px = 0, py = 0, pz = 0; int got = 0, events = 0;
    for (int t = 0; t < 1200; t++) {
        SDL_Event ev; while (SDL_PollEvent(&ev)) { if (ev.type == SDL_QUIT) t = 1200; }
        PDL_SensorEvent se; PDL_Err r; static int empties;
        memset(&se, 0x5a, sizeof se);
        while ((r = PDL_PollSensor(PDL_SENSOR_ACCELEROMETER, &se)) == PDL_NOERROR && se.type == PDL_SENSOR_ACCELEROMETER) {
            px = se.accelerometer.x; py = se.accelerometer.y; pz = se.accelerometer.z; got = 1; events++;
            memset(&se, 0x5a, sizeof se);
        }
        if (empties++ < 3) printf("empty poll: %d, type %d, '%s'\n", r, se.type, PDL_GetError());
        if (t == 50) { PDL_SensorEvent a; memset(&a, 0x5a, sizeof a); r = PDL_PollActiveSensors(&a); printf("PollActiveSensors %d type %d\n", r, a.type); }
        int ax = 0, ay = 0, az = 0;
        if (j) { SDL_JoystickUpdate(); ax = SDL_JoystickGetAxis(j, 0); ay = SDL_JoystickGetAxis(j, 1); az = SDL_JoystickGetAxis(j, 2); }
        if (t % 5 == 0) printf("t=%5.1f pdl %s(% .3f % .3f % .3f) events %d  joystick %6d %6d %6d\n", t / 10.0, got ? "" : "none ", px, py, pz, events, ax, ay, az);
        SDL_FillRect(s, NULL, SDL_MapRGB(s->format, 255, 255, 255));
        SDL_Rect top = {0, 0, s->w, 30}; SDL_FillRect(s, &top, SDL_MapRGB(s->format, 0, 120, 255));   /* blue along the screen's top */
        SDL_Rect mk = {s->w / 2 + (int)(px * (s->w / 2 - 60)) - 25, s->h / 2 - (int)(py * (s->h / 2 - 60)) - 25, 50, 50};
        SDL_FillRect(s, &mk, SDL_MapRGB(s->format, 0, 0, 0));
        SDL_Rect jk = {s->w / 2 + ax * (s->w / 2 - 60) / 32768 - 15, s->h / 2 - ay * (s->h / 2 - 60) / 32768 - 15, 30, 30};
        SDL_FillRect(s, &jk, SDL_MapRGB(s->format, 220, 0, 0));
        SDL_Flip(s);
        usleep(100000);
    }
    PDL_EnableSensor(PDL_SENSOR_ACCELEROMETER, PDL_FALSE);
    PDL_Quit(); SDL_Quit();
    return 0;
}

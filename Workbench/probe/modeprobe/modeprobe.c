/* Lunacy's probe: what SDL and PDL tell a game about the screen before it picks a mode
   (SDL_ListModes, SDL_GetVideoInfo, PDL's hardware and names). Reads only; sets no mode.
   Run from /tmp over novacom on a device, or as a PDK app in Lunacy. */
#include <SDL.h>
#include <PDL.h>
#include <stdio.h>
#include <unistd.h>
static void modes(const char *what, Uint32 flags) {
    SDL_Rect **m = SDL_ListModes(NULL, flags);
    printf("ListModes(%s): ", what);
    if (m == (SDL_Rect **)0) printf("none\n");
    else if (m == (SDL_Rect **)-1) printf("any\n");
    else { for (int i = 0; m[i]; i++) printf("%dx%d ", m[i]->w, m[i]->h); printf("\n"); }
}
int main(int argc, char **argv) {
    const char *out = argc > 1 ? argv[1] : "/media/internal/modeprobe.log";
    FILE *lf = fopen(out, "w"); if (lf) { dup2(fileno(lf), 1); dup2(fileno(lf), 2); setvbuf(stdout, NULL, _IONBF, 0); }
    SDL_Init(SDL_INIT_VIDEO);
    PDL_Init(0);
    const SDL_VideoInfo *vi = SDL_GetVideoInfo();
    printf("VideoInfo current %d x %d, bpp %d\n", vi->current_w, vi->current_h, vi->vfmt ? vi->vfmt->BitsPerPixel : 0);
    modes("0", 0);
    modes("FULLSCREEN", SDL_FULLSCREEN);
    modes("OPENGL", SDL_OPENGL);
    modes("OPENGL|FULLSCREEN", SDL_OPENGL | SDL_FULLSCREEN);
    modes("HWSURFACE|FULLSCREEN", SDL_HWSURFACE | SDL_FULLSCREEN);
    PDL_ScreenMetrics m; PDL_GetScreenMetrics(&m);
    printf("ScreenMetrics %d x %d, %d dpi, aspect %f\n", m.horizontalPixels, m.verticalPixels, m.horizontalDPI, m.aspectRatio);
    printf("Hardware '%s'\n", PDL_GetHardware());
    char buf[256] = "";
    PDL_GetDeviceName(buf, sizeof buf); printf("DeviceName '%s'\n", buf);
    PDL_OSVersion v; if (PDL_GetOSVersion(&v) == PDL_NOERROR) printf("OSVersion %d.%d.%d '%s'\n", v.majorVersion, v.minorVersion, v.revision, v.versionStr);
    printf("HardwareID %d\n", PDL_GetHardwareID());
    char drv[64] = ""; SDL_VideoDriverName(drv, sizeof drv); printf("VideoDriverName '%s'\n", drv);
    const SDL_version *lv = SDL_Linked_Version(); printf("Linked_Version %d.%d.%d\n", lv->major, lv->minor, lv->patch);
    SDL_InitSubSystem(SDL_INIT_JOYSTICK); printf("NumJoysticks %d name0 '%s'\n", SDL_NumJoysticks(), SDL_NumJoysticks() > 0 ? SDL_JoystickName(0) : "");
    PDL_Quit(); SDL_Quit();
    return 0;
}

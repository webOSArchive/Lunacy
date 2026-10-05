/* Lunacy's swap probe: how fast the TouchPad lets a PDK app's GL swaps go (a clear and a
   swap in a tight loop), and how long one swap takes. Run from /tmp over novacom. */
#include <SDL.h>
#include <PDL.h>
#include <GLES/gl.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/time.h>
static double now(void) { struct timeval t; gettimeofday(&t, NULL); return t.tv_sec + t.tv_usec / 1e6; }
int main(int argc, char **argv) {
    FILE *lf = fopen("/media/internal/swapprobe.log", "w"); if (lf) { dup2(fileno(lf), 1); dup2(fileno(lf), 2); setvbuf(stdout, NULL, _IONBF, 0); }
    SDL_Init(SDL_INIT_VIDEO); PDL_Init(0);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 1);
    SDL_SetVideoMode(0, 0, 0, SDL_OPENGL);
    double t0 = now(), worst = 0, mark = t0; int n = 0;
    while (now() - t0 < 5) {
        glClearColor((n & 1) ? 1 : 0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
        double a = now(); SDL_GL_SwapBuffers(); double d = now() - a; if (d > worst) worst = d;
        SDL_Event e; while (SDL_PollEvent(&e)) {}
        n++;
        if (now() - mark >= 1) { printf("%.1f swaps/s, longest swap %.1f ms\n", n / (now() - t0), worst * 1000); mark = now(); worst = 0; }
    }
    printf("total %d swaps in %.2f s\n", n, now() - t0);
    SDL_Quit();
    return 0;
}

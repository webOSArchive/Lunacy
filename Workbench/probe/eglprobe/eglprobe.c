#define _GNU_SOURCE
/* Lunacy's EGL probe: what the TouchPad's libEGL answers a PDK app whose context SDL made
   (apkenv's Android ports call it). Run from /tmp over novacom; argv[1] = GLES major version. */
#include <SDL.h>
#include <PDL.h>
#include <EGL/egl.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <dlfcn.h>
static void proc(const char *n) {
    void *p = (void *)eglGetProcAddress(n); Dl_info i; const char *lib = "-";
    if (p && dladdr(p, &i) && i.dli_fname) lib = i.dli_fname;
    void *d = dlsym(RTLD_DEFAULT, n);
    printf("proc %-28s %p %s %s\n", n, p, lib, p && p == d ? "(= dlsym)" : (d ? "(dlsym differs)" : "(no dlsym)"));
}
static void str(EGLDisplay d, const char *what, EGLint name) {
    const char *s = eglQueryString(d, name);
    printf("%s: %s (error 0x%x)\n", what, s ? s : "(null)", eglGetError());
}
int main(int argc, char **argv) {
    FILE *lf = fopen("/media/internal/eglprobe.log", "w"); if (lf) { dup2(fileno(lf), 1); dup2(fileno(lf), 2); setvbuf(stdout, NULL, _IONBF, 0); }
    int major = argc > 1 ? atoi(argv[1]) : 1;
    printf("== before SDL\n");
    printf("current display %p context %p draw %p\n", eglGetCurrentDisplay(), eglGetCurrentContext(), eglGetCurrentSurface(EGL_DRAW));
    str(EGL_NO_DISPLAY, "vendor(no display)", EGL_VENDOR);
    proc("glClear"); proc("glGenFramebuffersOES");
    SDL_Init(SDL_INIT_VIDEO); PDL_Init(0);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, major);
    SDL_Surface *s = SDL_SetVideoMode(0, 0, 0, SDL_OPENGL);
    printf("== after SDL_SetVideoMode (GLES %d): %p %dx%d\n", major, s, s ? s->w : 0, s ? s->h : 0);
    EGLDisplay cd = eglGetCurrentDisplay(); EGLContext cc = eglGetCurrentContext();
    EGLSurface cs = eglGetCurrentSurface(EGL_DRAW), cr = eglGetCurrentSurface(EGL_READ);
    printf("current display %p context %p draw %p read %p api 0x%x\n", cd, cc, cs, cr, eglQueryAPI());
    EGLDisplay dd = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    printf("default display %p (%s current)\n", dd, dd == cd ? "=" : "!=");
    EGLint ma = -1, mi = -1; EGLBoolean ok = eglInitialize(dd, &ma, &mi);
    printf("eglInitialize %d -> %d.%d error 0x%x\n", ok, ma, mi, eglGetError());
    str(cd, "vendor", EGL_VENDOR); str(cd, "version", EGL_VERSION);
    str(cd, "extensions", EGL_EXTENSIONS); str(cd, "client apis", 0x308D);
    str(cd, "bad name", 0x1234);
    EGLint v = -1;
    eglQuerySurface(cd, cs, EGL_WIDTH, &v); printf("surface width %d\n", v);
    eglQuerySurface(cd, cs, EGL_HEIGHT, &v); printf("surface height %d\n", v);
    v = -1; eglQueryContext(cd, cc, 0x3098 /*EGL_CONTEXT_CLIENT_VERSION*/, &v); printf("context client version %d\n", v);
    v = -1; eglQueryContext(cd, cc, EGL_CONFIG_ID, &v); printf("context config id %d\n", v);
    printf("-- procs\n");
    proc("glClear"); proc("glGenFramebuffersOES"); proc("glCreateShader"); proc("glGenFramebuffers");
    EGLint n = 0; eglGetConfigs(cd, NULL, 0, &n); printf("configs %d\n", n);
    proc("glClear"); proc("glGenFramebuffersOES"); proc("glCreateShader"); proc("glGenFramebuffers");
    proc("glDrawTexiOES"); proc("glMapBufferOES"); proc("glDiscardFramebufferEXT"); proc("eglCreateImageKHR");
    proc("eglLockSurfaceKHR"); proc("noSuchFunction");
    glClearColor(0, 0.5f, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
    printf("eglSwapBuffers %d error 0x%x\n", eglSwapBuffers(cd, cs), eglGetError());
    printf("eglSwapBuffers(no surface) %d error 0x%x\n", eglSwapBuffers(cd, EGL_NO_SURFACE), eglGetError());
    printf("swap interval 1: %d error 0x%x\n", eglSwapInterval(cd, 1), eglGetError());
    sleep(2);
    SDL_Quit();
    printf("== after SDL_Quit: display %p context %p\n", eglGetCurrentDisplay(), eglGetCurrentContext());
    return 0;
}

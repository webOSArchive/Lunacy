/* Lunacy's probe: logs a PDK game's SDL and PDL calls, in order, with arguments and results,
   to compare a game on the reference TouchPad with the same game on Lunacy. LD_PRELOAD it.
   Built with the PDK's toolchain; runs on both. Output to stderr, prefixed [wlog]. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <SDL.h>
#include <PDL.h>
#define REAL(ret, name, args) static ret (*real_)args; if (!real_) real_ = (ret (*)args)dlsym(RTLD_NEXT, #name)
#define L(...) do { fprintf(stderr, "[wlog] " __VA_ARGS__); fputc('\n', stderr); } while (0)
static int quota(int *n, int max) { return (*n)++ < max; }

int SDL_Init(Uint32 f) { REAL(int, SDL_Init, (Uint32)); int r = real_(f); L("SDL_Init %x -> %d", f, r); return r; }
int SDL_InitSubSystem(Uint32 f) { REAL(int, SDL_InitSubSystem, (Uint32)); int r = real_(f); L("SDL_InitSubSystem %x -> %d", f, r); return r; }
const SDL_VideoInfo *SDL_GetVideoInfo(void) { REAL(const SDL_VideoInfo *, SDL_GetVideoInfo, (void)); const SDL_VideoInfo *v = real_(); static int n; if (quota(&n, 6)) L("SDL_GetVideoInfo -> %d x %d", v ? v->current_w : -1, v ? v->current_h : -1); return v; }
SDL_Rect **SDL_ListModes(SDL_PixelFormat *fmt, Uint32 f) { REAL(SDL_Rect **, SDL_ListModes, (SDL_PixelFormat *, Uint32)); SDL_Rect **m = real_(fmt, f); L("SDL_ListModes %x -> %s", f, m == (SDL_Rect **)-1 ? "any" : m ? "list" : "none"); return m; }
SDL_Surface *SDL_SetVideoMode(int w, int h, int bpp, Uint32 f) { REAL(SDL_Surface *, SDL_SetVideoMode, (int, int, int, Uint32)); SDL_Surface *s = real_(w, h, bpp, f); L("SDL_SetVideoMode %d x %d x %d flags %x -> %d x %d flags %x", w, h, bpp, f, s ? s->w : -1, s ? s->h : -1, s ? s->flags : 0); return s; }
SDL_Surface *SDL_GetVideoSurface(void) { REAL(SDL_Surface *, SDL_GetVideoSurface, (void)); SDL_Surface *s = real_(); static int n; if (quota(&n, 6)) L("SDL_GetVideoSurface -> %d x %d", s ? s->w : -1, s ? s->h : -1); return s; }
int SDL_GL_SetAttribute(SDL_GLattr a, int v) { REAL(int, SDL_GL_SetAttribute, (SDL_GLattr, int)); int r = real_(a, v); L("SDL_GL_SetAttribute %d %d -> %d", a, v, r); return r; }
char *SDL_VideoDriverName(char *b, int n) { REAL(char *, SDL_VideoDriverName, (char *, int)); char *r = real_(b, n); L("SDL_VideoDriverName -> %s", r ? r : "(null)"); return r; }
int SDL_NumJoysticks(void) { REAL(int, SDL_NumJoysticks, (void)); int r = real_(); L("SDL_NumJoysticks -> %d", r); return r; }
SDL_Joystick *SDL_JoystickOpen(int i) { REAL(SDL_Joystick *, SDL_JoystickOpen, (int)); SDL_Joystick *j = real_(i); L("SDL_JoystickOpen %d -> %p", i, (void *)j); return j; }
int SDL_ShowCursor(int t) { REAL(int, SDL_ShowCursor, (int)); int r = real_(t); L("SDL_ShowCursor %d -> %d", t, r); return r; }
void SDL_WM_SetCaption(const char *t, const char *i) { REAL(void, SDL_WM_SetCaption, (const char *, const char *)); L("SDL_WM_SetCaption %s", t ? t : ""); real_(t, i); }

static void log_event(const char *from, SDL_Event *e) {
	static int n; if (!quota(&n, 60)) return;
	switch (e->type) {
	case SDL_ACTIVEEVENT: L("%s ACTIVE gain %d state %x", from, e->active.gain, e->active.state); break;
	case SDL_VIDEORESIZE: L("%s VIDEORESIZE %d x %d", from, e->resize.w, e->resize.h); break;
	case SDL_JOYAXISMOTION: L("%s JOYAXIS %d = %d", from, e->jaxis.axis, e->jaxis.value); break;
	case SDL_MOUSEBUTTONDOWN: case SDL_MOUSEBUTTONUP: L("%s MOUSEBUTTON %d which %d at %d,%d", from, e->type, e->button.which, e->button.x, e->button.y); break;
	case SDL_MOUSEMOTION: L("%s MOUSEMOTION which %d at %d,%d", from, e->motion.which, e->motion.x, e->motion.y); break;
	case SDL_KEYDOWN: case SDL_KEYUP: L("%s KEY %d sym %d", from, e->type, e->key.keysym.sym); break;
	default: L("%s event type %d", from, e->type);
	}
}
int SDL_PollEvent(SDL_Event *e) { REAL(int, SDL_PollEvent, (SDL_Event *)); int r = real_(e); if (r && e) log_event("PollEvent", e); return r; }
int SDL_WaitEvent(SDL_Event *e) { REAL(int, SDL_WaitEvent, (SDL_Event *)); int r = real_(e); if (r && e) log_event("WaitEvent", e); return r; }

static void (*real_glRotatef)(float, float, float, float);
static void (*real_glRotatex)(int, int, int, int);
static void w_glRotatef(float a, float x, float y, float z) { static int n; if (quota(&n, 6)) L("glRotatef %g %g %g %g", a, x, y, z); real_glRotatef(a, x, y, z); }
static void w_glRotatex(int a, int x, int y, int z) { static int n; if (quota(&n, 6)) L("glRotatex %d %d %d %d", a, x, y, z); real_glRotatex(a, x, y, z); }
void *SDL_GLES_GetProcAddress(const char *p) {
	REAL(void *, SDL_GLES_GetProcAddress, (const char *)); void *r = real_(p);
	static int n; if (quota(&n, 400)) L("SDL_GLES_GetProcAddress %s -> %p", p, r);
	if (r && p && !strcmp(p, "glRotatef")) { real_glRotatef = r; return (void *)w_glRotatef; }
	if (r && p && !strcmp(p, "glRotatex")) { real_glRotatex = r; return (void *)w_glRotatex; }
	return r;
}

PDL_Err PDL_Init(unsigned int f) { REAL(PDL_Err, PDL_Init, (unsigned int)); int r = real_(f); L("PDL_Init %u -> %d", f, r); return r; }
PDL_Err PDL_SetOrientation(PDL_Orientation o) { REAL(PDL_Err, PDL_SetOrientation, (PDL_Orientation)); PDL_Err r = real_(o); L("PDL_SetOrientation %d -> %d", (int)o, (int)r); return r; }
PDL_Err PDL_GesturesEnable(PDL_bool b) { REAL(PDL_Err, PDL_GesturesEnable, (PDL_bool)); PDL_Err r = real_(b); L("PDL_GesturesEnable %d -> %d", (int)b, (int)r); return r; }
PDL_Err PDL_ScreenTimeoutEnable(PDL_bool b) { REAL(PDL_Err, PDL_ScreenTimeoutEnable, (PDL_bool)); PDL_Err r = real_(b); L("PDL_ScreenTimeoutEnable %d -> %d", (int)b, (int)r); return r; }
const char *PDL_GetHardware(void) { REAL(const char *, PDL_GetHardware, (void)); const char *r = real_(); L("PDL_GetHardware -> %s", r ? r : "(null)"); return r; }
PDL_Err PDL_GetOSVersion(PDL_OSVersion *v) { REAL(PDL_Err, PDL_GetOSVersion, (PDL_OSVersion *)); PDL_Err r = real_(v); L("PDL_GetOSVersion -> %d %d.%d.%d '%s'", (int)r, v ? v->majorVersion : 0, v ? v->minorVersion : 0, v ? v->revision : 0, v ? v->versionStr : ""); return r; }
PDL_Err PDL_GetDeviceName(char *b, int n) { REAL(PDL_Err, PDL_GetDeviceName, (char *, int)); PDL_Err r = real_(b, n); L("PDL_GetDeviceName -> %d '%s'", (int)r, b ? b : ""); return r; }
PDL_Err PDL_GetLanguage(char *b, int n) { REAL(PDL_Err, PDL_GetLanguage, (char *, int)); PDL_Err r = real_(b, n); L("PDL_GetLanguage -> %d '%s'", (int)r, b ? b : ""); return r; }

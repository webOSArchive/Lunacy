/*
 * Lunacy's libEGL: the TouchPad's libEGL.so (Qualcomm's) as a PDK app saw it, for a PDK app
 * running under Lunacy (Docs/pdk.md, "EGL"). The PDK never published EGL headers - SDL
 * makes the one context and the one window surface - but the device's libEGL.so was there
 * to link, and the Android ports built with apkenv do: they look GL extensions up with
 * eglGetProcAddress. Here, as there, the context and surface are SDL's; this library
 * describes them and swaps them, and makes no others.
 *
 * What it answers was measured on the TouchPad (webOS 3.0.5) with
 * Workbench/probe/eglprobe, a PDK binary run from /tmp, GLES 1 and GLES 2 alike:
 *
 *   - no current display, context or surface until SDL_SetVideoMode makes a GL mode;
 *     then display 0x2, context 0x1, both surfaces 0x1. eglGetDisplay(EGL_DEFAULT_DISPLAY)
 *     is 0x1, not the current display; both work.
 *   - eglInitialize: 1.4. eglQueryString on no display: NULL, EGL_BAD_DISPLAY; an unknown
 *     name: NULL, EGL_BAD_PARAMETER. EGL_CLIENT_APIS answered the string "NULL".
 *   - eglQuerySurface: the mode's size; eglQueryContext(EGL_CONTEXT_CLIENT_VERSION): the
 *     GLES version SDL was asked for; EGL_CONFIG_ID 5.
 *   - eglGetProcAddress: NULL for core GL functions (glClear, and glCreateShader in a GLES 1
 *     context); the extension functions of the context's own GLES (glGenFramebuffersOES
 *     and glDrawTexiOES in GLES 1, nothing from libGLES_CM in GLES 2); before any context,
 *     GLES 1's; NULL for unknown names.
 *   - eglSwapBuffers on the current surface: EGL_TRUE; on EGL_NO_SURFACE: EGL_FALSE,
 *     EGL_BAD_SURFACE.
 *
 * Where the answer is the device's own (the vendor, its extension list) Lunacy gives its
 * own instead, as its libGLES_CM's glGetString does: "Lunacy", and only the EGL extensions
 * it implements, which is none. Making another context or surface fails with
 * EGL_BAD_ALLOC: the shell shows the one stream SDL opened.
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <string.h>
#include <dlfcn.h>
#include <stdint.h>

typedef int32_t EGLint;
typedef unsigned int EGLBoolean;
typedef unsigned int EGLenum;
typedef void *EGLDisplay, *EGLConfig, *EGLContext, *EGLSurface, *EGLClientBuffer;
typedef void *EGLNativeDisplayType, *EGLNativeWindowType, *EGLNativePixmapType;

#define EGL_FALSE 0
#define EGL_TRUE 1
#define EGL_SUCCESS             0x3000
#define EGL_NOT_INITIALIZED     0x3001
#define EGL_BAD_ACCESS          0x3002
#define EGL_BAD_ALLOC           0x3003
#define EGL_BAD_ATTRIBUTE       0x3004
#define EGL_BAD_CONFIG          0x3005
#define EGL_BAD_CONTEXT         0x3006
#define EGL_BAD_DISPLAY         0x3008
#define EGL_BAD_MATCH           0x3009
#define EGL_BAD_PARAMETER       0x300C
#define EGL_BAD_SURFACE         0x300D
#define EGL_CONFIG_ID           0x3028
#define EGL_HEIGHT              0x3056
#define EGL_WIDTH               0x3057
#define EGL_VENDOR              0x3053
#define EGL_VERSION             0x3054
#define EGL_EXTENSIONS          0x3055
#define EGL_CLIENT_APIS         0x308D
#define EGL_DRAW                0x3059
#define EGL_READ                0x305A
#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#define EGL_OPENGL_ES_API       0x30A0
#define EGL_NONE                0x3038

#define DEFAULT_DISPLAY ((EGLDisplay)1)
#define CURRENT_DISPLAY ((EGLDisplay)2)
#define THE_CONTEXT     ((EGLContext)1)
#define THE_SURFACE     ((EGLSurface)1)
#define THE_CONFIG      ((EGLConfig)5)

static __thread EGLint last_error = EGL_SUCCESS;
static EGLBoolean fail(EGLint e) { last_error = e; return EGL_FALSE; }
static EGLBoolean ok(void) { last_error = EGL_SUCCESS; return EGL_TRUE; }

/* SDL is in the process (a PDK app links it); looked up rather than linked, so the library
   loads in any order. SDL_Surface's flags and size are its first fields after the format. */
typedef struct { uint32_t flags; void *format; int w, h; } sdl_surface_head;
#define SDL_OPENGL 0x00000002
static sdl_surface_head *gl_screen(void)
{
	static sdl_surface_head *(*get)(void);
	if (!get) *(void **)&get = dlsym(RTLD_DEFAULT, "SDL_GetVideoSurface");
	sdl_surface_head *s = get ? get() : NULL;
	return s && (s->flags & SDL_OPENGL) ? s : NULL;
}
/* The GLES version SDL was asked for (Palm's SDL_GL_CONTEXT_MAJOR_VERSION, 17). */
static int gles_major(void)
{
	static int (*get)(int, int *);
	int v = 1;
	if (!get) *(void **)&get = dlsym(RTLD_DEFAULT, "SDL_GL_GetAttribute");
	if (get) get(17, &v);
	return v == 2 ? 2 : 1;
}

static int valid_display(EGLDisplay d) { return d == DEFAULT_DISPLAY || d == CURRENT_DISPLAY; }

EGLint eglGetError(void) { EGLint e = last_error; last_error = EGL_SUCCESS; return e; }

EGLDisplay eglGetDisplay(EGLNativeDisplayType id) { (void)id; ok(); return DEFAULT_DISPLAY; }

EGLBoolean eglInitialize(EGLDisplay d, EGLint *major, EGLint *minor)
{
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	if (major) *major = 1;
	if (minor) *minor = 4;
	return ok();
}

EGLBoolean eglTerminate(EGLDisplay d) { return valid_display(d) ? ok() : fail(EGL_BAD_DISPLAY); }

const char *eglQueryString(EGLDisplay d, EGLint name)
{
	if (!valid_display(d)) { fail(EGL_BAD_DISPLAY); return NULL; }
	switch (name) {
	case EGL_VENDOR: ok(); return "Lunacy";
	case EGL_VERSION: ok(); return "1.4";
	case EGL_EXTENSIONS: ok(); return "";
	case EGL_CLIENT_APIS: ok(); return "NULL";
	}
	fail(EGL_BAD_PARAMETER);
	return NULL;
}

EGLDisplay eglGetCurrentDisplay(void) { ok(); return gl_screen() ? CURRENT_DISPLAY : NULL; }
EGLContext eglGetCurrentContext(void) { ok(); return gl_screen() ? THE_CONTEXT : NULL; }
EGLSurface eglGetCurrentSurface(EGLint which)
{
	if (which != EGL_DRAW && which != EGL_READ) { fail(EGL_BAD_PARAMETER); return NULL; }
	ok();
	return gl_screen() ? THE_SURFACE : NULL;
}

EGLBoolean eglBindAPI(EGLenum api) { return api == EGL_OPENGL_ES_API ? ok() : fail(EGL_BAD_PARAMETER); }
EGLenum eglQueryAPI(void) { ok(); return EGL_OPENGL_ES_API; }

EGLBoolean eglQuerySurface(EGLDisplay d, EGLSurface s, EGLint attr, EGLint *value)
{
	sdl_surface_head *screen = gl_screen();
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	if (s != THE_SURFACE || !screen) return fail(EGL_BAD_SURFACE);
	switch (attr) {
	case EGL_WIDTH: *value = screen->w; return ok();
	case EGL_HEIGHT: *value = screen->h; return ok();
	case EGL_CONFIG_ID: *value = 5; return ok();
	}
	return fail(EGL_BAD_ATTRIBUTE);
}

EGLBoolean eglQueryContext(EGLDisplay d, EGLContext c, EGLint attr, EGLint *value)
{
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	if (c != THE_CONTEXT || !gl_screen()) return fail(EGL_BAD_CONTEXT);
	switch (attr) {
	case EGL_CONTEXT_CLIENT_VERSION: *value = gles_major(); return ok();
	case EGL_CONFIG_ID: *value = 5; return ok();
	}
	return fail(EGL_BAD_ATTRIBUTE);
}

/* One config, SDL's: the 27 the TouchPad listed were its driver's, for contexts it could
   make; Lunacy makes none here. */
EGLBoolean eglGetConfigs(EGLDisplay d, EGLConfig *configs, EGLint size, EGLint *num)
{
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	if (!num) return fail(EGL_BAD_PARAMETER);
	*num = configs ? (size > 0 ? 1 : 0) : 1;
	if (configs && size > 0) configs[0] = THE_CONFIG;
	return ok();
}

EGLBoolean eglChooseConfig(EGLDisplay d, const EGLint *attrs, EGLConfig *configs, EGLint size, EGLint *num)
{
	(void)attrs;
	return eglGetConfigs(d, configs, size, num);
}

EGLBoolean eglGetConfigAttrib(EGLDisplay d, EGLConfig c, EGLint attr, EGLint *value)
{
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	if (c != THE_CONFIG) return fail(EGL_BAD_CONFIG);
	switch (attr) {
	case EGL_CONFIG_ID: *value = 5; return ok();
	case 0x3020: *value = 32; return ok();                         /* EGL_BUFFER_SIZE */
	case 0x3021: case 0x3022: case 0x3023: case 0x3024: *value = 8; return ok();   /* alpha, blue, green, red */
	case 0x3025: *value = 16; return ok();                         /* EGL_DEPTH_SIZE */
	case 0x3026: *value = 8; return ok();                          /* EGL_STENCIL_SIZE */
	case 0x3033: *value = 0x4; return ok();                        /* EGL_SURFACE_TYPE: window */
	case 0x3040: *value = gles_major() == 2 ? 0x4 : 0x1; return ok();   /* EGL_RENDERABLE_TYPE */
	}
	*value = 0;
	return ok();
}

EGLContext eglCreateContext(EGLDisplay d, EGLConfig c, EGLContext share, const EGLint *attrs)
{
	(void)c; (void)share; (void)attrs;
	if (!valid_display(d)) { fail(EGL_BAD_DISPLAY); return NULL; }
	fail(EGL_BAD_ALLOC);
	return NULL;
}
EGLSurface eglCreateWindowSurface(EGLDisplay d, EGLConfig c, EGLNativeWindowType w, const EGLint *attrs)
{
	(void)c; (void)w; (void)attrs;
	if (!valid_display(d)) { fail(EGL_BAD_DISPLAY); return NULL; }
	fail(EGL_BAD_ALLOC);
	return NULL;
}
EGLSurface eglCreatePbufferSurface(EGLDisplay d, EGLConfig c, const EGLint *attrs)
{
	return eglCreateWindowSurface(d, c, NULL, attrs);
}
EGLSurface eglCreatePixmapSurface(EGLDisplay d, EGLConfig c, EGLNativePixmapType p, const EGLint *attrs)
{
	(void)p;
	return eglCreateWindowSurface(d, c, NULL, attrs);
}
EGLSurface eglCreatePbufferFromClientBuffer(EGLDisplay d, EGLenum t, EGLClientBuffer b, EGLConfig c, const EGLint *attrs)
{
	(void)t; (void)b;
	return eglCreateWindowSurface(d, c, NULL, attrs);
}
EGLBoolean eglDestroyContext(EGLDisplay d, EGLContext c)
{
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	return c == THE_CONTEXT ? fail(EGL_BAD_ACCESS) : fail(EGL_BAD_CONTEXT);
}
EGLBoolean eglDestroySurface(EGLDisplay d, EGLSurface s)
{
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	return s == THE_SURFACE ? fail(EGL_BAD_ACCESS) : fail(EGL_BAD_SURFACE);
}

/* Making SDL's context current again is what it already is; releasing it, or binding
   anything else, isn't something the one stream can do. */
EGLBoolean eglMakeCurrent(EGLDisplay d, EGLSurface draw, EGLSurface read, EGLContext c)
{
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	if (c == THE_CONTEXT && draw == THE_SURFACE && read == THE_SURFACE && gl_screen()) return ok();
	if (c != THE_CONTEXT && c != NULL) return fail(EGL_BAD_CONTEXT);
	return fail(EGL_BAD_MATCH);
}

EGLBoolean eglSwapBuffers(EGLDisplay d, EGLSurface s)
{
	static void (*swap)(void);
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	if (s != THE_SURFACE || !gl_screen()) return fail(EGL_BAD_SURFACE);
	if (!swap) *(void **)&swap = dlsym(RTLD_DEFAULT, "SDL_GL_SwapBuffers");
	if (swap) swap();
	return ok();
}

/* The TouchPad's eglSwapInterval ended the probe's process; the shell paces the stream
   (one frame in flight), so the interval is accepted and the pacing is the shell's. */
EGLBoolean eglSwapInterval(EGLDisplay d, EGLint interval)
{
	(void)interval;
	return valid_display(d) ? ok() : fail(EGL_BAD_DISPLAY);
}

EGLBoolean eglSurfaceAttrib(EGLDisplay d, EGLSurface s, EGLint attr, EGLint value)
{
	(void)attr; (void)value;
	if (!valid_display(d)) return fail(EGL_BAD_DISPLAY);
	return s == THE_SURFACE ? fail(EGL_BAD_ATTRIBUTE) : fail(EGL_BAD_SURFACE);
}
EGLBoolean eglBindTexImage(EGLDisplay d, EGLSurface s, EGLint buffer) { (void)buffer; return eglSurfaceAttrib(d, s, 0, 0) ? ok() : fail(EGL_BAD_MATCH); }
EGLBoolean eglReleaseTexImage(EGLDisplay d, EGLSurface s, EGLint buffer) { return eglBindTexImage(d, s, buffer); }
EGLBoolean eglCopyBuffers(EGLDisplay d, EGLSurface s, EGLNativePixmapType p) { (void)s; (void)p; return valid_display(d) ? fail(EGL_BAD_MATCH) : fail(EGL_BAD_DISPLAY); }

/* The stream is ordered: what the app sent is drawn before anything it sends after. */
EGLBoolean eglWaitClient(void) { return ok(); }
EGLBoolean eglWaitGL(void) { return ok(); }
EGLBoolean eglWaitNative(EGLint engine) { (void)engine; return ok(); }
EGLBoolean eglReleaseThread(void) { return ok(); }

/* The extension functions of the context's own GLES, from the client library that serves
   it. A core function's name has no vendor suffix: NULL, as on the device. */
static int extension_name(const char *n)
{
	static const char *suffixes[] = { "OES", "EXT", "IMG", "AMD", "QCOM", "ARB", "KHR", "NV", "APPLE", "ANGLE", NULL };
	size_t len = strlen(n);
	for (int i = 0; suffixes[i]; i++) {
		size_t s = strlen(suffixes[i]);
		if (len > s && strcmp(n + len - s, suffixes[i]) == 0) return 1;
	}
	return 0;
}

void (*eglGetProcAddress(const char *name))(void)
{
	void (*f)(void) = NULL;
	if (!name || strncmp(name, "gl", 2) != 0 || name[2] < 'A' || name[2] > 'Z' || !extension_name(name)) return NULL;
	const char *lib = gles_major() == 2 ? "libGLESv2.so" : "libGLES_CM.so";
	void *h = dlopen(lib, RTLD_LAZY | RTLD_NOLOAD);
	if (!h) h = dlopen(lib, RTLD_LAZY);
	if (h) *(void **)&f = dlsym(h, name);
	return f;
}

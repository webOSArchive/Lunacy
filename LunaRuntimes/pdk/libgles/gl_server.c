/*
 * liblunacygl: the shell's end of a PDK app's GL stream (Docs/pdk.md, "The GL stream").
 * Built with the NDK for Android, loaded by PdkGl.kt. Replays the batches the app's
 * libGLES_CM.so sends: each command's opcode is its index in the PDK's GLES headers, the
 * same numbering the client was generated with (gles_replay.inc, from gen_gles1.py server).
 *
 * The app gets what it had on a device: a framebuffer of its own screen's size (320 x 480
 * for a Pre game, 1024 x 768 for a TouchPad one), here an offscreen framebuffer object in
 * the app's context, which is current on a 1 x 1 pbuffer and never on a window. Every call
 * passes through untouched - viewports, scissors, draw-texture, copies and reads all mean
 * what they meant - and binding framebuffer 0 binds this one. At each swap a second context,
 * sharing the colour texture, draws it to the card's TextureView, letterboxed and turned,
 * as the TouchPad's compositor put an app's buffer on its screen; then the swap is
 * acknowledged at the TouchPad's 60 a second, which paces the app. Without a window (the
 * card not laid out yet, a dozing screen) the app keeps drawing into its framebuffer at the
 * same pace.
 */
#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
/* One source, two libraries: liblunacygl.so replays GLES 1.1 (GLES_VERSION 1, the default),
   liblunacygl2.so GLES 2 (built with -DGLES_VERSION=2; gen_gles2.py). */
#ifndef GLES_VERSION
#define GLES_VERSION 1
#endif
#if GLES_VERSION == 2
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#define REPLAY_INC "gles2_replay.inc"
#define JNI(f) Java_org_webosarchive_lunacy_card_PdkGl_##f##2
/* GLES 2 has framebuffers in its core; the OES names the shared code uses are the same values. */
#define GL_FRAMEBUFFER_OES GL_FRAMEBUFFER
#define GL_RENDERBUFFER_OES GL_RENDERBUFFER
#define GL_COLOR_ATTACHMENT0_OES GL_COLOR_ATTACHMENT0
#define GL_DEPTH_ATTACHMENT_OES GL_DEPTH_ATTACHMENT
#define GL_STENCIL_ATTACHMENT_OES GL_STENCIL_ATTACHMENT
#define GL_DEPTH_COMPONENT16_OES GL_DEPTH_COMPONENT16
#define GL_FRAMEBUFFER_COMPLETE_OES GL_FRAMEBUFFER_COMPLETE
#define GL_FRAMEBUFFER_BINDING_OES GL_FRAMEBUFFER_BINDING
#define GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME_OES GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
#else
#include <GLES/gl.h>
#include <GLES/glext.h>
#define REPLAY_INC "gles_replay.inc"
#define JNI(f) Java_org_webosarchive_lunacy_card_PdkGl_##f
#endif
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <time.h>
#include <unistd.h>
#include "pvrtc.h"

#define GLES_NAMES
#include REPLAY_INC
#undef GLES_NAMES
#define TAG "Lunacy"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

struct ctx {
	EGLDisplay display;
	EGLConfig config;
	EGLContext context;         /* the app's: current on the pbuffer, drawing into app_fb */
	EGLContext blit;            /* the card's: shares APP_TEX, current on the window to show it */
	EGLSurface pbuffer;
	EGLSurface window_surface;  /* EGL_NO_SURFACE until the card's TextureView has one */
	ANativeWindow *window;
	int view_w, view_h;         /* the card, in px */
	int game_w, game_h;         /* the app's screen */
	int turn;                   /* 0, 90, 180 or -90: how the picture is turned, clockwise */
	int errors;                 /* GL errors logged */
	int dump;                   /* this frame's commands go to the log (the dump file was seen) */
	double t_mark, last_swap;   /* for the frame rate, and the 60-a-second pace */
	int frames;
};

static struct ctx *cur;   /* the context the replay is for (one app at a time per thread) */

static void missing(const char *name) { LOGW("pdk gl: the device's GLES %d has no %s; skipped", GLES_VERSION, name); }

/* ---- the reader ---- */
struct reader { const uint8_t *p, *end; };
static uint32_t get_u32(struct reader *r) { uint32_t v = 0; if (r->p + 4 <= r->end) { memcpy(&v, r->p, 4); r->p += 4; } return v; }
static float get_f32(struct reader *r) { float v = 0; if (r->p + 4 <= r->end) { memcpy(&v, r->p, 4); r->p += 4; } return v; }
/* A copied pointer argument: [u32 len][bytes]; NULL when the app passed NULL. */
static uint32_t blob_len;   /* the last blob's length, for the checks before an upload */
static const void *get_blob(struct reader *r, uint32_t present)
{
	uint32_t len = get_u32(r), padded = (len + 3) & ~3u;   /* the client pads blobs to 4 */
	blob_len = len;
	const void *p = r->p;
	if (r->p + padded > r->end) { r->p = r->end; return NULL; }
	r->p += padded;
	return present ? p : NULL;
}

/* ---- arrays: the app's client-side arrays arrive with each draw ---- */
static uint8_t *index_scratch; static size_t index_cap;   /* a draw's indices, when in the app's memory */
static GLuint element_bound, array_bound;
static int client_unit;
#define ARRAYS 10
struct array { int enabled; GLint size; GLenum type; GLsizei stride; uint32_t offset; uint8_t *data; size_t cap; int fresh; GLboolean normalized; };
#if GLES_VERSION == 2
#undef ARRAYS
#define ARRAYS 16   /* vertex attributes */
#endif
static struct array arrays[ARRAYS];

/* An ARRAY command (GLES 1.1: slot, size, stride, type, 0, 0) or ATTRIB command (GLES 2:
   index, size, type, normalized, stride), then the bytes; slot 255 is a draw's indices. */
static void take_array(struct reader *r, int attrib)
{
	uint32_t s = get_u32(r), size, stride, type, normalized = 0;
	if (attrib) { size = get_u32(r); type = get_u32(r); normalized = get_u32(r); stride = get_u32(r); }
	else { size = get_u32(r); stride = get_u32(r); type = get_u32(r); get_u32(r); get_u32(r); }
	uint32_t len = get_u32(r), padded = (len + 3) & ~3u;
	if (r->p + padded > r->end) { r->p = r->end; return; }
	if (s == 255) {
		if (len > index_cap) { index_scratch = realloc(index_scratch, len); index_cap = len; }
		memcpy(index_scratch, r->p, len);
	} else if (s < ARRAYS) {
		struct array *a = &arrays[s];
		if (len > a->cap) { a->data = realloc(a->data, len); a->cap = len; }
		memcpy(a->data, r->p, len);
		a->size = (GLint)size; a->stride = (GLsizei)stride; a->type = type; a->normalized = (GLboolean)normalized; a->fresh = 1;
	}
	r->p += padded;
}

#if GLES_VERSION == 2
/* Before a draw: each attribute whose elements just arrived points at the copy. */
static void apply_attribs(void)
{
	for (GLuint i = 0; i < ARRAYS; i++) {
		struct array *a = &arrays[i];
		if (!a->fresh) continue;
		a->fresh = 0;
		if (array_bound) glBindBuffer(GL_ARRAY_BUFFER, 0);
		glVertexAttribPointer(i, a->size, a->type, a->normalized, a->stride, a->data);
		if (array_bound) glBindBuffer(GL_ARRAY_BUFFER, array_bound);
	}
}
#else
static void remember_pointer(int id, int unit, GLint size, GLenum type, GLsizei stride, uint32_t offset)
{
	int s = id == 3 ? 3 + unit : (id < 3 ? id : id + 3);
	struct array *a = &arrays[s];
	a->size = size; a->type = type; a->stride = stride; a->offset = offset;
	/* With a VBO bound the offset is the pointer: set it now, as the app did. */
	if (array_bound) {
		const GLvoid *p = (const GLvoid *)(uintptr_t)offset;
		switch (s) {
		case 0: glVertexPointer(size, type, stride, p); break;
		case 1: glColorPointer(size, type, stride, p); break;
		case 2: glNormalPointer(type, stride, p); break;
		case 3: case 4: case 5: case 6: glClientActiveTexture(GL_TEXTURE0 + (s - 3)); glTexCoordPointer(size, type, stride, p); glClientActiveTexture(GL_TEXTURE0 + client_unit); break;
		case 7: glPointSizePointerOES(type, stride, p); break;
		default: break;
		}
	}
}
static void server_enable(GLenum cap, int on)
{
	int s = -1;
	switch (cap) {
	case GL_VERTEX_ARRAY: s = 0; break; case GL_COLOR_ARRAY: s = 1; break; case GL_NORMAL_ARRAY: s = 2; break;
	case GL_TEXTURE_COORD_ARRAY: s = 3 + client_unit; break; case GL_POINT_SIZE_ARRAY_OES: s = 7; break;
	default: break;
	}
	if (s >= 0) arrays[s].enabled = on;
	if (on) glEnableClientState(cap); else glDisableClientState(cap);
}
/* Before a draw: point the real GL at the copies that just arrived. */
static void apply_arrays(void)
{
	for (int s = 0; s < ARRAYS; s++) {
		struct array *a = &arrays[s];
		if (!a->fresh) continue;
		a->fresh = 0;
		switch (s) {
		case 0: glVertexPointer(a->size, a->type, a->stride, a->data); break;
		case 1: glColorPointer(a->size, a->type, a->stride, a->data); break;
		case 2: glNormalPointer(a->type, a->stride, a->data); break;
		case 3: case 4: case 5: case 6:
			glClientActiveTexture(GL_TEXTURE0 + (s - 3)); glTexCoordPointer(a->size, a->type, a->stride, a->data);
			glClientActiveTexture(GL_TEXTURE0 + client_unit); break;
		case 7: glPointSizePointerOES(a->type, a->stride, a->data); break;
		default: break;
		}
	}
}
#endif

/* ---- uploads ---- */
static GLint unpack_alignment = 4;
static int has_bgra = -1;
static size_t upload_bytes(GLsizei w, GLsizei h, GLenum format, GLenum type)
{
	size_t bpp;
	switch (type) {
	case GL_UNSIGNED_SHORT_5_6_5: case GL_UNSIGNED_SHORT_4_4_4_4: case GL_UNSIGNED_SHORT_5_5_5_1: bpp = 2; break;
	default:
		switch (format) {
		case GL_RGBA: case 0x80E1: bpp = 4; break; case GL_RGB: bpp = 3; break;
		case GL_LUMINANCE_ALPHA: bpp = 2; break; default: bpp = 1; break;
		}
	}
	size_t a = unpack_alignment > 0 ? (size_t)unpack_alignment : 1;
	return ((size_t)w * bpp + a - 1) / a * a * (size_t)h;
}
/*
 * Before glTexImage2D / glTexSubImage2D: the driver reads as many bytes as the size says,
 * so an upload shorter than that is skipped rather than read past (logged once). BGRA
 * (GL_EXT_texture_format_BGRA8888, which the TouchPad's GPU had) is swizzled to RGBA on a
 * device whose GLES 1.1 hasn't it. Returns the pixels to use, or NULL to skip.
 */
static uint8_t *swizzled; static size_t swizzled_cap;
static const void *upload(const char *name, GLsizei w, GLsizei h, GLint *internalformat, GLenum *format, GLenum type, const void *pixels)
{
	if (!pixels) return NULL;
	size_t need = upload_bytes(w, h, *format, type);
	if (blob_len < need) {
		static int told;
		if (told++ < 5) LOGW("pdk gl: %s %d x %d format 0x%x type 0x%x needs %zu bytes, got %u; skipped", name, w, h, *format, type, need, blob_len);
		return (const void *)-1;
	}
	if (*format != 0x80E1) return pixels;
	if (has_bgra < 0) { const char *e = (const char *)glGetString(GL_EXTENSIONS); has_bgra = e && strstr(e, "GL_EXT_texture_format_BGRA8888") != NULL; }
	if (has_bgra) return pixels;
	if (need > swizzled_cap) { swizzled = realloc(swizzled, need); swizzled_cap = need; }
	const uint8_t *s = pixels;
	for (size_t i = 0; i + 3 < need; i += 4) { swizzled[i] = s[i + 2]; swizzled[i + 1] = s[i + 1]; swizzled[i + 2] = s[i]; swizzled[i + 3] = s[i + 3]; }
	*format = GL_RGBA; if (internalformat && *internalformat == 0x80E1) *internalformat = GL_RGBA;
	return swizzled;
}
/*
 * Before glCompressedTexImage2D / glCompressedTexSubImage2D: PVRTC (the TouchPad's own
 * compression, which PDK games ship their textures in) is decoded to RGBA8 and uploaded as
 * that where the driver hasn't GL_IMG_texture_compression_pvrtc (pvrtc.h). Returns 1 when
 * the call was handled here.
 */
static int has_pvrtc = -1;
static int compressed_upload(int sub, GLenum target, GLint level, GLint xoff, GLint yoff, GLenum format, GLsizei w, GLsizei h, const void *data)
{
	if (!data || !pvrtc_is_format(format)) return 0;
	if (has_pvrtc < 0) { const char *e = (const char *)glGetString(GL_EXTENSIONS); has_pvrtc = e && strstr(e, "GL_IMG_texture_compression_pvrtc") != NULL; }
	if (has_pvrtc) return 0;
	uint8_t *rgba = pvrtc_decode(data, blob_len, w, h, format == PVRTC_RGB_2BPP || format == PVRTC_RGBA_2BPP, format >= PVRTC_RGBA_4BPP);
	if (!rgba) {
		static int told;
		if (told++ < 5) LOGW("pdk gl: PVRTC 0x%x %d x %d in %u bytes not decoded; skipped", format, w, h, blob_len);
		return 1;
	}
	GLint align = 4; glGetIntegerv(GL_UNPACK_ALIGNMENT, &align); glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
	if (sub) glTexSubImage2D(target, level, xoff, yoff, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
	else glTexImage2D(target, level, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
	glPixelStorei(GL_UNPACK_ALIGNMENT, align);
	free(rgba);
	return 1;
}

/* ---- the app's framebuffer and the card ---- */
/* The colour texture's name is one the app can't reach (its own come from a counter starting
   at 1, libGLES_CM); textures may be named without glGenTextures in GLES 1.1. Framebuffers
   and renderbuffers may not, on Adreno at least: those are generated, and mapped below. */
#define APP_TEX   0x7FFFFF02u
#define LPDK_GL_PIXELS 7
#define LPDK_GL_ACK    8

/* The framebuffer entry points: GLES 1.1's extension, fetched by name, or GLES 2's core. */
static void (*bind_fb)(GLenum, GLuint);
static void (*fb_texture)(GLenum, GLenum, GLenum, GLuint, GLint);
static void (*bind_rb)(GLenum, GLuint);
static void (*rb_storage)(GLenum, GLenum, GLsizei, GLsizei);
static void (*fb_renderbuffer)(GLenum, GLenum, GLenum, GLuint);
static GLenum (*fb_status)(GLenum);
static void (*gen_fb)(GLsizei, GLuint *);
static void (*gen_rb)(GLsizei, GLuint *);
static GLuint app_fb, app_depth;   /* the app's framebuffer 0, and its depth buffer */

/* The app's names to the driver's: framebuffers and renderbuffers (made when first bound),
   and GLES 2's shaders and programs (made when the app creates one, add_name). */
enum { RB_NAMES, FB_NAMES, PROG_NAMES };
struct names { GLuint *app, *real; int n, cap; int fb; };
static struct names fb_names = { .fb = FB_NAMES }, rb_names = { .fb = RB_NAMES }, prog_names = { .fb = PROG_NAMES };
static void add_name(struct names *t, GLuint name, GLuint real);
static GLuint map_name(struct names *t, GLuint name, int make)
{
	if (!name) return t->fb == FB_NAMES ? app_fb : 0;
	for (int i = 0; i < t->n; i++) if (t->app[i] == name) return t->real[i];
	if (!make || t->fb == PROG_NAMES) return 0;
	GLuint real = 0;
	if (t->fb == FB_NAMES) { if (gen_fb) gen_fb(1, &real); } else { if (gen_rb) gen_rb(1, &real); }
	if (!real) return 0;
	add_name(t, name, real);
	return real;
}
static void add_name(struct names *t, GLuint name, GLuint real)
{
	if (t->n == t->cap) { t->cap = t->cap ? t->cap * 2 : 16; t->app = realloc(t->app, t->cap * sizeof *t->app); t->real = realloc(t->real, t->cap * sizeof *t->real); }
	t->app[t->n] = name; t->real[t->n] = real; t->n++;
}
static void forget_name(struct names *t, GLuint real)
{
	for (int i = 0; i < t->n; i++) if (t->real[i] == real) { t->app[i] = t->app[t->n - 1]; t->real[i] = t->real[t->n - 1]; t->n--; return; }
}
static void delete_names(struct names *t, GLsizei n, const GLuint *names, void (*del)(GLsizei, const GLuint *))
{
	for (GLsizei k = 0; names && k < n; k++) {
		for (int i = 0; i < t->n; i++) if (t->app[i] == names[k]) {
			GLuint real = t->real[i];
			if (real != app_fb && del) del(1, &real);
			t->app[i] = t->app[t->n - 1]; t->real[i] = t->real[t->n - 1]; t->n--;
			break;
		}
	}
}

static int make_app_framebuffer(struct ctx *c)
{
#if GLES_VERSION == 2
	bind_fb = glBindFramebuffer; fb_texture = glFramebufferTexture2D; bind_rb = glBindRenderbuffer;
	rb_storage = glRenderbufferStorage; fb_renderbuffer = glFramebufferRenderbuffer; fb_status = glCheckFramebufferStatus;
	gen_fb = glGenFramebuffers; gen_rb = glGenRenderbuffers;
#else
	bind_fb = (void (*)(GLenum, GLuint))eglGetProcAddress("glBindFramebufferOES");
	fb_texture = (void (*)(GLenum, GLenum, GLenum, GLuint, GLint))eglGetProcAddress("glFramebufferTexture2DOES");
	bind_rb = (void (*)(GLenum, GLuint))eglGetProcAddress("glBindRenderbufferOES");
	rb_storage = (void (*)(GLenum, GLenum, GLsizei, GLsizei))eglGetProcAddress("glRenderbufferStorageOES");
	fb_renderbuffer = (void (*)(GLenum, GLenum, GLenum, GLuint))eglGetProcAddress("glFramebufferRenderbufferOES");
	fb_status = (GLenum (*)(GLenum))eglGetProcAddress("glCheckFramebufferStatusOES");
	gen_fb = (void (*)(GLsizei, GLuint *))eglGetProcAddress("glGenFramebuffersOES");
	gen_rb = (void (*)(GLsizei, GLuint *))eglGetProcAddress("glGenRenderbuffersOES");
#endif
	if (!bind_fb || !fb_texture || !bind_rb || !rb_storage || !fb_renderbuffer || !fb_status || !gen_fb || !gen_rb) { LOGW("pdk gl: no GL_OES_framebuffer_object"); return 0; }
	gen_fb(1, &app_fb); gen_rb(1, &app_depth);
	glBindTexture(GL_TEXTURE_2D, APP_TEX);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, c->game_w, c->game_h, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	glBindTexture(GL_TEXTURE_2D, 0);
	bind_fb(GL_FRAMEBUFFER_OES, app_fb);
	fb_texture(GL_FRAMEBUFFER_OES, GL_COLOR_ATTACHMENT0_OES, GL_TEXTURE_2D, APP_TEX, 0);
	const char *ext = (const char *)glGetString(GL_EXTENSIONS);
	bind_rb(GL_RENDERBUFFER_OES, app_depth);
	if (ext && strstr(ext, "GL_OES_packed_depth_stencil")) {
		rb_storage(GL_RENDERBUFFER_OES, GL_DEPTH24_STENCIL8_OES, c->game_w, c->game_h);
		fb_renderbuffer(GL_FRAMEBUFFER_OES, GL_DEPTH_ATTACHMENT_OES, GL_RENDERBUFFER_OES, app_depth);
		fb_renderbuffer(GL_FRAMEBUFFER_OES, GL_STENCIL_ATTACHMENT_OES, GL_RENDERBUFFER_OES, app_depth);
	} else {
		rb_storage(GL_RENDERBUFFER_OES, GL_DEPTH_COMPONENT16_OES, c->game_w, c->game_h);
		fb_renderbuffer(GL_FRAMEBUFFER_OES, GL_DEPTH_ATTACHMENT_OES, GL_RENDERBUFFER_OES, app_depth);
	}
	bind_rb(GL_RENDERBUFFER_OES, 0);
	/* Complete, and the one bound: a bind that didn't take leaves the default framebuffer,
	 * which reports complete too. Tegra 3's GLES 1 doesn't answer GL_FRAMEBUFFER_BINDING_OES
	 * (GL_INVALID_ENUM, on the Nexus 7); there the bound framebuffer's colour attachment
	 * says which it is, as the default framebuffer has none to name. */
	while (glGetError() != GL_NO_ERROR) {}
	GLenum st = fb_status(GL_FRAMEBUFFER_OES); GLint bound = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING_OES, &bound);
	if (glGetError() == GL_INVALID_ENUM) {
#if GLES_VERSION == 2
		void (*attachment)(GLenum, GLenum, GLenum, GLint *) = glGetFramebufferAttachmentParameteriv;
#else
		void (*attachment)(GLenum, GLenum, GLenum, GLint *) = (void (*)(GLenum, GLenum, GLenum, GLint *))eglGetProcAddress("glGetFramebufferAttachmentParameterivOES");
#endif
		GLint tex = 0;
		if (attachment) attachment(GL_FRAMEBUFFER_OES, GL_COLOR_ATTACHMENT0_OES, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME_OES, &tex);
		if (glGetError() == GL_NO_ERROR && (GLuint)tex == APP_TEX) bound = (GLint)app_fb;
	}
	if (st != GL_FRAMEBUFFER_COMPLETE_OES || (GLuint)bound != app_fb) { LOGW("pdk gl: the app's framebuffer is incomplete (0x%x, bound %d)", st, bound); return 0; }
	/* GL's defaults for a fresh window: the whole screen, cleared black. */
	glViewport(0, 0, c->game_w, c->game_h);
	glScissor(0, 0, c->game_w, c->game_h);
	glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
	glClearColor(0, 0, 0, 0);
	return 1;
}

/* The card: the app's picture drawn into the view, scaled to fit and turned. Blit context. */
static void show(struct ctx *c)
{
	EGLint vw = 0, vh = 0;
	eglQuerySurface(c->display, c->window_surface, EGL_WIDTH, &vw);
	eglQuerySurface(c->display, c->window_surface, EGL_HEIGHT, &vh);
	if (vw != c->view_w || vh != c->view_h) {
		c->view_w = vw; c->view_h = vh;
		LOGI("pdk gl: card %d x %d, the app's %d x %d turned %d", vw, vh, c->game_w, c->game_h, c->turn);
	}
	int quarter = c->turn == 90 || c->turn == -90;
	float sw = quarter ? c->game_h : c->game_w, sh = quarter ? c->game_w : c->game_h;
	float k = vw / sw < vh / sh ? vw / sw : vh / sh;
	float hw = c->game_w * k / 2, hh = c->game_h * k / 2;
	const GLfloat quad[] = { -hw, -hh,  hw, -hh,  -hw, hh,  hw, hh };
	static const GLfloat uv[] = { 0, 0,  1, 0,  0, 1,  1, 1 };
#if GLES_VERSION == 2
	/* The same quad, turned on the CPU: GLES 2 has no matrix stack. */
	static GLuint prog; static GLint at_pos, at_uv, un_tex;
	if (!prog) {
		static const char *vs = "attribute vec2 pos; attribute vec2 uv; varying vec2 v; void main() { v = uv; gl_Position = vec4(pos, 0.0, 1.0); }";
		static const char *fs = "precision mediump float; varying vec2 v; uniform sampler2D tex; void main() { gl_FragColor = texture2D(tex, v); }";
		GLuint a = glCreateShader(GL_VERTEX_SHADER), b = glCreateShader(GL_FRAGMENT_SHADER);
		glShaderSource(a, 1, &vs, NULL); glCompileShader(a); glShaderSource(b, 1, &fs, NULL); glCompileShader(b);
		prog = glCreateProgram(); glAttachShader(prog, a); glAttachShader(prog, b); glLinkProgram(prog);
		at_pos = glGetAttribLocation(prog, "pos"); at_uv = glGetAttribLocation(prog, "uv"); un_tex = glGetUniformLocation(prog, "tex");
	}
	float rad = (float)-c->turn * 3.14159265f / 180.0f, cs = cosf(rad), sn = sinf(rad);
	GLfloat ndc[8];
	for (int i = 0; i < 4; i++) {
		float x = quad[2 * i], y = quad[2 * i + 1];
		ndc[2 * i] = (x * cs - y * sn) * 2.0f / vw; ndc[2 * i + 1] = (x * sn + y * cs) * 2.0f / vh;
	}
	glViewport(0, 0, vw, vh);
	glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
	glUseProgram(prog);
	glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, APP_TEX); glUniform1i(un_tex, 0);
	glEnableVertexAttribArray((GLuint)at_pos); glEnableVertexAttribArray((GLuint)at_uv);
	glVertexAttribPointer((GLuint)at_pos, 2, GL_FLOAT, GL_FALSE, 0, ndc); glVertexAttribPointer((GLuint)at_uv, 2, GL_FLOAT, GL_FALSE, 0, uv);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
#else
	glViewport(0, 0, vw, vh);
	glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
	glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrthof(0, (GLfloat)vw, 0, (GLfloat)vh, -1, 1);
	glMatrixMode(GL_MODELVIEW); glLoadIdentity();
	glTranslatef(vw / 2.0f, vh / 2.0f, 0);
	glRotatef((GLfloat)-c->turn, 0, 0, 1);   /* clockwise on the screen is negative with y up */
	glEnable(GL_TEXTURE_2D); glBindTexture(GL_TEXTURE_2D, APP_TEX);
	glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);
	glEnableClientState(GL_VERTEX_ARRAY); glEnableClientState(GL_TEXTURE_COORD_ARRAY);
	glVertexPointer(2, GL_FLOAT, 0, quad); glTexCoordPointer(2, GL_FLOAT, 0, uv);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
#endif
}

static uint8_t *replies; static size_t replies_len, replies_cap;
static void reply(uint32_t type, const void *payload, uint32_t len)
{
	if (replies_len + 12 + len > replies_cap) { replies_cap = (replies_len + 12 + len) * 2; replies = realloc(replies, replies_cap); }
	uint32_t h[3] = { type, 0, len };
	memcpy(replies + replies_len, h, 12); replies_len += 12;
	if (len) { memcpy(replies + replies_len, payload, len); replies_len += len; }
}

#if GLES_VERSION == 2
/* ---- GLES 2's queries: the answer is [u32 return value], then [u32 length][bytes] per output ---- */
#define LPDK_GL_RESULT 9
static size_t answer_at;   /* where the answer's header is in the replies */
static void answer_begin(uint32_t ret)
{
	answer_at = replies_len;
	reply(LPDK_GL_RESULT, &ret, 4);
}
static void answer_add(const void *p, uint32_t n)
{
	uint32_t padded = (n + 3) & ~3u, len;
	if (replies_len + 4 + padded > replies_cap) { replies_cap = (replies_len + 4 + padded) * 2; replies = realloc(replies, replies_cap); }
	memcpy(replies + replies_len, &n, 4); replies_len += 4;
	if (n) { if (p) memcpy(replies + replies_len, p, n); else memset(replies + replies_len, 0, n); memset(replies + replies_len + n, 0, padded - n); replies_len += padded; }
	len = (uint32_t)(replies_len - answer_at - 12);
	memcpy(replies + answer_at + 8, &len, 4);
}
/* A string output's buffer, the size the app gave. */
static char *out_str; static size_t out_cap;
static char *out_buffer(GLsizei n)
{
	if (n <= 0) return NULL;
	if ((size_t)n > out_cap) { out_str = realloc(out_str, (size_t)n); out_cap = (size_t)n; }
	memset(out_str, 0, (size_t)n);
	return out_str;
}
/* A shader that doesn't compile, or a program that doesn't link, is logged with the
   driver's message: GLSL the TouchPad's driver took and this one doesn't shows up here. */
static void check_compile(GLuint shader)
{
	GLint ok = 1; glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
	if (ok) return;
	char log[512] = "", src[600] = ""; GLsizei n = 0;
	glGetShaderInfoLog(shader, sizeof log, NULL, log);
	glGetShaderSource(shader, sizeof src, &n, src);
	for (char *p = src; *p; p++) if (*p == '\n') *p = ' ';
	LOGW("pdk gl: a shader didn't compile: %s; source: %.400s", log, src);
}
static void check_link(GLuint program)
{
	GLint ok = 1; glGetProgramiv(program, GL_LINK_STATUS, &ok);
	if (ok) return;
	char log[512] = ""; glGetProgramInfoLog(program, sizeof log, NULL, log);
	LOGW("pdk gl: a program didn't link: %s", log);
}
/* How many values a glGet*v answers, by pname (the GLES 2 spec's state tables). */
static int get_count(GLenum pname)
{
	GLint n = 0;
	switch (pname) {
	case GL_ALIASED_LINE_WIDTH_RANGE: case GL_ALIASED_POINT_SIZE_RANGE: case GL_DEPTH_RANGE: case GL_MAX_VIEWPORT_DIMS: return 2;
	case GL_BLEND_COLOR: case GL_COLOR_CLEAR_VALUE: case GL_COLOR_WRITEMASK: case GL_SCISSOR_BOX: case GL_VIEWPORT: return 4;
	case GL_COMPRESSED_TEXTURE_FORMATS: glGetIntegerv(GL_NUM_COMPRESSED_TEXTURE_FORMATS, &n); return n > 16 ? 16 : n;
	case GL_SHADER_BINARY_FORMATS: glGetIntegerv(GL_NUM_SHADER_BINARY_FORMATS, &n); return n > 16 ? 16 : n;
	default: return 1;
	}
}
/* How many values glGetUniform*v answers: the uniform's type, found by its location. */
static int uniform_count(GLuint program, GLint location)
{
	GLint count = 0; char name[256];
	glGetProgramiv(program, GL_ACTIVE_UNIFORMS, &count);
	for (GLint i = 0; i < count; i++) {
		GLint size; GLenum type;
		glGetActiveUniform(program, (GLuint)i, sizeof name, NULL, &size, &type, name);
		if (glGetUniformLocation(program, name) != location) continue;
		switch (type) {
		case GL_FLOAT_VEC2: case GL_INT_VEC2: case GL_BOOL_VEC2: return 2;
		case GL_FLOAT_VEC3: case GL_INT_VEC3: case GL_BOOL_VEC3: return 3;
		case GL_FLOAT_VEC4: case GL_INT_VEC4: case GL_BOOL_VEC4: case GL_FLOAT_MAT2: return 4;
		case GL_FLOAT_MAT3: return 9; case GL_FLOAT_MAT4: return 16;
		default: return 1;
		}
	}
	return 1;
}
#endif

static double now(void) { struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts); return ts.tv_sec + ts.tv_nsec / 1e9; }

/* The app's swap: its frame goes to the card, and is acknowledged. */
static void app_swap(struct ctx *c)
{
	if (c->window_surface != EGL_NO_SURFACE) {
		glFlush();
		if (eglMakeCurrent(c->display, c->window_surface, c->window_surface, c->blit)) {
			show(c);
			eglSwapBuffers(c->display, c->window_surface);
		}
		eglMakeCurrent(c->display, c->pbuffer, c->pbuffer, c->context);
	}
	/* The TouchPad's pace: a PDK app's swaps went at 60 a second, each waiting for the
	   display (Workbench/probe/swapprobe: 60.3 swaps/s, the longest 20 ms). A TextureView's
	   swap doesn't wait, and Where's My Water ran at 200 frames/s and starved its own sound,
	   so the acknowledgement keeps the 60, shown or not. A frame that came late starts the
	   count again rather than letting the next ones hurry. */
	double t = now(), due = c->last_swap + 1.0 / 60;
	if (t < due) { usleep((useconds_t)((due - t) * 1e6)); c->last_swap = due; }
	else c->last_swap = t;
	c->frames++;
	reply(LPDK_GL_ACK, NULL, 0);
	/* A dev tool: touching files/pdk/gldump logs one frame's commands (Docs/pdk.md). */
	if (c->dump) { LOGI("pdk gl dump: end of frame %d", c->frames); c->dump = 0; }
	else if (access("/data/data/org.webosarchive.lunacy/files/pdk/gldump", F_OK) == 0) { unlink("/data/data/org.webosarchive.lunacy/files/pdk/gldump"); c->dump = 1; LOGI("pdk gl dump: frame %d", c->frames + 1); }
	if (c->frames % 600 == 0) {
		double t = now();
		if (c->t_mark > 0) LOGI("pdk gl: %.1f frames/s", 600 / (t - c->t_mark));
		c->t_mark = t;
	}
}

/* glReadPixels from the app's framebuffer (whichever the app has bound). */
static void app_read(struct reader *r)
{
	GLint x = (GLint)get_u32(r), y = (GLint)get_u32(r); GLsizei w = (GLsizei)get_u32(r), h = (GLsizei)get_u32(r);
	GLenum format = get_u32(r), type = get_u32(r); uint32_t n = get_u32(r);
	if (n > 64u << 20) n = 0;
	uint8_t *p = n ? calloc(1, n) : NULL;
	if (p) glReadPixels(x, y, w, h, format, type, p);
	reply(LPDK_GL_PIXELS, p, p ? n : 0);
	free(p);
}

/* ---- the replay ---- */
static void replay(struct ctx *c, const uint8_t *data, size_t len)
{
	struct reader r = { data, data + len };
	cur = c;
	while (r.p + 8 <= r.end) {
		uint32_t op = get_u32(&r), payload = get_u32(&r);
		if (r.p + payload > r.end) {
			static int told;
			if (told++ < 5) LOGW("pdk gl: a batch broke at op 0x%x, %u bytes declared, %d left", op, payload, (int)(r.end - r.p));
			break;
		}
		if (c->dump && op >= 0xFFFCu) LOGI("pdk gl dump: %s %u bytes", op == 0xFFFFu ? "ARRAY" : op == 0xFFFCu ? "ATTRIB" : op == 0xFFFDu ? "SWAP" : "READ", payload);
		struct reader body = { r.p, r.p + payload };
		r.p += payload;
		if (op == 0xFFFFu) { take_array(&body, 0); continue; }
		if (op == 0xFFFCu) { take_array(&body, 1); continue; }
		if (op == 0xFFFEu) { app_read(&body); continue; }
		if (op == 0xFFFDu) { app_swap(c); continue; }
		if (c->dump) {
			char line[200]; int n = snprintf(line, sizeof line, "%s", op < sizeof op_names / sizeof *op_names ? op_names[op] : "?");
			struct reader a = body;
			for (int i = 0; i < 8 && a.p + 4 <= a.end && n < (int)sizeof line - 12; i++) n += snprintf(line + n, sizeof line - n, " %x", get_u32(&a));
			LOGI("pdk gl dump: %s", line);
		}
		{
			struct reader rd_ = body;
			switch (op) {
#include REPLAY_INC
#if GLES_VERSION == 2
#include "gl2_custom.inc"
#endif
			default: break;
			}
		}
		/* The first errors, with the command that made them: checked closely at first, then now and then. */
		if (c->errors < 20 && (c->frames < 200 || (c->frames & 63) == 0)) {
			GLenum e = glGetError();
			if (e) { c->errors++; LOGW("pdk gl: %s -> error 0x%x (frame %d)", op < sizeof op_names / sizeof *op_names ? op_names[op] : "?", e, c->frames); }
		}
	}
}

/* ---- EGL ---- */
static int create_contexts(struct ctx *c)
{
	c->display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
	if (c->display == EGL_NO_DISPLAY || !eglInitialize(c->display, NULL, NULL)) { LOGW("pdk gl: no EGL display"); return 0; }
	const EGLint attribs[] = { EGL_RENDERABLE_TYPE, GLES_VERSION == 2 ? EGL_OPENGL_ES2_BIT : EGL_OPENGL_ES_BIT, EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
		EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_NONE };
	EGLint n = 0;
	if (!eglChooseConfig(c->display, attribs, &c->config, 1, &n) || n < 1) { LOGW("pdk gl: no EGL config"); return 0; }
	const EGLint pb_attribs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
	c->pbuffer = eglCreatePbufferSurface(c->display, c->config, pb_attribs);
	const EGLint ctx_attribs[] = { EGL_CONTEXT_CLIENT_VERSION, GLES_VERSION, EGL_NONE };
	c->context = eglCreateContext(c->display, c->config, EGL_NO_CONTEXT, ctx_attribs);
	c->blit = eglCreateContext(c->display, c->config, c->context, ctx_attribs);
	c->window_surface = EGL_NO_SURFACE;
	if (c->pbuffer == EGL_NO_SURFACE || c->context == EGL_NO_CONTEXT || c->blit == EGL_NO_CONTEXT) { LOGW("pdk gl: no pbuffer or context (0x%x)", eglGetError()); return 0; }
	if (!eglMakeCurrent(c->display, c->pbuffer, c->pbuffer, c->context)) { LOGW("pdk gl: make current failed (0x%x)", eglGetError()); return 0; }
	if (!make_app_framebuffer(c)) return 0;
	LOGI("pdk gl: GLES %d on %s, the app's framebuffer %d x %d", GLES_VERSION, glGetString(GL_RENDERER), c->game_w, c->game_h);
	return 1;
}

/* ---- JNI: org.webosarchive.lunacy.card.PdkGl ---- */
JNIEXPORT jlong JNICALL JNI(create)(JNIEnv *env, jclass cls, jint game_w, jint game_h)
{
	struct ctx *c = calloc(1, sizeof *c);
	c->game_w = game_w; c->game_h = game_h;
	if (!create_contexts(c)) { free(c); return 0; }
	memset(arrays, 0, sizeof arrays); element_bound = array_bound = 0; client_unit = 0;
	fb_names.n = rb_names.n = prog_names.n = 0;
	return (jlong)(intptr_t)c;
}

/* The card's TextureView has a surface. */
JNIEXPORT void JNICALL JNI(attach)(JNIEnv *env, jclass cls, jlong handle, jobject surface)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	if (!c || c->window_surface != EGL_NO_SURFACE) return;
	c->window = ANativeWindow_fromSurface(env, surface);
	if (!c->window) return;
	EGLint format; eglGetConfigAttrib(c->display, c->config, EGL_NATIVE_VISUAL_ID, &format);
	ANativeWindow_setBuffersGeometry(c->window, 0, 0, format);
	c->window_surface = eglCreateWindowSurface(c->display, c->config, c->window, NULL);
	if (c->window_surface == EGL_NO_SURFACE) { LOGW("pdk gl: no window surface (0x%x)", eglGetError()); return; }
	/* The card shows black until the app's first swap. */
	if (eglMakeCurrent(c->display, c->window_surface, c->window_surface, c->blit)) {
		glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT); eglSwapBuffers(c->display, c->window_surface);
	}
	eglMakeCurrent(c->display, c->pbuffer, c->pbuffer, c->context);
}

/* Replays a batch; returns the answers for the app (acknowledgements, pixels), or null. */
JNIEXPORT jbyteArray JNICALL JNI(replay)(JNIEnv *env, jclass cls, jlong handle, jbyteArray data, jint len)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	if (!c) return NULL;
	jbyte *p = (*env)->GetByteArrayElements(env, data, NULL);
	if (!p) return NULL;
	replies_len = 0;
	replay(c, (const uint8_t *)p, (size_t)len);
	(*env)->ReleaseByteArrayElements(env, data, p, JNI_ABORT);
	if (!replies_len) return NULL;
	jbyteArray out = (*env)->NewByteArray(env, (jsize)replies_len);
	if (out) (*env)->SetByteArrayRegion(env, out, 0, (jsize)replies_len, (const jbyte *)replies);
	return out;
}

JNIEXPORT jint JNICALL JNI(frames)(JNIEnv *env, jclass cls, jlong handle)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	return c ? c->frames : 0;
}

JNIEXPORT void JNICALL JNI(turn)(JNIEnv *env, jclass cls, jlong handle, jint turn)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	if (c) c->turn = turn;
}

JNIEXPORT void JNICALL JNI(destroy)(JNIEnv *env, jclass cls, jlong handle)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	if (!c) return;
	eglMakeCurrent(c->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
	if (c->blit != EGL_NO_CONTEXT) eglDestroyContext(c->display, c->blit);
	if (c->context != EGL_NO_CONTEXT) eglDestroyContext(c->display, c->context);
	if (c->window_surface != EGL_NO_SURFACE) eglDestroySurface(c->display, c->window_surface);
	if (c->pbuffer != EGL_NO_SURFACE) eglDestroySurface(c->display, c->pbuffer);
	if (c->window) ANativeWindow_release(c->window);
	if (cur == c) cur = NULL;
	free(c);
}

/*
 * liblunacygl: the shell's end of a PDK app's GL stream (Docs/pdk.md, "Transformers G1").
 * Built with the NDK for Android, loaded by PdkGl.kt. Owns an OpenGL ES 1.1 context on
 * the card's TextureView and replays the batches the app's libGLES_CM.so sends: each
 * command's opcode is its index in the PDK's GLES headers, the same numbering the client
 * was generated with (gles_replay.inc, from gen_gles1.py server).
 *
 * The app drew for a screen of its own size (320 x 480 for Transformers); the view is the
 * card's. Every viewport and scissor the app sets is scaled and centred into the view,
 * keeping the aspect, so the picture is letterboxed as the card itself is. A phone-era
 * game drew for a portrait screen held sideways; the card asks for a quarter turn, which
 * is put into the projection matrix each time the app loads one, and into the viewport.
 */
#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define GLES_NAMES
#include "gles_replay.inc"
#undef GLES_NAMES
#define TAG "Lunacy"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

struct ctx {
	ANativeWindow *window;
	EGLDisplay display;
	EGLSurface surface;
	EGLContext context;
	int view_w, view_h;     /* the view, in px */
	int game_w, game_h;     /* the app's screen */
	int turn;               /* 0, 90, 180 or -90: how the picture is turned, Android's sense (clockwise) */
	GLint app_scissor[4];   /* the app's scissor rect, mapped */
	int errors;             /* GL errors logged */
	int dump;               /* this frame's commands go to the log (the dump file was seen) */
	double t_mark;          /* for the frame rate */
	float scale; int ox, oy;
	int frames;
};

static struct ctx *cur;   /* the context the replay is for (one app at a time per thread) */

static void missing(const char *name) { LOGW("pdk gl: the device's GLES 1.1 has no %s; skipped", name); }

/* ---- the reader ---- */
struct reader { const uint8_t *p, *end; };
static uint32_t get_u32(struct reader *r) { uint32_t v = 0; if (r->p + 4 <= r->end) { memcpy(&v, r->p, 4); r->p += 4; } return v; }
static float get_f32(struct reader *r) { float v = 0; if (r->p + 4 <= r->end) { memcpy(&v, r->p, 4); r->p += 4; } return v; }
/* A copied pointer argument: [u32 len][bytes]; NULL when the app passed NULL. */
static const void *get_blob(struct reader *r, uint32_t present)
{
	uint32_t len = get_u32(r), padded = (len + 3) & ~3u;   /* the client pads blobs to 4 */
	const void *p = r->p;
	if (r->p + padded > r->end) { r->p = r->end; return NULL; }
	r->p += padded;
	return present ? p : NULL;
}

/* ---- arrays: the app's client-side arrays arrive with each draw ---- */
#define ARRAYS 10
struct array { int enabled; GLint size; GLenum type; GLsizei stride; uint32_t offset; uint8_t *data; size_t cap; int fresh; };
static struct array arrays[ARRAYS];
static uint8_t *index_scratch; static size_t index_cap;
static GLuint element_bound, array_bound;
static int client_unit;

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
/* An ARRAY command: slot, size, stride, type, 0, 0, then the bytes. */
static void take_array(struct reader *r)
{
	uint32_t s = get_u32(r); GLint size = (GLint)get_u32(r); GLsizei stride = (GLsizei)get_u32(r); GLenum type = get_u32(r);
	get_u32(r); get_u32(r);
	uint32_t len = get_u32(r), padded = (len + 3) & ~3u;
	if (r->p + padded > r->end) { r->p = r->end; return; }
	if (s == 255) {
		if (len > index_cap) { index_scratch = realloc(index_scratch, len); index_cap = len; }
		memcpy(index_scratch, r->p, len);
	} else if (s < ARRAYS) {
		struct array *a = &arrays[s];
		if (len > a->cap) { a->data = realloc(a->data, len); a->cap = len; }
		memcpy(a->data, r->p, len);
		a->size = size; a->stride = stride; a->type = type; a->fresh = 1;
	}
	r->p += padded;
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

/* ---- the app's screen in the view ---- */
static void fit(struct ctx *c)
{
	int quarter = c->turn == 90 || c->turn == -90;
	int sw = quarter ? c->game_h : c->game_w, sh = quarter ? c->game_w : c->game_h;   /* as shown */
	if (sw <= 0 || sh <= 0 || c->view_w <= 0 || c->view_h <= 0) { c->scale = 1; c->ox = c->oy = 0; return; }
	float sx = (float)c->view_w / sw, sy = (float)c->view_h / sh;
	c->scale = sx < sy ? sx : sy;
	c->ox = (int)((c->view_w - sw * c->scale) / 2);
	c->oy = (int)((c->view_h - sh * c->scale) / 2);
}
/* A rect in the app's window coordinates (origin bottom left) to the view's. */
static void map_rect(struct ctx *c, GLint x, GLint y, GLsizei w, GLsizei h, GLint out[4])
{
	GLint rx = x, ry = y, rw = w, rh = h;
	if (c->turn == -90) { rx = c->game_h - (y + h); ry = x; rw = h; rh = w; }        /* counter-clockwise */
	else if (c->turn == 90) { rx = y; ry = c->game_w - (x + w); rw = h; rh = w; }   /* clockwise */
	else if (c->turn == 180) { rx = c->game_w - (x + w); ry = c->game_h - (y + h); }
	out[0] = c->ox + (GLint)(rx * c->scale); out[1] = c->oy + (GLint)(ry * c->scale);
	out[2] = (GLint)(rw * c->scale); out[3] = (GLint)(rh * c->scale);
}
static void gl_viewport(GLint x, GLint y, GLsizei w, GLsizei h)
{
	GLint r[4]; map_rect(cur, x, y, w, h, r); glViewport(r[0], r[1], r[2], r[3]);
}
/*
 * The scissor test is always on: the app draws inside its picture and nowhere else, since
 * GL clips to the frustum, not the viewport, and a draw-texture call or an oversized quad
 * would otherwise spill into the bars. The app's own scissor rect is intersected with it.
 */
static int scissor_on;
static void apply_scissor(void)
{
	struct ctx *c = cur;
	GLint p[4]; map_rect(c, 0, 0, c->game_w, c->game_h, p);
	if (scissor_on) {
		GLint *a = c->app_scissor;
		GLint x0 = a[0] > p[0] ? a[0] : p[0], y0 = a[1] > p[1] ? a[1] : p[1];
		GLint x1 = a[0] + a[2] < p[0] + p[2] ? a[0] + a[2] : p[0] + p[2], y1 = a[1] + a[3] < p[1] + p[3] ? a[1] + a[3] : p[1] + p[3];
		glScissor(x0, y0, x1 > x0 ? x1 - x0 : 0, y1 > y0 ? y1 - y0 : 0);
	} else glScissor(p[0], p[1], p[2], p[3]);
	glEnable(GL_SCISSOR_TEST);
}
static void gl_scissor(GLint x, GLint y, GLsizei w, GLsizei h)
{
	map_rect(cur, x, y, w, h, cur->app_scissor); apply_scissor();
}
/* The whole surface black, for the bars: after every swap, before the app's next frame. */
static void black(struct ctx *c)
{
	GLfloat col[4]; GLboolean mask[4];
	glGetFloatv(GL_COLOR_CLEAR_VALUE, col); glGetBooleanv(GL_COLOR_WRITEMASK, mask);
	glDisable(GL_SCISSOR_TEST); glColorMask(1, 1, 1, 1); glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
	glClearColor(col[0], col[1], col[2], col[3]); glColorMask(mask[0], mask[1], mask[2], mask[3]);
	cur = c; apply_scissor();
}
/* The projection starts from the turn, so everything the app draws comes out turned. */
static GLenum matrix_mode = GL_MODELVIEW;
static void load_identity(void)
{
	glLoadIdentity();
	if (matrix_mode == GL_PROJECTION && cur && cur->turn) glRotatef(cur->turn == 180 ? 180.0f : (cur->turn == -90 ? 90.0f : -90.0f), 0, 0, 1);
}
/* The surface's size follows the view's a frame late; checked after every swap. */
static void refit(struct ctx *c)
{
	EGLint w = 0, h = 0;
	eglQuerySurface(c->display, c->surface, EGL_WIDTH, &w);
	eglQuerySurface(c->display, c->surface, EGL_HEIGHT, &h);
	if (w == c->view_w && h == c->view_h) return;
	c->view_w = w; c->view_h = h;
	fit(c);
	black(c);
	LOGI("pdk gl: view now %d x %d, the app's %d x %d turned %d at x%.2f", w, h, c->game_w, c->game_h, c->turn, c->scale);
}

/* ---- the replay ---- */
static void replay(struct ctx *c, const uint8_t *data, size_t len)
{
	struct reader r = { data, data + len };
	cur = c;
	while (r.p + 8 <= r.end) {
		uint32_t op = get_u32(&r), payload = get_u32(&r);
		if (r.p + payload > r.end) break;
		struct reader body = { r.p, r.p + payload };
		r.p += payload;
		if (op == 0xFFFFu) { take_array(&body); continue; }
		if (c->dump) {
			char line[200]; int n = snprintf(line, sizeof line, "%s", op == 0xFFFFu ? "ARRAY" : (op < sizeof op_names / sizeof *op_names ? op_names[op] : "?"));
			struct reader a = body;
			for (int i = 0; i < 8 && a.p + 4 <= a.end && n < (int)sizeof line - 12; i++) n += snprintf(line + n, sizeof line - n, " %x", get_u32(&a));
			LOGI("pdk gl dump: %s", line);
		}
		{
			struct reader rd_ = body;
			switch (op) {
#include "gles_replay.inc"
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
static int create_context(struct ctx *c)
{
	c->display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
	if (c->display == EGL_NO_DISPLAY || !eglInitialize(c->display, NULL, NULL)) { LOGW("pdk gl: no EGL display"); return 0; }
	const EGLint attribs[] = { EGL_RENDERABLE_TYPE, EGL_OPENGL_ES_BIT, EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
		EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_DEPTH_SIZE, 16, EGL_STENCIL_SIZE, 8, EGL_NONE };
	EGLConfig config; EGLint n = 0;
	if (!eglChooseConfig(c->display, attribs, &config, 1, &n) || n < 1) {
		const EGLint loose[] = { EGL_RENDERABLE_TYPE, EGL_OPENGL_ES_BIT, EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_DEPTH_SIZE, 16, EGL_NONE };
		if (!eglChooseConfig(c->display, loose, &config, 1, &n) || n < 1) { LOGW("pdk gl: no EGL config"); return 0; }
	}
	EGLint format; eglGetConfigAttrib(c->display, config, EGL_NATIVE_VISUAL_ID, &format);
	ANativeWindow_setBuffersGeometry(c->window, 0, 0, format);
	c->surface = eglCreateWindowSurface(c->display, config, c->window, NULL);
	const EGLint ctx_attribs[] = { EGL_CONTEXT_CLIENT_VERSION, 1, EGL_NONE };
	c->context = eglCreateContext(c->display, config, EGL_NO_CONTEXT, ctx_attribs);
	if (c->surface == EGL_NO_SURFACE || c->context == EGL_NO_CONTEXT) { LOGW("pdk gl: no surface or context (0x%x)", eglGetError()); return 0; }
	if (!eglMakeCurrent(c->display, c->surface, c->surface, c->context)) { LOGW("pdk gl: make current failed (0x%x)", eglGetError()); return 0; }
	eglQuerySurface(c->display, c->surface, EGL_WIDTH, &c->view_w);
	eglQuerySurface(c->display, c->surface, EGL_HEIGHT, &c->view_h);
	fit(c);
	glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
	eglSwapBuffers(c->display, c->surface);
	LOGI("pdk gl: %s on %d x %d for an app screen of %d x %d", glGetString(GL_RENDERER), c->view_w, c->view_h, c->game_w, c->game_h);
	return 1;
}

/* ---- JNI: org.webosarchive.lunacy.card.PdkGl ---- */
JNIEXPORT jlong JNICALL Java_org_webosarchive_lunacy_card_PdkGl_create(JNIEnv *env, jclass cls, jobject surface, jint game_w, jint game_h)
{
	struct ctx *c = calloc(1, sizeof *c);
	c->game_w = game_w; c->game_h = game_h;
	c->window = ANativeWindow_fromSurface(env, surface);
	if (!c->window || !create_context(c)) { free(c); return 0; }
	memset(arrays, 0, sizeof arrays); element_bound = array_bound = 0; client_unit = 0; matrix_mode = GL_MODELVIEW; scissor_on = 0;
	return (jlong)(intptr_t)c;
}

JNIEXPORT void JNICALL Java_org_webosarchive_lunacy_card_PdkGl_replay(JNIEnv *env, jclass cls, jlong handle, jbyteArray data, jint len)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	if (!c) return;
	jbyte *p = (*env)->GetByteArrayElements(env, data, NULL);
	if (!p) return;
	replay(c, (const uint8_t *)p, (size_t)len);
	(*env)->ReleaseByteArrayElements(env, data, p, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_org_webosarchive_lunacy_card_PdkGl_swap(JNIEnv *env, jclass cls, jlong handle)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	if (!c) return;
	eglSwapBuffers(c->display, c->surface);
	c->frames++;
	refit(c);
	black(c);
	/* A dev tool: touching files/pdk/gldump logs one frame's commands (Docs/pdk.md). */
	if (c->dump) { LOGI("pdk gl dump: end of frame %d", c->frames); c->dump = 0; }
	else if (c->frames % 30 == 0 && access("/data/data/org.webosarchive.lunacy/files/pdk/gldump", F_OK) == 0) { unlink("/data/data/org.webosarchive.lunacy/files/pdk/gldump"); c->dump = 1; LOGI("pdk gl dump: frame %d", c->frames + 1); }
	if (c->frames % 600 == 0) {
		struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts); double t = ts.tv_sec + ts.tv_nsec / 1e9;
		if (c->t_mark > 0) LOGI("pdk gl: %.1f frames/s", 600 / (t - c->t_mark));
		c->t_mark = t;
	}
	if (c->frames == 1 || c->frames % 1000 == 0) {
		GLint vp[4]; glGetIntegerv(GL_VIEWPORT, vp);
		LOGI("pdk gl: frame %d, view %d x %d, viewport %d %d %d %d, error 0x%x", c->frames, c->view_w, c->view_h, vp[0], vp[1], vp[2], vp[3], glGetError());
	}
}

JNIEXPORT jint JNICALL Java_org_webosarchive_lunacy_card_PdkGl_frames(JNIEnv *env, jclass cls, jlong handle)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	return c ? c->frames : 0;
}

JNIEXPORT void JNICALL Java_org_webosarchive_lunacy_card_PdkGl_turn(JNIEnv *env, jclass cls, jlong handle, jint turn)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	if (!c || c->turn == turn) return;
	c->turn = turn;
	fit(c);
	black(c);
}

JNIEXPORT void JNICALL Java_org_webosarchive_lunacy_card_PdkGl_destroy(JNIEnv *env, jclass cls, jlong handle)
{
	struct ctx *c = (struct ctx *)(intptr_t)handle;
	if (!c) return;
	eglMakeCurrent(c->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
	if (c->context != EGL_NO_CONTEXT) eglDestroyContext(c->display, c->context);
	if (c->surface != EGL_NO_SURFACE) eglDestroySurface(c->display, c->surface);
	if (c->window) ANativeWindow_release(c->window);
	if (cur == c) cur = NULL;
	free(c);
}

/*
 * The hand-written half of libGLES_CM.so (Docs/pdk.md, "The GL stream"); gen_gles1.py
 * appends the generated glXxx functions below it. The transport is client_transport.h's;
 * this half keeps what GLES 1.1's fixed-function arrays need: which are enabled and where
 * they point, so a draw can send the elements it covers.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <GLES/gl.h>
typedef void *GLeglImageOES;
#include <GLES/glext.h>

#define STREAM_VERSION 1
#include "client_transport.h"

static int client_unit;

static int pname_count(GLenum pname, int n, const int *table)
{
	for (int i = 0; i < n; i++) if ((GLenum)table[2 * i] == pname) return table[2 * i + 1];
	return 1;
}

/* ---- client-side arrays ---- */

#define ARRAYS 10   /* vertex, color, normal, texcoord x4, point size, matrix index, weight */
struct array { int enabled; GLint size; GLenum type; GLsizei stride; const void *ptr; GLuint buffer; };
static struct array arrays[ARRAYS];

static int slot(int id, int unit) { return id == 3 ? 3 + unit : (id < 3 ? id : id + 3); }
static void set_array(int id, int unit, GLint size, GLenum type, GLsizei stride, const void *ptr)
{
	struct array *a = &arrays[slot(id, unit)];
	a->size = size; a->type = type; a->stride = stride; a->ptr = ptr; a->buffer = array_buffer;
}
static void enable_array(GLenum cap, int on)
{
	switch (cap) {
	case GL_VERTEX_ARRAY: arrays[0].enabled = on; break;
	case GL_COLOR_ARRAY: arrays[1].enabled = on; break;
	case GL_NORMAL_ARRAY: arrays[2].enabled = on; break;
	case GL_TEXTURE_COORD_ARRAY: arrays[3 + client_unit].enabled = on; break;
	case GL_POINT_SIZE_ARRAY_OES: arrays[7].enabled = on; break;
	case GL_MATRIX_INDEX_ARRAY_OES: arrays[8].enabled = on; break;
	case GL_WEIGHT_ARRAY_OES: arrays[9].enabled = on; break;
	default: break;
	}
}
static GLboolean array_enabled_cap(GLenum cap)
{
	switch (cap) {
	case GL_VERTEX_ARRAY: return arrays[0].enabled; case GL_COLOR_ARRAY: return arrays[1].enabled;
	case GL_NORMAL_ARRAY: return arrays[2].enabled; case GL_TEXTURE_COORD_ARRAY: return arrays[3 + client_unit].enabled;
	case GL_POINT_SIZE_ARRAY_OES: return arrays[7].enabled; case GL_MATRIX_INDEX_ARRAY_OES: return arrays[8].enabled;
	case GL_WEIGHT_ARRAY_OES: return arrays[9].enabled;
	default: return GL_FALSE;
	}
}

/* ---- the state an app reads back ----
 * The calls go to the shell and return nothing, so what an app asks of GL's state
 * (glIsEnabled, glGet*, glGetTexEnv*) is answered here from what it set, starting from
 * GLES 1.1's initial values. An answer of 0 for everything was a lie the app acts on:
 * apkenv saves the state around its own drawing and puts it back, and put texturing back
 * off and the active texture at 0 (Where's My Water drew white; Docs/pdk.md "EGL and the
 * Android ports"). Per texture unit: GL_TEXTURE_2D, the point sprite, the bound texture
 * and the texture environment. */
#define UNITS 4
static int server_unit;
static GLuint bound_texture[UNITS], bound_framebuffer, bound_renderbuffer;

static const GLenum unit_caps[] = { GL_TEXTURE_2D, GL_POINT_SPRITE_OES };
static unsigned char unit_cap_on[UNITS][2];
#define CAPS 48
static struct { GLenum cap; unsigned char on; } caps[CAPS] = {
	{ GL_DITHER, 1 }, { GL_MULTISAMPLE, 1 },   /* the two GLES 1.1 starts enabled */
};
static int ncaps = 2;

static int unit_cap(GLenum cap)
{
	for (int i = 0; i < (int)(sizeof unit_caps / sizeof unit_caps[0]); i++) if (unit_caps[i] == cap) return i;
	return -1;
}
static void set_cap(GLenum cap, int on)
{
	int u = unit_cap(cap);
	if (u >= 0) { unit_cap_on[server_unit][u] = (unsigned char)on; return; }
	for (int i = 0; i < ncaps; i++) if (caps[i].cap == cap) { caps[i].on = (unsigned char)on; return; }
	if (ncaps < CAPS) { caps[ncaps].cap = cap; caps[ncaps].on = (unsigned char)on; ncaps++; }
}
static GLboolean cap_enabled(GLenum cap)
{
	int u = unit_cap(cap);
	if (u >= 0) return unit_cap_on[server_unit][u];
	for (int i = 0; i < ncaps; i++) if (caps[i].cap == cap) return caps[i].on;
	return array_enabled_cap(cap);
}

/* Single values and vectors, as floats; enums and booleans are whole numbers in them. */
struct value { GLenum pname; int n; int is_color; GLfloat v[4]; };
static struct value values[] = {
	{ GL_MATRIX_MODE, 1, 0, { GL_MODELVIEW } },
	{ GL_BLEND_SRC, 1, 0, { GL_ONE } }, { GL_BLEND_DST, 1, 0, { GL_ZERO } },
	{ GL_DEPTH_FUNC, 1, 0, { GL_LESS } }, { GL_CULL_FACE_MODE, 1, 0, { GL_BACK } },
	{ GL_FRONT_FACE, 1, 0, { GL_CCW } }, { GL_SHADE_MODEL, 1, 0, { GL_SMOOTH } },
	{ GL_ALPHA_TEST_FUNC, 1, 0, { GL_ALWAYS } }, { GL_ALPHA_TEST_REF, 1, 1, { 0 } },
	{ GL_DEPTH_WRITEMASK, 1, 0, { 1 } }, { GL_COLOR_WRITEMASK, 4, 0, { 1, 1, 1, 1 } },
	{ GL_COLOR_CLEAR_VALUE, 4, 1, { 0, 0, 0, 0 } }, { GL_CURRENT_COLOR, 4, 1, { 1, 1, 1, 1 } },
	{ GL_DEPTH_CLEAR_VALUE, 1, 1, { 1 } }, { GL_LINE_WIDTH, 1, 0, { 1 } },
	{ GL_POLYGON_OFFSET_FACTOR, 1, 0, { 0 } }, { GL_POLYGON_OFFSET_UNITS, 1, 0, { 0 } },
	{ 0x8009 /* GL_BLEND_EQUATION_OES */, 1, 0, { 0x8006 /* GL_FUNC_ADD_OES */ } },
};
static struct value *value_of(GLenum pname)
{
	for (int i = 0; i < (int)(sizeof values / sizeof values[0]); i++) if (values[i].pname == pname) return &values[i];
	return NULL;
}
static void set_value(GLenum pname, GLfloat a, GLfloat b, GLfloat c, GLfloat d)
{
	struct value *v = value_of(pname);
	if (v) { v->v[0] = a; v->v[1] = b; v->v[2] = c; v->v[3] = d; }
}

/* The texture environment, per unit: GL_TEXTURE_ENV's names, and the point sprite's
   GL_COORD_REPLACE_OES. */
static const GLenum env_names[] = {
	GL_TEXTURE_ENV_MODE, GL_COMBINE_RGB, GL_COMBINE_ALPHA, GL_SRC0_RGB, GL_SRC1_RGB, GL_SRC2_RGB,
	GL_SRC0_ALPHA, GL_SRC1_ALPHA, GL_SRC2_ALPHA, GL_OPERAND0_RGB, GL_OPERAND1_RGB, GL_OPERAND2_RGB,
	GL_OPERAND0_ALPHA, GL_OPERAND1_ALPHA, GL_OPERAND2_ALPHA, GL_RGB_SCALE, GL_ALPHA_SCALE, GL_COORD_REPLACE_OES,
};
#define ENV_NAMES (int)(sizeof env_names / sizeof env_names[0])
static const GLfloat env_initial[ENV_NAMES] = {
	GL_MODULATE, GL_MODULATE, GL_MODULATE, GL_TEXTURE, GL_PREVIOUS, GL_CONSTANT,
	GL_TEXTURE, GL_PREVIOUS, GL_CONSTANT, GL_SRC_COLOR, GL_SRC_COLOR, GL_SRC_ALPHA,
	GL_SRC_ALPHA, GL_SRC_ALPHA, GL_SRC_ALPHA, 1, 1, GL_FALSE,
};
static GLfloat env[UNITS][ENV_NAMES], env_color[UNITS][4];
static int env_ready;
static int env_index(GLenum pname)
{
	if (!env_ready) { for (int u = 0; u < UNITS; u++) memcpy(env[u], env_initial, sizeof env_initial); env_ready = 1; }
	for (int i = 0; i < ENV_NAMES; i++) if (env_names[i] == pname) return i;
	return -1;
}
/* `fixed`: the value came through glTexEnvx, where only the scales and the colour are
   16.16 and an enum is passed as itself. */
static void set_env(GLenum pname, const GLfloat *f, int fixed)
{
	if (pname == GL_TEXTURE_ENV_COLOR) {
		for (int i = 0; i < 4; i++) env_color[server_unit][i] = fixed ? f[i] / 65536.0f : f[i];
		return;
	}
	int i = env_index(pname);
	if (i < 0) return;
	env[server_unit][i] = fixed && (pname == GL_RGB_SCALE || pname == GL_ALPHA_SCALE) ? f[0] / 65536.0f : f[0];
}
static int get_env(GLenum pname, GLfloat *out)
{
	if (pname == GL_TEXTURE_ENV_COLOR) { memcpy(out, env_color[server_unit], sizeof env_color[0]); return 4; }
	int i = env_index(pname);
	if (i < 0) return 0;
	out[0] = env[server_unit][i];
	return 1;
}

static void deleted_textures(GLsizei n, const GLuint *names)
{
	for (GLsizei i = 0; names && i < n; i++)
		for (int u = 0; u < UNITS; u++) if (bound_texture[u] == names[i]) bound_texture[u] = 0;
}

/* What glGet* knows beyond the fixed answers in glGetIntegerv: n values in out, as floats,
   and whether they are colours (which glGetIntegerv scales). 0: not known. */
static int get_state(GLenum pname, GLfloat *out, int *is_color)
{
	*is_color = 0;
	switch (pname) {
	case GL_ACTIVE_TEXTURE: out[0] = (GLfloat)(GL_TEXTURE0 + server_unit); return 1;
	case GL_CLIENT_ACTIVE_TEXTURE: out[0] = (GLfloat)(GL_TEXTURE0 + client_unit); return 1;
	case GL_TEXTURE_BINDING_2D: out[0] = (GLfloat)bound_texture[server_unit]; return 1;
	case GL_FRAMEBUFFER_BINDING_OES: out[0] = (GLfloat)bound_framebuffer; return 1;
	case GL_RENDERBUFFER_BINDING_OES: out[0] = (GLfloat)bound_renderbuffer; return 1;
	}
	struct value *v = value_of(pname);
	if (v) { memcpy(out, v->v, sizeof v->v); *is_color = v->is_color; return v->n; }
	if (pname != GL_VERTEX_ARRAY && pname != GL_COLOR_ARRAY && pname != GL_NORMAL_ARRAY && pname != GL_TEXTURE_COORD_ARRAY) {
		for (int i = 0; i < ncaps; i++) if (caps[i].cap == pname) { out[0] = caps[i].on; return 1; }
		if (unit_cap(pname) >= 0) { out[0] = cap_enabled(pname); return 1; }
	}
	switch (pname) {
	case GL_VERTEX_ARRAY: case GL_COLOR_ARRAY: case GL_NORMAL_ARRAY: case GL_TEXTURE_COORD_ARRAY:
	case GL_POINT_SIZE_ARRAY_OES: case GL_MATRIX_INDEX_ARRAY_OES: case GL_WEIGHT_ARRAY_OES:
		out[0] = array_enabled_cap(pname); return 1;
	}
	return 0;
}
/* A colour as an integer: [-1, 1] onto the whole range, as the spec maps it. */
static GLint color_int(GLfloat f)
{
	if (f >= 1.0f) return 0x7fffffff;
	if (f <= -1.0f) return (GLint)0x80000000;
	return (GLint)(f * 2147483647.0f);
}
/*
 * Before a draw: for every enabled array that points at the app's memory (no VBO), send
 * the bytes the draw covers - elements 0 to the highest it touches - as an ARRAY command.
 */
static void send_arrays(size_t vertices, size_t count, GLenum index_type, const void *indices)
{
	if (count && index_extent(&vertices, count, index_type, indices) < 0) return;
	for (int s = 0; s < ARRAYS; s++) {
		struct array *a = &arrays[s];
		if (!a->enabled || a->buffer || !a->ptr || !vertices) continue;
		size_t elem = (size_t)a->size * type_size(a->type);
		size_t stride = a->stride > 0 ? (size_t)a->stride : elem;
		size_t n = stride * (vertices - 1) + elem;
		uint8_t *w = begin(ARRAY_OP, 6 * 4 + 4 + blob_len(a->ptr, n));   /* the blob is padded to 4 */
		if (!w) return;
		w = put_u32(w, (uint32_t)s); w = put_u32(w, (uint32_t)a->size); w = put_u32(w, (uint32_t)a->stride);
		w = put_u32(w, a->type); w = put_u32(w, 0); w = put_u32(w, 0);
		w = put_blob(w, a->ptr, n);
		end(w);
	}
}


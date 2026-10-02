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
	default: return GL_FALSE;
	}
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


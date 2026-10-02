/*
 * The hand-written half of libGLES_CM.so (Docs/pdk.md, "Transformers G1"); gen_gles1.py
 * appends the generated glXxx functions below it. This half keeps the client-side state
 * the stream depends on - which arrays are enabled and where they point, which buffers
 * are bound and what they hold, the unpack alignment - batches the commands, and sends a
 * batch down the control socket the SDL video driver opened (LPDK_GL) when it is full, at
 * glFlush/glFinish, and at SDL_GL_SwapBuffers (the SDL driver calls lunacy_gl_flush).
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <GLES/gl.h>
typedef void *GLeglImageOES;
#include <GLES/glext.h>

#define LPDK_GL 6
#define ARRAY_OP 0xFFFFu
#define BATCH_SIZE (512 * 1024)

extern int LUNACY_control_sock;
extern int LUNACY_Send(int sock, uint32_t type, uint32_t a, const void *payload, uint32_t len);

static uint8_t *batch;
static size_t batch_len;
static int tracing = -1;

static GLenum gl_error = GL_NO_ERROR;
static GLuint next_name = 1;
static GLint viewport[4] = { 0, 0, 1024, 768 };
static GLint scissor[4] = { 0, 0, 1024, 768 };
static GLint unpack_alignment = 4;
static GLuint array_buffer, element_buffer;
static int client_unit;

static void trace(const char *name)
{
	if (tracing < 0) tracing = getenv("LUNACY_GL_TRACE") != NULL;
	if (tracing) fprintf(stderr, "[gl] %s\n", name);
}

static void gen(GLsizei n, GLuint *out) { while (n-- > 0) *out++ = next_name++; }

static int pname_count(GLenum pname, int n, const int *table)
{
	for (int i = 0; i < n; i++) if ((GLenum)table[2 * i] == pname) return table[2 * i + 1];
	return 1;
}

/* ---- the batch ---- */

void lunacy_gl_flush(void)
{
	if (!batch_len) return;
	if (LUNACY_control_sock >= 0) LUNACY_Send(LUNACY_control_sock, LPDK_GL, 0, batch, (uint32_t)batch_len);
	batch_len = 0;
}

static uint8_t *big_start;   /* a command too big for the batch, sent on its own */

static uint8_t *begin(uint32_t op, size_t payload)
{
	uint32_t p = (uint32_t)payload;
	if (!batch) { batch = malloc(BATCH_SIZE); if (!batch) return NULL; }
	if (8 + payload > BATCH_SIZE) {
		lunacy_gl_flush();
		big_start = malloc(8 + payload);
		if (!big_start) return NULL;
		memcpy(big_start, &op, 4); memcpy(big_start + 4, &p, 4);
		return big_start + 8;
	}
	if (batch_len + 8 + payload > BATCH_SIZE) lunacy_gl_flush();
	uint8_t *w = batch + batch_len;
	memcpy(w, &op, 4); memcpy(w + 4, &p, 4);
	return w + 8;
}

static void end(uint8_t *w)
{
	if (big_start) {
		uint32_t payload; memcpy(&payload, big_start + 4, 4);
		if (LUNACY_control_sock >= 0) LUNACY_Send(LUNACY_control_sock, LPDK_GL, 0, big_start, 8 + payload);
		free(big_start); big_start = NULL;
		return;
	}
	batch_len = (size_t)(w - batch);
}

static uint8_t *put_u32(uint8_t *w, uint32_t v) { memcpy(w, &v, 4); return w + 4; }
static uint8_t *put_f32(uint8_t *w, float v) { memcpy(w, &v, 4); return w + 4; }
/* A blob's bytes are padded to a multiple of 4, so every command and every blob the
   shell hands its GL driver starts 4-aligned: the driver's multi-word loads fault otherwise. */
static size_t blob_len(const void *p, size_t n) { return p ? (n + 3) & ~(size_t)3 : 0; }
static uint8_t *put_blob(uint8_t *w, const void *p, size_t n)
{
	uint32_t len = p ? (uint32_t)n : 0;
	memcpy(w, &len, 4); w += 4;
	if (len) { memcpy(w, p, len); w += len; while ((uintptr_t)w & 3) *w++ = 0; }
	return w;
}

/* ---- pixels ---- */

static size_t image_bytes(GLsizei width, GLsizei height, GLenum format, GLenum type)
{
	size_t bpp;
	switch (type) {
	case GL_UNSIGNED_SHORT_5_6_5: case GL_UNSIGNED_SHORT_4_4_4_4: case GL_UNSIGNED_SHORT_5_5_5_1: bpp = 2; break;
	default:
		switch (format) {
		case GL_RGBA: bpp = 4; break; case GL_RGB: bpp = 3; break;
		case GL_LUMINANCE_ALPHA: bpp = 2; break; default: bpp = 1; break;
		}
	}
	size_t row = (size_t)width * bpp;
	size_t a = (size_t)(unpack_alignment > 0 ? unpack_alignment : 1);
	row = (row + a - 1) / a * a;
	return row * (size_t)height;
}

/* ---- buffers: what a VBO holds, for finding the vertices a draw with indices covers ---- */

struct buffer { GLuint name; size_t size; uint8_t *data; struct buffer *next; };
static struct buffer *buffers;

static struct buffer *find_buffer(GLuint name, int make)
{
	for (struct buffer *b = buffers; b; b = b->next) if (b->name == name) return b;
	if (!make) return NULL;
	struct buffer *b = calloc(1, sizeof *b); b->name = name; b->next = buffers; buffers = b; return b;
}
static void bind_buffer(GLenum target, GLuint buffer)
{
	if (target == GL_ARRAY_BUFFER) array_buffer = buffer;
	else if (target == GL_ELEMENT_ARRAY_BUFFER) element_buffer = buffer;
}
static void remember_buffer(GLenum target, size_t size, const void *data)
{
	GLuint name = target == GL_ELEMENT_ARRAY_BUFFER ? element_buffer : array_buffer;
	if (!name) return;
	struct buffer *b = find_buffer(name, 1);
	free(b->data); b->data = calloc(1, size ? size : 1); b->size = size;
	if (data && size) memcpy(b->data, data, size);
}
static void remember_buffer_sub(GLenum target, size_t offset, size_t size, const void *data)
{
	GLuint name = target == GL_ELEMENT_ARRAY_BUFFER ? element_buffer : array_buffer;
	struct buffer *b = name ? find_buffer(name, 0) : NULL;
	if (b && data && offset + size <= b->size) memcpy(b->data + offset, data, size);
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
static size_t type_size(GLenum type)
{
	switch (type) { case GL_BYTE: case GL_UNSIGNED_BYTE: return 1; case GL_SHORT: case GL_UNSIGNED_SHORT: return 2; default: return 4; }
}

/*
 * Before a draw: for every enabled array that points at the app's memory (no VBO), send
 * the bytes the draw covers - elements 0 to the highest it touches - as an ARRAY command.
 * With indices in the app's memory the highest is read from them; with indices in a VBO,
 * from the copy of that buffer kept here.
 */
static void send_arrays(size_t vertices, size_t count, GLenum index_type, const void *indices)
{
	if (count) {
		const void *idx = indices;
		if (element_buffer) {
			struct buffer *b = find_buffer(element_buffer, 0);
			if (!b) return;
			size_t off = (size_t)(uintptr_t)indices;
			if (off + count * type_size(index_type) > b->size) return;
			idx = b->data + off;
		}
		size_t max = 0;
		if (index_type == GL_UNSIGNED_BYTE) { const uint8_t *p = idx; for (size_t i = 0; i < count; i++) if (p[i] > max) max = p[i]; }
		else { const uint16_t *p = idx; for (size_t i = 0; i < count; i++) if (p[i] > max) max = p[i]; }
		vertices = max + 1;
		if (!element_buffer) {
			/* The indices themselves travel with the draw, as a nameless array (slot 255). */
			size_t n = count * type_size(index_type);
			uint8_t *w = begin(ARRAY_OP, 6 * 4 + 4 + n);
			if (!w) return;
			w = put_u32(w, 255); w = put_u32(w, 0); w = put_u32(w, 0); w = put_u32(w, index_type); w = put_u32(w, 0); w = put_u32(w, 0);
			w = put_blob(w, indices, n);
			end(w);
		}
	}
	for (int s = 0; s < ARRAYS; s++) {
		struct array *a = &arrays[s];
		if (!a->enabled || a->buffer || !a->ptr || !vertices) continue;
		size_t elem = (size_t)a->size * type_size(a->type);
		size_t stride = a->stride > 0 ? (size_t)a->stride : elem;
		size_t n = stride * (vertices - 1) + elem;
		uint8_t *w = begin(ARRAY_OP, 6 * 4 + 4 + n);
		if (!w) return;
		w = put_u32(w, (uint32_t)s); w = put_u32(w, (uint32_t)a->size); w = put_u32(w, (uint32_t)a->stride);
		w = put_u32(w, a->type); w = put_u32(w, 0); w = put_u32(w, 0);
		w = put_blob(w, a->ptr, n);
		end(w);
	}
}

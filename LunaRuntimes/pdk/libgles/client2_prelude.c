/*
 * The hand-written half of libGLESv2.so (Docs/pdk.md, "GLES 2"); gen_gles2.py appends the
 * generated glXxx functions below it, after the opcode table. The transport is
 * client_transport.h's; this half keeps what GLES 2 needs on the app's side: the vertex
 * attribute arrays, so a draw can send the elements it covers; the names of shaders and
 * programs (a counter, mapped to the driver's by the shell); and the reading of a query's
 * answer.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <GLES2/gl2.h>
typedef void *GLeglImageOES;
#include <GLES2/gl2ext.h>

#define STREAM_VERSION 2
#include "client_transport.h"

#define ATTRIB_OP 0xFFFCu
#define ATTRIBS 16

/* ---- vertex attribute arrays ---- */

struct attrib { int enabled; GLint size; GLenum type; GLboolean normalized; GLsizei stride; const void *ptr; GLuint buffer; };
static struct attrib attribs[ATTRIBS];

static size_t attrib_type_size(GLenum type)
{
	switch (type) { case GL_BYTE: case GL_UNSIGNED_BYTE: return 1; case GL_SHORT: case GL_UNSIGNED_SHORT: return 2; default: return 4; }
}

/* Before a draw: each enabled attribute array in the app's memory, the elements it covers. */
static void send_attribs(size_t vertices, size_t count, GLenum index_type, const void *indices)
{
	if (count && index_extent(&vertices, count, index_type, indices) < 0) return;
	for (uint32_t i = 0; i < ATTRIBS; i++) {
		struct attrib *a = &attribs[i];
		if (!a->enabled || a->buffer || !a->ptr || !vertices) continue;
		size_t elem = (size_t)a->size * attrib_type_size(a->type);
		size_t stride = a->stride > 0 ? (size_t)a->stride : elem;
		size_t n = stride * (vertices - 1) + elem;
		uint8_t *w = begin(ATTRIB_OP, 5 * 4 + 4 + blob_len(a->ptr, n));   /* the blob is padded to 4 */
		if (!w) return;
		w = put_u32(w, i); w = put_u32(w, (uint32_t)a->size); w = put_u32(w, a->type);
		w = put_u32(w, a->normalized); w = put_u32(w, (uint32_t)a->stride);
		w = put_blob(w, a->ptr, n);
		end(w);
	}
}

/* ---- a query's answer: [u32 return value], then [u32 length][bytes] per output ---- */

struct answer { uint8_t *p; uint32_t len, at; uint32_t ret; };

/* Sends what is batched and waits for the answer to the call just written. */
static int ask(struct answer *a)
{
	memset(a, 0, sizeof *a);
	lunacy_gl_flush();
	if (read_msg(LPDK_GL_RESULT, &a->p, &a->len) < 0 || a->len < 4) { free(a->p); a->p = NULL; return -1; }
	memcpy(&a->ret, a->p, 4); a->at = 4;
	return 0;
}
/* The next output into dst: all of it, or at most cap bytes (a string's buffer size). */
static void take(struct answer *a, void *dst, long cap)
{
	uint32_t n = 0;
	if (a->at + 4 > a->len) return;
	memcpy(&n, a->p + a->at, 4); a->at += 4;
	if (a->at + n > a->len) n = a->len - a->at;
	if (dst && n) {
		uint32_t k = cap >= 0 && (long)n > cap ? (uint32_t)cap : n;
		memcpy(dst, a->p + a->at, k);
		if (cap > 0 && k == (uint32_t)cap) ((char *)dst)[cap - 1] = 0;
	}
	a->at += (n + 3) & ~3u;
}
static void done(struct answer *a) { free(a->p); a->p = NULL; }

/* ---- the calls written by hand ---- */

GL_APICALL GLenum GL_APIENTRY glGetError(void) { GLenum e = gl_error; gl_error = GL_NO_ERROR; return e; }

GL_APICALL const GLubyte *GL_APIENTRY glGetString(GLenum name)
{
	static char vendor[] = "Lunacy", renderer[] = "Lunacy GLES 2 stream", version[] = "OpenGL ES 2.0",
		glsl[] = "OpenGL ES GLSL ES 1.00",
		extensions[] = "GL_OES_texture_npot GL_OES_depth24 GL_OES_packed_depth_stencil GL_OES_rgb8_rgba8 GL_OES_compressed_ETC1_RGB8_texture GL_EXT_texture_format_BGRA8888 GL_OES_standard_derivatives",
		none[] = "";
	switch (name) {
	case GL_VENDOR: return (const GLubyte *)vendor; case GL_RENDERER: return (const GLubyte *)renderer;
	case GL_VERSION: return (const GLubyte *)version; case GL_SHADING_LANGUAGE_VERSION: return (const GLubyte *)glsl;
	case GL_EXTENSIONS: return (const GLubyte *)extensions; default: return (const GLubyte *)none;
	}
}

GL_APICALL void GL_APIENTRY glGenTextures(GLsizei n, GLuint *names) { gen(n, names); }
GL_APICALL void GL_APIENTRY glGenBuffers(GLsizei n, GLuint *names) { gen(n, names); }
GL_APICALL void GL_APIENTRY glGenFramebuffers(GLsizei n, GLuint *names) { gen(n, names); }
GL_APICALL void GL_APIENTRY glGenRenderbuffers(GLsizei n, GLuint *names) { gen(n, names); }

/* Shaders and programs: named here; the shell creates the real one and maps the name. */
GL_APICALL GLuint GL_APIENTRY glCreateShader(GLenum type)
{
	GLuint name = next_name++;
	uint8_t *w = begin(OP_glCreateShader, 8);
	if (!w) return 0;
	w = put_u32(w, type); w = put_u32(w, name); end(w);
	return name;
}
GL_APICALL GLuint GL_APIENTRY glCreateProgram(void)
{
	GLuint name = next_name++;
	uint8_t *w = begin(OP_glCreateProgram, 4);
	if (!w) return 0;
	w = put_u32(w, name); end(w);
	return name;
}

/* The source as one string: the pieces joined, each by its length or to its end. */
GL_APICALL void GL_APIENTRY glShaderSource(GLuint shader, GLsizei count, const char **string, const GLint *length)
{
	size_t total = 0;
	for (GLsizei i = 0; i < count; i++) if (string[i]) total += length && length[i] >= 0 ? (size_t)length[i] : strlen(string[i]);
	char *src = malloc(total + 1);
	if (!src) return;
	size_t at = 0;
	for (GLsizei i = 0; i < count; i++) {
		if (!string[i]) continue;
		size_t n = length && length[i] >= 0 ? (size_t)length[i] : strlen(string[i]);
		memcpy(src + at, string[i], n); at += n;
	}
	src[at] = 0;
	uint8_t *w = begin(OP_glShaderSource, 4 + 4 + blob_len(src, total + 1));
	if (w) { w = put_u32(w, shader); w = put_blob(w, src, total + 1); end(w); }
	free(src);
}

/* No GPU of the TouchPad's shader binaries runs here: refused, as a driver without the format would. */
GL_APICALL void GL_APIENTRY glShaderBinary(GLsizei n, const GLuint *shaders, GLenum binaryformat, const void *binary, GLsizei length)
{
	gl_error = GL_INVALID_ENUM;
}

GL_APICALL void GL_APIENTRY glVertexAttribPointer(GLuint index, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const void *ptr)
{
	trace("glVertexAttribPointer");
	if (index < ATTRIBS) {
		struct attrib *a = &attribs[index];
		a->size = size; a->type = type; a->normalized = normalized; a->stride = stride; a->ptr = ptr; a->buffer = array_buffer;
	}
	/* With a buffer bound the pointer is an offset and is set now; otherwise at each draw. */
	uint8_t *w = begin(OP_glVertexAttribPointer, 6 * 4);
	if (!w) return;
	w = put_u32(w, index); w = put_u32(w, (uint32_t)size); w = put_u32(w, type); w = put_u32(w, normalized);
	w = put_u32(w, (uint32_t)stride); w = put_u32(w, (uint32_t)(uintptr_t)ptr);
	end(w);
}

GL_APICALL void GL_APIENTRY glGetVertexAttribPointerv(GLuint index, GLenum pname, void **pointer)
{
	if (pointer) *pointer = index < ATTRIBS ? (void *)attribs[index].ptr : NULL;
}

GL_APICALL void GL_APIENTRY glReadPixels(GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void *pixels)
{
	trace("glReadPixels");
	read_pixels(x, y, width, height, format, type, pixels);
}

/* The driver's names for the shaders would mean nothing to the app; none listed. */
GL_APICALL void GL_APIENTRY glGetAttachedShaders(GLuint program, GLsizei maxcount, GLsizei *count, GLuint *shaders)
{
	if (count) *count = 0;
}

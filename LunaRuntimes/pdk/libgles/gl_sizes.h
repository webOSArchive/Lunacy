/*
 * Sizes GLES data takes, worked out the same on the app's side (the clients, to send enough
 * bytes) and the shell's (the replay, to check it got them). Needs a GLES header first.
 */
#ifndef LUNACY_GL_SIZES_H
#define LUNACY_GL_SIZES_H

#include <stddef.h>

/* One component of an array of [type]: a vertex array's or an index's. */
static size_t gl_type_size(GLenum type)
{
	switch (type) { case GL_BYTE: case GL_UNSIGNED_BYTE: return 1; case GL_SHORT: case GL_UNSIGNED_SHORT: return 2; default: return 4; }
}

/* A width x height image of [format] and [type], each row padded to [alignment]. */
static size_t gl_image_bytes(GLsizei width, GLsizei height, GLenum format, GLenum type, GLint alignment)
{
	size_t bpp;
	switch (type) {
	case GL_UNSIGNED_SHORT_5_6_5: case GL_UNSIGNED_SHORT_4_4_4_4: case GL_UNSIGNED_SHORT_5_5_5_1: bpp = 2; break;
	default:
		switch (format) {
		case GL_RGBA: case 0x80E1 /* GL_BGRA_EXT */: bpp = 4; break; case GL_RGB: bpp = 3; break;
		case GL_LUMINANCE_ALPHA: bpp = 2; break; default: bpp = 1; break;
		}
	}
	size_t a = (size_t)(alignment > 0 ? alignment : 1);
	return ((size_t)width * bpp + a - 1) / a * a * (size_t)height;
}

#endif

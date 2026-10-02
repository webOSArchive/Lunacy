/*
 * The transport both of Lunacy's GL client libraries share (libGLES_CM.so for GLES 1.1,
 * libGLESv2.so for GLES 2; Docs/pdk.md, "The GL stream"): the batch and the connection to
 * the shell (greeting 'G', blocking, answers coming back on it), the swap with its pacing,
 * glReadPixels, what buffer objects hold, the sizes of uploads. Included by each prelude
 * after its GLES headers and its STREAM_VERSION, which every batch carries so the shell
 * replays it on the right GLES.
 */
#include <unistd.h>
#include <errno.h>

#define LPDK_GL        6   /* to the shell: a batch */
#define LPDK_GL_PIXELS 7   /* from the shell: a glReadPixels' bytes */
#define LPDK_GL_ACK    8   /* from the shell: a swap was shown */
#define ARRAY_OP 0xFFFFu
#define READ_OP  0xFFFEu
#define SWAP_OP  0xFFFDu
#define BATCH_SIZE (512 * 1024)

extern int LUNACY_Connect(const char *hello);
extern int LUNACY_Send(int sock, uint32_t type, uint32_t a, const void *payload, uint32_t len);
#define LPDK_GL_RESULT 9   /* from the shell: a query's answer (GLES 2) */

static int gl_sock = -2;     /* -2: not tried yet; -1: no shell (LUNACY_PDK_NOSHELL), calls go nowhere */
static int swaps_in_flight;
static int sock(void)
{
	if (gl_sock == -2) gl_sock = getenv("LUNACY_PDK_NOSHELL") ? -1 : LUNACY_Connect("G");
	return gl_sock;
}
static int read_all(int fd, void *p, size_t n)
{
	char *c = p;
	while (n) {
		ssize_t r = read(fd, c, n);
		if (r < 0 && errno == EINTR) continue;
		if (r <= 0) return -1;
		c += r; n -= (size_t)r;
	}
	return 0;
}
/* Reads one message from the shell; an acknowledgement is counted and the next one read
   unless it was asked for. Returns the type, the payload in *out (malloc'd), its length in *len. */
static int read_msg(uint32_t want, uint8_t **out, uint32_t *len)
{
	for (;;) {
		uint32_t h[3];
		if (sock() < 0 || read_all(gl_sock, h, sizeof h) < 0) { gl_sock = -1; return -1; }
		uint8_t *p = h[2] ? malloc(h[2]) : NULL;
		if (h[2] && (!p || read_all(gl_sock, p, h[2]) < 0)) { free(p); gl_sock = -1; return -1; }
		if (h[0] == LPDK_GL_ACK && swaps_in_flight > 0) swaps_in_flight--;
		if (h[0] == want) { if (out) *out = p; else free(p); if (len) *len = h[2]; return (int)h[0]; }
		free(p);
	}
}

static uint8_t *batch;
static size_t batch_len;
static int tracing = -1;

static GLenum gl_error = GL_NO_ERROR;
static GLuint next_name = 1;
static GLint viewport[4] = { 0, 0, 1024, 768 };
static GLint scissor[4] = { 0, 0, 1024, 768 };
static GLint unpack_alignment = 4, pack_alignment = 4;
static GLuint array_buffer, element_buffer;

static void trace(const char *name)
{
	if (tracing < 0) tracing = getenv("LUNACY_GL_TRACE") != NULL;
	if (tracing) fprintf(stderr, "[gl] %s\n", name);
}

static void gen(GLsizei n, GLuint *out) { while (n-- > 0) *out++ = next_name++; }

/* ---- the batch ---- */

void lunacy_gl_flush(void)
{
	if (!batch_len) return;
	if (sock() >= 0 && LUNACY_Send(gl_sock, LPDK_GL, STREAM_VERSION, batch, (uint32_t)batch_len) < 0) gl_sock = -1;
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
		if (sock() >= 0 && LUNACY_Send(gl_sock, LPDK_GL, STREAM_VERSION, big_start, 8 + payload) < 0) gl_sock = -1;
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

static size_t image_bytes_aligned(GLsizei width, GLsizei height, GLenum format, GLenum type, GLint alignment);
static size_t image_bytes(GLsizei width, GLsizei height, GLenum format, GLenum type)
{
	return image_bytes_aligned(width, height, format, type, unpack_alignment);
}
static size_t image_bytes_aligned(GLsizei width, GLsizei height, GLenum format, GLenum type, GLint alignment)
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
	size_t row = (size_t)width * bpp;
	size_t a = (size_t)(alignment > 0 ? alignment : 1);
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


static size_t type_size(GLenum type)
{
	switch (type) { case GL_BYTE: case GL_UNSIGNED_BYTE: return 1; case GL_SHORT: case GL_UNSIGNED_SHORT: return 2; default: return 4; }
}

/*
 * Before a draw with indices: how many vertices it covers (the highest index, plus one),
 * read from the app's indices or from the copy kept of an element buffer; indices in the
 * app's memory travel with the draw as a nameless array (slot 255). -1: nothing to draw.
 */
static int index_extent(size_t *vertices, size_t count, GLenum index_type, const void *indices)
{
	const void *idx = indices;
	if (element_buffer) {
		struct buffer *b = find_buffer(element_buffer, 0);
		if (!b) return -1;
		size_t off = (size_t)(uintptr_t)indices;
		if (off + count * type_size(index_type) > b->size) return -1;
		idx = b->data + off;
	}
	size_t max = 0;
	if (index_type == GL_UNSIGNED_BYTE) { const uint8_t *p = idx; for (size_t i = 0; i < count; i++) if (p[i] > max) max = p[i]; }
	else { const uint16_t *p = idx; for (size_t i = 0; i < count; i++) if (p[i] > max) max = p[i]; }
	*vertices = max + 1;
	if (!element_buffer) {
		size_t n = count * type_size(index_type);
		uint8_t *w = begin(ARRAY_OP, 6 * 4 + 4 + blob_len(indices, n));   /* the blob is padded to 4 */
		if (!w) return -1;
		w = put_u32(w, 255); w = put_u32(w, 0); w = put_u32(w, 0); w = put_u32(w, index_type); w = put_u32(w, 0); w = put_u32(w, 0);
		w = put_blob(w, indices, n);
		end(w);
	}
	return 0;
}

/* ---- what the SDL driver calls ---- */

/* The mode is set: GL's default viewport and scissor box are the whole screen. */
void lunacy_gl_mode(int w, int h)
{
	viewport[0] = viewport[1] = 0; viewport[2] = w; viewport[3] = h;
	memcpy(scissor, viewport, sizeof scissor);
}

/* SDL_GL_SwapBuffers: the swap goes in the stream, after the frame's commands, and the app
   waits only while two swaps are unanswered - the shell drawing one, the app the next. */
int lunacy_gl_swap(void)
{
	uint8_t *w = begin(SWAP_OP, 0);
	if (w) end(w);
	lunacy_gl_flush();
	if (sock() < 0) return 1;
	swaps_in_flight++;
	while (swaps_in_flight > 1) if (read_msg(LPDK_GL_ACK, NULL, NULL) < 0) { swaps_in_flight = 0; break; }
	return 1;
}

/* glReadPixels: the shell reads its copy of the app's framebuffer and sends the bytes. */
static void read_pixels(GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void *pixels)
{
	size_t n = image_bytes_aligned(width, height, format, type, pack_alignment);
	if (!pixels || !n) return;
	memset(pixels, 0, n);
	uint8_t *w = begin(READ_OP, 7 * 4);
	if (!w) return;
	w = put_u32(w, (uint32_t)x); w = put_u32(w, (uint32_t)y); w = put_u32(w, (uint32_t)width); w = put_u32(w, (uint32_t)height);
	w = put_u32(w, format); w = put_u32(w, type); w = put_u32(w, (uint32_t)n);
	end(w);
	lunacy_gl_flush();
	uint8_t *p = NULL; uint32_t len = 0;
	if (read_msg(LPDK_GL_PIXELS, &p, &len) < 0) return;
	memcpy(pixels, p, len < n ? len : n);
	free(p);
}

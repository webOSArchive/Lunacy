/*
 * PVRTC 1 (GL_IMG_texture_compression_pvrtc) decoded in the shell, for GPUs that haven't it.
 *
 * The TouchPad's PowerVR SGX read PVRTC in hardware and PDK games ship their textures in it;
 * Mali, Adreno and Tegra refuse the formats (GL_INVALID_ENUM) and the texture stays white.
 * gl_server.c passes the upload through when the driver lists the extension, and otherwise
 * decodes it here to RGBA8 and uploads that.
 *
 * The format: 64-bit blocks, 4 x 4 pixels at 4 bpp or 8 x 4 at 2 bpp, in Morton order. A block
 * holds two low-resolution colours, A and B (one texel per block, bilinearly scaled up over
 * the image, each block's colour sitting at the middle of its block), and a modulation value
 * per pixel that picks a mix of the two. The image wraps, as GL_REPEAT does.
 */
#ifndef PVRTC_H
#define PVRTC_H

#define PVRTC_RGB_4BPP  0x8C00
#define PVRTC_RGB_2BPP  0x8C01
#define PVRTC_RGBA_4BPP 0x8C02
#define PVRTC_RGBA_2BPP 0x8C03

struct pvrtc_rgba { int r, g, b, a; };

static int pvrtc_is_format(unsigned f) { return f >= PVRTC_RGB_4BPP && f <= PVRTC_RGBA_2BPP; }

/* The block's index in the data: its (x, y) in blocks, twiddled (y is the low bit of each pair). */
static unsigned pvrtc_twiddle(unsigned wb, unsigned hb, unsigned x, unsigned y)
{
	unsigned min = wb < hb ? wb : hb, rest = wb < hb ? x : y, t = 0, src = 1, dst = 1;
	int shift = 0;
	while (src < min) {
		if (y & src) t |= dst;
		if (x & src) t |= dst << 1;
		src <<= 1; dst <<= 2; shift++;
	}
	return t | ((rest >> shift) << (2 * shift));
}

static int pvrtc_c3(int v) { return (v << 5) | (v << 2) | (v >> 1); }
static int pvrtc_c5(int v) { return (v << 3) | (v >> 2); }

/* A: bit 15 set = opaque, R5 G5 B4; else A3 R4 G4 B3. Bits 1..14 are the colour; bit 0 is the mode. */
static struct pvrtc_rgba pvrtc_color_a(uint32_t c)
{
	struct pvrtc_rgba o;
	if (c & 0x8000) { o.r = pvrtc_c5((c >> 10) & 31); o.g = pvrtc_c5((c >> 5) & 31); o.b = (((c >> 1) & 15) * 17); o.a = 255; }
	else { o.a = pvrtc_c3((c >> 12) & 7); o.r = ((c >> 8) & 15) * 17; o.g = ((c >> 4) & 15) * 17; o.b = pvrtc_c3((c >> 1) & 7); }
	return o;
}
/* B: bit 31 set = opaque, R5 G5 B5; else A3 R4 G4 B4. */
static struct pvrtc_rgba pvrtc_color_b(uint32_t c)
{
	struct pvrtc_rgba o;
	if (c & 0x80000000u) { o.r = pvrtc_c5((c >> 26) & 31); o.g = pvrtc_c5((c >> 21) & 31); o.b = pvrtc_c5((c >> 16) & 31); o.a = 255; }
	else { o.a = pvrtc_c3((c >> 28) & 7); o.r = ((c >> 24) & 15) * 17; o.g = ((c >> 20) & 15) * 17; o.b = ((c >> 16) & 15) * 17; }
	return o;
}

static uint32_t pvrtc_word(const uint8_t *d)
{
	return (uint32_t)d[0] | ((uint32_t)d[1] << 8) | ((uint32_t)d[2] << 16) | ((uint32_t)d[3] << 24);
}

/*
 * One block's modulation, unpacked into a 2 x 2 blocks' window `mod`/`mode` (stride 16), as
 * a mix weight in eighths. 2 bpp has three layouts: a bit per pixel (colour word's mode bit
 * clear); and with it set, 2 bits for the pixels where (x ^ y) is even, the rest filled in from
 * their neighbours - both ways, or only across, or only down, chosen by two of the data's bits.
 */
static void pvrtc_unpack(uint32_t colour, uint32_t m, int ox, int oy, int bpp2, int mod[8][16], int mode[8][16])
{
	static const int w[4] = { 0, 3, 5, 8 };
	int md = colour & 1;
	if (bpp2) {
		if (md) {
			if (m & 1) { md = (m & (1u << 20)) ? 3 : 2; if (m & (1u << 21)) m |= 1u << 20; else m &= ~(1u << 20); }
			if (m & 1) m |= 2; else m &= ~2u;
			for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) {
				mode[oy + y][ox + x] = md;
				if (((x ^ y) & 1) == 0) { mod[oy + y][ox + x] = w[m & 3]; m >>= 2; }
			}
		} else {
			for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) { mode[oy + y][ox + x] = 0; mod[oy + y][ox + x] = (m & 1) ? 8 : 0; m >>= 1; }
		}
	} else {
		for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
			int v = m & 3;
			/* the mode bit makes the 4 bpp data "punch-through": 0, half, half and transparent, 1 */
			mode[oy + y][ox + x] = md ? (v == 2 ? 2 : 1) : 0;
			mod[oy + y][ox + x] = md ? (v == 0 ? 0 : v == 3 ? 8 : 4) : w[v];
			m >>= 2;
		}
	}
}

/* A decoded w x h level as RGBA8, or NULL. */
static uint8_t *pvrtc_decode(const uint8_t *data, size_t size, int w, int h, int bpp2, int has_alpha)
{
	int xd = bpp2 ? 8 : 4;
	unsigned wb = (unsigned)(w / xd > 2 ? w / xd : 2), hb = (unsigned)(h / 4 > 2 ? h / 4 : 2);
	if (w <= 0 || h <= 0 || size < (size_t)wb * hb * 8) return NULL;
	uint8_t *out = malloc((size_t)w * h * 4);
	if (!out) return NULL;
	for (unsigned by = 0; by < hb; by++) for (unsigned bx = 0; bx < wb; bx++) {
		/* the 2 x 2 blocks whose colours bracket the pixels from this block's middle to the next's */
		unsigned bxs[2] = { bx, (bx + 1) % wb }, bys[2] = { by, (by + 1) % hb };
		struct pvrtc_rgba ca[2][2], cb[2][2];
		int mod[8][16], mode[8][16];
		for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) {
			const uint8_t *blk = data + (size_t)pvrtc_twiddle(wb, hb, bxs[i], bys[j]) * 8;
			uint32_t m = pvrtc_word(blk), col = pvrtc_word(blk + 4);
			ca[j][i] = pvrtc_color_a(col); cb[j][i] = pvrtc_color_b(col);
			pvrtc_unpack(col, m, i * xd, j * 4, bpp2, mod, mode);
		}
		for (int y = 0; y < 4; y++) for (int x = 0; x < xd; x++) {
			/* this pixel is the window's (x + xd/2, y + 2); P = block 0 of the window, S = block 3 */
			int ax = x + xd / 2, ay = y + 2;
			int mv = mod[ay][ax], md = mode[ay][ax];
			if (bpp2 && md && ((ax ^ ay) & 1)) {   /* a pixel with no stored value: its neighbours' mean */
				if (md == 1) mv = (mod[ay - 1][ax] + mod[ay + 1][ax] + mod[ay][ax - 1] + mod[ay][ax + 1] + 2) / 4;
				else if (md == 2) mv = (mod[ay][ax - 1] + mod[ay][ax + 1] + 1) / 2;
				else mv = (mod[ay - 1][ax] + mod[ay + 1][ax] + 1) / 2;
			}
			int punch = !bpp2 && md == 2;
			int wp = (xd - x) * (4 - y), wq = x * (4 - y), wr = (xd - x) * y, ws = x * y, den = xd * 4;
			int ch[2][4];
			for (int k = 0; k < 2; k++) {
				const struct pvrtc_rgba (*c)[2] = k ? cb : ca;
				ch[k][0] = c[0][0].r * wp + c[0][1].r * wq + c[1][0].r * wr + c[1][1].r * ws;
				ch[k][1] = c[0][0].g * wp + c[0][1].g * wq + c[1][0].g * wr + c[1][1].g * ws;
				ch[k][2] = c[0][0].b * wp + c[0][1].b * wq + c[1][0].b * wr + c[1][1].b * ws;
				ch[k][3] = c[0][0].a * wp + c[0][1].a * wq + c[1][0].a * wr + c[1][1].a * ws;
			}
			/* the block's own pixel (x + 0..xd-1 from the block's left); the window's origin is the middle of block P */
			int px = (int)bx * xd + xd / 2 + x, py = (int)by * 4 + 2 + y;
			px %= (int)(wb * xd); py %= (int)(hb * 4);
			if (px >= w || py >= h) continue;
			uint8_t *o = out + ((size_t)py * w + px) * 4;
			int d = den * 8;
			for (int k = 0; k < 3; k++) o[k] = (uint8_t)((ch[0][k] * (8 - mv) + ch[1][k] * mv + d / 2) / d);
			o[3] = !has_alpha ? 255 : punch ? 0 : (uint8_t)((ch[0][3] * (8 - mv) + ch[1][3] * mv + d / 2) / d);
		}
	}
	return out;
}

#endif

/*
 * VFP short vectors on a CPU without them (Docs/pdk.md, "VFP short vectors").
 *
 * VFP before ARMv8 could run one data-processing instruction over a short vector: with
 * FPSCR.LEN set to n-1, `vadd.f32 s8, s16, s24` adds s8..s15 from s16..s23 and s24..s31 when
 * n is 8. Code of the TouchPad's time used it for speed: Gameloft's 4x4 matrix multiply
 * (Asphalt 5, LEN 4) and EA's audio mixer (Dead Space's MixWithGain and five more, LEN 8).
 * The TouchPad's Scorpion has it in hardware, and a 32-bit Linux kernel emulates it on a CPU
 * without it (the HP 10 G2). An ARMv8 CPU running 32-bit code under a 64-bit kernel ignores
 * LEN, so each such instruction computes its first element only: Dead Space mixed one sample
 * in eight (a screech), Asphalt's matrices came out wrong (a white screen). Measured on the
 * Fold3, the Kyocera and the A7 Lite: a LEN-8 vadd gives 11 0 0 0 0 0 0 0 where the HP 10 G2
 * and qemu give 11 22 .. 88.
 *
 * So each run of code that sets LEN (an `orr` of LEN or STRIDE bits into a register, then
 * `vmsr fpscr` from it, up to the next `vmsr fpscr`) is rewritten once, as it is loaded:
 * copied into a trampoline with each vector instruction spelt out as its n scalar ones, in
 * the order the architecture defines, and FPSCR written with LEN clear; branches out of the
 * run and PC-relative operands are made absolute, and the run's entry points branch to the
 * copy. The copy is correct on every CPU, so it runs everywhere: on one that ignores LEN it
 * is what makes the code right, and where a 32-bit kernel emulates short vectors it saves a
 * trap into the kernel on every vector instruction (Asphalt 5 was unplayable on the HP 10 G2
 * for it). A probe at start says in the log which kind of CPU this is. One mechanical rule for every program's code, applied in memory: no file changes.
 * Code is scanned as the glibc loader maps it (at start and after dlopen) and as apkenv's
 * linker finishes a library (its mprotect to read+execute). A run the rewriter can't take is
 * left as it was and logged.
 */
#include <link.h>
#include <sys/mman.h>
#include <stdint.h>

static int vv_on;          /* 1 once the scan is set up */
static int vv_ignored;     /* 1 when this CPU ignores FPSCR.LEN (the log says so) */
static int vv_log = -1;    /* stderr as it was at start (apkenv sends its own to a file) */
static long vv_page;

static void vv_say(const char *fmt, ...)
{
	char b[300]; va_list ap; int n;
	va_start(ap, fmt); n = vsnprintf(b, sizeof b, fmt, ap); va_end(ap);
	if (n > 0 && vv_log >= 0) { if (n > (int)sizeof b - 1) n = sizeof b - 1; (void)!write(vv_log, b, (size_t)n); }
}

/* Whether a LEN-8 vadd adds all eight. */
static int vv_honoured(void)
{
	static float a[8] = { 1, 2, 3, 4, 5, 6, 7, 8 }, b[8] = { 10, 20, 30, 40, 50, 60, 70, 80 };
	float c[8] = { 0 };
	__asm__ volatile(
		"vmrs r3, fpscr\n\t"
		"mov r2, r3\n\t"
		"bic r3, r3, #0x370000\n\t"
		"orr r3, r3, #0x70000\n\t"
		"vmsr fpscr, r3\n\t"
		"vldmia %0, {s8-s15}\n\t"
		"vldmia %1, {s16-s23}\n\t"
		"vadd.f32 s24, s8, s16\n\t"
		"vmsr fpscr, r2\n\t"
		"vstmia %2, {s24-s31}\n\t"
		:: "r"(a), "r"(b), "r"(c)
		: "r2", "r3", "memory", "d4", "d5", "d6", "d7", "d8", "d9", "d10", "d11", "d12", "d13", "d14", "d15");
	return c[7] == 88.0f;
}

/* ---- decoding ---- */

#define VV_VMSR(w) (((w) & 0x0fff0fffu) == 0x0ee10a10u)     /* vmsr fpscr, Rt */

static uint32_t vv_ror(uint32_t v, unsigned r) { r &= 31; return r ? (v >> r) | (v << (32 - r)) : v; }

/* The LEN and STRIDE bits the register holds at a `vmsr fpscr, Rt`, from the instructions
   just before it (an orr of them after a bic or vmrs, or a mov); 0 if it sets none. */
static uint32_t vv_lenbits(const uint32_t *p, const uint32_t *lo, unsigned rt)
{
	uint32_t bits = 0;
	for (int k = 1; k <= 8 && p - k >= lo; k++) {
		uint32_t q = p[-k];
		if ((q & 0x0fff0fffu) == (0x0ef10a10u | 0)) { if (((q >> 12) & 15) == rt) break; continue; } /* vmrs Rt, fpscr */
		if ((q & 0x0e000000u) != 0x02000000u) continue;      /* data processing, immediate */
		if (((q >> 12) & 15) != rt) continue;
		unsigned op = (q >> 21) & 15;
		uint32_t imm = vv_ror(q & 0xff, ((q >> 8) & 15) * 2);
		if (op == 12) { bits |= imm & 0x370000u; if (((q >> 16) & 15) != rt) break; }  /* orr */
		else if (op == 13) { bits |= imm & 0x370000u; break; }                          /* mov */
		else break;                                                                       /* bic, or anything else */
	}
	return bits;
}

/* A VFP register field: d (Vd, D), n (Vn, N) or m (Vm, M). */
static unsigned vv_get(uint32_t q, char f, int dbl)
{
	unsigned v, x;
	if (f == 'd') { v = (q >> 12) & 15; x = (q >> 22) & 1; }
	else if (f == 'n') { v = (q >> 16) & 15; x = (q >> 7) & 1; }
	else { v = q & 15; x = (q >> 5) & 1; }
	return dbl ? (x << 4) | v : (v << 1) | x;
}
static uint32_t vv_set(uint32_t q, char f, int dbl, unsigned r)
{
	unsigned v = dbl ? r & 15 : r >> 1, x = dbl ? r >> 4 : r & 1;
	if (f == 'd') return (q & ~0x0040f000u) | (v << 12) | (x << 22);
	if (f == 'n') return (q & ~0x000f0080u) | (v << 16) | (x << 7);
	return (q & ~0x0000002fu) | v | (x << 5);
}

/* A VFP data-processing instruction under LEN/STRIDE as its scalar ones: how many written to
   o, 0 if it is scalar anyway (copied as it is), -1 if it can't be spelt out. */
static int vv_expand(uint32_t q, uint32_t *o, int len, int stride)
{
	int dbl = (q >> 8) & 1;
	unsigned t = ((q >> 21) & 4) | ((q >> 20) & 3);
	int has_n = 1, has_m = 1;
	if (t == 7) {
		if (!((q >> 6) & 1)) { has_n = 0; has_m = 0; }      /* vmov immediate */
		else {
			unsigned opc2 = (q >> 16) & 15;
			if (opc2 > 1) return 0;                          /* vcvt, vcmp: always scalar */
			has_n = 0;                                       /* vmov, vabs, vneg, vsqrt */
		}
	}
	unsigned B = dbl ? 4 : 8;
	unsigned d = vv_get(q, 'd', dbl), n = has_n ? vv_get(q, 'n', dbl) : 0, m = has_m ? vv_get(q, 'm', dbl) : 0;
	if (dbl && (d > 15 || n > 15 || m > 15)) return d < B ? 0 : -1;
	if (d < B) return 0;                                     /* destination in bank 0: scalar */
	if ((unsigned)(len * stride) > B) return -1;             /* UNPREDICTABLE */
	int scalar_m = m < B;
	for (int i = 0; i < len; i++) {
		unsigned off = (unsigned)(i * stride);
		uint32_t w = vv_set(q, 'd', dbl, (d & ~(B - 1)) + ((d + off) & (B - 1)));
		if (has_n) w = vv_set(w, 'n', dbl, (n & ~(B - 1)) + ((n + off) & (B - 1)));
		if (has_m && !scalar_m) w = vv_set(w, 'm', dbl, (m & ~(B - 1)) + ((m + off) & (B - 1)));
		o[i] = w;
	}
	return len;
}

/* ---- the rewrite ---- */

#define VV_MAX_RUN 2048
#define VV_OUT_MAX (VV_MAX_RUN * 8 + 64)

struct vv_fix { int at, to; uint32_t cond_link; };

/* Writes one word of code, through an anonymous copy of its page so no file-backed page is
   written to (execmem, which the shell's qemu needs anyway, not execmod). */
static uintptr_t vv_ours[64]; static int vv_nours;
static int vv_poke(uint32_t *addr, uint32_t w)
{
	uintptr_t pg = (uintptr_t)addr & ~(uintptr_t)(vv_page - 1);
	int ours = 0;
	for (int i = 0; i < vv_nours; i++) if (vv_ours[i] == pg) ours = 1;
	if (!ours) {
		void *c = mmap(NULL, (size_t)vv_page, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
		if (c == MAP_FAILED) return -1;
		memcpy(c, (void *)pg, (size_t)vv_page);
		if (mremap(c, (size_t)vv_page, (size_t)vv_page, MREMAP_MAYMOVE | MREMAP_FIXED, (void *)pg) == MAP_FAILED) { munmap(c, (size_t)vv_page); return -1; }
		if (vv_nours < 64) vv_ours[vv_nours++] = pg;
	}
	if (syscall(SYS_mprotect, (void *)pg, (size_t)vv_page, PROT_READ | PROT_WRITE)) return -1;
	*addr = w;
	syscall(SYS_mprotect, (void *)pg, (size_t)vv_page, PROT_READ | PROT_EXEC);
	__builtin___clear_cache((char *)addr, (char *)(addr + 1));
	return 0;
}

static int vv_reach(uintptr_t from, uintptr_t to)
{
	long d = (long)to - (long)(from + 8);
	return d >= -(32L << 20) + 64 && d < (32L << 20) - 64;
}
static uint32_t vv_b(uintptr_t from, uintptr_t to, uint32_t cond_link)
{
	return cond_link | 0x0a000000u | ((uint32_t)(((long)to - (long)(from + 8)) >> 2) & 0xffffffu);
}

/* Room for a trampoline within a branch's reach of the code. */
static uint32_t *vv_room(uintptr_t lo, uintptr_t hi, size_t bytes)
{
	size_t sz = (bytes + (size_t)vv_page - 1) & ~(size_t)(vv_page - 1);
	/* Above the code, then below it, a step further each time (the kernel takes a hint only
	   where it is free). */
	for (int i = 0; i < 128; i++) {
		uintptr_t step = (uintptr_t)(1 + i / 2) << 18, hint;
		if (i % 2 == 0) hint = hi + step;
		else if (lo > step + sz + 0x10000) hint = lo - step - sz;
		else continue;
		hint &= ~(uintptr_t)(vv_page - 1);
		void *m = mmap((void *)hint, sz, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
		if (m == MAP_FAILED) continue;
		if (vv_reach(lo, (uintptr_t)m) && vv_reach(hi, (uintptr_t)m) && vv_reach((uintptr_t)m + sz, lo) && vv_reach((uintptr_t)m + sz, hi)) return m;
		munmap(m, sz);
	}
	return NULL;
}

/* The run [p, e] (both vmsr fpscr), with LEN/STRIDE bits `bits`, in the code [lo, hi). */
static int vv_rewrite(uint32_t *p, uint32_t *e, uint32_t bits, uint32_t *lo, uint32_t *hi)
{
	static uint32_t out[VV_OUT_MAX]; static int at[VV_MAX_RUN + 1]; static struct vv_fix fix[VV_MAX_RUN];
	int len = (int)((bits >> 16) & 7) + 1, sb = (int)((bits >> 20) & 3), stride = sb == 0 ? 1 : sb == 3 ? 2 : 0;
	int n = (int)(e - p) + 1, o = 0, nf = 0, vec = 0;
	if (!stride) return -1;
	for (int i = 0; i < n; i++) {
		uint32_t q = p[i], A = (uint32_t)(uintptr_t)(p + i), cond = q & 0xf0000000u;
		at[i] = o;
		if (o > VV_OUT_MAX - 40) return -2;
		if (i == 0) {
			/* The vmsr that sets LEN, with LEN and STRIDE left clear, so the scalar copy is
			   scalar on a CPU that would honour them too; the register keeps its value. */
			uint32_t rt = (q >> 12) & 15;
			out[o++] = cond | 0x03c00837u | (rt << 16) | (rt << 12);   /* bic Rt, Rt, #0x370000 */
			out[o++] = q;
			for (uint32_t b = bits; b; ) {                              /* orr Rt, Rt, #each byte of bits */
				int sh = __builtin_ctz(b) & ~1; uint32_t byte = (b >> sh) & 0xff;
				out[o++] = cond | 0x03800000u | (rt << 16) | (rt << 12) | (((32 - sh) & 31) / 2) << 8 | byte;
				b &= ~(byte << sh);
			}
			continue;
		}
		if (cond == 0xf0000000u) {                           /* unconditional space: pld etc. use no pc here */
			if (((q >> 16) & 15) == 15 && (q & 0x0c000000u) == 0x04000000u) return -3;
			out[o++] = q; continue;
		}
		if ((q & 0x0f000e10u) == 0x0e000a00u) {              /* VFP data processing */
			int k = vv_expand(q, out + o, len, stride);
			if (k < 0) return -4;
			if (k == 0) out[o++] = q; else { o += k; vec++; }
			continue;
		}
		if ((q & 0x0e000000u) == 0x0a000000u) {              /* b, bl */
			uint32_t link = q & 0x01000000u;
			uint32_t t = A + 8 + (uint32_t)(((int32_t)(q << 8)) >> 6);
			if (t >= (uint32_t)(uintptr_t)p && t <= (uint32_t)(uintptr_t)e) {
				fix[nf].at = o; fix[nf].to = (int)((t - (uint32_t)(uintptr_t)p) / 4); fix[nf].cond_link = cond | link; nf++;
				out[o++] = 0;
			} else {
				if (link) out[o++] = cond | 0x028fe008u;       /* add<c> lr, pc, #8 */
				out[o++] = cond | 0x059ff000u;                 /* ldr<c> pc, [pc] */
				out[o++] = 0xea000000u;                        /* b over the word */
				out[o++] = t;
			}
			continue;
		}
		if ((q & 0x0f7f0000u) == 0x051f0000u) {              /* ldr Rt, [pc, #+-imm] */
			uint32_t a = A + 8 + ((q & 0x00800000u) ? (q & 0xfff) : -(q & 0xfff));
			out[o++] = (q & 0xf000f000u) | 0x059f0000u;        /* ldr<c> Rt, [pc] */
			out[o++] = 0xea000000u;
			out[o++] = *(uint32_t *)(uintptr_t)a;
			continue;
		}
		if ((q & 0x0f3f0e00u) == 0x0d1f0a00u) {              /* vldr, pc-relative */
			uint32_t a = A + 8 + ((q & 0x00800000u) ? (q & 0xff) * 4 : -(q & 0xff) * 4);
			int dbl = (q >> 8) & 1;
			out[o++] = (q & ~0x008000ffu) | 0x00800000u;       /* vldr<c> Vd, [pc] */
			out[o++] = dbl ? 0xea000001u : 0xea000000u;
			out[o++] = ((uint32_t *)(uintptr_t)a)[0];
			if (dbl) out[o++] = ((uint32_t *)(uintptr_t)a)[1];
			continue;
		}
		if ((q & 0x0c000000u) == 0x00000000u && (q & 0x0e000090u) != 0x00000090u && (q & 0x0d900000u) != 0x01000000u) {
			/* data processing (not multiply, extra load/store, misc, movw/movt or msr) */
			unsigned op = (q >> 21) & 15, rn = (q >> 16) & 15, rd = (q >> 12) & 15, reg = !(q & 0x02000000u);
			unsigned rm = q & 15, rs = (q >> 8) & 15, shreg = reg && (q & 0x10);
			if (reg && (rm == 15 || (shreg && rs == 15))) return -5;
			if (rn == 15 && op != 13 && op != 15) {
				if (rd == 15) return -6;
				unsigned s = 0;
				while (s == rd || (reg && s == rm) || (shreg && s == rs)) s++;
				out[o++] = 0xe52d0004u | (s << 12);            /* str s, [sp, #-4]! */
				out[o++] = 0xe59f0000u | (s << 12);            /* ldr s, [pc] */
				out[o++] = 0xea000000u;
				out[o++] = A + 8;
				out[o++] = (q & ~0x000f0000u) | (s << 16);      /* the op, with s for pc */
				out[o++] = 0xe49d0004u | (s << 12);            /* ldr s, [sp], #4 */
				continue;
			}
			if (rd == 15 && !(op == 13 && reg && !shreg && (q & 0xff0) == 0)) return -7;  /* a computed jump */
			out[o++] = q; continue;
		}
		if ((q & 0x0c000000u) == 0x04000000u && ((q >> 16) & 15) == 15) return -8;  /* other pc-relative load/store */
		if ((q & 0x0e000090u) == 0x00000090u && ((q >> 16) & 15) == 15) return -9;
		if ((q & 0x0e000000u) == 0x0c000000u && ((q >> 16) & 15) == 15) return -10; /* coprocessor load/store */
		out[o++] = q;
	}
	at[n] = o;
	out[o++] = 0xe51ff004u;                                  /* ldr pc, [pc, #-4] */
	out[o++] = (uint32_t)(uintptr_t)(e + 1);
	if (!vec) return 0;

	/* Entry points: the run's start, and any direct branch into it from the code outside. */
	static int entry[64]; int ne = 0;
	entry[ne++] = 0;
	for (uint32_t *c = lo; c < hi; c++) {
		uint32_t q = *c;
		if ((q & 0x0e000000u) != 0x0a000000u || (q & 0xf0000000u) == 0xf0000000u || (c >= p && c <= e)) continue;
		uint32_t t = (uint32_t)(uintptr_t)c + 8 + (uint32_t)(((int32_t)(q << 8)) >> 6);
		if (t <= (uint32_t)(uintptr_t)p || t > (uint32_t)(uintptr_t)e) continue;
		int idx = (int)((t - (uint32_t)(uintptr_t)p) / 4), dup = 0;
		for (int j = 0; j < ne; j++) if (entry[j] == idx) dup = 1;
		if (!dup) { if (ne == 64) return -12; entry[ne++] = idx; }
	}

	uint32_t *tr = vv_room((uintptr_t)p, (uintptr_t)e, (size_t)o * 4);
	if (!tr) return -13;
	for (int f = 0; f < nf; f++) out[fix[f].at] = vv_b((uintptr_t)(tr + fix[f].at), (uintptr_t)(tr + at[fix[f].to]), fix[f].cond_link);
	memcpy(tr, out, (size_t)o * 4);
	syscall(SYS_mprotect, tr, ((size_t)o * 4 + (size_t)vv_page - 1) & ~(size_t)(vv_page - 1), PROT_READ | PROT_EXEC);
	__builtin___clear_cache((char *)tr, (char *)(tr + o));
	for (int j = 0; j < ne; j++)
		if (vv_poke(p + entry[j], vv_b((uintptr_t)(p + entry[j]), (uintptr_t)(tr + at[entry[j]]), 0xe0000000u))) return -14;
	return vec;
}

static uintptr_t vv_done[256]; static int vv_ndone;
static pthread_mutex_t vv_lock = PTHREAD_MUTEX_INITIALIZER;

/* Every run in the executable code [lo, hi) of `what`. */
static void vv_scan(uint32_t *lo, uint32_t *hi, const char *what)
{
	pthread_mutex_lock(&vv_lock);
	for (int i = 0; i < vv_ndone; i++) if (vv_done[i] == (uintptr_t)lo) { pthread_mutex_unlock(&vv_lock); return; }
	if (vv_ndone < 256) vv_done[vv_ndone++] = (uintptr_t)lo;
	int runs = 0, ops = 0;
	for (uint32_t *p = lo; p < hi; p++) {
		if ((*p & 0xf0000000u) != 0xe0000000u || !VV_VMSR(*p)) continue;
		uint32_t bits = vv_lenbits(p, lo, (*p >> 12) & 15);
		if (!bits) continue;
		uint32_t *e = p + 1;
		while (e < hi && e - p < VV_MAX_RUN && !VV_VMSR(*e)) e++;
		if (e >= hi || !VV_VMSR(*e)) { vv_say("lunacy: vector VFP at %p in %s has no end; left\n", (void *)p, what); continue; }
		int r = vv_rewrite(p, e, bits, lo, hi);
		if (r < 0) vv_say("lunacy: vector VFP at %p in %s (%d instructions) not rewritten (%d)\n", (void *)p, what, (int)(e - p), r);
		else if (r > 0) { runs++; ops += r; }
		p = e;
	}
	if (runs) vv_say("lunacy: %s: %d vector VFP runs rewritten as scalar (%d instructions); this CPU %s\n", what, runs, ops,
		vv_ignored ? "ignores FPSCR.LEN" : "honours FPSCR.LEN (or its kernel emulates it)");
	pthread_mutex_unlock(&vv_lock);
}

static int vv_object(struct dl_phdr_info *info, size_t size, void *data)
{
	(void)size; (void)data;
	for (int i = 0; i < info->dlpi_phnum; i++) {
		const ElfW(Phdr) *ph = &info->dlpi_phdr[i];
		if (ph->p_type != PT_LOAD || !(ph->p_flags & PF_X)) continue;
		uint32_t *lo = (uint32_t *)(info->dlpi_addr + ph->p_vaddr);
		const char *name = info->dlpi_name && *info->dlpi_name ? info->dlpi_name : "the program";
		if (strstr(name, "liblunacy-preload")) continue;
		vv_scan(lo, lo + ph->p_memsz / 4, name);
	}
	return 0;
}

__attribute__((constructor)) static void vv_start(void)
{
	vv_page = sysconf(_SC_PAGESIZE);
	vv_ignored = !vv_honoured();
	vv_log = fcntl(2, F_DUPFD_CLOEXEC, 100);
	vv_on = 1;
	dl_iterate_phdr(vv_object, NULL);
}

/* apkenv's linker makes a library's code read+execute once it has relocated it. */
int mprotect(void *addr, size_t len, int prot)
{
	int r = (int)syscall(SYS_mprotect, addr, len, prot);
	if (r == 0 && vv_on && prot == (PROT_READ | PROT_EXEC)) vv_scan((uint32_t *)addr, (uint32_t *)((char *)addr + len), "a library mapped by its own loader");
	return r;
}

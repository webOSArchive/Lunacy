/*
 * Preloaded into every PDK app (Docs/pdk.md), natively and under qemu. Two things a PDK
 * binary assumes about the machine it runs on, answered for Android:
 *
 * - /proc/self/exe. Natively the kernel exec'd the glibc loader, so the link names
 *   libld-linux.so where on a device it named the app's binary; a game that finds its data
 *   from its own path (Transformers G1: readlink, then the folder it is in) reads the wrong
 *   place and gives up. Answered with the binary the shell started (LUNACY_PDK_EXE).
 *
 * - webOS's own paths. Apps open the system fonts by absolute path
 *   (/usr/share/fonts/PreludeCondensed-Medium.ttf, with SDL_ttf), save into /media/internal,
 *   read /usr/palm and /etc/palm, and OpenAL reads /etc/openal/alsoft.conf. Android has none
 *   of /usr, /media, /var, /tmp, /etc/palm, /etc/openal or /home/root (Quick Office's plugin
 *   works in a folder it makes in /tmp; Adobe Reader's reads /etc/mtab), so a path under them
 *   is looked up in Lunacy's webOS root (LUNACY_PDK_ROOT), which has the same tree. One rule
 *   for every app; nothing else is touched.
 *
 * - Android's own system folders, /system and /vendor, which a TouchPad didn't have. They
 *   are looked up in the webOS root too, where they don't exist either. apkenv's bionic
 *   linker (the Android ports, Docs/pdk.md "EGL and the Android ports") searches
 *   /vendor/lib and /system/lib before its own bionic: on webOS it found nothing there,
 *   on Android it loaded the device's libc.so and quit on its pthread_gettid_np.
 *
 * - /proc/meminfo, at a TouchPad's size. Apps written for 1 GB devices did sums in a signed
 *   32-bit int: Marmalade (Worms) turns MemFree's kB into bytes, and an Android device's
 *   2 GB free came out negative ("Insufficient memory to start application"). A device with
 *   more memory than the TouchPad (940368 kB MemTotal, measured) shows every figure scaled by
 *   the same factor, so the proportions are the device's and no sum passes a TouchPad's.
 *
 * - uname's machine, armv7l as on the TouchPad (measured). A 64-bit kernel says aarch64 or
 *   armv8l; the explusalpha emulators' launcher picks its binary by finding "v7" in it.
 *
 * - exec of another webOS binary, which that launcher does next. The kernel can't run one
 *   here, so it goes through the loader or qemu (webos_exec.h, LUNACY_PDK_REEXEC).
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <pthread.h>
#include <errno.h>
#include <dirent.h>
#include <fcntl.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <time.h>
#include "../execpdk/webos_exec.h"

/* ---- /proc/self/exe ---- */

static int is_self_exe(const char *path)
{
	return path && (strcmp(path, "/proc/self/exe") == 0 ||
		(strncmp(path, "/proc/", 6) == 0 && strstr(path, "/exe") && atoi(path + 6) == getpid()));
}

static ssize_t answer(char *buf, size_t len)
{
	const char *exe = getenv("LUNACY_PDK_EXE");
	size_t n;
	if (!exe || !*exe) return -1;
	n = strlen(exe);
	if (n > len) n = len;
	memcpy(buf, exe, n);
	return (ssize_t)n;
}

/* ---- webOS paths ---- */

static const char *const webos_dirs[] = { "/usr/", "/media/", "/var/", "/tmp/", "/etc/palm/", "/etc/openal/", "/etc/ssl/", "/etc/resolv.conf/", "/etc/mtab/", "/etc/hosts/", "/home/root/", "/system/", "/vendor/", NULL };

static const char *webos_root(size_t *len)
{
	static const char *root; static size_t root_len; static int looked;
	if (!looked) { root = getenv("LUNACY_PDK_ROOT"); root_len = root ? strlen(root) : 0; looked = 1; }
	if (len) *len = root_len;
	return root_len ? root : NULL;
}

/* One entry of LUNACY_PDK_MEDIA ("name=dir:name=dir") from *at, which moves past it; 0 when
   there are no more. An entry with no '=' is skipped. */
struct mapping { const char *name; size_t name_len; const char *dir; size_t dir_len; };
static int next_mapping(const char **at, struct mapping *m)
{
	while (*at && **at) {
		const char *start = *at, *end = start + strcspn(start, ":"), *eq = memchr(start, '=', end - start);
		*at = *end ? end + 1 : end;
		if (!eq) continue;
		m->name = start; m->name_len = (size_t)(eq - start);
		m->dir = eq + 1; m->dir_len = (size_t)(end - eq - 1);
		return 1;
	}
	return 0;
}

/*
 * /media/internal's folders that are Android's own (UserFiles.kt): Documents, Download and the
 * rest, which the shell names in LUNACY_PDK_MEDIA as "name=dir:name=dir". As in the shell, a
 * folder Lunacy's own tree has is that one; one it hasn't is Android's, if there is one. The
 * path with "/media/internal/<name>" replaced by the folder, or NULL.
 */
static const char *media(const char *path, char *buf)
{
	static const char prefix[] = "/media/internal/";
	const char *spec = getenv("LUNACY_PDK_MEDIA"), *rest, *at;
	size_t n, root_len;
	const char *root = webos_root(&root_len);
	struct stat st;
	char own[PATH_MAX];
	if (!spec || !root || strncmp(path, prefix, sizeof prefix - 1) != 0) return NULL;
	rest = path + sizeof prefix - 1;
	n = strcspn(rest, "/");
	if (!n || root_len + sizeof prefix + n >= sizeof own) return NULL;
	snprintf(own, sizeof own, "%s%s%.*s", root, prefix, (int)n, rest);
	if (fstatat(AT_FDCWD, own, &st, AT_SYMLINK_NOFOLLOW) == 0) return NULL;
	/* A webOS path's folder name matches whatever its case, as the TouchPad's FAT-formatted
	   /media/internal did. */
	struct mapping m;
	for (at = spec; next_mapping(&at, &m); ) {
		if (m.name_len == n && strncasecmp(m.name, rest, n) == 0) {
			if (m.dir_len + strlen(rest + n) + 1 > PATH_MAX) return NULL;
			memcpy(buf, m.dir, m.dir_len); strcpy(buf + m.dir_len, rest + n);
			return buf;
		}
	}
	return NULL;
}

/* A real path inside a mapped folder: its /media/internal path in out. */
static int mapped_path(const char *cwd, char *out)
{
	/* A real path compares as Android's filesystem does: exactly. */
	const char *at = getenv("LUNACY_PDK_MEDIA");
	struct mapping m;
	while (next_mapping(&at, &m)) {
		size_t n = m.dir_len;
		if (n && strncmp(cwd, m.dir, n) == 0 && (cwd[n] == 0 || cwd[n] == '/')) {
			snprintf(out, PATH_MAX, "/media/internal/%.*s%s", (int)m.name_len, m.name, cwd + n);
			return 1;
		}
	}
	return 0;
}

/* The working directory, when it is inside a mapped folder. */
static int in_mapped(char *out)
{
	char cwd[PATH_MAX];
	return syscall(SYS_getcwd, cwd, sizeof cwd) > 0 && mapped_path(cwd, out);
}

/*
 * A relative path that means a mapped folder, from where the app has chdir'd: "downloads" in
 * /media/internal, "." or ".." in the folder itself. Its absolute webOS path in abs, or NULL for
 * any other. NES.emu's picker works this way: chdir, scandir("."), then a stat of each name.
 */
static const char *media_relative(const char *path, char *abs)
{
	char cwd[PATH_MAX], probe[PATH_MAX];
	size_t root_len;
	const char *root = webos_root(&root_len);
	if (!root || !getenv("LUNACY_PDK_MEDIA")) return NULL;
	if (in_mapped(cwd)) {
		char *end = cwd + strlen(cwd);
		for (const char *seg = path; *seg; ) {
			size_t n = strcspn(seg, "/");
			if (n == 2 && seg[0] == '.' && seg[1] == '.') { while (end > cwd && *--end != '/') ; *end = 0; }
			else if (n && !(n == 1 && seg[0] == '.')) {
				if ((size_t)(end - cwd) + n + 2 > sizeof cwd) return NULL;
				*end++ = '/'; memcpy(end, seg, n); end += n; *end = 0;
			}
			seg += n; if (*seg) seg++;
		}
		snprintf(abs, PATH_MAX, "%s", cwd);
		return abs;
	}
	if (syscall(SYS_getcwd, cwd, sizeof cwd) <= 0) return NULL;
	if (strncmp(cwd, root, root_len) != 0 || strcmp(cwd + root_len, "/media/internal") != 0) return NULL;
	while (path[0] == '.' && path[1] == '/') path += 2;
	snprintf(abs, PATH_MAX, "/media/internal/%s", path);
	return media(abs, probe) ? abs : NULL;
}

/* The path to use for `path`: in the webOS root if it is a webOS path, else itself. */
static const char *map(const char *path, char *buf)
{
	size_t root_len;
	const char *root = webos_root(&root_len), *m;
	char abs[PATH_MAX];
	if (!path || !root) return path;
	if (path[0] != '/') { if (!path[0] || !media_relative(path, abs)) return path; path = abs; }
	if ((m = media(path, buf))) return m;
	for (int i = 0; webos_dirs[i]; i++) {
		size_t n = strlen(webos_dirs[i]);
		if (strncmp(path, webos_dirs[i], n) == 0 || (strncmp(path, webos_dirs[i], n - 1) == 0 && path[n - 1] == 0)) {
			if (root_len + strlen(path) + 1 > PATH_MAX) return path;
			memcpy(buf, root, root_len); strcpy(buf + root_len, path);
			return buf;
		}
	}
	if (path == abs) { strcpy(buf, abs); return buf; }
	return path;
}

/* glibc keeps these for old binaries but no longer declares them. */
int __xstat(int, const char *, struct stat *); int __lxstat(int, const char *, struct stat *);
int __xstat64(int, const char *, struct stat64 *); int __lxstat64(int, const char *, struct stat64 *);
/* The libc function a wrapper stands in front of. One that isn't there is a broken runtime:
   the process stops saying which, rather than calling a null pointer. */
static void *next_sym(const char *name)
{
	void *p = dlsym(RTLD_NEXT, name);
	if (!p) {
		static const char msg[] = "lunacy preload: no libc function ";
		write(2, msg, sizeof msg - 1); write(2, name, strlen(name)); write(2, "\n", 1);
		abort();
	}
	return p;
}
#define REAL(name) static __typeof__(&name) real_##name; if (!real_##name) real_##name = (__typeof__(&name))next_sym(#name)
#define MAPPED(p) char buf_[PATH_MAX]; const char *mp_ = map(p, buf_)

ssize_t readlink(const char *path, char *buf, size_t len)
{
	if (is_self_exe(path)) { ssize_t r = answer(buf, len); if (r >= 0) return r; }
	MAPPED(path);
	return readlinkat(AT_FDCWD, mp_, buf, len);
}

ssize_t readlinkat(int dirfd, const char *path, char *buf, size_t len)
{
	REAL(readlinkat);
	if (is_self_exe(path)) { ssize_t r = answer(buf, len); if (r >= 0) return r; }
	MAPPED(path);
	return real_readlinkat(dirfd, mp_, buf, len);
}

/* open and friends take a mode only with O_CREAT (or O_TMPFILE). */
#define MODE_ARG(flags) mode_t mode = 0; if ((flags) & O_CREAT) { va_list ap; va_start(ap, flags); mode = va_arg(ap, mode_t); va_end(ap); }

/* ---- /proc/meminfo ---- */

#define TOUCHPAD_MEMTOTAL_KB 940368.0

static int is_meminfo(const char *path) { return path && strcmp(path, "/proc/meminfo") == 0; }

/* A read-only descriptor on a scaled copy of /proc/meminfo, or -1 to let the real one be opened. */
static int meminfo_fd(void)
{
	char in[16384], out[16384];
	char tmpl[PATH_MAX];
	size_t root_len, n = 0, o = 0;
	const char *root = webos_root(&root_len);
	double total = 0, f;
	int fd;
	REAL(open);
	if (!root || root_len + 32 > sizeof tmpl) return -1;
	if ((fd = real_open("/proc/meminfo", O_RDONLY, 0)) < 0) return -1;
	for (ssize_t r; n < sizeof in - 1 && (r = read(fd, in + n, sizeof in - 1 - n)) > 0; ) n += r;
	close(fd);
	in[n] = 0;
	sscanf(in, "MemTotal: %lf", &total);
	if (total <= TOUCHPAD_MEMTOTAL_KB) return -1;
	f = TOUCHPAD_MEMTOTAL_KB / total;
	for (char *line = in, *nl; *line && o < sizeof out - 128; line = nl ? nl + 1 : line + strlen(line)) {
		char name[64]; unsigned long long kb;
		nl = strchr(line, '\n');
		if (nl) *nl = 0;
		int w;
		if (sscanf(line, "%63[^:]: %llu kB", name, &kb) == 2 && strstr(line, " kB"))
			w = snprintf(out + o, sizeof out - o, "%s:%*llu kB\n", name, (int)(24 - strlen(name)), (unsigned long long)(kb * f + 0.5));
		else
			w = snprintf(out + o, sizeof out - o, "%s\n", line);
		/* snprintf answers the length it wanted: a line that didn't fit ends the copy there. */
		if (w < 0 || (size_t)w >= sizeof out - o) { o = sizeof out - 1; break; }
		o += (size_t)w;
		if (!nl) break;
	}
	memcpy(tmpl, root, root_len); strcpy(tmpl + root_len, "/tmp/.lunacy-meminfo-XXXXXX");
	if ((fd = mkstemp(tmpl)) < 0) return -1;
	unlink(tmpl);
	if (write(fd, out, o) != (ssize_t)o || lseek(fd, 0, SEEK_SET) != 0) { close(fd); return -1; }
	return fd;
}

/*
 * File modes, which /media/internal and /media/cryptofs didn't have: every file there reads
 * rwxrwxrwx on a TouchPad, whatever it was created with. NesEm HD makes its settings with
 * open(O_CREAT, S_IWUSR), which Android's file systems honour, so it could never read them back.
 * What an app creates, its owner can read and write; a file an earlier run left so is put right.
 */
#define OWNER_RW(flags) if ((flags) & O_CREAT) mode |= S_IRUSR | S_IWUSR
static int own_again(const char *path)
{
	struct stat st;
	if (errno != EACCES || fstatat(AT_FDCWD, path, &st, 0) != 0 || st.st_uid != geteuid()) { return 0; }
	return fchmodat(AT_FDCWD, path, (st.st_mode & 07777) | S_IRUSR | S_IWUSR, 0) == 0;
}

int open(const char *path, int flags, ...)
{
	REAL(open);
	MODE_ARG(flags); MAPPED(path); OWNER_RW(flags);
	if (is_meminfo(path)) { int fd = meminfo_fd(); if (fd >= 0) return fd; }
	int fd = real_open(mp_, flags, mode);
	return fd < 0 && own_again(mp_) ? real_open(mp_, flags, mode) : fd;
}
int open64(const char *path, int flags, ...)
{
	REAL(open64);
	MODE_ARG(flags); MAPPED(path); OWNER_RW(flags);
	if (is_meminfo(path)) { int fd = meminfo_fd(); if (fd >= 0) return fd; }
	int fd = real_open64(mp_, flags, mode);
	return fd < 0 && own_again(mp_) ? real_open64(mp_, flags, mode) : fd;
}
int openat(int dirfd, const char *path, int flags, ...)
{
	REAL(openat);
	MODE_ARG(flags); MAPPED(path); OWNER_RW(flags);
	return real_openat(dirfd, mp_, flags, mode);
}
int openat64(int dirfd, const char *path, int flags, ...)
{
	REAL(openat64);
	MODE_ARG(flags); MAPPED(path); OWNER_RW(flags);
	return real_openat64(dirfd, mp_, flags, mode);
}
int creat(const char *path, mode_t mode)
{
	REAL(creat);
	MAPPED(path); return real_creat(mp_, mode | S_IRUSR | S_IWUSR);
}
/* ---- stdio buffers on a page, as the TouchPad's glibc had them ----
 *
 * The TouchPad's glibc (2.8) mmap'd each stream's buffer, so it began on a page; Debian's
 * mallocs it. Code that reads a FILE's insides relies on what was there without knowing it:
 * an Android library built against bionic's stdio.h tests ferror() and feof() inline, as
 * bits 0x40 and 0x20 of a 16-bit field at offset 12 - in glibc's FILE, the low half of the
 * buffer's address. On a page those bits are 0; malloc'd they are whatever the address is.
 * Fruit Ninja's zip reader (libzip in libmortargame.so, under apkenv) saw a read error after
 * every good read of its APK, opened nothing and asked for -1 entries (std::bad_alloc;
 * Docs/pdk.md, 6e). So each stream gets a page-aligned buffer of
 * its own, as on the device, before anything reads or writes it; a terminal keeps glibc's
 * own line buffering. The buffer goes when the stream does. */
#include <sys/mman.h>
#define STDIO_BUF 8192
#define STDIO_STREAMS 256
static struct { FILE *f; void *buf; } stdio_bufs[STDIO_STREAMS];
static pthread_mutex_t stdio_lock = PTHREAD_MUTEX_INITIALIZER;

/* Takes a stream's buffer out of the table; the caller unmaps it once glibc is done with it. */
static void *stdio_take(FILE *f)
{
	void *buf = NULL;
	pthread_mutex_lock(&stdio_lock);
	for (int i = 0; i < STDIO_STREAMS; i++) if (stdio_bufs[i].f == f) { buf = stdio_bufs[i].buf; stdio_bufs[i].f = NULL; break; }
	pthread_mutex_unlock(&stdio_lock);
	return buf;
}
static void stdio_release(FILE *f) { void *buf = stdio_take(f); if (buf) munmap(buf, STDIO_BUF); }

static FILE *stdio_paged(FILE *f)
{
	int slot = -1, fd;
	void *buf;
	if (!f) return f;
	fd = fileno(f);
	if (fd >= 0 && isatty(fd)) return f;
	buf = mmap(NULL, STDIO_BUF, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
	if (buf == MAP_FAILED) return f;
	pthread_mutex_lock(&stdio_lock);
	for (int i = 0; i < STDIO_STREAMS && slot < 0; i++) if (!stdio_bufs[i].f) slot = i;
	if (slot >= 0) { stdio_bufs[slot].f = f; stdio_bufs[slot].buf = buf; }
	pthread_mutex_unlock(&stdio_lock);
	if (slot < 0 || setvbuf(f, buf, _IOFBF, STDIO_BUF) != 0) {
		if (slot >= 0) stdio_release(f); else munmap(buf, STDIO_BUF);
	}
	return f;
}

FILE *fopen(const char *path, const char *how)
{
	REAL(fopen);
	MAPPED(path);
	if (is_meminfo(path)) { int fd = meminfo_fd(); if (fd >= 0) return fdopen(fd, "r"); }
	return stdio_paged(real_fopen(mp_, how));
}
FILE *fopen64(const char *path, const char *how)
{
	REAL(fopen64);
	MAPPED(path);
	if (is_meminfo(path)) { int fd = meminfo_fd(); if (fd >= 0) return fdopen(fd, "r"); }
	return stdio_paged(real_fopen64(mp_, how));
}
FILE *freopen(const char *path, const char *how, FILE *f)
{
	REAL(freopen);
	void *old = stdio_take(f);
	MAPPED(path); FILE *r = real_freopen(mp_, how, f);
	if (old) munmap(old, STDIO_BUF);
	return stdio_paged(r);
}
FILE *freopen64(const char *path, const char *how, FILE *f)
{
	REAL(freopen64);
	void *old = stdio_take(f);
	MAPPED(path); FILE *r = real_freopen64(mp_, how, f);
	if (old) munmap(old, STDIO_BUF);
	return stdio_paged(r);
}
FILE *fdopen(int fd, const char *how)
{
	REAL(fdopen);
	return stdio_paged(real_fdopen(fd, how));
}
int fclose(FILE *f)
{
	REAL(fclose);
	/* Out of the table first: once closed, the FILE's address can be another stream's. */
	void *buf = stdio_take(f);
	int r = real_fclose(f);
	if (buf) munmap(buf, STDIO_BUF);
	return r;
}

/* stat: the functions a binary built against today's glibc calls, and the __xstat ones a
   2010 binary calls (glibc still has them for it). */
int stat(const char *path, struct stat *st) { REAL(stat); MAPPED(path); return real_stat(mp_, st); }
int lstat(const char *path, struct stat *st) { REAL(lstat); MAPPED(path); return real_lstat(mp_, st); }
int stat64(const char *path, struct stat64 *st) { REAL(stat64); MAPPED(path); return real_stat64(mp_, st); }
int lstat64(const char *path, struct stat64 *st) { REAL(lstat64); MAPPED(path); return real_lstat64(mp_, st); }
int __xstat(int v, const char *path, struct stat *st) { REAL(__xstat); MAPPED(path); return real___xstat(v, mp_, st); }
int __lxstat(int v, const char *path, struct stat *st) { REAL(__lxstat); MAPPED(path); return real___lxstat(v, mp_, st); }
int __xstat64(int v, const char *path, struct stat64 *st) { REAL(__xstat64); MAPPED(path); return real___xstat64(v, mp_, st); }
int __lxstat64(int v, const char *path, struct stat64 *st) { REAL(__lxstat64); MAPPED(path); return real___lxstat64(v, mp_, st); }

/* These go through the *at calls: glibc makes ARM's legacy calls for them (chmod 15, rmdir
   40...), which Android's app seccomp policy refuses, and the TouchPad's file cache could make
   no file. The *at forms are the calls bionic itself uses. */
int access(const char *path, int how) { MAPPED(path); return faccessat(AT_FDCWD, mp_, how, 0); }

/*
 * /media/internal's own listing names its mapped folders too (downloads, documents, music...),
 * as a TouchPad's listed its own: a file browser (ScummVM's) finds them where the user looks,
 * not only by a path typed in. They come after the real entries, each one Lunacy's own tree
 * hasn't got (that one is already listed). A few listings at once; more are left as they are.
 */
#define MEDIA_LISTINGS 8
static struct { DIR *dir; const char *next; struct dirent ent; struct dirent64 ent64; } listings[MEDIA_LISTINGS];
static pthread_mutex_t listings_lock = PTHREAD_MUTEX_INITIALIZER;

static int is_media_internal(const char *path)
{
	static const char m[] = "/media/internal";
	char cwd[PATH_MAX];
	if (path && path[0] == '.' && (path[1] == 0 || (path[1] == '/' && path[2] == 0)))
		return getcwd(cwd, sizeof cwd) && strcmp(cwd, m) == 0;
	if (!path || strncmp(path, m, sizeof m - 1) != 0) return 0;
	for (path += sizeof m - 1; *path == '/'; path++) ;
	return *path == 0;
}

/* The next mapped folder from *at, a place in LUNACY_PDK_MEDIA: its name in name[NAME_MAX + 1]. */
static int mapped_after(const char **at, char *name)
{
	size_t root_len;
	const char *root = webos_root(&root_len);
	struct mapping m;
	while (next_mapping(at, &m)) {
		size_t n = m.name_len;
		char own[PATH_MAX];
		struct stat st;
		if (n && n <= NAME_MAX && root && root_len + n + 17 < sizeof own) {
			snprintf(own, sizeof own, "%s/media/internal/%.*s", root, (int)n, m.name);
			if (fstatat(AT_FDCWD, own, &st, AT_SYMLINK_NOFOLLOW) != 0) { memcpy(name, m.name, n); name[n] = 0; return 1; }
		}
	}
	return 0;
}

/* The next mapped folder for this listing, or NULL: its name in name[NAME_MAX + 1]. */
static int next_mapped(DIR *d, char *name)
{
	int found = 0;
	pthread_mutex_lock(&listings_lock);
	for (int i = 0; i < MEDIA_LISTINGS; i++) {
		if (listings[i].dir != d) continue;
		found = mapped_after(&listings[i].next, name);
		break;
	}
	pthread_mutex_unlock(&listings_lock);
	return found;
}

static int listing_slot(DIR *d)
{
	for (int i = 0; i < MEDIA_LISTINGS; i++) if (listings[i].dir == d) return i;
	return -1;
}

DIR *opendir(const char *path)
{
	REAL(opendir); MAPPED(path);
	DIR *d = real_opendir(mp_);
	const char *spec = getenv("LUNACY_PDK_MEDIA");
	if (d && spec && is_media_internal(path)) {
		pthread_mutex_lock(&listings_lock);
		int i = listing_slot(NULL);
		if (i >= 0) { listings[i].dir = d; listings[i].next = spec; }
		pthread_mutex_unlock(&listings_lock);
	}
	return d;
}

struct dirent *readdir(DIR *d)
{
	REAL(readdir);
	int e = errno;
	struct dirent *r = real_readdir(d);
	char name[NAME_MAX + 1];
	if (r || errno != e || !next_mapped(d, name)) return r;
	pthread_mutex_lock(&listings_lock);
	int i = listing_slot(d);
	struct dirent *ent = i >= 0 ? &listings[i].ent : NULL;
	if (ent) { memset(ent, 0, sizeof *ent); ent->d_ino = 1; ent->d_type = DT_DIR; ent->d_reclen = sizeof *ent; strcpy(ent->d_name, name); }
	pthread_mutex_unlock(&listings_lock);
	return ent;
}

struct dirent64 *readdir64(DIR *d)
{
	REAL(readdir64);
	int e = errno;
	struct dirent64 *r = real_readdir64(d);
	char name[NAME_MAX + 1];
	if (r || errno != e || !next_mapped(d, name)) return r;
	pthread_mutex_lock(&listings_lock);
	int i = listing_slot(d);
	struct dirent64 *ent = i >= 0 ? &listings[i].ent64 : NULL;
	if (ent) { memset(ent, 0, sizeof *ent); ent->d_ino = 1; ent->d_type = DT_DIR; ent->d_reclen = sizeof *ent; strcpy(ent->d_name, name); }
	pthread_mutex_unlock(&listings_lock);
	return ent;
}

void rewinddir(DIR *d)
{
	REAL(rewinddir);
	real_rewinddir(d);
	pthread_mutex_lock(&listings_lock);
	int i = listing_slot(d);
	if (i >= 0) listings[i].next = getenv("LUNACY_PDK_MEDIA");
	pthread_mutex_unlock(&listings_lock);
}

int closedir(DIR *d)
{
	REAL(closedir);
	pthread_mutex_lock(&listings_lock);
	int i = listing_slot(d);
	if (i >= 0) listings[i].dir = NULL;
	pthread_mutex_unlock(&listings_lock);
	return real_closedir(d);
}

/*
 * dlopen by a webOS path: libpurple loads its protocol plugins from /usr/lib/purple-2 (through
 * glib's g_module_open), which only the webOS root has. A bare name ("libfoo.so") is the
 * loader's own search and is left alone.
 */
#include "vfpvec.h"
void *dlopen(const char *path, int flags)
{
	REAL(dlopen); MAPPED(path);
	void *h = real_dlopen(mp_, flags);
	if (h && vv_on) dl_iterate_phdr(vv_object, NULL);  /* vfpvec.h: the new library's code */
	return h;
}
/* The working directory as webOS names it: an app showing a file browser shows /media/internal. */
char *getcwd(char *buf, size_t size)
{
	REAL(getcwd);
	size_t root_len;
	const char *root = webos_root(&root_len);
	char *r = real_getcwd(buf, size), mapped[PATH_MAX];
	if (r && in_mapped(mapped)) {
		size_t need = strlen(mapped) + 1;
		/* With no buffer given, glibc allocated just the real path's length, which the webOS
		   one may be longer than; a caller's buffer is its size. */
		size_t room = buf ? size : (size ? size : strlen(r) + 1);
		if (need > room) {
			if (buf || size) { if (!buf) free(r); errno = ERANGE; return NULL; }
			char *bigger = realloc(r, need);
			if (!bigger) { free(r); errno = ENOMEM; return NULL; }
			r = bigger;
		}
		strcpy(r, mapped);
		return r;
	}
	if (r && root && strncmp(r, root, root_len) == 0 && r[root_len] == '/') memmove(r, r + root_len, strlen(r + root_len) + 1);
	return r;
}

/* realpath walks the path inside glibc, past the mapping: a webOS path goes in as the real one,
   and the answer comes back in webOS's terms, as getcwd's does. */
char *realpath(const char *path, char *resolved)
{
	REAL(realpath); MAPPED(path);
	char out[PATH_MAX], webos[PATH_MAX];
	size_t root_len;
	const char *root = webos_root(&root_len);
	if (!real_realpath(mp_, out)) return NULL;
	if (mapped_path(out, webos)) strcpy(out, webos);
	else if (root && strncmp(out, root, root_len) == 0 && out[root_len] == '/') memmove(out, out + root_len, strlen(out + root_len) + 1);
	return resolved ? strcpy(resolved, out) : strdup(out);
}

/* scandir opens and reads its folder inside glibc, past opendir and readdir above, so
   /media/internal's mapped folders join its list here, through the caller's filter and sort. */
#define SCAN_MAPPED(T) \
	const char *at = getenv("LUNACY_PDK_MEDIA"); \
	char name[NAME_MAX + 1]; \
	int root_ = n >= 0 && is_media_internal(dir); \
	while (root_ && mapped_after(&at, name)) { \
		T *e = calloc(1, sizeof *e), **more; \
		if (!e) break; \
		e->d_ino = 1; e->d_type = DT_DIR; e->d_reclen = sizeof *e; strcpy(e->d_name, name); \
		if (filter && !filter(e)) { free(e); continue; } \
		more = realloc(*list, (n + 1) * sizeof *more); \
		if (!more) { free(e); break; } \
		*list = more; more[n++] = e; \
	} \
	if (n > 0 && compare && root_) qsort(*list, n, sizeof **list, (int (*)(const void *, const void *))compare); \
	return n
int scandir(const char *dir, struct dirent ***list, int (*filter)(const struct dirent *), int (*compare)(const struct dirent **, const struct dirent **))
{
	REAL(scandir); MAPPED(dir);
	int n = real_scandir(mp_, list, filter, compare);
	SCAN_MAPPED(struct dirent);
}
int scandir64(const char *dir, struct dirent64 ***list, int (*filter)(const struct dirent64 *), int (*compare)(const struct dirent64 **, const struct dirent64 **))
{
	REAL(scandir64); MAPPED(dir);
	int n = real_scandir64(mp_, list, filter, compare);
	SCAN_MAPPED(struct dirent64);
}

int mkdir(const char *path, mode_t mode) { MAPPED(path); return mkdirat(AT_FDCWD, mp_, mode); }
int rmdir(const char *path) { MAPPED(path); return unlinkat(AT_FDCWD, mp_, AT_REMOVEDIR); }
int unlink(const char *path) { MAPPED(path); return unlinkat(AT_FDCWD, mp_, 0); }
int chmod(const char *path, mode_t mode) { MAPPED(path); return fchmodat(AT_FDCWD, mp_, mode, 0); }
int chown(const char *path, uid_t u, gid_t g) { MAPPED(path); return fchownat(AT_FDCWD, mp_, u, g, 0); }
int lchown(const char *path, uid_t u, gid_t g) { MAPPED(path); return fchownat(AT_FDCWD, mp_, u, g, AT_SYMLINK_NOFOLLOW); }
int link(const char *from, const char *to) { char b1[PATH_MAX], b2[PATH_MAX]; return linkat(AT_FDCWD, map(from, b1), AT_FDCWD, map(to, b2), 0); }
int symlink(const char *target, const char *path) { MAPPED(path); return symlinkat(target, AT_FDCWD, mp_); }
int remove(const char *path) { REAL(remove); MAPPED(path); return real_remove(mp_); }
int chdir(const char *path) { REAL(chdir); MAPPED(path); return real_chdir(mp_); }
/* truncate and ftruncate: glibc makes ARM's legacy calls (92, 93), which Android's app seccomp
   policy refuses; the 64-bit forms (193, 194) are the ones bionic uses. A 2D PDK game's
   framebuffer is sized with ftruncate (SDL's video driver), and Commander Keen got ENOSYS on
   Android 10 and later. */
int truncate(const char *path, off_t len) { MAPPED(path); return truncate64(mp_, (off64_t)len); }
int ftruncate(int fd, off_t len) { return ftruncate64(fd, (off64_t)len); }
int rename(const char *from, const char *to)
{
	char b1[PATH_MAX], b2[PATH_MAX];
	return renameat(AT_FDCWD, map(from, b1), AT_FDCWD, map(to, b2));
}

/* ---- nothrow new, as GCC 4.3's libstdc++ had it ----
   The TouchPad's libstdc++ (GCC 4.3) made `new (std::nothrow)` call malloc; today's makes it
   call the throwing `operator new` inside a try. A library that defines the throwing one by
   calling the nothrow one - Palm's libmojocore does, under every native system service -
   then recurses until the stack runs out. Answered as the device's library did: malloc, with
   the new-handler given its chance, and NULL when nothing is left. The throwing forms are
   left to libstdc++ (or to whichever library replaced them). */
typedef void (*new_handler_fn)(void);
extern new_handler_fn _ZSt15get_new_handlerv(void) __attribute__((weak));
static void *nothrow_new(size_t n)
{
	if (n == 0) n = 1;
	for (;;) {
		void *p = malloc(n);
		if (p) return p;
		new_handler_fn h = _ZSt15get_new_handlerv ? _ZSt15get_new_handlerv() : NULL;
		if (!h) return NULL;
		h();   /* a handler that can't free memory throws or aborts, as the device's did */
	}
}
void *_ZnwjRKSt9nothrow_t(size_t n, const void *nt) { return nothrow_new(n); }
void *_ZnajRKSt9nothrow_t(size_t n, const void *nt) { return nothrow_new(n); }

/* ---- pthread_atfork ----
   glibc has kept pthread_atfork out of its shared libraries since 2.28 (it lives in the static
   libc_nonshared.a, linked into each program), so a 2011 library that imports it from the
   shared one - the TouchPad's libjemalloc_mt.so, under its mail services - can't be bound.
   It is what the static stub is: glibc's exported __register_atfork, with no DSO handle (the
   handlers stay registered for the life of the process). */
extern int __register_atfork(void (*prepare)(void), void (*parent)(void), void (*child)(void), void *dso);
int pthread_atfork(void (*prepare)(void), void (*parent)(void), void (*child)(void))
{
	return __register_atfork(prepare, parent, child, NULL);
}

/* ---- syslog ----
   webOS's services logged through syslog (PmLogLib, libmojocore), to syslogd's /dev/log, and
   that is where palm-log read them. Android has no /dev/log for an app, so the messages went
   nowhere. They go to stderr instead, as "<priority>ident: message" lines, which the shell
   reads and puts in the webOS root's /var/log/messages (SysLog.kt) and Android's log. */
#include <syslog.h>
static char syslog_ident[64];
void openlog(const char *ident, int option, int facility)
{
	snprintf(syslog_ident, sizeof syslog_ident, "%s", ident ? ident : "");
}
void closelog(void) {}
void vsyslog(int priority, const char *format, va_list ap)
{
	char msg[2048];
	int saved = errno;
	if (!syslog_ident[0]) {
		const char *exe = getenv("LUNACY_PDK_EXE");
		const char *slash = exe ? strrchr(exe, '/') : NULL;
		snprintf(syslog_ident, sizeof syslog_ident, "%s", slash ? slash + 1 : exe ? exe : "native");
	}
	errno = saved;   /* %m names the caller's errno */
	int n = vsnprintf(msg, sizeof msg, format, ap);
	if (n < 0) return;
	for (char *p = msg; *p; p++) if (*p == '\n') *p = ' ';
	dprintf(2, "<%d>%s: %s\n", priority & 7, syslog_ident, msg);
}
void syslog(int priority, const char *format, ...)
{
	va_list ap; va_start(ap, format); vsyslog(priority, format, ap); va_end(ap);
}
void __syslog_chk(int priority, int flag, const char *format, ...)
{
	va_list ap; va_start(ap, format); vsyslog(priority, format, ap); va_end(ap);
}
void __vsyslog_chk(int priority, int flag, const char *format, va_list ap) { vsyslog(priority, format, ap); }

/* ---- socket calls Android's seccomp policy refuses ----
   glibc makes send(), recv() and accept() with ARM's own system calls for them (289, 291,
   285), which bionic never uses and Android's app policy doesn't list: under libenosys they
   came back ENOSYS, so c-ares couldn't send a DNS query and the mail services found no host.
   Each is the general call it is a case of, which the policy allows. Only callers outside
   glibc see these, which is where the TouchPad's libraries call from. */
#include <sys/socket.h>
ssize_t send(int fd, const void *buf, size_t len, int flags) { return sendto(fd, buf, len, flags, NULL, 0); }
ssize_t recv(int fd, void *buf, size_t len, int flags) { return recvfrom(fd, buf, len, flags, NULL, NULL); }
int accept(int fd, struct sockaddr *addr, socklen_t *len) { return accept4(fd, addr, len, 0); }

/* ---- more webOS paths: extended attributes, file systems, times, tree walks ----
   The TouchPad's file cache keeps each object's name and type in extended attributes
   (setxattr, getxattr), sizes its cache with statvfs and walks it with nftw; GIO and
   boost::filesystem do the rest. */
#include <ftw.h>
#include <sys/statfs.h>
#include <sys/statvfs.h>
#include <sys/time.h>
#include <sys/xattr.h>
#include <utime.h>
ssize_t getxattr(const char *p, const char *n, void *v, size_t s) { REAL(getxattr); MAPPED(p); return real_getxattr(mp_, n, v, s); }
ssize_t lgetxattr(const char *p, const char *n, void *v, size_t s) { REAL(lgetxattr); MAPPED(p); return real_lgetxattr(mp_, n, v, s); }
int setxattr(const char *p, const char *n, const void *v, size_t s, int f) { REAL(setxattr); MAPPED(p); return real_setxattr(mp_, n, v, s, f); }
int lsetxattr(const char *p, const char *n, const void *v, size_t s, int f) { REAL(lsetxattr); MAPPED(p); return real_lsetxattr(mp_, n, v, s, f); }
ssize_t listxattr(const char *p, char *l, size_t s) { REAL(listxattr); MAPPED(p); return real_listxattr(mp_, l, s); }
ssize_t llistxattr(const char *p, char *l, size_t s) { REAL(llistxattr); MAPPED(p); return real_llistxattr(mp_, l, s); }
int removexattr(const char *p, const char *n) { REAL(removexattr); MAPPED(p); return real_removexattr(mp_, n); }
int lremovexattr(const char *p, const char *n) { REAL(lremovexattr); MAPPED(p); return real_lremovexattr(mp_, n); }
int statvfs(const char *p, struct statvfs *b) { REAL(statvfs); MAPPED(p); return real_statvfs(mp_, b); }
int statvfs64(const char *p, struct statvfs64 *b) { REAL(statvfs64); MAPPED(p); return real_statvfs64(mp_, b); }
int statfs(const char *p, struct statfs *b) { REAL(statfs); MAPPED(p); return real_statfs(mp_, b); }
int statfs64(const char *p, struct statfs64 *b) { REAL(statfs64); MAPPED(p); return real_statfs64(mp_, b); }
int utime(const char *p, const struct utimbuf *t)
{
	MAPPED(p);
	struct timespec ts[2];
	if (!t) return utimensat(AT_FDCWD, mp_, NULL, 0);
	ts[0].tv_sec = t->actime; ts[0].tv_nsec = 0; ts[1].tv_sec = t->modtime; ts[1].tv_nsec = 0;
	return utimensat(AT_FDCWD, mp_, ts, 0);
}
int utimes(const char *p, const struct timeval t[2])
{
	MAPPED(p);
	struct timespec ts[2];
	if (!t) return utimensat(AT_FDCWD, mp_, NULL, 0);
	for (int i = 0; i < 2; i++) { ts[i].tv_sec = t[i].tv_sec; ts[i].tv_nsec = t[i].tv_usec * 1000; }
	return utimensat(AT_FDCWD, mp_, ts, 0);
}

/* nftw walks the real tree; the callback is given the paths under the webOS name it asked
   for, as a device gave them. One walk at a time per thread. */
static __thread int (*nftw_fn)(const char *, const struct stat *, int, struct FTW *);
static __thread const char *nftw_dir;
static __thread size_t nftw_real;
static int nftw_back(const char *path, const struct stat *st, int flag, struct FTW *f)
{
	char name[PATH_MAX];
	snprintf(name, sizeof name, "%s%s", nftw_dir, path + nftw_real);
	return nftw_fn(name, st, flag, f);
}
int nftw(const char *dir, int (*fn)(const char *, const struct stat *, int, struct FTW *), int fds, int flags)
{
	REAL(nftw);
	MAPPED(dir);
	if (mp_ == dir) return real_nftw(dir, fn, fds, flags);
	int (*saved_fn)(const char *, const struct stat *, int, struct FTW *) = nftw_fn;
	const char *saved_dir = nftw_dir;
	size_t saved_real = nftw_real;
	nftw_fn = fn; nftw_dir = dir; nftw_real = strlen(mp_);
	int r = real_nftw(mp_, nftw_back, fds, flags);
	nftw_fn = saved_fn; nftw_dir = saved_dir; nftw_real = saved_real;
	return r;
}

/* ---- commands: popen and system ----
   A plugin runs shell commands on webOS paths (Adobe Reader's: `ls -l "<document>" | awk … |
   md5sum`, to name a document's page cache). On a device /bin/sh was busybox in the same
   filesystem. Here the command runs in the webOS root's busybox, a bionic program the preload
   doesn't reach, so its webOS paths are pointed into the root first: the rule package scripts
   get (WebosRoot.mapPaths), a path under one of webOS's own top-level folders, starting a word,
   a quoted string or an assignment, with /media/internal's Android folders as above. glibc's
   popen and system exec /bin/sh from inside glibc, where a preload can't reach, so these
   replace them; the shell is started with posix_spawn, whose child makes no call Android's
   seccomp policy refuses before the exec. The environment loses what was for glibc's loader. */
#include <spawn.h>
#include <sys/wait.h>
extern char **environ;

static const char *const command_dirs[] = { "usr", "media", "bin", "sbin", "var", "etc", "tmp", "home", "opt", "lib", NULL };

/* Appends n bytes to a growing string. */
static int put(char **out, size_t *len, size_t *cap, const char *p, size_t n)
{
	if (*len + n + 1 > *cap) {
		size_t c = (*len + n + 1) * 2;
		char *o = realloc(*out, c);
		if (!o) return -1;
		*out = o; *cap = c;
	}
	memcpy(*out + *len, p, n); *len += n; (*out)[*len] = 0;
	return 0;
}

static char *map_command(const char *cmd)
{
	size_t root_len, len = 0, cap = 0;
	const char *root = webos_root(&root_len);
	char *out = NULL;
	for (size_t i = 0; cmd[i]; ) {
		if (cmd[i] == '/' && (i == 0 || strchr(" \t\n'\"=(;|&<>`", cmd[i - 1]))) {
			size_t seg = strcspn(cmd + i + 1, "/ \t\n'\";|&<>)`");
			for (int d = 0; command_dirs[d]; d++) {
				if (strlen(command_dirs[d]) != seg || strncmp(cmd + i + 1, command_dirs[d], seg) != 0) continue;
				/* An Android folder replaces "/media/internal/<name>"; the root goes before anything else. */
				char word[PATH_MAX], buf[PATH_MAX];
				size_t n = strcspn(cmd + i, " \t\n'\";|&<>)`");
				const char *m = NULL;
				if (n < sizeof word) { memcpy(word, cmd + i, n); word[n] = 0; m = media(word, buf); }
				if (m) {
					size_t head = 16 + strcspn(word + 16, "/");   /* "/media/internal/<name>" */
					if (put(&out, &len, &cap, m, strlen(m) - strlen(word + head)) < 0) { free(out); return NULL; }
					i += head;
				} else if (put(&out, &len, &cap, root, root_len) < 0) { free(out); return NULL; }
				break;
			}
		}
		if (put(&out, &len, &cap, cmd + i, 1) < 0) { free(out); return NULL; }
		i++;
	}
	return out ? out : strdup("");
}

/* The shell's environment: the process's, less the loader's variables, with webOS's PATH. */
static char **command_env(const char *root)
{
	size_t n = 0, k = 0;
	char **env;
	while (environ[n]) n++;
	env = calloc(n + 2, sizeof *env);
	if (!env) return NULL;
	for (size_t i = 0; i < n; i++) {
		if (!strncmp(environ[i], "LD_PRELOAD=", 11) || !strncmp(environ[i], "LD_LIBRARY_PATH=", 16) ||
		    !strncmp(environ[i], "LUNACY_PDK_PRELOAD=", 19) || !strncmp(environ[i], "PATH=", 5)) continue;
		env[k++] = environ[i];
	}
	if (asprintf(&env[k], "PATH=%s/usr/sbin:%s/usr/bin:%s/sbin:%s/bin:/system/bin", root, root, root, root) < 0) { free(env); return NULL; }
	return env;
}

/* Starts the command in the root's shell; fd, if not -1, becomes its stdin (which = 0) or stdout (1). */
static pid_t spawn_command(const char *cmd, int fd, int which, int other)
{
	size_t root_len;
	const char *root = webos_root(&root_len);
	char sh[PATH_MAX];
	char *mapped, **env;
	pid_t pid = -1;
	posix_spawn_file_actions_t fa;
	snprintf(sh, sizeof sh, "%s/bin/sh", root);
	if (!(mapped = map_command(cmd))) return -1;
	if (!(env = command_env(root))) { free(mapped); return -1; }
	char *argv[] = { "sh", "-c", mapped, NULL };
	posix_spawn_file_actions_init(&fa);
	if (fd >= 0) {
		posix_spawn_file_actions_adddup2(&fa, fd, which);
		posix_spawn_file_actions_addclose(&fa, fd);
		if (other >= 0) posix_spawn_file_actions_addclose(&fa, other);
	}
	int e = posix_spawn(&pid, sh, &fa, NULL, argv, env);
	posix_spawn_file_actions_destroy(&fa);
	{ size_t i = 0; while (env[i]) i++; free(env[i - 1]); }   /* the PATH it made */
	free(env); free(mapped);
	if (e) { errno = e; return -1; }
	return pid;
}

static int have_root_shell(void)
{
	size_t root_len;
	const char *root = webos_root(&root_len);
	char sh[PATH_MAX];
	if (!root) return 0;
	snprintf(sh, sizeof sh, "%s/bin/sh", root);
	return faccessat(AT_FDCWD, sh, X_OK, 0) == 0;
}

static pthread_mutex_t popen_lock = PTHREAD_MUTEX_INITIALIZER;
static struct popened { FILE *f; pid_t pid; struct popened *next; } *popened;

FILE *popen(const char *cmd, const char *type)
{
	REAL(popen);
	int fds[2], reading;
	pid_t pid;
	FILE *f;
	struct popened *p;
	if (!cmd || !type || !have_root_shell()) return real_popen(cmd, type);
	reading = type[0] == 'r';
	if (!reading && type[0] != 'w') { errno = EINVAL; return NULL; }
	if (pipe2(fds, O_CLOEXEC) < 0) return NULL;
	/* fds[1] is the command's stdout when we read; fds[0] its stdin when we write. */
	pid = spawn_command(cmd, reading ? fds[1] : fds[0], reading ? 1 : 0, reading ? fds[0] : fds[1]);
	close(reading ? fds[1] : fds[0]);
	if (pid < 0) { close(reading ? fds[0] : fds[1]); return NULL; }
	f = fdopen(reading ? fds[0] : fds[1], reading ? "r" : "w");
	p = malloc(sizeof *p);
	if (!f || !p) { free(p); if (f) fclose(f); else close(reading ? fds[0] : fds[1]); waitpid(pid, NULL, 0); return NULL; }
	p->f = f; p->pid = pid;
	pthread_mutex_lock(&popen_lock); p->next = popened; popened = p; pthread_mutex_unlock(&popen_lock);
	return f;
}

int pclose(FILE *f)
{
	REAL(pclose);
	struct popened **pp, *p = NULL;
	int status;
	pthread_mutex_lock(&popen_lock);
	for (pp = &popened; *pp; pp = &(*pp)->next) if ((*pp)->f == f) { p = *pp; *pp = p->next; break; }
	pthread_mutex_unlock(&popen_lock);
	if (!p) return real_pclose(f);
	fclose(f);
	while (waitpid(p->pid, &status, 0) < 0) if (errno != EINTR) { free(p); return -1; }
	free(p);
	return status;
}

int system(const char *cmd)
{
	REAL(system);
	int status;
	pid_t pid;
	if (!cmd) return have_root_shell() ? 1 : real_system(cmd);
	if (!have_root_shell()) return real_system(cmd);
	if ((pid = spawn_command(cmd, -1, 0, -1)) < 0) return -1;
	while (waitpid(pid, &status, 0) < 0) if (errno != EINTR) return -1;
	return status;
}

/*
 * Host names, through glibc's own resolver. glibc reads /etc/hosts and /etc/resolv.conf with
 * its internal calls, which no preload sees, so it would read Android's (no nameservers; a
 * hosts file with localhost alone) instead of the webOS root's. c-ares (the mail services,
 * curl) reads them with fopen and was mapped already; libpurple's resolver is glibc's.
 * A name in the root's /etc/hosts is answered from there, as nss_files would; any other goes
 * to glibc with the root's nameservers, held against glibc's reload of Android's empty file.
 */
#include <netdb.h>
#include <resolv.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <ctype.h>

/* The address `name` has in the webOS root's /etc/hosts (IPv4), or 0. */
static int webos_host(const char *name, struct in_addr *out)
{
	REAL(fopen);
	char buf_[PATH_MAX], line[512];
	const char *path = map("/etc/hosts", buf_);
	FILE *f;
	int found = 0;
	if (!name || path[0] != '/' || strcmp(path, "/etc/hosts") == 0 || !(f = real_fopen(path, "r"))) return 0;
	while (!found && fgets(line, sizeof line, f)) {
		char *save, *addr, *tok;
		if ((tok = strchr(line, '#'))) *tok = 0;
		if (!(addr = strtok_r(line, " \t\r\n", &save)) || inet_pton(AF_INET, addr, out) != 1) continue;
		while ((tok = strtok_r(NULL, " \t\r\n", &save)))
			if (strcasecmp(tok, name) == 0) { found = 1; break; }
	}
	fclose(f);
	return found;
}

/* Points this thread's resolver at the webOS root's nameservers. */
static void webos_nameservers(void)
{
	REAL(fopen);
	char buf_[PATH_MAX], line[256];
	const char *path = map("/etc/resolv.conf", buf_);
	FILE *f;
	int n = 0;
	if (path[0] != '/' || strcmp(path, "/etc/resolv.conf") == 0 || !(f = real_fopen(path, "r"))) return;
	if (!(_res.options & RES_INIT)) res_init();
	while (n < MAXNS && fgets(line, sizeof line, f)) {
		char ns[64];
		struct in_addr a;
		if (sscanf(line, " nameserver %63s", ns) != 1 || inet_pton(AF_INET, ns, &a) != 1) continue;
		_res.nsaddr_list[n].sin_family = AF_INET;
		_res.nsaddr_list[n].sin_port = htons(53);
		_res.nsaddr_list[n].sin_addr = a;
		n++;
	}
	fclose(f);
	if (n) { _res.nscount = n; _res.options |= RES_NORELOAD; }
}

int getaddrinfo(const char *node, const char *service, const struct addrinfo *hints, struct addrinfo **res)
{
	REAL(getaddrinfo);
	struct in_addr a;
	int family = hints ? hints->ai_family : AF_UNSPEC;
	if (node && !(hints && (hints->ai_flags & AI_NUMERICHOST))) {
		if ((family == AF_UNSPEC || family == AF_INET) && webos_host(node, &a)) {
			char ip[INET_ADDRSTRLEN];
			struct addrinfo h = { 0 };
			if (hints) h = *hints;
			h.ai_family = AF_INET;
			h.ai_flags |= AI_NUMERICHOST;
			inet_ntop(AF_INET, &a, ip, sizeof ip);
			return real_getaddrinfo(ip, service, &h, res);
		}
		webos_nameservers();
	}
	return real_getaddrinfo(node, service, hints, res);
}

struct hostent *gethostbyname(const char *name)
{
	REAL(gethostbyname);
	static __thread struct hostent he;
	static __thread struct in_addr addr;
	static __thread char *addrs[2], *aliases[1], hname[256];
	if (name && webos_host(name, &addr)) {
		snprintf(hname, sizeof hname, "%s", name);
		addrs[0] = (char *)&addr; addrs[1] = NULL; aliases[0] = NULL;
		he.h_name = hname; he.h_aliases = aliases; he.h_addrtype = AF_INET; he.h_length = 4; he.h_addr_list = addrs;
		return &he;
	}
	webos_nameservers();
	return real_gethostbyname(name);
}

/* ---- the machine, and running another webOS binary ---- */

int uname(struct utsname *u)
{
	REAL(uname);
	int r = real_uname(u);
	if (r == 0) snprintf(u->machine, sizeof u->machine, "armv7l");
	return r;
}

/* The real execve, on what the path means here, absolute (getcwd's answer is webOS's). glibc's
   exec family calls its own, so each name is wrapped. */
static int exec_mapped(const char *path, char *const argv[], char *const envp[])
{
	REAL(execve);
	char whole[PATH_MAX], cwd[PATH_MAX];
	if (path && path[0] != '/' && getcwd(cwd, sizeof cwd)) { snprintf(whole, sizeof whole, "%s/%s", cwd, path); path = whole; }
	MAPPED(path);
	return webos_exec(getenv("LUNACY_PDK_REEXEC"), real_execve, mp_, argv, envp);
}

int execve(const char *path, char *const argv[], char *const envp[]) { return exec_mapped(path, argv, envp); }
int execv(const char *path, char *const argv[]) { return exec_mapped(path, argv, environ); }
int execvp(const char *file, char *const argv[])
{
	REAL(execvp);
	return strchr(file, '/') ? exec_mapped(file, argv, environ) : real_execvp(file, argv);
}

/* ---- System V semaphores ----
 *
 * webOS's kernel had System V IPC, and Mono 2.6's io-layer (Unity games under apkenv: Temple
 * Run 2) sets up its shared handles with semget, semctl and semop. Android forbids the calls:
 * from Android 8 its seccomp filter kills an app that makes one (SIGSYS; under qemu the guest's
 * call reaches the host as arm64 semget, 190), and before that its kernels lack them (ENOSYS,
 * on which Mono's semget retries for ever). The semaphores are kept here, inside the process,
 * with System V's semantics: a set per key, each operation list applied whole or not at all,
 * waiting until it can be (or failing at once with IPC_NOWAIT), semtimedop's timeout. Nothing
 * on webOS shared them with another PDK app, so being private to the process loses nothing a
 * game could see. SEM_UNDO is accepted; the undo is the process's exit, as it was there. */
#include <sys/ipc.h>
#include <sys/sem.h>
#include <time.h>

#define SEM_SETS 32
#define SEM_MAX 64
static struct semset { int used; key_t key; int nsems; unsigned short val[SEM_MAX]; pid_t pid[SEM_MAX]; struct semid_ds ds; } semsets[SEM_SETS];
static pthread_mutex_t sem_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t sem_changed = PTHREAD_COND_INITIALIZER;
#define SEM_ID(i) ((i) + 1)   /* never 0, never -1 */

static struct semset *semset_of(int id)
{
	return id >= 1 && id <= SEM_SETS && semsets[id - 1].used ? &semsets[id - 1] : NULL;
}

int semget(key_t key, int nsems, int flags)
{
	int i, free_slot = -1, id = -1;
	pthread_mutex_lock(&sem_lock);
	for (i = 0; i < SEM_SETS; i++) {
		if (!semsets[i].used) { if (free_slot < 0) free_slot = i; continue; }
		if (key != IPC_PRIVATE && semsets[i].key == key) {
			if ((flags & IPC_CREAT) && (flags & IPC_EXCL)) errno = EEXIST;
			else if (nsems > semsets[i].nsems) errno = EINVAL;
			else id = SEM_ID(i);
			goto out;
		}
	}
	if (!(flags & IPC_CREAT) && key != IPC_PRIVATE) { errno = ENOENT; goto out; }
	if (nsems <= 0 || nsems > SEM_MAX) { errno = EINVAL; goto out; }
	if (free_slot < 0) { errno = ENOSPC; goto out; }
	memset(&semsets[free_slot], 0, sizeof semsets[free_slot]);
	semsets[free_slot].used = 1; semsets[free_slot].key = key; semsets[free_slot].nsems = nsems;
	semsets[free_slot].ds.sem_nsems = (unsigned long)nsems;
	semsets[free_slot].ds.sem_perm.__key = key;
	semsets[free_slot].ds.sem_perm.mode = (unsigned short)(flags & 0777);
	semsets[free_slot].ds.sem_perm.uid = semsets[free_slot].ds.sem_perm.cuid = getuid();
	semsets[free_slot].ds.sem_perm.gid = semsets[free_slot].ds.sem_perm.cgid = getgid();
	semsets[free_slot].ds.sem_ctime = time(NULL);
	id = SEM_ID(free_slot);
out:
	pthread_mutex_unlock(&sem_lock);
	return id;
}

/* Whether the whole list can be applied now; -1 if an operation is out of range. */
static int sem_ready(struct semset *s, struct sembuf *ops, size_t n)
{
	int want[SEM_MAX];
	size_t i;
	for (i = 0; i < (size_t)s->nsems; i++) want[i] = s->val[i];
	for (i = 0; i < n; i++) {
		if (ops[i].sem_num >= s->nsems) return -1;
		if (ops[i].sem_op == 0) { if (want[ops[i].sem_num] != 0) return 0; }
		else if (want[ops[i].sem_num] + ops[i].sem_op < 0) return 0;
		else want[ops[i].sem_num] += ops[i].sem_op;
		if (want[ops[i].sem_num] > 32767) return -2;
	}
	return 1;
}

int semtimedop(int id, struct sembuf *ops, size_t n, const struct timespec *timeout)
{
	struct timespec until;
	int r = 0, nowait = 0;
	size_t i;
	if (!ops || n == 0) { errno = EINVAL; return -1; }
	for (i = 0; i < n; i++) if (ops[i].sem_flg & IPC_NOWAIT) nowait = 1;
	if (timeout) {
		clock_gettime(CLOCK_REALTIME, &until);
		until.tv_sec += timeout->tv_sec; until.tv_nsec += timeout->tv_nsec;
		if (until.tv_nsec >= 1000000000L) { until.tv_sec++; until.tv_nsec -= 1000000000L; }
	}
	pthread_mutex_lock(&sem_lock);
	for (;;) {
		struct semset *s = semset_of(id);
		int ready;
		if (!s) { errno = EIDRM; r = -1; break; }
		ready = sem_ready(s, ops, n);
		if (ready == -1) { errno = EFBIG; r = -1; break; }
		if (ready == -2) { errno = ERANGE; r = -1; break; }
		if (ready) {
			for (i = 0; i < n; i++) { s->val[ops[i].sem_num] += ops[i].sem_op; s->pid[ops[i].sem_num] = getpid(); }
			s->ds.sem_otime = time(NULL);
			pthread_cond_broadcast(&sem_changed);
			break;
		}
		if (nowait) { errno = EAGAIN; r = -1; break; }
		if (timeout ? pthread_cond_timedwait(&sem_changed, &sem_lock, &until) == ETIMEDOUT
				: pthread_cond_wait(&sem_changed, &sem_lock) != 0) {
			if (timeout) { errno = EAGAIN; r = -1; break; }
		}
	}
	pthread_mutex_unlock(&sem_lock);
	return r;
}

int semop(int id, struct sembuf *ops, size_t n) { return semtimedop(id, ops, n, NULL); }

union lunacy_semun { int val; struct semid_ds *buf; unsigned short *array; };

int semctl(int id, int num, int cmd, ...)
{
	union lunacy_semun arg = { 0 };
	struct semset *s;
	int r = 0, i;
	va_list ap;
	va_start(ap, cmd);
	if (cmd == SETVAL || cmd == SETALL || cmd == GETALL || cmd == IPC_STAT || cmd == IPC_SET || cmd == SEM_STAT)
		arg = va_arg(ap, union lunacy_semun);
	va_end(ap);
	pthread_mutex_lock(&sem_lock);
	s = semset_of(id);
	if (!s) { errno = EINVAL; r = -1; goto out; }
	if ((cmd == GETVAL || cmd == SETVAL || cmd == GETPID || cmd == GETNCNT || cmd == GETZCNT) && (num < 0 || num >= s->nsems)) {
		errno = EINVAL; r = -1; goto out;
	}
	switch (cmd) {
	case IPC_RMID: s->used = 0; pthread_cond_broadcast(&sem_changed); break;
	case IPC_STAT: case SEM_STAT: if (arg.buf) *arg.buf = s->ds; break;
	case IPC_SET: if (arg.buf) s->ds.sem_perm.mode = arg.buf->sem_perm.mode & 0777; s->ds.sem_ctime = time(NULL); break;
	case GETVAL: r = s->val[num]; break;
	case GETPID: r = s->pid[num]; break;
	case GETNCNT: case GETZCNT: r = 0; break;
	case SETVAL:
		if (arg.val < 0 || arg.val > 32767) { errno = ERANGE; r = -1; break; }
		s->val[num] = (unsigned short)arg.val; s->ds.sem_ctime = time(NULL);
		pthread_cond_broadcast(&sem_changed); break;
	case GETALL: if (arg.array) for (i = 0; i < s->nsems; i++) arg.array[i] = s->val[i]; break;
	case SETALL:
		if (arg.array) for (i = 0; i < s->nsems; i++) s->val[i] = arg.array[i];
		s->ds.sem_ctime = time(NULL); pthread_cond_broadcast(&sem_changed); break;
	default: errno = EINVAL; r = -1; break;
	}
out:
	pthread_mutex_unlock(&sem_lock);
	return r;
}

/* ---- OAuth for the mail services (mailauth.h, Docs/oauth.md) ----
 *
 * Palm's mail services read and write their connections, plain and TLS alike, with glib's
 * g_io_channel_read_chars and g_io_channel_write_chars (libemail-common's GIOChannelWrapper,
 * over libpalmsocket's channels). Here those calls pass through mailauth.h, which turns a login
 * carrying an OAuth handle into XOAUTH2. The token comes from Lunacy's OAuth service over the
 * process's own luna-send link (LUNACY_BUS, with the caller token Lunacy gave the process), so
 * the service answers only the processes Lunacy started. Channels on files and pipes are left
 * alone. glib's own headers aren't in the PDK sysroot; the four types used are its ABI.
 */
#include <poll.h>
#include <sys/un.h>
#include "mailauth.h"
#include "wbxmlfilter.h"
#include "composemail.h"
#include "backoff.h"
#include "calfilter.h"

typedef int GIOStatus;   /* G_IO_STATUS_ERROR 0, NORMAL 1, EOF 2, AGAIN 3 */
enum { MA_G_ERROR = 0, MA_G_NORMAL = 1, MA_G_AGAIN = 3 };
typedef struct { int ref_count; } GIOChannel;   /* glib 2's public struct starts so */
typedef struct _GError GError;
GIOStatus g_io_channel_write_chars(GIOChannel *, const char *, long, size_t *, GError **);
GIOStatus g_io_channel_read_chars(GIOChannel *, char *, size_t, size_t *, GError **);
GIOChannel *g_io_channel_new_file(const char *, const char *, GError **);
GIOChannel *g_io_channel_unix_new(int);
void g_io_channel_unref(GIOChannel *);

#define MA_CHANNELS 64
static struct ma_channel { GIOChannel *ch; int excluded; struct mailauth *m; } ma_channels[MA_CHANNELS];
static pthread_mutex_t ma_lock = PTHREAD_MUTEX_INITIALIZER;

static void *ma_next(const char *name) { return dlsym(RTLD_NEXT, name); }

/* The channel's entry, made when make is set (an excluded channel needs no state). */
static struct ma_channel *ma_find(GIOChannel *ch, int make)
{
	struct ma_channel *free_slot = NULL;
	for (int i = 0; i < MA_CHANNELS; i++) {
		if (ma_channels[i].ch == ch) return &ma_channels[i];
		if (!ma_channels[i].ch && !free_slot) free_slot = &ma_channels[i];
	}
	if (!make || !free_slot) return NULL;
	free_slot->ch = ch; free_slot->excluded = 0; free_slot->m = NULL;
	return free_slot;
}

static void ma_exclude(GIOChannel *ch)
{
	if (!ch) return;
	pthread_mutex_lock(&ma_lock);
	struct ma_channel *c = ma_find(ch, 1);
	if (c) c->excluded = 1;
	pthread_mutex_unlock(&ma_lock);
}

GIOChannel *g_io_channel_new_file(const char *name, const char *mode, GError **err)
{
	static GIOChannel *(*real)(const char *, const char *, GError **);
	if (!real) real = ma_next("g_io_channel_new_file");
	GIOChannel *ch = real(name, mode, err);
	ma_exclude(ch);
	return ch;
}

GIOChannel *g_io_channel_unix_new(int fd)
{
	static GIOChannel *(*real)(int);
	if (!real) real = ma_next("g_io_channel_unix_new");
	GIOChannel *ch = real(fd);
	struct stat st;
	if (fstat(fd, &st) != 0 || !S_ISSOCK(st.st_mode)) ma_exclude(ch);
	return ch;
}

void g_io_channel_unref(GIOChannel *ch)
{
	static void (*real)(GIOChannel *);
	if (!real) real = ma_next("g_io_channel_unref");
	if (ch && ch->ref_count == 1) {   /* this unref frees it: its address can come back as another channel */
		pthread_mutex_lock(&ma_lock);
		struct ma_channel *c = ma_find(ch, 0);
		if (c) { if (c->m) { mailauth_free(c->m); free(c->m); } c->ch = NULL; c->m = NULL; c->excluded = 0; }
		pthread_mutex_unlock(&ma_lock);
	}
	real(ch);
}

static GIOStatus (*ma_real_write)(GIOChannel *, const char *, long, size_t *, GError **);
static GIOStatus (*ma_real_read)(GIOChannel *, char *, size_t, size_t *, GError **);

/* All of p to the server, waiting out a full socket (the lines sent here are short). */
static int ma_send(void *ctx, const char *p, size_t n)
{
	GIOChannel *ch = ctx;
	int waited = 0;
	while (n) {
		size_t w = 0;
		GIOStatus s = ma_real_write(ch, p, (long)n, &w, NULL);
		if (s == MA_G_NORMAL || s == MA_G_AGAIN) { p += w; n -= w; }
		else return -1;
		if (n && !w) { if (++waited > 15000) return -1; usleep(1000); }
	}
	return 0;
}

/* A JSON string's contents, escaped. */
static size_t ma_json(char *out, size_t cap, const char *s)
{
	size_t o = 0;
	for (; *s && o + 7 < cap; s++) {
		unsigned char c = (unsigned char)*s;
		if (c == '"' || c == '\\') { out[o++] = '\\'; out[o++] = (char)c; }
		else if (c < 0x20) o += (size_t)snprintf(out + o, cap - o, "\\u%04x", c);
		else out[o++] = (char)c;
	}
	out[o] = 0;
	return o;
}

/* The access token for a handle, from palm://org.webosarchive.lunacy.oauth/token: mailauth_io's
   token (1, or 2 for one just issued; 0 for a handle the service doesn't know; -1 when no
   token can be had now - the service unreachable, or the provider for a refresh). */
static int ma_token(void *ctx, const char *user, const char *handle, int stale, char *buf, size_t cap)
{
	(void)ctx;
	const char *bus = getenv("LUNACY_BUS"), *caller = getenv("LUNACY_BUS_TOKEN");
	if (!bus || !caller) return 0;   /* not under Lunacy: no handle is one of ours */
	char eu[1100], payload[1400], ep[3000], req[4000];
	ma_json(eu, sizeof eu, user);
	snprintf(payload, sizeof payload, "{\"handle\":\"%s\",\"user\":\"%s\"%s}", handle, eu, stale ? ",\"stale\":true" : "");
	ma_json(ep, sizeof ep, payload);
	int len = snprintf(req, sizeof req, "{\"token\":\"%s\",\"url\":\"palm://org.webosarchive.lunacy.oauth/token\",\"payload\":\"%s\"}\n", caller, ep);
	int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
	if (fd < 0) return -1;
	struct sockaddr_un a = { .sun_family = AF_UNIX };
	strncpy(a.sun_path, bus, sizeof a.sun_path - 1);
	static char reply[MAILAUTH_TOKEN_MAX + 512];
	size_t got = 0;
	if (connect(fd, (struct sockaddr *)&a, sizeof a) != 0 || write(fd, req, (size_t)len) != len) { close(fd); return -1; }
	/* A refresh can take the service a few seconds; the service's own login waits 30. */
	while (got < sizeof reply - 1) {
		struct pollfd pf = { fd, POLLIN, 0 };
		if (poll(&pf, 1, 25000) <= 0) break;
		ssize_t r = read(fd, reply + got, sizeof reply - 1 - got);
		if (r <= 0) break;
		got += (size_t)r;
		if (memchr(reply, '\n', got)) break;
	}
	close(fd);
	reply[got] = 0;
	/* -11: the service has no such handle. Any other error, or no answer, is for now. */
	if (!strstr(reply, "\"returnValue\":true")) { int r = strstr(reply, "\"errorCode\":-11") ? 0 : -1; memset(reply, 0, got); return r; }
	const char *t = strstr(reply, "\"accessToken\":\"");
	if (!t) return -1;
	int fresh = strstr(reply, "\"fresh\":true") != NULL;
	size_t o = 0;
	for (t += 15; *t && *t != '"' && o + 1 < cap; t++) {
		if (*t == '\\' && t[1]) t++;   /* \/ and the like: tokens are printable ASCII */
		buf[o++] = *t;
	}
	buf[o] = 0;
	memset(reply, 0, got);
	return fresh ? 2 : 1;
}

GIOStatus g_io_channel_write_chars(GIOChannel *ch, const char *buf, long count, size_t *written, GError **err)
{
	if (!ma_real_write) ma_real_write = ma_next("g_io_channel_write_chars");
	if (count < 0) count = buf ? (long)strlen(buf) : 0;
	pthread_mutex_lock(&ma_lock);
	struct ma_channel *c = ma_find(ch, 1);
	if (!c || c->excluded || !count) { pthread_mutex_unlock(&ma_lock); return ma_real_write(ch, buf, count, written, err); }
	if (!c->m && (c->m = calloc(1, sizeof *c->m))) mailauth_init(c->m);
	struct mailauth *m = c->m;
	pthread_mutex_unlock(&ma_lock);
	if (!m) return ma_real_write(ch, buf, count, written, err);
	struct mailauth_io io = { ma_send, ma_token, ch };
	int direct;
	long r = mailauth_out(m, &io, buf, (size_t)count, &direct);
	if (r < 0) { if (written) *written = 0; return MA_G_ERROR; }
	if (direct) {
		size_t w = 0;
		GIOStatus s = ma_real_write(ch, buf, r, &w, err);
		if (w < (size_t)r) m->out_mode = MA_PASS;   /* the rest of the line is still to come */
		if (written) *written = w;
		return s;
	}
	if (written) *written = (size_t)r;
	return MA_G_NORMAL;
}

GIOStatus g_io_channel_read_chars(GIOChannel *ch, char *buf, size_t count, size_t *got, GError **err)
{
	if (!ma_real_read) ma_real_read = ma_next("g_io_channel_read_chars");
	if (!ma_real_write) ma_real_write = ma_next("g_io_channel_write_chars");
	pthread_mutex_lock(&ma_lock);
	struct ma_channel *c = ma_find(ch, 0);
	struct mailauth *m = c && !c->excluded ? c->m : NULL;
	pthread_mutex_unlock(&ma_lock);
	if (!m || !count) return ma_real_read(ch, buf, count, got, err);
	size_t n = mailauth_pending(m, buf, count);
	if (n) { if (got) *got = n; return MA_G_NORMAL; }
	size_t r = 0;
	GIOStatus s = ma_real_read(ch, buf, count, &r, err);
	if (got) *got = r;
	if (s != MA_G_NORMAL || !r || !mailauth_watching(m)) return s;
	struct mailauth_io io = { ma_send, ma_token, ch };
	long k = mailauth_in(m, &io, buf, r, count);
	if (k < 0) { if (got) *got = 0; return MA_G_ERROR; }
	if (got) *got = (size_t)k;
	return k ? MA_G_NORMAL : MA_G_AGAIN;
}

/* ---- OAuth for Exchange ActiveSync (Docs/oauth.md) ----
 *
 * mojomail-eas reaches its server through libemail-common's CurlConnection, which hands curl
 * the account's "user:password" as CURLOPT_USERPWD (Basic auth) and its own headers as
 * CURLOPT_HTTPHEADER. A password that is an OAuth handle is taken out here: the request goes
 * without Basic auth and with `Authorization: Bearer <token>` added to its own header list
 * (a copy, so the caller's list is never changed). A 401 to a bearer request marks the token
 * refused, so the next login asks for a fresh one (mailauth.h's stale), as for IMAP. Every
 * other option and request passes as it was.
 */
typedef void CURL;
struct curl_slist { char *data; struct curl_slist *next; };
typedef size_t (*lc_header_fn)(char *, size_t, size_t, void *);
typedef size_t (*lc_read_fn)(char *, size_t, size_t, void *);
typedef int (*lc_seek_fn)(void *, long long, int);   /* curl_off_t is 64-bit here (the *_LARGE options) */
#define LC_CURLOPT_USERPWD 10005
#define LC_CURLOPT_HTTPHEADER 10023
#define LC_CURLOPT_HTTPAUTH 107
#define LC_CURLINFO_RESPONSE_CODE 0x200002
#define LC_OFF_T_BASE 30000

#define LC_HANDLES 32
static struct lc_handle {
	CURL *h;
	struct curl_slist *theirs;     /* the caller's own header list */
	struct curl_slist *ours;       /* theirs + Authorization, set in its place */
	char bearer[MAILAUTH_TOKEN_MAX];
	char handle[64];
	lc_header_fn their_header;     /* the caller's header callback, and its data */
	void *header_data;
	lc_header_fn their_write;      /* the caller's body callback, and its data */
	void *write_data;
	char host[128];                /* the request's server, for the versions it offers */
	long post_size;                /* the request body's size, as the caller set it */
	int initial_provision;         /* the body is Palm's initial Provision (lc_with_device_info) */
	unsigned char *post;           /* a body of ours set in place of the caller's */
	int body_seen;                 /* bytes of this response's body looked at */
	int provision;                 /* the body asked for provisioning (status 142-144) */
	int filtering;                 /* the body goes through wbxmlfilter.h (a 16.1 reply to a 12.1 client) */
	struct wbxf wbxf;
	lc_read_fn their_read;         /* the caller's request body callback, and its data */
	void *read_data;
	lc_seek_fn their_seek;         /* the caller's rewind callback, and its data */
	void *seek_data;
	int sending;                   /* this request is a send, in ComposeMail form (composemail.h) */
	unsigned char cm_pre[1024];    /* the bytes before the message */
	size_t cm_pre_len, cm_pos;     /* and how far into the prefix or suffix */
	int cm_phase;                  /* 0 prefix, 1 the caller's message, 2 suffix, 3 done */
	int send_status;               /* the reply's ComposeMail status */
	struct bo_reply bo;            /* what this reply's headers say about backing off (backoff.h) */
	char url[1024];                /* the request's own URL, while one held back stands in for it */
	int held;                      /* the URL set is the stand-in: the host's back-off hadn't passed */
	int no_token;                  /* an OAuth handle with no token to be had just now: the request fails locally */
	int token_final;               /* the token sent was fresh, or a retry's: a 401 to it is the server's last word */
	const void *post_ptr;          /* the request body being sent (the caller's, or ours in its place) */
	int post_fresh;                /* post_ptr was set for the request now being made, not an earlier one */
} lc_handles[LC_HANDLES];
static pthread_mutex_t lc_lock = PTHREAD_MUTEX_INITIALIZER;
static int lc_version(const char *host, const char *asked, char *out, size_t cap);

static int (*lc_real_setopt)(CURL *, int, ...);
static struct curl_slist *(*lc_slist_append)(struct curl_slist *, const char *);
static void (*lc_slist_free)(struct curl_slist *);

static void lc_init(void)
{
	if (!lc_real_setopt) lc_real_setopt = ma_next("curl_easy_setopt");
	if (!lc_slist_append) lc_slist_append = ma_next("curl_slist_append");
	if (!lc_slist_free) lc_slist_free = ma_next("curl_slist_free_all");
}

static struct lc_handle *lc_find(CURL *h, int make)
{
	struct lc_handle *free_slot = NULL;
	for (int i = 0; i < LC_HANDLES; i++) {
		if (lc_handles[i].h == h) return &lc_handles[i];
		if (!lc_handles[i].h && !free_slot) free_slot = &lc_handles[i];
	}
	if (!make || !free_slot) return NULL;
	memset(free_slot, 0, sizeof *free_slot);
	free_slot->h = h;
	return free_slot;
}

/* Sets the caller's headers plus the bearer on the handle (lock held). */
static int lc_apply_headers(struct lc_handle *e)
{
	struct curl_slist *list = NULL;
	for (struct curl_slist *s = e->theirs; s; s = s->next) {
		if (e->handle[0] && strncasecmp(s->data, "Authorization:", 14) == 0) continue;
		char v[16], line[64];
		if (e->sending && strncasecmp(s->data, "Content-Type:", 13) == 0 && strcasecmp(s->data + 13 + strspn(s->data + 13, " "), "message/rfc822") == 0) {
			list = lc_slist_append(list, "Content-Type: application/vnd.ms-sync.wbxml");
		} else if (strncasecmp(s->data, "MS-ASProtocolVersion:", 21) == 0 && lc_version(e->host, s->data + 21 + strspn(s->data + 21, " "), v, sizeof v)) {
			snprintf(line, sizeof line, "MS-ASProtocolVersion: %s", v);
			list = lc_slist_append(list, line);
		} else list = lc_slist_append(list, s->data);
	}
	char line[MAILAUTH_TOKEN_MAX + 32];
	if (e->handle[0]) {
		snprintf(line, sizeof line, "Authorization: Bearer %s", e->bearer);
		list = lc_slist_append(list, line);
	}
	memset(line, 0, sizeof line);
	int r = lc_real_setopt(e->h, LC_CURLOPT_HTTPHEADER, list);
	if (e->ours) lc_slist_free(e->ours);
	e->ours = list;
	return r;
}

/*
 * Palm's mojomail-eas speaks EAS 2.5 to 14.1 but takes the highest version a server offers
 * (MS-ASProtocolVersions); Microsoft 365 offers 16.0 and 16.1, and the service then sent 14.1's
 * requests as 16.1 and got 400s. The header is shown to it with only the versions up to 14.1, as
 * a server of its day would have offered. Its header callback (one for every handle in
 * libemail-common, CurlHeaderCallback) is wrapped to do it; every other header passes as it was.
 */
#define LC_CURLOPT_HEADERFUNCTION 20079
#define LC_CURLOPT_HEADERDATA 10029

/*
 * The versions the server last offered (MS-ASProtocolVersions, comma-separated). Palm's service
 * sends some requests at a version it chose for itself (FolderSync at 12.1 whatever the server
 * said); a version the server no longer offers - Exchange Online takes 16.1 only since March 2026
 * - is sent as the nearest one it does, the lowest offered above it. FolderSync, Sync and the
 * rest of mail are the same on the wire from 12.1 to 16.1.
 */
#define LC_SERVERS 8
static struct { char host[128]; char offered[128]; } lc_servers[LC_SERVERS];

static char *lc_offered_for(const char *host, int make)
{
	for (int i = 0; i < LC_SERVERS; i++) if (lc_servers[i].host[0] && strcmp(lc_servers[i].host, host) == 0) return lc_servers[i].offered;
	if (!make) return NULL;
	for (int i = 0; i < LC_SERVERS; i++) if (!lc_servers[i].host[0]) { snprintf(lc_servers[i].host, sizeof lc_servers[i].host, "%s", host); return lc_servers[i].offered; }
	snprintf(lc_servers[0].host, sizeof lc_servers[0].host, "%s", host);   /* full: the oldest goes */
	return lc_servers[0].offered;
}

/* The version to send for `asked` to host, into out; 0 when asked is fine as it is (lock held). */
static int lc_version(const char *host, const char *asked, char *out, size_t cap)
{
	const char *offered = host && host[0] ? lc_offered_for(host, 0) : NULL;
	if (!offered || !offered[0]) return 0;
	double want = atof(asked), best = 0;
	char bestv[16] = "";
	const char *p = offered;
	while (*p) {
		const char *q = strchr(p, ','); size_t n = q ? (size_t)(q - p) : strlen(p);
		char one[16]; if (n >= sizeof one) n = sizeof one - 1;
		memcpy(one, p, n); one[n] = 0;
		double v = atof(one);
		if (v > want - 1e-6 && v < want + 1e-6) return 0;              /* offered: as it is */
		if (v > want && (best == 0 || v < best)) { best = v; strcpy(bestv, one); }
		p = q ? q + 1 : p + n;
	}
	if (!bestv[0]) return 0;
	snprintf(out, cap, "%s", bestv);
	return 1;
}

/*
 * The server's back-off (backoff.h). Kept in the webOS root, so every service process and every
 * restart sees it; while it hasn't passed, a request to that host is pointed at a closed port on
 * this device instead (lc_hold), and fails at once as a network failure, which the service
 * retries later. Nothing reaches the server until the time it asked for.
 */
/*
 * Development only: when /var/preferences/lunacy-eas-dump names a host (one line), every
 * request body sent to it and every reply body from it is appended to /tmp/lunacy-eas-dump, as
 * "> <url> <length>\n" or "< <length>\n" and the bytes, for decoding on a desk. No file, no
 * dump. Bodies hold mail, so the file is for a test account (Docs/outlook-eas.md).
 */
static int lc_dump_wanted(const char *host)
{
	size_t root_len;
	const char *root = webos_root(&root_len);
	char path[PATH_MAX], want[128] = "";
	if (!root || !host[0] || snprintf(path, sizeof path, "%s/var/preferences/lunacy-eas-dump", root) >= (int)sizeof path) return 0;
	FILE *f = fopen(path, "r");
	if (!f) return 0;
	if (!fgets(want, sizeof want, f)) want[0] = 0;
	fclose(f);
	want[strcspn(want, "\r\n ")] = 0;
	return want[0] && strcmp(want, host) == 0;
}

static void lc_dump(const char *host, const char *head, const void *bytes, size_t n)
{
	if (!lc_dump_wanted(host)) return;
	size_t root_len;
	const char *root = webos_root(&root_len);
	char path[PATH_MAX];
	if (snprintf(path, sizeof path, "%s/tmp/lunacy-eas-dump", root) >= (int)sizeof path) return;
	FILE *f = fopen(path, "a");
	if (!f) return;
	fprintf(f, "%s %lu\n", head, (unsigned long)n);
	if (n) fwrite(bytes, 1, n, f);
	fputc('\n', f);
	fclose(f);
}

#define LC_BACKOFF_FILE "/var/preferences/lunacy-eas-backoff"
#define LC_HELD_URL "http://127.0.0.1:1/"

static int lc_backoff_path(char *out, size_t cap)
{
	size_t root_len;
	const char *root = webos_root(&root_len);
	return root && snprintf(out, cap, "%s%s", root, LC_BACKOFF_FILE) < (int)cap;
}

/* One header line of a reply; at the blank line that ends them, a back-off asked for is kept. */
static void lc_backoff_header(void *data, const char *buf, size_t len)
{
	char host[128] = "", throttle[64] = "";
	long seconds = 0;
	pthread_mutex_lock(&lc_lock);
	for (int i = 0; i < LC_HANDLES; i++) {
		struct lc_handle *e = &lc_handles[i];
		if (!e->h || e->header_data != data || !e->host[0]) continue;
		if (len <= 2 && (len == 0 || buf[0] == '\r' || buf[0] == '\n')) {
			seconds = bo_seconds(&e->bo);
			if (seconds > 0) { snprintf(host, sizeof host, "%s", e->host); snprintf(throttle, sizeof throttle, "%s", e->bo.throttle); }
			bo_reset(&e->bo);
		} else bo_header(&e->bo, buf, len);
		break;
	}
	pthread_mutex_unlock(&lc_lock);
	if (seconds <= 0) return;
	char path[PATH_MAX];
	if (!lc_backoff_path(path, sizeof path)) { fprintf(stderr, "lunacy: %s asks to be left alone for %ld s; no webOS root to keep it in\n", host, seconds); return; }
	long now = (long)time(NULL), until = bo_until(path, host);
	if (until < now + seconds) until = now + seconds;
	int kept = bo_set(path, host, until, now) == 0;
	fprintf(stderr, "lunacy: %s asks to be left alone for %ld s%s%s; %s\n", host, seconds,
		throttle[0] ? ", throttled: " : "", throttle, kept ? "nothing goes to it until then" : "couldn't keep it");
}

/* Whether a request to this handle's host must wait out a back-off; points it elsewhere if so (lock held). */
static void lc_hold(CURL *h, struct lc_handle *e)
{
	char path[PATH_MAX];
	long now = (long)time(NULL);
	long until = e->host[0] && lc_backoff_path(path, sizeof path) ? bo_until(path, e->host) : 0;
	if (e->no_token) {
		/* No token to send (the sign-in couldn't be refreshed just now): fails as a network
		   failure, which the service tries again later, rather than a login refused. */
		if (!e->held) lc_real_setopt(h, 10002, (void *)LC_HELD_URL);
		e->held = 1;
		fprintf(stderr, "lunacy: no token for %s just now; the request waits for a later try\n", e->host);
	} else if (until > now) {
		if (!e->held) lc_real_setopt(h, 10002, (void *)LC_HELD_URL);
		e->held = 1;
		fprintf(stderr, "lunacy: a request to %s waits: %ld s of its back-off left\n", e->host, until - now);
	} else if (e->held) {
		/* The same request again after the back-off: its own URL back. */
		lc_real_setopt(h, 10002, (void *)e->url);
		e->held = 0;
	}
}

int curl_multi_add_handle(void *multi, CURL *h)
{
	static int (*real)(void *, CURL *);
	if (!real) real = ma_next("curl_multi_add_handle");
	lc_init();
	pthread_mutex_lock(&lc_lock);
	struct lc_handle *e = lc_find(h, 0);
	if (e) lc_hold(h, e);
	if (e && !e->held) {
		char head[1100];
		snprintf(head, sizeof head, "> %s", e->url);
		lc_dump(e->host, head, e->post_ptr, e->post_fresh && e->post_ptr && e->post_size > 0 ? (size_t)e->post_size : 0);
	}
	if (e) e->post_fresh = 0;   /* the next request says its own body */
	pthread_mutex_unlock(&lc_lock);
	return real(multi, h);
}

/* The caller's header callback for the request whose header data is this (lock taken here). */
static lc_header_fn lc_their_header(void *data)
{
	lc_header_fn f = NULL;
	pthread_mutex_lock(&lc_lock);
	for (int i = 0; i < LC_HANDLES && !f; i++)
		if (lc_handles[i].h && lc_handles[i].their_header && lc_handles[i].header_data == data) f = lc_handles[i].their_header;
	pthread_mutex_unlock(&lc_lock);
	return f;
}

static size_t lc_header(char *buf, size_t size, size_t n, void *data)
{
	lc_header_fn theirs = lc_their_header(data);
	if (!theirs) return size * n;   /* can't happen: set together with the callback */
	size_t len = size * n;
	lc_backoff_header(data, buf, len);
	static const char name[] = "MS-ASProtocolVersions:";
	if (len > sizeof name && len < 512 && strncasecmp(buf, name, sizeof name - 1) == 0) {
		/* The server's list, for the requests that follow to it (lc_version). */
		pthread_mutex_lock(&lc_lock);
		for (int i = 0; i < LC_HANDLES; i++) {
			if (!lc_handles[i].h || lc_handles[i].header_data != data || !lc_handles[i].host[0]) continue;
			char *offered = lc_offered_for(lc_handles[i].host, 1);
			size_t k = 0;
			for (const char *c = buf + sizeof name - 1; c < buf + len && k < 127; c++)
				if (*c != ' ' && *c != '\r' && *c != '\n') offered[k++] = *c;
			offered[k] = 0;
			break;
		}
		pthread_mutex_unlock(&lc_lock);
		/* Offered as the one version it should take: the highest the server has up to 14.1 (how
		   the service picks from a list isn't known, and a full list gave it 2.5). */
		char out[512], *w = out, best[16] = "";
		double bestv = 0;
		const char *p = buf + sizeof name - 1;
		while (p < buf + len) {
			while (p < buf + len && (*p == ' ' || *p == ',')) p++;
			const char *q = p;
			while (q < buf + len && *q != ',' && *q != '\r' && *q != '\n') q++;
			if (q > p && q - p < (long)sizeof best) {
				char one[16]; memcpy(one, p, (size_t)(q - p)); one[q - p] = 0;
				double v = atof(one);
				if (v <= 14.1 + 1e-6 && v > bestv) { bestv = v; strcpy(best, one); }
			}
			p = q;
			if (p < buf + len && (*p == '\r' || *p == '\n')) break;
		}
		if (!best[0]) return theirs(buf, size, n, data);   /* nothing it knows: Exchange Online since March 2026 (16.1 only) */
		w += snprintf(out, sizeof out, "%s %s\r\n", name, best);
		size_t r = theirs(out, 1, (size_t)(w - out), data);
		return r == (size_t)(w - out) ? len : 0;   /* curl wants its own length back */
	}
	return theirs(buf, size, n, data);
}

/*
 * EAS 12.1, which Palm's service works at, asked a device to provision (accept the server's
 * security policy) with HTTP 449; from 14.0 the server says it in the reply's top-level Status
 * (142 not provisioned, 143 policy refresh, 144 bad policy key) with HTTP 200, which the service
 * takes for a failed command. A reply whose WBXML opens with such a status is reported to it as
 * 449, and it provisions as it always did. Only the first bytes of a body are looked at.
 */
#define LC_CURLOPT_WRITEFUNCTION 20011
#define LC_CURLOPT_WRITEDATA 10001

static struct lc_handle *lc_by_write_data(void *data)
{
	for (int i = 0; i < LC_HANDLES; i++)
		if (lc_handles[i].h && lc_handles[i].their_write && lc_handles[i].write_data == data) return &lc_handles[i];
	return NULL;
}

static size_t lc_write(char *buf, size_t size, size_t n, void *data)
{
	pthread_mutex_lock(&lc_lock);
	struct lc_handle *e = lc_by_write_data(data);
	lc_header_fn theirs = e ? e->their_write : NULL;
	if (e) lc_dump(e->host, "<", buf, size * n);
	if (e && e->body_seen == 0) {
		/* A WBXML reply from a server the version adapter speaks 16.1 to: newer code pages out. */
		char ver[16];
		e->filtering = !e->sending && size * n >= 4 && (unsigned char)buf[0] == 0x03 && (unsigned char)buf[2] == 0x6a && lc_version(e->host, "12.1", ver, sizeof ver);
		if (e->filtering) wbxf_init(&e->wbxf);
		if (e->sending) {
			e->send_status = cm_status((unsigned char *)buf, size * n);
			if (e->send_status && e->send_status != 1) fprintf(stderr, "lunacy: %s refused a send: ComposeMail status %d\n", e->host, e->send_status);
		}
	}
	if (e && e->body_seen < 48) {
		/* WBXML: 03 01 6a 00, then a page switch and the command's tags; the status is an
		   inline string (03 "142" 00) a few bytes in. */
		size_t len = size * n, look = len < (size_t)(48 - e->body_seen) ? len : (size_t)(48 - e->body_seen);
		if (e->body_seen == 0 && len >= 4 && (unsigned char)buf[0] == 0x03 && (unsigned char)buf[2] == 0x6a) {
			for (size_t i = 4; i + 4 < look; i++)
				if (buf[i] == 0x03 && buf[i + 1] == '1' && buf[i + 2] == '4' && buf[i + 3] >= '2' && buf[i + 3] <= '4' && buf[i + 4] == 0) e->provision = 1;
		}
		e->body_seen += (int)look;
	}
	if (e && e->filtering && theirs) {
		size_t len = size * n;
		unsigned char *out = malloc(len + 16 + WBXF_HOLD);
		size_t o = out ? wbxf_feed(&e->wbxf, (unsigned char *)buf, len, out) : 0;
		pthread_mutex_unlock(&lc_lock);
		if (!out) return 0;
		size_t r = o ? theirs((char *)out, 1, o, data) : 0;
		free(out);
		return r == o ? len : 0;   /* curl wants its own length back */
	}
	pthread_mutex_unlock(&lc_lock);
	return theirs ? theirs(buf, size, n, data) : size * n;
}

/*
 * EAS 14.1 and later want a device to describe itself in its first Provision request
 * (settings:DeviceInformation); without it Exchange Online answers status 165 and never gives a
 * policy key. Palm's service, written for 12.1, sends only the policy type. Where the version
 * adapter is at work (the server no longer offers the version the service asks for), that one
 * request - Palm's exact initial Provision - gets the block, saying what this is: Lunacy, on
 * webOS's mail service. Every other body is left as it was.
 */
#define LC_CURLOPT_POSTFIELDS 10015
#define LC_CURLOPT_POSTFIELDSIZE 60

static const unsigned char lc_provision_head[] = { 0x03, 0x01, 0x6a, 0x00, 0x00, 0x0e, 0x45 };
static const unsigned char lc_provision_rest[] = { 0x46, 0x47, 0x48, 0x03,
	'M','S','-','E','A','S','-','P','r','o','v','i','s','i','o','n','i','n','g','-','W','B','X','M','L', 0x00,
	0x01, 0x01, 0x01, 0x01 };

/* The initial Provision with DeviceInformation, into a new buffer; its length in *len. */
static unsigned char *lc_with_device_info(size_t *len)
{
	static const char *const fields[][2] = {
		{ "\x57", "Lunacy" },            /* Model */
		{ "\x59", "Lunacy" },            /* FriendlyName */
		{ "\x5a", "webOS" },             /* OS */
		{ "\x5b", "English" },           /* OSLanguage */
		{ "\x60", "Palm/1.0.1" },        /* UserAgent: what the service sends as User-Agent */
	};
	unsigned char *b = malloc(256), *w = b;
	if (!b) return NULL;
	memcpy(w, lc_provision_head, sizeof lc_provision_head); w += sizeof lc_provision_head;
	*w++ = 0x00; *w++ = 0x12;         /* the Settings page */
	*w++ = 0x56; *w++ = 0x48;         /* DeviceInformation, Set */
	for (size_t i = 0; i < sizeof fields / sizeof fields[0]; i++) {
		*w++ = (unsigned char)fields[i][0][0]; *w++ = 0x03;
		size_t n = strlen(fields[i][1]); memcpy(w, fields[i][1], n); w += n; *w++ = 0x00;
		*w++ = 0x01;
	}
	*w++ = 0x01; *w++ = 0x01;         /* /Set, /DeviceInformation */
	*w++ = 0x00; *w++ = 0x0e;         /* back to the Provision page */
	memcpy(w, lc_provision_rest, sizeof lc_provision_rest); w += sizeof lc_provision_rest;
	*len = (size_t)(w - b);
	return b;
}

/* The request body of a send, in ComposeMail form: prefix, the caller's message unread, suffix. */
#define LC_CURLOPT_READFUNCTION 20012
#define LC_CURLOPT_READDATA 10009
#define LC_CURLOPT_SEEKFUNCTION 20167
#define LC_CURLOPT_SEEKDATA 10168
#define LC_READFUNC_ABORT 0x10000000
#define LC_READFUNC_PAUSE 0x10000001

static struct lc_handle *lc_by_read_data(void *data)
{
	for (int i = 0; i < LC_HANDLES; i++)
		if (lc_handles[i].h && lc_handles[i].sending && lc_handles[i].read_data == data) return &lc_handles[i];
	return NULL;
}

static size_t lc_read(char *buf, size_t size, size_t n, void *data)
{
	size_t cap = size * n, o = 0;
	pthread_mutex_lock(&lc_lock);
	struct lc_handle *e = lc_by_read_data(data);
	if (!e) { pthread_mutex_unlock(&lc_lock); return LC_READFUNC_ABORT; }   /* can't happen: set with the handle */
	if (e->cm_phase == 0) {
		size_t k = e->cm_pre_len - e->cm_pos < cap ? e->cm_pre_len - e->cm_pos : cap;
		memcpy(buf, e->cm_pre + e->cm_pos, k); e->cm_pos += k; o += k;
		if (e->cm_pos == e->cm_pre_len) { e->cm_phase = 1; e->cm_pos = 0; }
		if (o == cap) { pthread_mutex_unlock(&lc_lock); return o; }
	}
	if (e->cm_phase == 1) {
		lc_read_fn theirs = e->their_read;
		pthread_mutex_unlock(&lc_lock);
		size_t r = theirs(buf + o, 1, cap - o, data);
		if (r == LC_READFUNC_ABORT) return r;
		if (r == LC_READFUNC_PAUSE) return o ? o : r;   /* asked again once it's unpaused */
		if (r) return o + r;
		pthread_mutex_lock(&lc_lock);
		e = lc_by_read_data(data);
		if (!e) { pthread_mutex_unlock(&lc_lock); return o; }
		e->cm_phase = 2; e->cm_pos = 0;
	}
	if (e->cm_phase == 2) {
		size_t k = sizeof cm_suffix - e->cm_pos < cap - o ? sizeof cm_suffix - e->cm_pos : cap - o;
		memcpy(buf + o, cm_suffix + e->cm_pos, k); e->cm_pos += k; o += k;
		if (e->cm_pos == sizeof cm_suffix) e->cm_phase = 3;
	}
	pthread_mutex_unlock(&lc_lock);
	return o;
}

static int lc_seek(void *data, long long offset, int origin)
{
	pthread_mutex_lock(&lc_lock);
	struct lc_handle *e = lc_by_read_data(data);
	lc_seek_fn theirs = e ? e->their_seek : NULL;
	void *their_data = e ? e->seek_data : NULL;
	if (e && offset == 0 && origin == 0) { e->cm_phase = 0; e->cm_pos = 0; }
	pthread_mutex_unlock(&lc_lock);
	if (!e || offset != 0 || origin != 0) return 2;   /* CURL_SEEKFUNC_CANTSEEK: only a rewind to the start */
	return theirs ? theirs(their_data, 0, 0) : 0;
}

/* Whether the request this handle is about to make is a send to wrap; sets it up (lock held). */
static void lc_send_setup(CURL *h, struct lc_handle *e, const char *url)
{
	struct cm_send cs;
	e->sending = 0; e->send_status = 0;
	if (!e->their_read || e->post_size <= 0 || !cm_parse_url(url, &cs)) return;
	int rfc822 = 0;
	char asked[16] = "", ver[16];
	for (struct curl_slist *q = e->theirs; q; q = q->next) {
		if (strncasecmp(q->data, "Content-Type:", 13) == 0 && strcasecmp(q->data + 13 + strspn(q->data + 13, " "), "message/rfc822") == 0) rfc822 = 1;
		if (strncasecmp(q->data, "MS-ASProtocolVersion:", 21) == 0) snprintf(asked, sizeof asked, "%s", q->data + 21 + strspn(q->data + 21, " "));
	}
	/* Only where the request goes out at 14.0 or later, which the version adapter decides. */
	if (!rfc822 || !asked[0] || !lc_version(e->host, asked, ver, sizeof ver) || atof(ver) < 14.0 - 1e-6) return;
	static unsigned counter;
	char client_id[40];
	snprintf(client_id, sizeof client_id, "lunacy%lx%x%x", (unsigned long)time(NULL), (unsigned)getpid(), ++counter);
	if (strlen(cs.item) + strlen(cs.folder) > sizeof e->cm_pre - 128) return;
	e->cm_pre_len = cm_prefix(&cs, client_id, (unsigned long)e->post_size, e->cm_pre);
	e->cm_pos = 0; e->cm_phase = 0;
	e->sending = 1;
	lc_real_setopt(h, LC_CURLOPT_POSTFIELDSIZE, (long)(e->cm_pre_len + (size_t)e->post_size + sizeof cm_suffix));
	lc_real_setopt(h, LC_CURLOPT_READFUNCTION, (void *)lc_read);
	if (e->their_seek) lc_real_setopt(h, LC_CURLOPT_SEEKFUNCTION, (void *)lc_seek);
	lc_apply_headers(e);
}

int curl_easy_setopt(CURL *h, int option, ...)
{
	lc_init();
	va_list ap;
	va_start(ap, option);
	if (option >= LC_OFF_T_BASE && option < LC_OFF_T_BASE + 10000) {
		long long v = va_arg(ap, long long);
		va_end(ap);
		return lc_real_setopt(h, option, v);
	}
	void *v = va_arg(ap, void *);
	va_end(ap);
	if (option == LC_CURLOPT_READFUNCTION || option == LC_CURLOPT_READDATA || option == LC_CURLOPT_SEEKFUNCTION || option == LC_CURLOPT_SEEKDATA) {
		pthread_mutex_lock(&lc_lock);
		struct lc_handle *e = lc_find(h, 1);
		int ours = 0;
		if (e) {
			if (option == LC_CURLOPT_READFUNCTION) e->their_read = (lc_read_fn)v;
			else if (option == LC_CURLOPT_READDATA) e->read_data = v;
			else if (option == LC_CURLOPT_SEEKFUNCTION) { e->their_seek = (lc_seek_fn)v; ours = e->sending && v; }
			else e->seek_data = v;
		}
		pthread_mutex_unlock(&lc_lock);
		if (ours) return lc_real_setopt(h, option, (void *)lc_seek);
	}
	if (option == LC_CURLOPT_POSTFIELDSIZE || option == LC_CURLOPT_POSTFIELDS) {
		pthread_mutex_lock(&lc_lock);
		struct lc_handle *e = lc_find(h, 1);
		if (e && option == LC_CURLOPT_POSTFIELDSIZE) e->post_size = (long)v;
		if (e && option == LC_CURLOPT_POSTFIELDS) { e->post_ptr = v; e->post_fresh = v != NULL; }
		/* Palm sets the body before the URL: it is marked here and swapped once the server is known. */
		if (e && option == LC_CURLOPT_POSTFIELDS)
			e->initial_provision = v && e->post_size == (long)(sizeof lc_provision_head + sizeof lc_provision_rest)
				&& memcmp(v, lc_provision_head, sizeof lc_provision_head) == 0
				&& memcmp((char *)v + sizeof lc_provision_head, lc_provision_rest, sizeof lc_provision_rest) == 0;
		pthread_mutex_unlock(&lc_lock);
	}
	if (option == LC_CURLOPT_WRITEFUNCTION || option == LC_CURLOPT_WRITEDATA || option == 10002 /* URL: a new request */) {
		pthread_mutex_lock(&lc_lock);
		struct lc_handle *e = lc_find(h, 1);
		if (e) {
			if (option == LC_CURLOPT_WRITEDATA) e->write_data = v;
			else if (option == LC_CURLOPT_WRITEFUNCTION) e->their_write = (lc_header_fn)v;
			else {
				e->body_seen = 0; e->provision = 0; e->filtering = 0; e->host[0] = 0;
				e->held = 0; bo_reset(&e->bo);
				const char *u = v, *hs = u ? strstr(u, "://") : NULL;
				snprintf(e->url, sizeof e->url, "%s", u ? u : "");
				if (hs) { hs += 3; size_t n = strcspn(hs, "/:?"); if (n < sizeof e->host) { memcpy(e->host, hs, n); e->host[n] = 0; } }
				char ver[16];
				if (e->initial_provision && lc_version(e->host, "12.1", ver, sizeof ver)) {
					size_t len;
					unsigned char *b = lc_with_device_info(&len);
					if (b) {
						free(e->post); e->post = b; e->post_ptr = b; e->post_size = (long)len;
						lc_real_setopt(h, LC_CURLOPT_POSTFIELDSIZE, (long)len);
						lc_real_setopt(h, LC_CURLOPT_POSTFIELDS, (void *)b);
					}
				}
				e->initial_provision = 0;
				/* A Sync going out at 16.x: its calendar items as such a server takes them (calfilter.h). */
				if (u && strstr(u, "Cmd=Sync") && e->post_fresh && e->post_ptr && e->post_size > 0 && lc_version(e->host, "12.1", ver, sizeof ver) && atof(ver) >= 16.0 - 1e-6) {
					unsigned char *b = malloc(CALF_OUT_CAP((size_t)e->post_size));
					size_t len = b ? calf_rewrite(e->post_ptr, (size_t)e->post_size, b) : 0;
					if (len && len != (size_t)-1) {
						free(e->post); e->post = b; e->post_ptr = b; e->post_size = (long)len;
						lc_real_setopt(h, LC_CURLOPT_POSTFIELDSIZE, (long)len);
						lc_real_setopt(h, LC_CURLOPT_POSTFIELDS, (void *)b);
						fprintf(stderr, "lunacy: calendar items in a Sync to %s rewritten for %s\n", e->host, ver);
					} else free(b);
					/* A series' Exceptions as Changes and Deletes of their own occurrences. */
					size_t slen = 0;
					unsigned char *sb = calf_split(e->post_ptr, (size_t)e->post_size, &slen);
					if (sb) {
						free(e->post); e->post = sb; e->post_ptr = sb; e->post_size = (long)slen;
						lc_real_setopt(h, LC_CURLOPT_POSTFIELDSIZE, (long)slen);
						lc_real_setopt(h, LC_CURLOPT_POSTFIELDS, (void *)sb);
						fprintf(stderr, "lunacy: a series' exceptions in a Sync to %s sent as their own occurrences\n", e->host);
					}
				}
				/* A Sync to any other EAS server (the legacy Exchange account type): only the zones
				   Palm builds, in Windows' form (calfilter.h, calf_fix_zones); nothing else changes. */
				else if (u && strstr(u, "Cmd=Sync") && e->post_fresh && e->post_ptr && e->post_size > 0) {
					unsigned char *b = malloc((size_t)e->post_size);
					if (b && calf_fix_zones(e->post_ptr, (size_t)e->post_size, b)) {
						free(e->post); e->post = b; e->post_ptr = b;
						lc_real_setopt(h, LC_CURLOPT_POSTFIELDSIZE, e->post_size);
						lc_real_setopt(h, LC_CURLOPT_POSTFIELDS, (void *)b);
						fprintf(stderr, "lunacy: an event's time zone in a Sync to %s written in Windows' form\n", e->host);
					} else free(b);
				}
				if (e->sending && e->their_read) lc_real_setopt(h, LC_CURLOPT_READFUNCTION, (void *)e->their_read);   /* the last request's */
				lc_send_setup(h, e, u);
			}
		}
		pthread_mutex_unlock(&lc_lock);
		if (e && option == LC_CURLOPT_WRITEFUNCTION && v) return lc_real_setopt(h, option, (void *)lc_write);
	}
	if (option == LC_CURLOPT_HEADERFUNCTION || option == LC_CURLOPT_HEADERDATA) {
		pthread_mutex_lock(&lc_lock);
		struct lc_handle *e = lc_find(h, 1);
		if (e) {
			if (option == LC_CURLOPT_HEADERDATA) e->header_data = v;
			else e->their_header = (lc_header_fn)v;
		}
		pthread_mutex_unlock(&lc_lock);
		if (e && option == LC_CURLOPT_HEADERFUNCTION && v) return lc_real_setopt(h, option, (void *)lc_header);
	}
	if (option == LC_CURLOPT_USERPWD) {
		/* Whatever this handle carried for an earlier request, this one says afresh. */
		pthread_mutex_lock(&lc_lock);
		struct lc_handle *was = lc_find(h, 0);
		if (was && was->handle[0]) { was->handle[0] = 0; memset(was->bearer, 0, sizeof was->bearer); lc_real_setopt(h, LC_CURLOPT_HTTPHEADER, was->theirs); }
		if (was) { was->no_token = 0; was->token_final = 0; }
		pthread_mutex_unlock(&lc_lock);
	}
	if (option == LC_CURLOPT_USERPWD && v) {
		const char *up = v, *colon = strchr(up, ':');
		if (colon && ma_is_handle(colon + 1)) {
			char user[512];
			size_t ul = (size_t)(colon - up) < sizeof user - 1 ? (size_t)(colon - up) : sizeof user - 1;
			memcpy(user, up, ul); user[ul] = 0;
			pthread_mutex_lock(&lc_lock);
			struct lc_handle *e = lc_find(h, 1);
			if (e) {
				int stale = strcmp(ma_refused, colon + 1) == 0;
				int t = ma_token(NULL, user, colon + 1, stale, e->bearer, sizeof e->bearer);
				if (t < 0) {
					/* No token just now: no Basic password with the handle either. The request goes
					   nowhere when it starts (lc_hold) and the service tries again later, as IMAP's
					   connection is dropped (mailauth.h). */
					snprintf(e->handle, sizeof e->handle, "%s", colon + 1);
					memset(e->bearer, 0, sizeof e->bearer);
					e->no_token = 1;
					lc_real_setopt(h, LC_CURLOPT_USERPWD, (void *)NULL);
					lc_real_setopt(h, LC_CURLOPT_HTTPAUTH, 0L);
					int r = lc_apply_headers(e);
					pthread_mutex_unlock(&lc_lock);
					return r;
				}
				if (t > 0) {
					/* A refusal of this one is final: it is fresh (2), a retry's, or empty (the sign-in
					   is over, and Email is to ask for it again). */
					e->token_final = t == 2 || stale || !e->bearer[0];
					if (stale) ma_refused[0] = 0;
					snprintf(e->handle, sizeof e->handle, "%s", colon + 1);
					lc_real_setopt(h, LC_CURLOPT_USERPWD, (void *)NULL);
					lc_real_setopt(h, LC_CURLOPT_HTTPAUTH, 0L);
					int r = lc_apply_headers(e);
					pthread_mutex_unlock(&lc_lock);
					return r;
				}
				e->handle[0] = 0;   /* unknown handle: an ordinary password after all */
			}
			pthread_mutex_unlock(&lc_lock);
		}
	}
	if (option == LC_CURLOPT_HTTPHEADER) {
		pthread_mutex_lock(&lc_lock);
		struct lc_handle *e = lc_find(h, 1);   /* kept even before any credentials: they may come later */
		if (e) {
			e->theirs = v;
			int change = e->handle[0] != 0;
			char ver[16];
			for (struct curl_slist *q = v; q && !change; q = q->next)
				if (strncasecmp(q->data, "MS-ASProtocolVersion:", 21) == 0 && lc_version(e->host, q->data + 21 + strspn(q->data + 21, " "), ver, sizeof ver)) change = 1;
			if (change) { int r = lc_apply_headers(e); pthread_mutex_unlock(&lc_lock); return r; }
		}
		pthread_mutex_unlock(&lc_lock);
	}
	return lc_real_setopt(h, option, v);
}

/* A server's 401 to a bearer request: its token was refused (mailauth.h's ma_refused). */
int curl_easy_getinfo(CURL *h, int info, ...)
{
	static int (*real)(CURL *, int, ...);
	if (!real) real = ma_next("curl_easy_getinfo");
	va_list ap;
	va_start(ap, info);
	void *out = va_arg(ap, void *);
	va_end(ap);
	int r = real(h, info, out);
	if (r == 0 && info == LC_CURLINFO_RESPONSE_CODE && out && *(long *)out == 200) {
		pthread_mutex_lock(&lc_lock);
		struct lc_handle *e = lc_find(h, 0);
		if (e && e->provision) *(long *)out = 449;
		else if (e && e->sending && e->send_status && e->send_status != 1) *(long *)out = 500;   /* the send failed: the service retries */
		pthread_mutex_unlock(&lc_lock);
	}
	if (r == 0 && info == LC_CURLINFO_RESPONSE_CODE && out && *(long *)out == 401) {
		pthread_mutex_lock(&lc_lock);
		struct lc_handle *e = lc_find(h, 0);
		if (e && e->handle[0]) {
			snprintf(ma_refused, sizeof ma_refused, "%s", e->handle);
			/* The first refusal of a token that should have worked (the provider ended it early):
			   a server error to the service, which tries again, and that try asks for a fresh
			   one. Mojomail-eas takes a 401 as a wrong password and asks for a new sign-in. */
			if (!e->token_final) *(long *)out = 500;
		}
		pthread_mutex_unlock(&lc_lock);
	}
	return r;
}

void curl_easy_cleanup(CURL *h)
{
	static void (*real)(CURL *);
	if (!real) real = ma_next("curl_easy_cleanup");
	lc_init();
	real(h);
	pthread_mutex_lock(&lc_lock);
	struct lc_handle *e = lc_find(h, 0);
	if (e) {
		if (e->ours) lc_slist_free(e->ours);
		free(e->post);
		memset(e, 0, sizeof *e);
	}
	pthread_mutex_unlock(&lc_lock);
}

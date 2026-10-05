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
 *   of /usr, /media, /var, /etc/palm, /etc/openal or /home/root, so a path under them is
 *   looked up in Lunacy's webOS root (LUNACY_PDK_ROOT), which has the same tree. One rule
 *   for every app; nothing else is touched.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <dirent.h>
#include <fcntl.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>

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

static const char *const webos_dirs[] = { "/usr/", "/media/", "/var/", "/etc/palm/", "/etc/openal/", "/home/root/", NULL };

/* The path to use for `path`: in the webOS root if it is a webOS path, else itself. */
static const char *map(const char *path, char *buf)
{
	static const char *root; static size_t root_len; static int looked;
	if (!looked) { root = getenv("LUNACY_PDK_ROOT"); root_len = root ? strlen(root) : 0; looked = 1; }
	if (!path || path[0] != '/' || !root_len) return path;
	for (int i = 0; webos_dirs[i]; i++) {
		size_t n = strlen(webos_dirs[i]);
		if (strncmp(path, webos_dirs[i], n) == 0 || (strncmp(path, webos_dirs[i], n - 1) == 0 && path[n - 1] == 0)) {
			if (root_len + strlen(path) + 1 > PATH_MAX) return path;
			memcpy(buf, root, root_len); strcpy(buf + root_len, path);
			return buf;
		}
	}
	return path;
}

/* glibc keeps these for old binaries but no longer declares them. */
int __xstat(int, const char *, struct stat *); int __lxstat(int, const char *, struct stat *);
int __xstat64(int, const char *, struct stat64 *); int __lxstat64(int, const char *, struct stat64 *);
#define REAL(name) static __typeof__(&name) real_##name; if (!real_##name) real_##name = (__typeof__(&name))dlsym(RTLD_NEXT, #name)
#define MAPPED(p) char buf_[PATH_MAX]; const char *mp_ = map(p, buf_)

ssize_t readlink(const char *path, char *buf, size_t len)
{
	REAL(readlink);
	if (is_self_exe(path)) { ssize_t r = answer(buf, len); if (r >= 0) return r; }
	MAPPED(path);
	return real_readlink(mp_, buf, len);
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

int open(const char *path, int flags, ...)
{
	REAL(open);
	MODE_ARG(flags); MAPPED(path);
	return real_open(mp_, flags, mode);
}
int open64(const char *path, int flags, ...)
{
	REAL(open64);
	MODE_ARG(flags); MAPPED(path);
	return real_open64(mp_, flags, mode);
}
int openat(int dirfd, const char *path, int flags, ...)
{
	REAL(openat);
	MODE_ARG(flags); MAPPED(path);
	return real_openat(dirfd, mp_, flags, mode);
}
int openat64(int dirfd, const char *path, int flags, ...)
{
	REAL(openat64);
	MODE_ARG(flags); MAPPED(path);
	return real_openat64(dirfd, mp_, flags, mode);
}
int creat(const char *path, mode_t mode)
{
	REAL(creat);
	MAPPED(path); return real_creat(mp_, mode);
}
FILE *fopen(const char *path, const char *how)
{
	REAL(fopen);
	MAPPED(path); return real_fopen(mp_, how);
}
FILE *fopen64(const char *path, const char *how)
{
	REAL(fopen64);
	MAPPED(path); return real_fopen64(mp_, how);
}
FILE *freopen(const char *path, const char *how, FILE *f)
{
	REAL(freopen);
	MAPPED(path); return real_freopen(mp_, how, f);
}
FILE *freopen64(const char *path, const char *how, FILE *f)
{
	REAL(freopen64);
	MAPPED(path); return real_freopen64(mp_, how, f);
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

int access(const char *path, int how) { REAL(access); MAPPED(path); return real_access(mp_, how); }
DIR *opendir(const char *path) { REAL(opendir); MAPPED(path); return real_opendir(mp_); }
int mkdir(const char *path, mode_t mode) { REAL(mkdir); MAPPED(path); return real_mkdir(mp_, mode); }
int rmdir(const char *path) { REAL(rmdir); MAPPED(path); return real_rmdir(mp_); }
int unlink(const char *path) { REAL(unlink); MAPPED(path); return real_unlink(mp_); }
int remove(const char *path) { REAL(remove); MAPPED(path); return real_remove(mp_); }
int chdir(const char *path) { REAL(chdir); MAPPED(path); return real_chdir(mp_); }
int truncate(const char *path, off_t len) { REAL(truncate); MAPPED(path); return real_truncate(mp_, len); }
int rename(const char *from, const char *to)
{
	REAL(rename);
	char b1[PATH_MAX], b2[PATH_MAX];
	return real_rename(map(from, b1), map(to, b2));
}

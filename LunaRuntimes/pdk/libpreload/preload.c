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
 *
 * - Android's own system folders, /system and /vendor, which a TouchPad didn't have. They
 *   are looked up in the webOS root too, where they don't exist either. apkenv's bionic
 *   linker (the Android ports, Docs/pdk.md "EGL and the Android ports") searches
 *   /vendor/lib and /system/lib before its own bionic: on webOS it found nothing there,
 *   on Android it loaded the device's libc.so and quit on its pthread_gettid_np.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
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

static const char *const webos_dirs[] = { "/usr/", "/media/", "/var/", "/etc/palm/", "/etc/openal/", "/etc/ssl/", "/etc/resolv.conf/", "/etc/hosts/", "/home/root/", "/system/", "/vendor/", NULL };

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

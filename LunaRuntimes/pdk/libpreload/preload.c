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

static const char *const webos_dirs[] = { "/usr/", "/media/", "/var/", "/tmp/", "/etc/palm/", "/etc/openal/", "/etc/ssl/", "/etc/resolv.conf/", "/etc/mtab/", "/etc/hosts/", "/home/root/", "/system/", "/vendor/", NULL };

static const char *webos_root(size_t *len)
{
	static const char *root; static size_t root_len; static int looked;
	if (!looked) { root = getenv("LUNACY_PDK_ROOT"); root_len = root ? strlen(root) : 0; looked = 1; }
	if (len) *len = root_len;
	return root_len ? root : NULL;
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
	for (at = spec; *at; ) {
		const char *end = at + strcspn(at, ":"), *eq = memchr(at, '=', end - at);
		if (eq && (size_t)(eq - at) == n && strncasecmp(at, rest, n) == 0) {
			size_t dir = end - eq - 1;
			if (dir + strlen(rest + n) + 1 > PATH_MAX) return NULL;
			memcpy(buf, eq + 1, dir); strcpy(buf + dir, rest + n);
			return buf;
		}
		at = *end ? end + 1 : end;
	}
	return NULL;
}

/* The path to use for `path`: in the webOS root if it is a webOS path, else itself. */
static const char *map(const char *path, char *buf)
{
	size_t root_len;
	const char *root = webos_root(&root_len), *m;
	if (!path || path[0] != '/' || !root) return path;
	if ((m = media(path, buf))) return m;
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

/* These go through the *at calls: glibc makes ARM's legacy calls for them (chmod 15, rmdir
   40...), which Android's app seccomp policy refuses, and the TouchPad's file cache could make
   no file. The *at forms are the calls bionic itself uses. */
int access(const char *path, int how) { MAPPED(path); return faccessat(AT_FDCWD, mp_, how, 0); }
DIR *opendir(const char *path) { REAL(opendir); MAPPED(path); return real_opendir(mp_); }
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

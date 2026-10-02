/*
 * Preloaded into every PDK app run through the glibc loader (Docs/pdk.md). The kernel
 * exec'd the loader, so /proc/self/exe names libld-linux.so, where on a device it named
 * the app's binary; a game that finds its data from its own path (Transformers G1:
 * readlink("/proc/self/exe"), then the folder it is in) reads the wrong place and gives
 * up. These answer that one link with the binary the shell started, from LUNACY_PDK_EXE.
 * Under qemu the emulator already answers with the guest binary, and this isn't loaded.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>

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

ssize_t readlink(const char *path, char *buf, size_t len)
{
	static ssize_t (*real)(const char *, char *, size_t);
	if (is_self_exe(path)) { ssize_t r = answer(buf, len); if (r >= 0) return r; }
	if (!real) real = dlsym(RTLD_NEXT, "readlink");
	return real(path, buf, len);
}

ssize_t readlinkat(int dirfd, const char *path, char *buf, size_t len)
{
	static ssize_t (*real)(int, const char *, char *, size_t);
	if (is_self_exe(path)) { ssize_t r = answer(buf, len); if (r >= 0) return r; }
	if (!real) real = dlsym(RTLD_NEXT, "readlinkat");
	return real(dirfd, path, buf, len);
}

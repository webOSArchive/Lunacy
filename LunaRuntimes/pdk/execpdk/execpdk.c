/*
 * libexecpdk.so: lets a PDK app's start script run the app's binary (Docs/pdk.md, "Start scripts").
 *
 * An app's "main" may be a shell script rather than its binary (ScummVM's `start` makes its
 * folders in /media/internal, then `exec`s bin/scummvm). LunaSysMgr exec'd it and the kernel
 * ran it with /bin/sh. Here the script runs in busybox's sh, a bionic program, with this
 * library as its LD_PRELOAD: an exec of a webOS binary (32-bit ARM, its interpreter not
 * Android's) goes through the same command the shell starts a PDK binary with - the glibc
 * loader, or qemu - which the kernel can't do by itself. Every other exec is left as it is.
 *
 *   LUNACY_PDK_EXEC          the command before the binary, one argument a line
 *   LUNACY_PDK_GUEST_PRELOAD becomes the binary's LD_PRELOAD (the glibc preload, native path)
 *   LUNACY_PDK_HOST_LIBS     becomes its LD_LIBRARY_PATH (qemu's own libraries)
 *
 * The binary runs as the `.lunacy.` copy beside it when there is one (PdkRuntime.runnable),
 * and LUNACY_PDK_EXE names it for the glibc preload's /proc/self/exe.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <stdlib.h>
#include "webos_exec.h"

int execve(const char *path, char *const argv[], char *const envp[])
{
	static int (*real)(const char *, char *const[], char *const[]);
	if (!real) real = (int (*)(const char *, char *const[], char *const[]))dlsym(RTLD_NEXT, "execve");
	/* No execve behind this one is a broken runtime: fail the exec, don't call a null pointer. */
	if (!real) { errno = ENOSYS; return -1; }
	return webos_exec(getenv("LUNACY_PDK_EXEC"), real, path, argv, envp);
}

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
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/* Whether path is a webOS binary: ELF, 32-bit ARM, with an interpreter outside Android's own. */
static int webos_binary(const char *path)
{
	int fd = open(path, O_RDONLY | O_CLOEXEC);
	if (fd < 0) return 0;
	Elf32_Ehdr eh;
	int yes = 0;
	if (read(fd, &eh, sizeof eh) == sizeof eh && memcmp(eh.e_ident, ELFMAG, SELFMAG) == 0 &&
	    eh.e_ident[EI_CLASS] == ELFCLASS32 && eh.e_machine == EM_ARM && eh.e_phentsize == sizeof(Elf32_Phdr)) {
		for (int i = 0; i < eh.e_phnum; i++) {
			Elf32_Phdr ph;
			if (pread(fd, &ph, sizeof ph, eh.e_phoff + i * sizeof ph) != sizeof ph) break;
			if (ph.p_type != PT_INTERP) continue;
			char interp[64] = { 0 };
			size_t n = ph.p_filesz < sizeof interp - 1 ? ph.p_filesz : sizeof interp - 1;
			if (pread(fd, interp, n, ph.p_offset) == (ssize_t)n)
				yes = strncmp(interp, "/system/", 8) != 0 && strncmp(interp, "/apex/", 6) != 0;
			break;
		}
	}
	close(fd);
	return yes;
}

static int starts(const char *s, const char *prefix) { return strncmp(s, prefix, strlen(prefix)) == 0; }

int execve(const char *path, char *const argv[], char *const envp[])
{
	static int (*real)(const char *, char *const[], char *const[]);
	if (!real) real = (int (*)(const char *, char *const[], char *const[]))dlsym(RTLD_NEXT, "execve");
	const char *prefix = getenv("LUNACY_PDK_EXEC");
	if (!prefix || !path || !webos_binary(path)) return real(path, argv, envp);

	/* The `.lunacy.` copy, when the shell made one. */
	char target[4096];
	const char *slash = strrchr(path, '/');
	int dirlen = slash ? (int)(slash - path + 1) : 0;
	snprintf(target, sizeof target, "%.*s.lunacy.%s", dirlen, path, slash ? slash + 1 : path);
	if (access(target, X_OK) != 0) snprintf(target, sizeof target, "%s", path);

	/* argv: the command, the binary, then the binary's own arguments. */
	char *words = strdup(prefix);
	int nprefix = 1, nargs = 0;
	for (const char *c = words; *c; c++) if (*c == '\n') nprefix++;
	while (argv && argv[nargs]) nargs++;
	char **nargv = calloc(nprefix + nargs + 2, sizeof *nargv);
	int k = 0;
	for (char *w = strtok(words, "\n"); w; w = strtok(NULL, "\n")) nargv[k++] = w;
	nargv[k++] = target;
	for (int i = 1; i < nargs; i++) nargv[k++] = argv[i];

	/* envp: without this library, the binary named, and the binary's own preload and libraries. */
	int nenv = 0;
	while (envp && envp[nenv]) nenv++;
	char **nenvp = calloc(nenv + 4, sizeof *nenvp);
	int e = 0;
	for (int i = 0; i < nenv; i++)
		if (!starts(envp[i], "LD_PRELOAD=") && !starts(envp[i], "LD_LIBRARY_PATH=") && !starts(envp[i], "LUNACY_PDK_EXE="))
			nenvp[e++] = envp[i];
	char exe[4200], preload[4200], libs[4200];
	snprintf(exe, sizeof exe, "LUNACY_PDK_EXE=%s", target); nenvp[e++] = exe;
	const char *v;
	if ((v = getenv("LUNACY_PDK_GUEST_PRELOAD"))) { snprintf(preload, sizeof preload, "LD_PRELOAD=%s", v); nenvp[e++] = preload; }
	if ((v = getenv("LUNACY_PDK_HOST_LIBS"))) { snprintf(libs, sizeof libs, "LD_LIBRARY_PATH=%s", v); nenvp[e++] = libs; }

	int r = real(nargv[0], nargv, nenvp);
	int saved = errno;
	free(nargv); free(nenvp); free(words);
	errno = saved;
	return r;
}

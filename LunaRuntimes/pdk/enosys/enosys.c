/*
 * libenosys.so: runs a PDK app's glibc process on Android 10 and later, answering the system
 * calls Android refuses it with -ENOSYS (Docs/pdk.md, "Android 10 and later").
 *
 * An app's seccomp policy kills a process that makes a call the policy doesn't list (SIGSYS).
 * The TouchPad's kernel had every call its glibc knew, and Debian's 32-bit glibc 2.36 makes
 * calls Android refuses an app: set_robust_list and rseq as the loader sets up the first
 * thread, and again as each new thread starts. glibc goes on without them when the kernel
 * says it hasn't the call, so each refused call answers -ENOSYS, as a kernel without it would.
 *
 * That has to happen outside the process: glibc makes the calls before any preload is loaded,
 * and as a thread starts with every signal blocked, where a SIGSYS can't be caught and kills
 * the process whatever handler it has. So the app runs as this program's ptrace child. The
 * kernel stops it only for a refused call, a signal, a new thread or the exec; its other
 * calls run as they would untraced. Every other signal is passed on as it came.
 *
 *   libenosys.so <program> [args...]
 *
 * LUNACY_PDK_PRELOAD becomes the program's LD_PRELOAD: this is a bionic program, and bionic's
 * linker would refuse the glibc preload as LD_PRELOAD for this one. The exit status is the
 * program's, 128 + the signal when a signal ended it, as the shell reads a process's status.
 * If this program is killed, the kernel kills the app with it (PTRACE_O_EXITKILL).
 */
#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ptrace.h>
#include <sys/uio.h>
#include <sys/user.h>
#include <sys/wait.h>
#include <unistd.h>
#include <elf.h>
#include <asm/ptrace.h>

#ifndef SYS_SECCOMP
#define SYS_SECCOMP 1
#endif

int main(int argc, char **argv)
{
	if (argc < 2) { fprintf(stderr, "usage: %s program [args...]\n", argv[0]); return 2; }
	pid_t app = fork();
	if (app < 0) { perror("enosys: fork"); return 1; }
	if (app == 0) {
		const char *preload = getenv("LUNACY_PDK_PRELOAD");
		if (preload) { setenv("LD_PRELOAD", preload, 1); unsetenv("LUNACY_PDK_PRELOAD"); }
		if (ptrace(PTRACE_TRACEME, 0, 0, 0) != 0) { perror("enosys: ptrace"); _exit(1); }
		raise(SIGSTOP);
		execv(argv[1], argv + 1);
		perror("enosys: exec");
		_exit(127);
	}
	int status;
	if (waitpid(app, &status, 0) != app || !WIFSTOPPED(status)) { fprintf(stderr, "enosys: the app didn't start\n"); return 1; }
	ptrace(PTRACE_SETOPTIONS, app, 0, (void *)(PTRACE_O_EXITKILL | PTRACE_O_TRACECLONE | PTRACE_O_TRACEEXEC));
	ptrace(PTRACE_CONT, app, 0, 0);

	static unsigned char told[1024];
	for (;;) {
		pid_t who = waitpid(-1, &status, __WALL);
		if (who < 0) {
			if (errno == EINTR) continue;
			return 1;
		}
		if (WIFEXITED(status) || WIFSIGNALED(status)) {
			if (who != app) continue;   /* one of its threads */
			return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
		}
		if (!WIFSTOPPED(status)) continue;
		int sig = WSTOPSIG(status);
		if (status >> 16) { ptrace(PTRACE_CONT, who, 0, 0); continue; }   /* a new thread, the exec */
		if (sig == SIGSYS) {
			siginfo_t si;
			if (ptrace(PTRACE_GETSIGINFO, who, 0, &si) == 0 && si.si_code == SYS_SECCOMP) {
				int nr = si.si_syscall;
				if (nr >= 0 && nr < (int)sizeof told && !told[nr]) {
					told[nr] = 1;
					fprintf(stderr, "lunacy: Android refused system call %d; answered ENOSYS\n", nr);
				}
				struct pt_regs regs;
				struct iovec io = { &regs, sizeof regs };
				if (ptrace(PTRACE_GETREGSET, who, (void *)NT_PRSTATUS, &io) == 0) {
					regs.ARM_r0 = (unsigned long)-ENOSYS;
					ptrace(PTRACE_SETREGSET, who, (void *)NT_PRSTATUS, &io);
				}
				ptrace(PTRACE_CONT, who, 0, 0);
				continue;
			}
		}
		/* A new thread starts stopped; any other signal goes on to the app. */
		ptrace(PTRACE_CONT, who, 0, (void *)(long)(sig == SIGSTOP ? 0 : sig));
	}
}

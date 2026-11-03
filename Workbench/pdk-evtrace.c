/*
 * Development tool (Docs/pdk.md 6i): preloaded into a PDK game after Lunacy's own, it logs the
 * video mode, orientation and mouse events the game receives, and on a crash the faulting PC and
 * LR with their library. Build with the runtime's compiler (AndroidLuna/tools/build-pdk.sh's $CC):
 *   $CC -O2 -shared -fPIC pdk-evtrace.c -o libevtrace.so -ldl
 * copy it into files/pdk/ and put LUNACY_PDK_PRELOAD=<lib>/liblunacy-preload.so:<files>/pdk/libevtrace.so
 * in files/pdk/env. Remove both when done.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <stdint.h>
typedef struct { uint8_t type; uint8_t a, b, c; uint16_t x, y; int16_t xr, yr; } Ev; /* SDL 1.2 layout enough for mouse */
int SDL_WaitEvent(void *e) {
	static int (*real)(void *); if (!real) real = dlsym(RTLD_NEXT, "SDL_WaitEvent");
	int r = real(e);
	if (r && e) { uint8_t *p = e; uint8_t t = p[0];
		if (t == 4) printf("evtrace motion which=%u state=%u x=%u y=%u\n", p[1], p[2], *(uint16_t*)(p+4), *(uint16_t*)(p+6));
		else if (t == 5 || t == 6) printf("evtrace button %s which=%u btn=%u x=%u y=%u\n", t==5?"down":"up", p[1], p[2], *(uint16_t*)(p+4), *(uint16_t*)(p+6));
		else printf("evtrace type=%u\n", t);
		fflush(stdout); }
	return r;
}
int SDL_PollEvent(void *e) {
	static int (*real)(void *); if (!real) real = dlsym(RTLD_NEXT, "SDL_PollEvent");
	int r = real(e);
	if (r && e) { uint8_t *p = e; uint8_t t = p[0]; if (t >= 4 && t <= 6) { printf("evtrace poll type=%u which=%u x=%u y=%u\n", t, p[1], *(uint16_t*)(p+4), *(uint16_t*)(p+6)); fflush(stdout);} }
	return r;
}
void *SDL_SetVideoMode(int w, int h, int bpp, uint32_t flags) {
	static void *(*real)(int,int,int,uint32_t); if (!real) real = dlsym(RTLD_NEXT, "SDL_SetVideoMode");
	printf("evtrace SetVideoMode %d x %d bpp %d flags 0x%x\n", w, h, bpp, flags); fflush(stdout);
	return real(w, h, bpp, flags);
}
int PDL_SetOrientation(int o) {
	static int (*real)(int); if (!real) real = dlsym(RTLD_NEXT, "PDL_SetOrientation");
	printf("evtrace PDL_SetOrientation %d\n", o); fflush(stdout); return real(o);
}
#include <signal.h>
#include <ucontext.h>
#include <string.h>
#include <unistd.h>
static void where(const char *what, unsigned long a) {
	Dl_info i; if (dladdr((void *)a, &i) && i.dli_fname) printf("evtrace   %s %08lx = %s+%lx (%s)\n", what, a, i.dli_fname, a - (unsigned long)i.dli_fbase, i.dli_sname ? i.dli_sname : "?");
	else printf("evtrace   %s %08lx\n", what, a);
}
static void segv(int sig, siginfo_t *si, void *uc) {
	ucontext_t *u = uc; mcontext_t *m = &u->uc_mcontext;
	printf("evtrace SIGNAL %d at addr %p\n", sig, si->si_addr);
	where("pc", m->arm_pc); where("lr", m->arm_lr);
	printf("evtrace   r0=%08lx r1=%08lx r2=%08lx r3=%08lx sp=%08lx\n", m->arm_r0, m->arm_r1, m->arm_r2, m->arm_r3, m->arm_sp);
	unsigned long *sp = (unsigned long *)m->arm_sp;
	for (int k = 0; k < 64; k++) { Dl_info i; if (dladdr((void *)sp[k], &i) && i.dli_fname && strstr(i.dli_fname, "applications")) where("stack", sp[k]); }
	fflush(stdout); signal(sig, SIG_DFL); raise(sig);
}
__attribute__((constructor)) static void init(void) { struct sigaction sa; memset(&sa, 0, sizeof sa); sa.sa_sigaction = segv; sa.sa_flags = SA_SIGINFO; sigaction(SIGSEGV, &sa, 0); sigaction(SIGBUS, &sa, 0); }

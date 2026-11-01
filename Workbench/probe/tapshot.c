/*
 * tapshot: a harness for a PDK app on the TouchPad, which has no remote touch. Scripted taps go in
 * through SDL_PollEvent and SDL_WaitEvent (an app's menu blocks in the second), and frames are
 * saved from SDL_GL_SwapBuffers as 1024 x 768 RGBA, bottom row first, to /media/internal/shotN.raw.
 *
 * /media/internal/inject.txt, times in ms from the first poll, positions in the app's px:
 *   t <time> <x> <y> <hold>
 *   s <time>
 * Built with the PDK's gcc as a shared library. A PDK app's exe path is checked against its Luna
 * role, so it can't be renamed behind a launcher: copy the app's binary, `patchelf --add-needed
 * <app dir>/inject.so`, and launch it as the app. Put the original back afterwards. Docs/pdk.md §6h.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/time.h>
#include "SDL.h"
#include <GLES2/gl2.h>
struct tap { int t, x, y, hold, st; };
static struct tap taps[64]; static int ntaps, shots[32], nshots, loaded;
static long t0;
static long now(void){ struct timeval v; gettimeofday(&v,0); return v.tv_sec*1000+v.tv_usec/1000; }
static void load(void){ if(loaded) return; loaded=1; t0=now(); FILE*f=fopen("/media/internal/inject.txt","r"); char c; int a,b,d,e;
 if(!f) return; char l[80]; while(fgets(l,80,f)){ if(sscanf(l,"t %d %d %d %d",&a,&b,&d,&e)==4){ taps[ntaps].t=a;taps[ntaps].x=b;taps[ntaps].y=d;taps[ntaps].hold=e;ntaps++; } else if(sscanf(l,"s %d",&a)==1) shots[nshots++]=a; } fclose(f); (void)c; }
int SDL_PollEvent(SDL_Event *e){
 static int (*real)(SDL_Event*); if(!real) real=dlsym(RTLD_NEXT,"SDL_PollEvent"); load();
 long t=now()-t0;
 for(int i=0;i<ntaps;i++){ struct tap*p=&taps[i];
  if(p->st==0&&t>=p->t){ p->st=1; if(!e) return 1; memset(e,0,sizeof *e); e->type=SDL_MOUSEBUTTONDOWN; e->button.button=1; e->button.state=SDL_PRESSED; e->button.x=p->x; e->button.y=p->y; return 1; }
  if(p->st==1&&t>=p->t+p->hold){ p->st=2; if(!e) return 1; memset(e,0,sizeof *e); e->type=SDL_MOUSEBUTTONUP; e->button.button=1; e->button.state=SDL_RELEASED; e->button.x=p->x; e->button.y=p->y; return 1; } }
 return real(e);
}
void SDL_GL_SwapBuffers(void){
 static void (*real)(void); if(!real) real=dlsym(RTLD_NEXT,"SDL_GL_SwapBuffers"); load();
 long t=now()-t0;
 for(int i=0;i<nshots;i++) if(shots[i]>=0&&t>=shots[i]){ char n[64]; sprintf(n,"/media/internal/shot%d.raw",i); shots[i]=-1;
  static unsigned char *b; if(!b) b=malloc(1024*768*4); glReadPixels(0,0,1024,768,GL_RGBA,GL_UNSIGNED_BYTE,b); FILE*f=fopen(n,"wb"); if(f){fwrite(b,1,1024*768*4,f);fclose(f);} break; }
 real();
}
int SDL_WaitEvent(SDL_Event *e){ while(!SDL_PollEvent(e)) usleep(10000); return 1; }

/* Lunacy probe: three routines in VFP short-vector mode (vectors.S), checked against plain C.
   Without the preload's rewrite (LunaRuntimes/pdk/libpreload/vfpvec.h) an ARMv8 CPU under a
   64-bit kernel gets "11 0 .. 0"; with it, or on a CPU that honours FPSCR.LEN, PASS. Build:
     /opt/PalmPDK/arm-gcc/bin/arm-none-linux-gnueabi-gcc -std=gnu99 -O1 -marm -mfpu=vfp \
       -mfloat-abi=softfp -Wl,-z,noexecstack -o vfpvec vfpvec.c vectors.S
   and run it through the runtime's loader with and without --preload (Docs/pdk.md 6k). */
#include <stdio.h>
void vadd8(float *, float *, float *); void mat4(float *, float *); void gainloop(float *, float *, int);
int main(void){
  float a[8]={1,2,3,4,5,6,7,8}, b[8]={10,20,30,40,50,60,70,80}, c[8]={0}; int bad=0;
  vadd8(a,b,c); for(int i=0;i<8;i++) bad += c[i]!=a[i]+b[i]; printf("vadd8: %g %g .. %g\n", c[0], c[1], c[7]);
  float m[16]; for(int i=0;i<16;i++) m[i]=i+1; float v[4]={1,2,3,4}, e[4];
  for(int r=0;r<4;r++){ e[r]=0; for(int k=0;k<4;k++) e[r]+=m[k*4+r]*v[k]; }
  mat4(m,v); for(int r=0;r<4;r++) bad += v[r]!=e[r]; printf("mat4: %g %g %g %g (want %g %g %g %g)\n", v[0],v[1],v[2],v[3], e[0],e[1],e[2],e[3]);
  float d[24], s[24], w[24]; for(int i=0;i<24;i++){ d[i]=i; s[i]=100+i; w[i]=d[i]+s[i]*0.5f; }
  gainloop(d,s,3); for(int i=0;i<24;i++) bad += d[i]!=w[i]; printf("gainloop: %g %g .. %g (want %g %g .. %g)\n", d[0], d[1], d[23], w[0], w[1], w[23]);
  printf(bad ? "FAIL %d\n" : "PASS\n", bad); return bad; }

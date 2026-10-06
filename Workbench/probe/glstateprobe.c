/*
 * glstateprobe: what an Android device's GLES 1.1 driver does with one fogged, textured quad
 * under the conditions Lunacy's GL server puts it in (Docs/pdk.md, "showing the frame").
 * It draws the quad at eye depth 500 with linear grey fog from 50 to 1000, reads the centre
 * pixel back and prints it: 0 255 0 is the quad unfogged, 60 194 60 the right blend.
 *
 * Build with the NDK for the device's ABI and run it inside Lunacy's sandbox through the
 * SDK's novacom relay (Docs/dev-workflow.md), where it uses the same driver the shell does:
 *   clang --target=armv7a-linux-androideabi21 -O2 -o glstateprobe glstateprobe.c -lEGL -lGLESv1_CM
 *   novacom put file:///tmp/glstateprobe < glstateprobe; novacom run -- file:///bin/busybox chmod 755 /tmp/glstateprobe
 *   novacom run -- file:///tmp/glstateprobe switch tex nofog
 *
 * Flags: nofog, fbo (draw into a framebuffer object as the server does), tex, combine, light,
 * vbo, persp, exp, nicest, fastest, range (a depth range), big, posz (a Direct3D-style
 * projection looking down +z), gameP / gameM (Tiger Woods PGA Tour's own matrices), negfog,
 * switch (another context current and back before the draw, what the server's old blit
 * context did at every swap), surfswitch (the same context on another surface and back),
 * and after a switch: rebind, rebind0, retex, reenv, fogtoggle, touchfog, reupload, finish.
 *
 * Measured 2026-10-05 on a Nexus 5 (Adreno 330, Android 6): every state combination fogs
 * right, and so does a surface switch, but after `switch` the texture samples black until
 * it is bound again (`rebind`): the driver's GLES 1 layer forgets GPU-side state another
 * context has been current over while its cache still says it is set. A Kyocera's Adreno
 * 620 and an HP 10 G2's Mali-450 keep it.
 */
#include <EGL/egl.h>
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
static int has(int argc, char **argv, const char *f) { for (int i = 1; i < argc; i++) if (!strcmp(argv[i], f)) return 1; return 0; }
int main(int argc, char **argv)
{
	int W = has(argc, argv, "big") ? 320 : 64, H = has(argc, argv, "big") ? 480 : 64;
	EGLDisplay d = eglGetDisplay(EGL_DEFAULT_DISPLAY); eglInitialize(d, 0, 0);
	EGLint a[] = { EGL_RENDERABLE_TYPE, EGL_OPENGL_ES_BIT, EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_DEPTH_SIZE, 16, EGL_NONE };
	EGLConfig cfg; EGLint n; eglChooseConfig(d, a, &cfg, 1, &n);
	int fbo = has(argc, argv, "fbo");
	EGLint pa[] = { EGL_WIDTH, fbo ? 1 : W, EGL_HEIGHT, fbo ? 1 : H, EGL_NONE };
	EGLSurface pb = eglCreatePbufferSurface(d, cfg, pa);
	EGLint ca[] = { EGL_CONTEXT_CLIENT_VERSION, 1, EGL_NONE };
	EGLContext c = eglCreateContext(d, cfg, EGL_NO_CONTEXT, ca);
	if (!eglMakeCurrent(d, pb, pb, c)) { printf("no context\n"); return 1; }
	if (fbo) {
		void (*bind_fb)(GLenum, GLuint) = (void *)eglGetProcAddress("glBindFramebufferOES");
		void (*fb_tex)(GLenum, GLenum, GLenum, GLuint, GLint) = (void *)eglGetProcAddress("glFramebufferTexture2DOES");
		void (*gen_fb)(GLsizei, GLuint *) = (void *)eglGetProcAddress("glGenFramebuffersOES");
		void (*gen_rb)(GLsizei, GLuint *) = (void *)eglGetProcAddress("glGenRenderbuffersOES");
		void (*bind_rb)(GLenum, GLuint) = (void *)eglGetProcAddress("glBindRenderbufferOES");
		void (*rb_storage)(GLenum, GLenum, GLsizei, GLsizei) = (void *)eglGetProcAddress("glRenderbufferStorageOES");
		void (*fb_rb)(GLenum, GLenum, GLenum, GLuint) = (void *)eglGetProcAddress("glFramebufferRenderbufferOES");
		GLenum (*status)(GLenum) = (void *)eglGetProcAddress("glCheckFramebufferStatusOES");
		GLuint fb, rb; gen_fb(1, &fb); gen_rb(1, &rb);
		glBindTexture(GL_TEXTURE_2D, 0x7FFFFF02u);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glBindTexture(GL_TEXTURE_2D, 0);
		bind_fb(GL_FRAMEBUFFER_OES, fb);
		fb_tex(GL_FRAMEBUFFER_OES, GL_COLOR_ATTACHMENT0_OES, GL_TEXTURE_2D, 0x7FFFFF02u, 0);
		bind_rb(GL_RENDERBUFFER_OES, rb);
		rb_storage(GL_RENDERBUFFER_OES, GL_DEPTH_COMPONENT16_OES, W, H);
		fb_rb(GL_FRAMEBUFFER_OES, GL_DEPTH_ATTACHMENT_OES, GL_RENDERBUFFER_OES, rb);
		printf("fbo status %#x\n", status(GL_FRAMEBUFFER_OES));
	}
	glViewport(0, 0, W, H);
	glClearColor(0, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	glEnable(GL_DEPTH_TEST);
	if (has(argc, argv, "range")) glDepthRangef(0.1f, 1.0f);
	glMatrixMode(GL_PROJECTION); glLoadIdentity();
	int posz = has(argc, argv, "posz") || has(argc, argv, "gameP");
	/* Tiger Woods PGA Tour's own matrices at a course draw, read back on the Nexus 5. */
	static const GLfloat gameP[16] = { -6.24334e-08f, -0.952207f, 0, 0,  1.42831f, -4.16223e-08f, 0, 0,  0, 0, 1.00001f, 1,  0, 0, -0.500003f, 0 };
	static const GLfloat gameM[16] = { 0.946314f, 0.0531434f, 0.318852f, 0,  3.72529e-09f, 0.986393f, -0.164403f, 0,  -0.323251f, 0.155577f, 0.933437f, 0,  5.752f, -30.4179f, -13.3552f, 1 };
	if (has(argc, argv, "gameP")) glLoadMatrixf(gameP);
	else if (posz) { /* a Direct3D-style perspective: the eye looks down +z, clip w = +z */
		GLfloat P[16] = { 1, 0, 0, 0,  0, 1, 0, 0,  0, 0, 2000.0f / 1999, 1,  0, 0, -2 * 2000.0f / 1999, 0 };
		glLoadMatrixf(P);
	} else if (has(argc, argv, "persp")) glFrustumf(-1, 1, -1, 1, 1, 2000); else glOrthof(-1, 1, -1, 1, 1, 2000);
	glMatrixMode(GL_MODELVIEW); glLoadIdentity();
	int gm = has(argc, argv, "gameM");
	if (gm) glLoadMatrixf(gameM);
	if (!has(argc, argv, "nofog")) {
		glEnable(GL_FOG);
		glFogf(GL_FOG_MODE, has(argc, argv, "exp") ? GL_EXP : GL_LINEAR);
		float sg = has(argc, argv, "negfog") ? -1 : 1;
		glFogf(GL_FOG_START, 50 * sg); glFogf(GL_FOG_END, 1000 * sg); glFogf(GL_FOG_DENSITY, 0.003f * sg);
		GLfloat fc[4] = { 0.5f, 0.5f, 0.5f, 1 }; glFogfv(GL_FOG_COLOR, fc);
		if (has(argc, argv, "nicest")) glHint(GL_FOG_HINT, GL_NICEST);
		if (has(argc, argv, "fastest")) glHint(GL_FOG_HINT, GL_FASTEST);
	}
	if (has(argc, argv, "light")) {
		glEnable(GL_LIGHTING); glEnable(GL_LIGHT0); glEnable(GL_COLOR_MATERIAL);
		GLfloat amb[4] = { 1, 1, 1, 1 }; glLightModelfv(GL_LIGHT_MODEL_AMBIENT, amb);
	}
	if (has(argc, argv, "tex")) {
		static const unsigned char g[16] = { 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255 };
		glBindTexture(GL_TEXTURE_2D, 7); glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 2, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, g);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glEnable(GL_TEXTURE_2D);
		if (has(argc, argv, "combine")) { glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_COMBINE); glTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_RGB, GL_MODULATE); glTexEnvf(GL_TEXTURE_ENV, GL_RGB_SCALE, 2.0f); }
		glColor4f(has(argc, argv, "combine") ? 0.5f : 1, has(argc, argv, "combine") ? 0.5f : 1, has(argc, argv, "combine") ? 0.5f : 1, 1);
	} else glColor4f(0, 1, 0, 1);
	float z = posz ? 500 : -500;
	GLfloat v[] = { -1, -1, z,  1, -1, z,  -1, 1, z,  1, 1, z };
	if (has(argc, argv, "persp") || posz) for (int i = 0; i < 4; i++) { v[i * 3] *= 500; v[i * 3 + 1] *= 500; }
	if (gm) { /* the quad's eye-space corners, taken back to world space through the inverse of the rigid gameM */
		for (int i = 0; i < 4; i++) {
			float ex = v[i * 3], ey = v[i * 3 + 1], ez = v[i * 3 + 2];
			float px = ex - gameM[12], py = ey - gameM[13], pz = ez - gameM[14];
			v[i * 3] = gameM[0] * px + gameM[1] * py + gameM[2] * pz;
			v[i * 3 + 1] = gameM[4] * px + gameM[5] * py + gameM[6] * pz;
			v[i * 3 + 2] = gameM[8] * px + gameM[9] * py + gameM[10] * pz;
		}
	}
	if (has(argc, argv, "surfswitch")) { /* the same context on another surface, then back */
		EGLint pa2[] = { EGL_WIDTH, 8, EGL_HEIGHT, 8, EGL_NONE }; EGLSurface pb2 = eglCreatePbufferSurface(d, cfg, pa2);
		printf("surf %d ", eglMakeCurrent(d, pb2, pb2, c)); glClearColor(1, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT); eglSwapBuffers(d, pb2);
		printf("back %d ", eglMakeCurrent(d, pb, pb, c));
		glClearColor(0, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	}
	if (has(argc, argv, "switch")) { /* what the GL server does at every swap: another context current, then back */
		EGLContext c2 = eglCreateContext(d, cfg, c, ca);
		EGLint pa2[] = { EGL_WIDTH, 8, EGL_HEIGHT, 8, EGL_NONE }; EGLSurface pb2 = eglCreatePbufferSurface(d, cfg, pa2);
		eglMakeCurrent(d, pb2, pb2, c2); glClearColor(1, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT); eglSwapBuffers(d, pb2);
		eglMakeCurrent(d, pb, pb, c);
		if (has(argc, argv, "switchclear")) glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		if (has(argc, argv, "rebind")) glBindTexture(GL_TEXTURE_2D, 7);
		if (has(argc, argv, "rebind0")) { glBindTexture(GL_TEXTURE_2D, 0); glBindTexture(GL_TEXTURE_2D, 7); }
		if (has(argc, argv, "retex")) { glDisable(GL_TEXTURE_2D); glEnable(GL_TEXTURE_2D); }
		if (has(argc, argv, "reenv")) glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);
		if (has(argc, argv, "fogtoggle")) { glDisable(GL_FOG); glEnable(GL_FOG); }
		if (has(argc, argv, "touchfog")) { glFogf(GL_FOG_START, 51); glFogf(GL_FOG_START, 50); glFogf(GL_FOG_END, 999); glFogf(GL_FOG_END, 1000); glFogf(GL_FOG_MODE, GL_EXP); glFogf(GL_FOG_MODE, GL_LINEAR); }
		if (has(argc, argv, "reupload")) { static const unsigned char g[16] = { 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255 }; glBindTexture(GL_TEXTURE_2D, 7); glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 2, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, g); }
		if (has(argc, argv, "finish")) glFinish();
	}
	static const GLfloat uv[] = { 0, 0, 1, 0, 0, 1, 1, 1 };
	glEnableClientState(GL_VERTEX_ARRAY);
	if (has(argc, argv, "tex")) { glEnableClientState(GL_TEXTURE_COORD_ARRAY); glTexCoordPointer(2, GL_FLOAT, 0, uv); }
	if (has(argc, argv, "vbo")) {
		GLuint b; glGenBuffers(1, &b); glBindBuffer(GL_ARRAY_BUFFER, b); glBufferData(GL_ARRAY_BUFFER, sizeof v, v, GL_STATIC_DRAW);
		glVertexPointer(3, GL_FLOAT, 0, 0);
	} else glVertexPointer(3, GL_FLOAT, 0, v);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
	glFinish();
	unsigned char px[4] = { 0 };
	glReadPixels(W / 2, H / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
	printf("pixel %d %d %d %d err %#x\n", px[0], px[1], px[2], px[3], glGetError());
	return 0;
}

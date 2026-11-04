/*
 * The GL link between a PDK app's GLES libraries (client_transport.h, client_prelude.c,
 * client2_prelude.c) and the shell's replay (gl_server.c): its message types and the
 * operations a batch carries besides the GL calls themselves. Both ends include this, so
 * they can't drift apart. The generators (gen_gles1.py, gen_gles2.py) number the GL calls
 * below these and carry their own copies of ARRAY_OP and ATTRIB_OP, which must match.
 */
#ifndef LUNACY_GL_PROTOCOL_H
#define LUNACY_GL_PROTOCOL_H

/* Message types on the socket (lunacy_protocol.h numbers the rest). */
#define LPDK_GL        6   /* to the shell: a batch */
#define LPDK_GL_PIXELS 7   /* to the app: a glReadPixels' bytes */
#define LPDK_GL_ACK    8   /* to the app: a swap was shown */
#define LPDK_GL_RESULT 9   /* to the app: a query's answer (GLES 2) */

/* Operations in a batch that aren't GL calls. */
#define ARRAY_OP  0xFFFFu   /* a client-side array's bytes (GLES 1.1) */
#define READ_OP   0xFFFEu   /* glReadPixels from the app's framebuffer */
#define SWAP_OP   0xFFFDu   /* eglSwapBuffers */
#define ATTRIB_OP 0xFFFCu   /* a vertex attribute's bytes (GLES 2) */

#endif

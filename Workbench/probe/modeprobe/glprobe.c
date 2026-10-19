/* Lunacy's probe: the GL strings and state a GLES 1.1 PDK game sees once its mode is set.
   Run from /tmp over novacom; prints and exits. */
#include <SDL.h>
#include <PDL.h>
#include <GLES/gl.h>
#include <stdio.h>
#include <unistd.h>
int main(int argc, char **argv) {
    SDL_Init(SDL_INIT_VIDEO);
    PDL_Init(0);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 1);
    SDL_Surface *s = SDL_SetVideoMode(0, 0, 0, SDL_OPENGL);
    if (!s) { printf("no mode: %s\n", SDL_GetError()); return 1; }
    printf("surface %d x %d\n", s->w, s->h);
    printf("GL_VENDOR '%s'\nGL_RENDERER '%s'\nGL_VERSION '%s'\nGL_EXTENSIONS '%s'\n",
        glGetString(GL_VENDOR), glGetString(GL_RENDERER), glGetString(GL_VERSION), glGetString(GL_EXTENSIONS));
    GLint vp[4] = {0}, mts = 0; glGetIntegerv(GL_VIEWPORT, vp); glGetIntegerv(GL_MAX_TEXTURE_SIZE, &mts);
    printf("viewport %d %d %d %d, max texture %d\n", vp[0], vp[1], vp[2], vp[3], mts);
    PDL_Quit(); SDL_Quit();
    return 0;
}

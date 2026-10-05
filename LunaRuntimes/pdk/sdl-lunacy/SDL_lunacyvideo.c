/*
    SDL - Simple DirectMedia Layer
    Copyright (C) 1997-2012 Sam Lantinga

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    The Lunacy video driver, written from the dummy driver (Docs/pdk.md): the screen
    surface is a file the shell mmaps too, and a frame is a message on the socket.
*/
#include "SDL_config.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <dlfcn.h>

#include "SDL_video.h"
#include "SDL_mouse.h"
#include "../SDL_sysvideo.h"
#include "../SDL_pixels_c.h"
#include "../../events/SDL_events_c.h"

#include "SDL_lunacyvideo.h"
#include "SDL_lunacyevents_c.h"
#include "lunacy_protocol.h"

#define LUNACYVID_DRIVER_NAME "lunacy"

int LUNACY_control_sock = -1;

/* ---- the socket ---- */

/** Connects to the shell's abstract Unix socket and sends the one-byte hello. -1 if it can't. */
int LUNACY_Connect(const char *hello)
{
	const char *name = getenv("LUNACY_PDK_SOCKET");
	struct sockaddr_un addr;
	int s, len;
	if (!name || !*name) { SDL_SetError("LUNACY_PDK_SOCKET is not set"); return -1; }
	s = socket(AF_UNIX, SOCK_STREAM, 0);
	if (s < 0) { SDL_SetError("socket: %s", strerror(errno)); return -1; }
	memset(&addr, 0, sizeof addr);
	addr.sun_family = AF_UNIX;
	addr.sun_path[0] = 0;  /* abstract namespace, as Android's LocalServerSocket listens */
	strncpy(addr.sun_path + 1, name, sizeof(addr.sun_path) - 2);
	len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(name);
	if (connect(s, (struct sockaddr *)&addr, len) < 0) {
		SDL_SetError("connect %s: %s", name, strerror(errno));
		close(s);
		return -1;
	}
	if (write(s, hello, 1) != 1) { close(s); return -1; }
	return s;
}

static int write_all(int fd, const void *p, size_t n)
{
	const char *c = p;
	while (n) {
		ssize_t w = write(fd, c, n);
		if (w < 0) { if (errno == EINTR) continue; if (errno == EAGAIN) { usleep(1000); continue; } return -1; }
		c += w; n -= w;
	}
	return 0;
}

int LUNACY_Send(int sock, uint32_t type, uint32_t a, const void *payload, uint32_t len)
{
	uint32_t h[3] = { type, a, len };
	if (sock < 0) return -1;
	if (write_all(sock, h, sizeof h) < 0) return -1;
	if (len && write_all(sock, payload, len) < 0) return -1;
	return 0;
}

/* ---- the driver ---- */

static int LUNACY_VideoInit(_THIS, SDL_PixelFormat *vformat);
static SDL_Rect **LUNACY_ListModes(_THIS, SDL_PixelFormat *format, Uint32 flags);
static SDL_Surface *LUNACY_SetVideoMode(_THIS, SDL_Surface *current, int width, int height, int bpp, Uint32 flags);
static int LUNACY_SetColors(_THIS, int firstcolor, int ncolors, SDL_Color *colors);
static void LUNACY_VideoQuit(_THIS);
static int LUNACY_AllocHWSurface(_THIS, SDL_Surface *surface);
static int LUNACY_LockHWSurface(_THIS, SDL_Surface *surface);
static void LUNACY_UnlockHWSurface(_THIS, SDL_Surface *surface);
static void LUNACY_FreeHWSurface(_THIS, SDL_Surface *surface);
static void LUNACY_UpdateRects(_THIS, int numrects, SDL_Rect *rects);
static int LUNACY_FlipHWSurface(_THIS, SDL_Surface *surface);
static void LUNACY_SetCaption(_THIS, const char *title, const char *icon);
static int LUNACY_GL_LoadLibrary(_THIS, const char *path);
static void *LUNACY_GL_GetProcAddress(_THIS, const char *proc);
static int LUNACY_GL_GetAttribute(_THIS, SDL_GLattr attrib, int *value);
static int LUNACY_GL_MakeCurrent(_THIS);
static void LUNACY_GL_SwapBuffers(_THIS);

static int LUNACY_Available(void)
{
	const char *envr = SDL_getenv("SDL_VIDEODRIVER");
	return envr && SDL_strcmp(envr, LUNACYVID_DRIVER_NAME) == 0;
}

static void LUNACY_DeleteDevice(SDL_VideoDevice *device)
{
	SDL_free(device->hidden);
	SDL_free(device);
}

static SDL_VideoDevice *LUNACY_CreateDevice(int devindex)
{
	SDL_VideoDevice *device = (SDL_VideoDevice *)SDL_malloc(sizeof(SDL_VideoDevice));
	if (device) {
		SDL_memset(device, 0, sizeof *device);
		device->hidden = (struct SDL_PrivateVideoData *)SDL_malloc(sizeof *device->hidden);
	}
	if (!device || !device->hidden) {
		SDL_OutOfMemory();
		if (device) SDL_free(device);
		return 0;
	}
	SDL_memset(device->hidden, 0, sizeof *device->hidden);
	device->hidden->fb_fd = -1;
	device->hidden->sock = -1;

	device->VideoInit = LUNACY_VideoInit;
	device->ListModes = LUNACY_ListModes;
	device->SetVideoMode = LUNACY_SetVideoMode;
	device->CreateYUVOverlay = NULL;
	device->SetColors = LUNACY_SetColors;
	device->UpdateRects = LUNACY_UpdateRects;
	device->VideoQuit = LUNACY_VideoQuit;
	device->AllocHWSurface = LUNACY_AllocHWSurface;
	device->CheckHWBlit = NULL;
	device->FillHWRect = NULL;
	device->SetHWColorKey = NULL;
	device->SetHWAlpha = NULL;
	device->LockHWSurface = LUNACY_LockHWSurface;
	device->UnlockHWSurface = LUNACY_UnlockHWSurface;
	device->FlipHWSurface = LUNACY_FlipHWSurface;
	device->FreeHWSurface = LUNACY_FreeHWSurface;
	device->SetCaption = LUNACY_SetCaption;
	device->SetIcon = NULL;
	device->IconifyWindow = NULL;
	device->GrabInput = NULL;
	device->GetWMInfo = NULL;
	device->InitOSKeymap = LUNACY_InitOSKeymap;
	device->PumpEvents = LUNACY_PumpEvents;
	device->free = LUNACY_DeleteDevice;
	/* OpenGL ES, as a PDK game asked SDL for it (Docs/pdk.md, "Transformers G1"): the
	   calls themselves go through libGLES_CM.so, which the game links; SDL only has to
	   grant the mode and carry the swap. */
	device->GL_LoadLibrary = LUNACY_GL_LoadLibrary;
	device->GL_GetProcAddress = LUNACY_GL_GetProcAddress;
	device->GL_GetAttribute = LUNACY_GL_GetAttribute;
	device->GL_MakeCurrent = LUNACY_GL_MakeCurrent;
	device->GL_SwapBuffers = LUNACY_GL_SwapBuffers;
	return device;
}

VideoBootStrap LUNACY_bootstrap = {
	LUNACYVID_DRIVER_NAME, "Lunacy shared-framebuffer video driver",
	LUNACY_Available, LUNACY_CreateDevice
};

int LUNACY_VideoInit(_THIS, SDL_PixelFormat *vformat)
{
	/* 32-bit, R G B X in memory: what an Android ARGB_8888 bitmap holds, so the shell
	   copies the file into one without converting. An app that asks for 8 or 16 bits
	   gets SDL's shadow surface and a conversion on each flip. */
	vformat->BitsPerPixel = 32;
	vformat->BytesPerPixel = 4;
	vformat->Rmask = 0x000000ff;
	vformat->Gmask = 0x0000ff00;
	vformat->Bmask = 0x00ff0000;
	/* The current mode (SDL_GetVideoInfo, and SetVideoMode(0, 0)) is the device's screen,
	   held landscape: PDK games start from landscape (codepoet), and the TouchPad's own mode
	   was its 1024 x 768. On a portrait screen (the phone profile's Pre3, 480 x 800) the
	   mode is turned, so a game that asks for the native mode draws landscape and the card
	   turns its buffer, as it does one an app sized itself. PDL_GetScreenMetrics still
	   reports the screen as measured. */
	{
		int w = getenv("LUNACY_PDK_SCREEN_W") ? atoi(getenv("LUNACY_PDK_SCREEN_W")) : 1024;
		int h = getenv("LUNACY_PDK_SCREEN_H") ? atoi(getenv("LUNACY_PDK_SCREEN_H")) : 768;
		this->info.current_w = w > h ? w : h;
		this->info.current_h = w > h ? h : w;
	}
	/* LUNACY_PDK_NOSHELL: a desk test under qemu with no shell to reach; the mode is
	   granted, frames and events go nowhere. */
	if (getenv("LUNACY_PDK_NOSHELL")) { this->hidden->sock = -1; return 0; }
	this->hidden->sock = LUNACY_Connect("V");
	if (this->hidden->sock < 0) return -1;
	fcntl(this->hidden->sock, F_SETFL, fcntl(this->hidden->sock, F_GETFL) | O_NONBLOCK);
	LUNACY_control_sock = this->hidden->sock;
	return 0;
}

SDL_Rect **LUNACY_ListModes(_THIS, SDL_PixelFormat *format, Uint32 flags)
{
	return (SDL_Rect **)-1;  /* any size: the shell scales the frame to the card */
}

static void unmap(_THIS)
{
	if (this->hidden->buffer) {
		/* A mapped framebuffer file (2D), or malloc'd pixels (GL): told apart by the fd. */
		if (this->hidden->fb_fd >= 0) munmap(this->hidden->buffer, this->hidden->buffer_len); else SDL_free(this->hidden->buffer);
		this->hidden->buffer = NULL;
	}
	if (this->hidden->fb_fd >= 0) { close(this->hidden->fb_fd); this->hidden->fb_fd = -1; }
}

SDL_Surface *LUNACY_SetVideoMode(_THIS, SDL_Surface *current, int width, int height, int bpp, Uint32 flags)
{
	const char *path = getenv("LUNACY_PDK_FB");
	size_t len;
	int32_t mode[3];
	Uint32 rmask = 0, gmask = 0, bmask = 0;

	/* Palm's SDL_OPENGLES (0x40), the PDK's own flag for a GLES context, is an OpenGL mode
	   here: SDL's own checks look for SDL_OPENGL, so both flags are kept on the surface. */
	if (flags & (SDL_OPENGL | 0x40)) {
		flags |= SDL_OPENGL;
		/* The GL library draws and the shell shows it; the screen surface still gets
		   pixels of its own, because SDL clears and blits the screen surface as it sets
		   the mode up whatever the flags, and a game may touch it too. */
		unmap(this);
		this->hidden->buffer = SDL_malloc((size_t)width * height * 4);
		if (!this->hidden->buffer) { SDL_OutOfMemory(); return NULL; }
		this->hidden->buffer_len = (size_t)width * height * 4;
		this->hidden->fb_fd = -1;
		SDL_memset(this->hidden->buffer, 0, this->hidden->buffer_len);
		if (!SDL_ReallocFormat(current, 32, 0x000000ff, 0x0000ff00, 0x00ff0000, 0)) return NULL;
		current->flags = SDL_OPENGL | (flags & (SDL_FULLSCREEN | 0x40));
		this->hidden->w = current->w = width;
		this->hidden->h = current->h = height;
		this->hidden->bpp = 0;
		current->pitch = width * 4;
		current->pixels = this->hidden->buffer;
		this->gl_config.driver_loaded = 1;
		mode[0] = width; mode[1] = height; mode[2] = 0;
		LUNACY_Send(this->hidden->sock, LPDK_VIDEO_MODE, 1, mode, sizeof mode);
		/* libGLES_CM's default viewport and scissor are the screen, as on a device. */
		{ void (*gl_mode)(int, int) = (void (*)(int, int))dlsym(RTLD_DEFAULT, "lunacy_gl_mode"); if (gl_mode) gl_mode(width, height); }
		return current;
	}
	if (!path || !*path) { SDL_SetError("LUNACY_PDK_FB is not set"); return NULL; }
	if (bpp != 16 && bpp != 32) bpp = 32;
	if (bpp == 32) { rmask = 0x000000ff; gmask = 0x0000ff00; bmask = 0x00ff0000; }
	else { rmask = 0xf800; gmask = 0x07e0; bmask = 0x001f; }   /* RGB_565, as Android's */

	unmap(this);
	len = (size_t)width * height * (bpp / 8);
	this->hidden->fb_fd = open(path, O_RDWR | O_CREAT, 0600);
	if (this->hidden->fb_fd < 0) { SDL_SetError("open %s: %s", path, strerror(errno)); return NULL; }
	if (ftruncate(this->hidden->fb_fd, len) < 0) { SDL_SetError("ftruncate: %s", strerror(errno)); unmap(this); return NULL; }
	this->hidden->buffer = mmap(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED, this->hidden->fb_fd, 0);
	if (this->hidden->buffer == MAP_FAILED) { this->hidden->buffer = NULL; SDL_SetError("mmap: %s", strerror(errno)); unmap(this); return NULL; }
	this->hidden->buffer_len = len;
	SDL_memset(this->hidden->buffer, 0, len);

	if (!SDL_ReallocFormat(current, bpp, rmask, gmask, bmask, 0)) {
		unmap(this);
		SDL_SetError("Couldn't allocate new pixel format for requested mode");
		return NULL;
	}
	current->flags = (flags & SDL_FULLSCREEN) | SDL_HWSURFACE;
	this->hidden->w = current->w = width;
	this->hidden->h = current->h = height;
	this->hidden->bpp = bpp;
	current->pitch = current->w * (bpp / 8);
	current->pixels = this->hidden->buffer;

	mode[0] = width; mode[1] = height; mode[2] = bpp;
	LUNACY_Send(this->hidden->sock, LPDK_VIDEO_MODE, 0, mode, sizeof mode);
	return current;
}

static int LUNACY_AllocHWSurface(_THIS, SDL_Surface *surface) { return -1; }
static void LUNACY_FreeHWSurface(_THIS, SDL_Surface *surface) { }
static int LUNACY_LockHWSurface(_THIS, SDL_Surface *surface) { return 0; }
static void LUNACY_UnlockHWSurface(_THIS, SDL_Surface *surface) { }

/* Every update is a whole frame to the shell, which copies the file once. */
static void LUNACY_UpdateRects(_THIS, int numrects, SDL_Rect *rects)
{
	LUNACY_Send(this->hidden->sock, LPDK_FRAME, 0, NULL, 0);
}

static int LUNACY_FlipHWSurface(_THIS, SDL_Surface *surface)
{
	LUNACY_Send(this->hidden->sock, LPDK_FRAME, 0, NULL, 0);
	return 0;
}

int LUNACY_SetColors(_THIS, int firstcolor, int ncolors, SDL_Color *colors)
{
	return 1;  /* the screen is never palettised; a palette lives on the shadow surface */
}

static void LUNACY_SetCaption(_THIS, const char *title, const char *icon)
{
	if (title) LUNACY_Send(this->hidden->sock, LPDK_CAPTION, 0, title, strlen(title));
}

static int LUNACY_GL_LoadLibrary(_THIS, const char *path)
{
	this->gl_config.driver_loaded = 1;
	return 0;
}

static void *LUNACY_GL_GetProcAddress(_THIS, const char *proc)
{
	return dlsym(RTLD_DEFAULT, proc);
}

static int LUNACY_GL_GetAttribute(_THIS, SDL_GLattr attrib, int *value)
{
	switch (attrib) {
	case SDL_GL_RED_SIZE: case SDL_GL_GREEN_SIZE: case SDL_GL_BLUE_SIZE: case SDL_GL_ALPHA_SIZE: *value = 8; break;
	case SDL_GL_DEPTH_SIZE: *value = 16; break;
	case SDL_GL_STENCIL_SIZE: *value = 8; break;
	case SDL_GL_BUFFER_SIZE: *value = 32; break;
	case SDL_GL_DOUBLEBUFFER: *value = 1; break;
	case 16: *value = 0; break;   /* Palm: SDL_GL_RETAINED_BACKING */
	case 17: *value = 1; break;   /* Palm: SDL_GL_CONTEXT_MAJOR_VERSION, a GLES 1.1 context */
	case 18: *value = 1; break;   /* Palm: SDL_GL_CONTEXT_MINOR_VERSION */
	default: *value = 0; break;
	}
	return 0;
}

static int LUNACY_GL_MakeCurrent(_THIS) { return 0; }

static void LUNACY_GL_SwapBuffers(_THIS)
{
	/* Lunacy's libGLES_CM.so puts the swap in its stream, after the frame's commands, and
	   waits there for the shell to keep up. Another GL library: tell the shell directly. */
	static int (*gl_swap)(void); static int looked;
	if (!looked) { gl_swap = (int (*)(void))dlsym(RTLD_DEFAULT, "lunacy_gl_swap"); looked = 1; }
	if (gl_swap && gl_swap()) return;
	LUNACY_Send(this->hidden->sock, LPDK_FRAME, 1, NULL, 0);
}

void LUNACY_VideoQuit(_THIS)
{
	if (this->screen && this->screen->pixels == this->hidden->buffer) this->screen->pixels = NULL;
	unmap(this);
	if (this->hidden->sock >= 0) { close(this->hidden->sock); this->hidden->sock = -1; LUNACY_control_sock = -1; }
}

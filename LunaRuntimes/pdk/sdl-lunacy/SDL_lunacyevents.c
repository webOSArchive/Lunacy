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

    The Lunacy event pump (Docs/pdk.md). The shell sends touches and keys down the control
    socket; each pump drains what has arrived and posts it as SDL events. A touch is a
    mouse event, as Palm's SDL delivered it: the first finger drives SDL's own mouse state
    and every finger's events carry its index in `which`, which is how PDK games told
    fingers apart.
*/
#include "SDL_config.h"

#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>

#include "SDL.h"
#include "../../events/SDL_sysevents.h"
#include "../../events/SDL_events_c.h"
#include "SDL_lunacyvideo.h"
#include "SDL_lunacyevents_c.h"
#include "lunacy_protocol.h"

/* Bytes read off the socket but not yet a whole message. */
static unsigned char pending[4096];
static size_t pending_len = 0;

static void touch(_THIS, uint32_t finger, int32_t action, int32_t x, int32_t y)
{
	SDL_Event e;
	if (finger >= 16) return;
	if (x < 0) x = 0; if (y < 0) y = 0;
	if (x >= this->hidden->w) x = this->hidden->w - 1;
	if (y >= this->hidden->h) y = this->hidden->h - 1;
	if (finger == 0) {
		/* SDL's own mouse: position, buttons, and the events that come with them. */
		if (action == 1) SDL_PrivateMouseMotion(SDL_BUTTON_LMASK, 0, x, y);
		else SDL_PrivateMouseButton(action == 0 ? SDL_PRESSED : SDL_RELEASED, SDL_BUTTON_LEFT, x, y);
		this->hidden->finger_down[0] = action != 2;
		return;
	}
	SDL_memset(&e, 0, sizeof e);
	if (action == 1) {
		e.type = SDL_MOUSEMOTION;
		e.motion.which = finger; e.motion.state = SDL_BUTTON_LMASK;
		e.motion.x = x; e.motion.y = y;
	} else {
		e.type = action == 0 ? SDL_MOUSEBUTTONDOWN : SDL_MOUSEBUTTONUP;
		e.button.which = finger; e.button.button = SDL_BUTTON_LEFT;
		e.button.state = action == 0 ? SDL_PRESSED : SDL_RELEASED;
		e.button.x = x; e.button.y = y;
	}
	this->hidden->finger_down[finger] = action != 2;
	SDL_PushEvent(&e);
}

static void key(int down, int32_t sym, int32_t unicode)
{
	SDL_keysym ks;
	SDL_memset(&ks, 0, sizeof ks);
	ks.scancode = 0;
	ks.sym = (SDLKey)sym;
	ks.mod = KMOD_NONE;
	ks.unicode = (Uint16)unicode;
	SDL_PrivateKeyboard(down ? SDL_PRESSED : SDL_RELEASED, &ks);
}

static void handle(_THIS, uint32_t type, uint32_t a, const unsigned char *p, uint32_t len)
{
	int32_t v[3];
	switch (type) {
	case LPDK_TOUCH:
		if (len < 12) return;
		memcpy(v, p, 12);
		touch(this, a, v[0], v[1], v[2]);
		break;
	case LPDK_KEY:
		if (len < 8) return;
		memcpy(v, p, 8);
		key(a != 0, v[0], v[1]);
		break;
	case LPDK_QUIT:
		SDL_PrivateQuit();
		break;
	case LPDK_ACTIVE:
		SDL_PrivateAppActive(a != 0, SDL_APPACTIVE | SDL_APPINPUTFOCUS);
		break;
	case LPDK_ACCEL: {
		extern int LUNACY_accel[3];   /* SDL_lunacyjoystick.c */
		if (len < 12) return;
		memcpy(v, p, 12);
		LUNACY_accel[0] = v[0]; LUNACY_accel[1] = v[1]; LUNACY_accel[2] = v[2];
		break;
	}
	default:
		break;  /* a PDL reply is read by libpdl, which drains its own messages */
	}
}

void LUNACY_PumpEvents(_THIS)
{
	int sock = this->hidden->sock;
	if (sock < 0) return;
	for (;;) {
		ssize_t n = read(sock, pending + pending_len, sizeof pending - pending_len);
		if (n < 0) {
			if (errno == EINTR) continue;
			break;  /* EAGAIN: nothing more now */
		}
		if (n == 0) { SDL_PrivateQuit(); this->hidden->sock = -1; LUNACY_control_sock = -1; return; }
		pending_len += n;
		for (;;) {
			uint32_t h[3], len;
			if (pending_len < LPDK_HEADER) break;
			memcpy(h, pending, LPDK_HEADER);
			len = h[2];
			if (len > sizeof pending - LPDK_HEADER) { pending_len = 0; break; }  /* malformed: drop it */
			if (pending_len < LPDK_HEADER + len) break;
			handle(this, h[0], h[1], pending + LPDK_HEADER, len);
			pending_len -= LPDK_HEADER + len;
			memmove(pending, pending + LPDK_HEADER + len, pending_len);
		}
		if (pending_len == sizeof pending) pending_len = 0;
	}
}

void LUNACY_InitOSKeymap(_THIS)
{
	/* Keys arrive as SDL keysyms already: the shell maps Android's. */
}

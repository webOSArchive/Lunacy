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

    The Lunacy audio driver, written from the dummy driver (Docs/pdk.md): each mixed
    buffer goes to the shell on the 'A' connection, and the shell plays it through an
    AudioTrack. The socket's own buffering is the pacing: the shell reads a buffer only as
    the track takes it, and a write here blocks until there is room.
*/
#include "SDL_config.h"

#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "SDL_rwops.h"
#include "SDL_timer.h"
#include "SDL_audio.h"
#include "../SDL_audiomem.h"
#include "../SDL_audio_c.h"
#include "../SDL_audiodev_c.h"
#include "SDL_lunacyaudio.h"
#include "../../video/dummy/lunacy_protocol.h"

/* From the video driver (SDL_lunacyvideo.c), whose header defines a video _THIS. */
extern int LUNACY_Connect(const char *hello);
extern int LUNACY_Send(int sock, uint32_t type, uint32_t a, const void *payload, uint32_t len);

#define LUNACYAUD_DRIVER_NAME "lunacy"

static int LUNACYAUD_OpenAudio(_THIS, SDL_AudioSpec *spec);
static void LUNACYAUD_WaitAudio(_THIS);
static void LUNACYAUD_PlayAudio(_THIS);
static Uint8 *LUNACYAUD_GetAudioBuf(_THIS);
static void LUNACYAUD_CloseAudio(_THIS);

static int LUNACYAUD_Available(void)
{
	const char *envr = SDL_getenv("SDL_AUDIODRIVER");
	return envr && SDL_strcmp(envr, LUNACYAUD_DRIVER_NAME) == 0;
}

static void LUNACYAUD_DeleteDevice(SDL_AudioDevice *device)
{
	SDL_free(device->hidden);
	SDL_free(device);
}

static SDL_AudioDevice *LUNACYAUD_CreateDevice(int devindex)
{
	SDL_AudioDevice *this = (SDL_AudioDevice *)SDL_malloc(sizeof(SDL_AudioDevice));
	if (this) {
		SDL_memset(this, 0, sizeof *this);
		this->hidden = (struct SDL_PrivateAudioData *)SDL_malloc(sizeof *this->hidden);
	}
	if (!this || !this->hidden) {
		SDL_OutOfMemory();
		if (this) SDL_free(this);
		return 0;
	}
	SDL_memset(this->hidden, 0, sizeof *this->hidden);
	this->hidden->sock = -1;
	this->OpenAudio = LUNACYAUD_OpenAudio;
	this->WaitAudio = LUNACYAUD_WaitAudio;
	this->PlayAudio = LUNACYAUD_PlayAudio;
	this->GetAudioBuf = LUNACYAUD_GetAudioBuf;
	this->CloseAudio = LUNACYAUD_CloseAudio;
	this->free = LUNACYAUD_DeleteDevice;
	return this;
}

AudioBootStrap LUNACYAUD_bootstrap = {
	LUNACYAUD_DRIVER_NAME, "Lunacy socket audio driver",
	LUNACYAUD_Available, LUNACYAUD_CreateDevice
};

static void LUNACYAUD_WaitAudio(_THIS)
{
	/* PlayAudio blocks on the socket, which the shell drains at the track's pace. */
}

static void LUNACYAUD_PlayAudio(_THIS)
{
	if (this->hidden->sock < 0) { SDL_Delay(this->hidden->write_delay); return; }
	if (LUNACY_Send(this->hidden->sock, LPDK_AUDIO_DATA, 0, this->hidden->mixbuf, this->hidden->mixlen) < 0) {
		close(this->hidden->sock);
		this->hidden->sock = -1;
	}
}

static Uint8 *LUNACYAUD_GetAudioBuf(_THIS)
{
	return this->hidden->mixbuf;
}

static void LUNACYAUD_CloseAudio(_THIS)
{
	if (this->hidden->sock >= 0) {
		LUNACY_Send(this->hidden->sock, LPDK_AUDIO_CLOSE, 0, NULL, 0);
		close(this->hidden->sock);
		this->hidden->sock = -1;
	}
	if (this->hidden->mixbuf) {
		SDL_FreeAudioMem(this->hidden->mixbuf);
		this->hidden->mixbuf = NULL;
	}
}

static int LUNACYAUD_OpenAudio(_THIS, SDL_AudioSpec *spec)
{
	int32_t open_msg[4];
	float bytes_per_sec;

	/* What the shell's AudioTrack takes: signed 16-bit, little-endian. */
	spec->format = AUDIO_S16LSB;
	if (spec->channels > 2) spec->channels = 2;
	SDL_CalculateAudioSpec(spec);

	this->hidden->mixlen = spec->size;
	this->hidden->mixbuf = (Uint8 *)SDL_AllocAudioMem(this->hidden->mixlen);
	if (!this->hidden->mixbuf) return -1;
	SDL_memset(this->hidden->mixbuf, spec->silence, spec->size);
	bytes_per_sec = (float)(((spec->format & 0xFF) / 8) * spec->channels * spec->freq);
	this->hidden->write_delay = (Uint32)((((float)spec->size) / bytes_per_sec) * 1000.0f);

	this->hidden->sock = LUNACY_Connect("A");
	if (this->hidden->sock < 0) {
		fprintf(stderr, "lunacy audio: no shell to play to (%s); running silent\n", SDL_GetError());
		return 0;  /* an app without sound is better than no app */
	}
	open_msg[0] = spec->freq; open_msg[1] = spec->format; open_msg[2] = spec->channels; open_msg[3] = spec->samples;
	LUNACY_Send(this->hidden->sock, LPDK_AUDIO_OPEN, 0, open_msg, sizeof open_msg);
	return 0;
}

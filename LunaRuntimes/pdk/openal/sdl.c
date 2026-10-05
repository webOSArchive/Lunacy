/**
 * OpenAL cross platform audio library
 * This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Library General Public License for more details.
 *
 * The SDL backend webOS's OpenAL Soft 1.11.753 played through (Docs/pdk.md), written again
 * for Lunacy from what Palm's build shows of it: the TouchPad's libopenal.so.1 has
 * Alc/sdl.c with sdl_load, sdl_open_playback, sdl_reset_playback, sdl_callback,
 * sdl_stop_playback, sdl_close_playback and no capture, loads libSDL.so with dlopen, names
 * its device "Simple Directmedia Layer", and its messages are the ones used below. So
 * OpenAL's mix goes out through SDL's own audio, which is Lunacy's SDL audio driver, as it
 * went through Palm's SDL on a device - and an app that opens SDL's audio itself as well
 * meets the same one device it met there.
 */

#include "config.h"

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <dlfcn.h>
#include "alMain.h"
#include "AL/al.h"
#include "AL/alc.h"

/* SDL 1.2's audio interface, as much of it as is called here. */
#define SDL_INIT_AUDIO 0x00000010
#define AUDIO_U8       0x0008
#define AUDIO_S16LSB   0x8010
typedef struct {
    int freq;
    unsigned short format;
    unsigned char channels;
    unsigned char silence;
    unsigned short samples;
    unsigned short padding;
    unsigned int size;
    void (*callback)(void *userdata, unsigned char *stream, int len);
    void *userdata;
} SDL_AudioSpec;

static const ALCchar sdl_device[] = "Simple Directmedia Layer";

static void *sdl_handle;
static int (*pSDL_InitSubSystem)(unsigned int);
static void (*pSDL_QuitSubSystem)(unsigned int);
static unsigned int (*pSDL_WasInit)(unsigned int);
static int (*pSDL_OpenAudio)(SDL_AudioSpec *, SDL_AudioSpec *);
static void (*pSDL_PauseAudio)(int);
static void (*pSDL_CloseAudio)(void);
static char *(*pSDL_GetError)(void);

typedef struct {
    ALuint frameSize;
    ALboolean initedAudio;
    ALboolean opened;
} sdl_data;

static ALboolean sdl_load(void)
{
    if(sdl_handle)
        return AL_TRUE;
    sdl_handle = dlopen("libSDL.so", RTLD_NOW);
    if(!sdl_handle)
        return AL_FALSE;
#define LOAD(f) do { \
    p##f = dlsym(sdl_handle, #f); \
    if(!p##f) { \
        AL_PRINT("Could not load %s from libSDL.so: %s\n", #f, dlerror()); \
        dlclose(sdl_handle); sdl_handle = NULL; return AL_FALSE; \
    } } while(0)
    LOAD(SDL_InitSubSystem);
    LOAD(SDL_QuitSubSystem);
    LOAD(SDL_WasInit);
    LOAD(SDL_OpenAudio);
    LOAD(SDL_PauseAudio);
    LOAD(SDL_CloseAudio);
    LOAD(SDL_GetError);
#undef LOAD
    return AL_TRUE;
}

static void sdl_callback(void *userdata, unsigned char *stream, int len)
{
    ALCdevice *device = (ALCdevice*)userdata;
    sdl_data *data = (sdl_data*)device->ExtraData;
    aluMixData(device, stream, len / data->frameSize);
}

static ALCboolean sdl_open_playback(ALCdevice *device, const ALCchar *deviceName)
{
    sdl_data *data;

    if(!deviceName)
        deviceName = sdl_device;
    else if(strcmp(deviceName, sdl_device) != 0)
        return ALC_FALSE;
    if(!sdl_load())
        return ALC_FALSE;

    data = (sdl_data*)calloc(1, sizeof(sdl_data));
    if(!pSDL_WasInit(SDL_INIT_AUDIO))
    {
        if(pSDL_InitSubSystem(SDL_INIT_AUDIO) < 0)
        {
            AL_PRINT("SDL_InitSubSystem(SDL_INIT_AUDIO) failed: %s\n", pSDL_GetError());
            free(data);
            return ALC_FALSE;
        }
        data->initedAudio = AL_TRUE;
    }

    device->szDeviceName = strdup(deviceName);
    device->ExtraData = data;
    return ALC_TRUE;
}

static void sdl_close_playback(ALCdevice *device)
{
    sdl_data *data = (sdl_data*)device->ExtraData;

    if(data->initedAudio)
        pSDL_QuitSubSystem(SDL_INIT_AUDIO);
    free(data);
    device->ExtraData = NULL;
}

static ALCboolean sdl_reset_playback(ALCdevice *device)
{
    sdl_data *data = (sdl_data*)device->ExtraData;
    SDL_AudioSpec want, have;
    ALuint channels = aluChannelsFromFormat(device->Format);
    ALuint bytes = aluBytesFromFormat(device->Format);

    if(channels > 2)
    {
        AL_PRINT("Too many Channels: %d\n", channels);
        device->Format = (bytes == 1) ? AL_FORMAT_STEREO8 : AL_FORMAT_STEREO16;
        channels = 2;
    }
    memset(&want, 0, sizeof(want));
    switch(bytes)
    {
        case 1: want.format = AUDIO_U8; break;
        case 2: want.format = AUDIO_S16LSB; break;
        default:
            AL_PRINT("Unknown format: 0x%x\n", device->Format);
            device->Format = (channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
            want.format = AUDIO_S16LSB;
            break;
    }
    want.freq = device->Frequency;
    want.channels = channels;
    want.samples = device->UpdateSize;
    want.callback = sdl_callback;
    want.userdata = device;

    if(pSDL_OpenAudio(&want, &have) < 0)
    {
        AL_PRINT("SDL_OpenAudio failed: %s\n", pSDL_GetError());
        return ALC_FALSE;
    }
    data->opened = AL_TRUE;

    /* What SDL gave is what the mixer makes. */
    device->Frequency = have.freq;
    if(have.format == AUDIO_U8)
        device->Format = (have.channels == 1) ? AL_FORMAT_MONO8 : AL_FORMAT_STEREO8;
    else
        device->Format = (have.channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
    device->UpdateSize = have.samples;
    data->frameSize = aluBytesFromFormat(device->Format) * aluChannelsFromFormat(device->Format);

    SetDefaultChannelOrder(device);
    pSDL_PauseAudio(0);
    return ALC_TRUE;
}

static void sdl_stop_playback(ALCdevice *device)
{
    sdl_data *data = (sdl_data*)device->ExtraData;

    if(!data->opened)
        return;
    pSDL_PauseAudio(1);
    pSDL_CloseAudio();
    data->opened = AL_FALSE;
}

static ALCboolean sdl_open_capture(ALCdevice *device, const ALCchar *deviceName)
{
    (void)device;
    (void)deviceName;
    return ALC_FALSE;
}
static void sdl_close_capture(ALCdevice *device) { (void)device; }
static void sdl_start_capture(ALCdevice *device) { (void)device; }
static void sdl_stop_capture(ALCdevice *device) { (void)device; }
static void sdl_capture_samples(ALCdevice *device, ALCvoid *buffer, ALCuint samples)
{ (void)device; (void)buffer; (void)samples; }
static ALCuint sdl_available_samples(ALCdevice *device) { (void)device; return 0; }

static BackendFuncs sdl_funcs = {
    sdl_open_playback,
    sdl_close_playback,
    sdl_reset_playback,
    sdl_stop_playback,
    sdl_open_capture,
    sdl_close_capture,
    sdl_start_capture,
    sdl_stop_capture,
    sdl_capture_samples,
    sdl_available_samples
};

void alc_sdl_init(BackendFuncs *func_list)
{
    *func_list = sdl_funcs;
}

void alc_sdl_deinit(void)
{
    if(sdl_handle)
        dlclose(sdl_handle);
    sdl_handle = NULL;
}

void alc_sdl_probe(int type)
{
    if(!sdl_load())
        return;
    if(type == DEVICE_PROBE)
        AppendDeviceList(sdl_device);
    else if(type == ALL_DEVICE_PROBE)
        AppendAllDeviceList(sdl_device);
}

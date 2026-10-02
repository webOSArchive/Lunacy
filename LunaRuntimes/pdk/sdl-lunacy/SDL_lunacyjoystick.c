/*
 * The accelerometer as SDL joystick 0, as webOS's SDL had it (Docs/pdk.md): measured on the
 * reference TouchPad with a probe built with the PDK's toolchain - one joystick, named
 * "webOS accelerometer", three axes, no buttons; at rest a magnitude of about 32768 (1 g);
 * axis 1 positive toward the top of the screen, axis 2 negative with the screen tilted back
 * (into the screen, the opposite of Android's z). Axis 0's sign (assumed positive to the
 * right) wasn't measurable at the pose the TouchPad was in.
 *
 * The shell sends the readings (LPDK_ACCEL, already in the device's frame and in these
 * units); the event reader keeps the latest, and an update reports what changed.
 * Built in place of SDL's Linux joystick driver (tools/build-pdk.sh).
 */
#include "SDL_config.h"
#include "SDL_joystick.h"
#include "../SDL_sysjoystick.h"
#include "../SDL_joystick_c.h"

int LUNACY_accel[3];   /* written by the event reader (SDL_lunacyevents.c) */

int SDL_SYS_JoystickInit(void) { return 1; }

const char *SDL_SYS_JoystickName(int index) { return index == 0 ? "webOS accelerometer" : NULL; }

int SDL_SYS_JoystickOpen(SDL_Joystick *joystick)
{
	if (joystick->index != 0) { SDL_SetError("No such joystick"); return -1; }
	joystick->naxes = 3; joystick->nbuttons = 0; joystick->nhats = 0; joystick->nballs = 0;
	return 0;
}

void SDL_SYS_JoystickUpdate(SDL_Joystick *joystick)
{
	for (int i = 0; i < 3; i++) {
		int v = LUNACY_accel[i];
		if (v > 32767) v = 32767;
		if (v < -32768) v = -32768;
		if (joystick->axes[i] != v) SDL_PrivateJoystickAxis(joystick, (Uint8)i, (Sint16)v);
	}
}

void SDL_SYS_JoystickClose(SDL_Joystick *joystick) { }

void SDL_SYS_JoystickQuit(void) { }

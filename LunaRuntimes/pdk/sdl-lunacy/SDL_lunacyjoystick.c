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
int LUNACY_accel_count;   /* readings so far, so libpdl can tell a new one */

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

/*
 * Palm's SDL exported SDL 1.3's haptic (force feedback) API, with no header in the PDK; FIFA
 * and Raging Thunder 2 declare and call it themselves. A TouchPad has no vibration motor, so
 * here, as on one, there are no haptic devices: nothing opens, and every call on a device
 * fails with SDL's error. The effect is an opaque pointer, as the callers pass it.
 */
#include "SDL_error.h"
typedef struct _SDL_Haptic SDL_Haptic;
#define NO_HAPTIC(r) { SDL_SetError("Haptic: There are 0 haptic devices available"); return r; }

DECLSPEC int SDLCALL SDL_NumHaptics(void) { return 0; }
DECLSPEC const char * SDLCALL SDL_HapticName(int index) NO_HAPTIC(NULL)
DECLSPEC SDL_Haptic * SDLCALL SDL_HapticOpen(int index) NO_HAPTIC(NULL)
DECLSPEC int SDLCALL SDL_HapticOpened(int index) { return 0; }
DECLSPEC int SDLCALL SDL_HapticIndex(SDL_Haptic *h) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_MouseIsHaptic(void) { return 0; }
DECLSPEC SDL_Haptic * SDLCALL SDL_HapticOpenFromMouse(void) NO_HAPTIC(NULL)
DECLSPEC int SDLCALL SDL_JoystickIsHaptic(SDL_Joystick *j) { return 0; }
DECLSPEC SDL_Haptic * SDLCALL SDL_HapticOpenFromJoystick(SDL_Joystick *j) NO_HAPTIC(NULL)
DECLSPEC void SDLCALL SDL_HapticClose(SDL_Haptic *h) {}
DECLSPEC int SDLCALL SDL_HapticNumEffects(SDL_Haptic *h) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticNumEffectsPlaying(SDL_Haptic *h) NO_HAPTIC(-1)
DECLSPEC unsigned int SDLCALL SDL_HapticQuery(SDL_Haptic *h) NO_HAPTIC(0)
DECLSPEC int SDLCALL SDL_HapticNumAxes(SDL_Haptic *h) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticEffectSupported(SDL_Haptic *h, void *effect) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticNewEffect(SDL_Haptic *h, void *effect) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticUpdateEffect(SDL_Haptic *h, int id, void *effect) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticRunEffect(SDL_Haptic *h, int id, Uint32 iterations) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticStopEffect(SDL_Haptic *h, int id) NO_HAPTIC(-1)
DECLSPEC void SDLCALL SDL_HapticDestroyEffect(SDL_Haptic *h, int id) {}
DECLSPEC int SDLCALL SDL_HapticGetEffectStatus(SDL_Haptic *h, int id) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticSetGain(SDL_Haptic *h, int gain) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticSetAutocenter(SDL_Haptic *h, int autocenter) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticPause(SDL_Haptic *h) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticUnpause(SDL_Haptic *h) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticStopAll(SDL_Haptic *h) NO_HAPTIC(-1)
/* Palm's own two, beyond SDL 1.3's: their arguments are unknown, and with no device unused. */
DECLSPEC int SDLCALL SDL_HapticMaxDuration(SDL_Haptic *h) NO_HAPTIC(-1)
DECLSPEC int SDLCALL SDL_HapticMinDuration(SDL_Haptic *h) NO_HAPTIC(-1)

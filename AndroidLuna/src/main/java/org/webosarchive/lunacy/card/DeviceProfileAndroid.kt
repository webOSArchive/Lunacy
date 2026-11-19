package org.webosarchive.lunacy.card

import android.content.Context
import android.os.Build

// DeviceProfile (in LunaCore)'s Android half: which profile this screen is, and the device id
// and serial, from Android's ids and the app's preferences. Companion extensions, so callers
// still write DeviceProfile.nduid(context).

/**
     * The webOS device this screen is most like: a Pre3 on a phone, a TouchPad on a
     * tablet. [FormFactor] decides which, and the shell's own layout follows the same
     * decision, so apps and shell never disagree about what the machine is.
     */
fun DeviceProfile.Companion.forScreen(context: Context): DeviceProfile = if (FormFactor.isPhone(context)) DeviceProfile.PRE3 else DeviceProfile.TOUCHPAD

    /**
     * This device's serial, in HP's shape for this profile. Derived from the
     * same hardware ids as the device id, so it too survives a reinstall - some apps send
     * it to webOS Archive's services as well - and made up rather than copied: it says
     * "a TouchPad", not "codepoet's TouchPad".
     */
fun DeviceProfile.Companion.serial(context: Context, profile: DeviceProfile): String =
    DeviceProfile.serialFor(profile, DeviceProfile.derivedNduid(context))

    /**
     * webOS's device id: 40 hex digits.
     *
     * This is the identity webOS Archive's services know a device by - the App Museum and
     * the shared updater library both send it as `clientid` - and app licences were tied
     * to it too. So two things matter about it: it has to be **stable**, and its owner has
     * to be able to **carry one over**.
     *
     * Stable: it is derived from this device's own hardware ids rather than made up, so
     * reinstalling Lunacy gives the same id back, and a service's analytics don't see a
     * new device every time. It is a SHA-1 of them, which is 40 hex digits exactly, as a
     * webOS nduid is; the hardware ids themselves don't leave the device.
     *
     * Carried over: [setNduid] stores one the owner typed in - a TouchPad's own id, say,
     * when they are moving off hardware that is failing - and that one wins until they
     * ask for this device's own back ([clearNduid]). On a real device the id came from the
     * hardware's token and couldn't be changed, but it was never a secret either: every
     * app could read it.
     */
fun DeviceProfile.Companion.nduid(context: Context): String =
        FormFactor.prefs(context).getString("nduid", null)
            ?: derivedNduid(context)

    /** Whether the id is this device's own or one its owner carried over. */
fun DeviceProfile.Companion.nduidSource(context: Context): String =
        if (FormFactor.prefs(context).contains("nduid")) DeviceProfile.USER else DeviceProfile.DERIVED

    /**
     * This device's own id: the same after a reinstall, different on another device.
     *
     * ANDROID_ID survives reinstalling and changes on a factory reset, which is as close
     * to a webOS nduid as Android offers. The first id computed is kept in the app's
     * preferences and reused, because the ingredients drift under the device: from API 26
     * ANDROID_ID is per signing key, and from a target of 26 `Build.SERIAL` reads "unknown"
     * (the 64-bit build targets 28), so a build change would otherwise hand the same
     * device a new id. A reinstall still clears the preferences and recomputes the id
     * from the same hardware, which is what "the same after a reinstall" relies on.
     */
fun DeviceProfile.Companion.derivedNduid(context: Context): String {
        val prefs = FormFactor.prefs(context)
        prefs.getString("derivedNduid", null)?.let { return it }
        @Suppress("HardwareIds")
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
        @Suppress("DEPRECATION")
        val serial = Build.SERIAL.orEmpty()
        val seed = "lunacy-nduid:" + androidId + ":" + serial + ":" + Build.MANUFACTURER + ":" + Build.MODEL
        // A webOS nduid is 40 hex digits, which is exactly a SHA-1.
        val id = java.security.MessageDigest.getInstance("SHA-1")
            .digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
        prefs.edit().putString("derivedNduid", id).apply()
        return id
    }

    /**
     * Stores an id its owner typed in. Accepts it written with spaces or dashes and in any
     * case, which is how people copy one off an old device, and stores webOS's own form:
     * 40 lower-case hex digits. Returns the stored id, or null if it isn't one.
     */
fun DeviceProfile.Companion.setNduid(context: Context, raw: String): String? {
        val id = raw.filterNot { it == ' ' || it == '-' || it == ':' }.lowercase()
        if (!Regex("^[0-9a-f]{40}$").matches(id)) return null
        FormFactor.prefs(context).edit().putString("nduid", id).apply()
        return id
    }

    /** Back to this device's own id. Deterministic, so nothing sees a new device. */
fun DeviceProfile.Companion.clearNduid(context: Context): String {
        FormFactor.prefs(context).edit().remove("nduid").apply()
        return derivedNduid(context)
    }

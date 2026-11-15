package org.webosarchive.lunacy.card


/**
 * The webOS device Lunacy reports itself as.
 *
 * Lunacy is not pretending to be webOS - the shell, the bus and Device Info all say plainly
 * what they are - but to an *app* it has to be a webOS device, because that is the contract
 * apps were written against: they branch on the model, the platform version and the user
 * agent, and the servers they talk to were built for those values. The webOS device closest
 * to an Android tablet is the TouchPad, and to a phone the Pre3, so Lunacy answers as one of
 * those two, consistently, everywhere an app can look:
 *
 *   - `PalmSystem.deviceInfo` (the shell),
 *   - the user agent, in the page and on the wire (the card host),
 *   - `com.palm.preferences/systemProperties/Get` (SystemProperties),
 *   - `X-Palm-Carrier` on every request the network shim sends (NetShim).
 *
 * A device's own identity - its serial and its nduid - is made up per install rather than
 * copied from anyone's device: it is a TouchPad-shaped serial, not a particular TouchPad's.
 *
 * Both devices' values are measured on hardware: the TouchPad's on the reference device
 * (Docs/spike-1.md, Workbench/results/touchpad-net.txt, luna-send on the device), the Pre3's
 * on 2026-09-21 (Docs/pre3.md). The Pre3 measured was an AT&T unit, so the values that
 * carriers change - buildName, carrierName, carrierCode - are that unit's; an unlocked Pre3
 * will differ, and Docs/pre3.md says so.
 */
enum class DeviceProfile(
    /** deviceInfo.modelName. Palm branded the phone "Pre" with a macron, so this isn't ASCII. */
    val modelName: String,
    /** deviceInfo.modelNameAscii, and the deviceNameShort property. */
    val modelNameAscii: String,
    val deviceName: String,
    /** DMMODEL and PRODoID, which apps key off to tell webOS devices apart. */
    val model: String,
    val boardType: String,
    val platformVersion: String,
    val userAgent: String,
    /** X-Palm-Carrier, sent on every request. c090-01 is the WiFi TouchPad's, c001-01 an AT&T Pre3's. */
    val carrierCode: String,
    /** deviceInfo.carrierName and the DMCARRIER property. Empty on a WiFi TouchPad, "ATT" on that Pre3. */
    val carrierName: String,
    /** The productLineVersion property, which is also the device version the user agent ends with. */
    val productLineVersion: String,
    /** The browserOsName property. 2.2.4 hasn't got it, so null means "don't answer that key". */
    val browserOsName: String?,
    val buildName: String,
    val buildNumber: String,
    /** The shape of an HP serial for this device: prefix plus random, per install. */
    val serialPrefix: String,
    /** Characters after the prefix. A TouchPad serial is 10 long, the Pre3's 14. */
    val serialBodyLength: Int,
    val keyboardAvailable: Boolean,
    val keyboardSlider: Boolean,
    val keyboardType: String,
    val bluetoothAvailable: Boolean,
    val carrierAvailable: Boolean,
    val coreNaviButton: Boolean,
    val swappableBattery: Boolean,
    val minimumCardHeight: Int,
    val touchableRows: Int,
    /**
     * Whether deviceInfo reports the screen's long side as its width. A TouchPad says
     * 1024 x 768 whichever way up it is; a Pre3 says 480 x 800. Neither changes when the
     * screen turns - rotation reaches apps through screenOrientation - so this is just which
     * way round the device names its own screen.
     */
    val naturalLandscape: Boolean,
    /**
     * PositiveSpaceTopPadding from the device's luna-platform.conf, which is what
     * deviceInfo.maximumCardHeight is short of the screen: 28 on the TouchPad (its status bar
     * height), 42 on the Pre3.
     */
    val positiveSpaceTopPadding: Int,
    /** Whether deviceInfo carries a carrierAvailable member at all. The Pre3's hasn't got one. */
    val reportsCarrierAvailable: Boolean,
    /**
     * HomeButtonOrientationAngle from the device's luna-platform.conf, which is how far the
     * home button sits round from the screen's own top. LunaSysMgr turns the *screen's*
     * orientation into the *window's* with it, so it is what PalmSystem.windowOrientation
     * reports: see [windowOrientationFor].
     */
    val homeButtonOrientationAngle: Int,
) {
    /** Measured on the reference TouchPad (webOS CE 3.1.0). */
    TOUCHPAD(
        modelName = "TouchPad",
        modelNameAscii = "TouchPad",
        deviceName = "HP TouchPad",
        model = "HSTNH-I29C",
        boardType = "topaz-Wifi-pvt\n",
        platformVersion = "3.1.0",
        userAgent = "Mozilla/5.0 (hp-tablet; Linux; hpwOS/3.1.0; U; en-US) AppleWebKit/534.6 " +
            "(KHTML, like Gecko) wOSSystem/234.83 Safari/534.6 TouchPad/1.0",
        carrierCode = "c090-01",
        carrierName = "",
        productLineVersion = "1.0",
        browserOsName = "hpwOS",
        buildName = "Nova-HP-Topaz",
        buildNumber = "86",
        serialPrefix = "5CL",
        serialBodyLength = 7,
        keyboardAvailable = false,
        keyboardSlider = false,
        keyboardType = "Unknown",
        // The TouchPad has Bluetooth and reports it, whatever Lunacy can do with it: a service
        // an app then calls gets an honest error, as any unimplemented one does.
        bluetoothAvailable = true,
        carrierAvailable = false,
        coreNaviButton = false,
        swappableBattery = false,
        minimumCardHeight = 318,
        touchableRows = 14,
        naturalLandscape = true,
        positiveSpaceTopPadding = 28,
        reportsCarrierAvailable = true,
        // Read off the reference device: /etc/palm/luna-platform.conf, HomeButtonOrientationAngle=270.
        homeButtonOrientationAngle = 270,
    ),

    /**
     * Measured on an AT&T Pre3 (HP webOS 2.2.4) on 2026-09-21: Docs/pre3.md. The user agent
     * and X-Palm-Carrier were read off the wire from an app context, the rest from
     * PalmSystem.deviceInfo and systemProperties/Get on the device.
     *
     * Carrier-specific, and this unit's rather than every Pre3's: buildName, carrierName and
     * carrierCode. An unlocked Pre3 would report its own; none was available to measure.
     */
    PRE3(
        // Palm's own branding, and what deviceInfo reports: "Pre" with a macron.
        modelName = "Prē3",
        modelNameAscii = "Pre3",
        deviceName = "HP Pre3",
        model = "HSTNH-F30CN",
        // No trailing newline, where the TouchPad's boardType has one.
        boardType = "mantaray-pvt",
        platformVersion = "2.2.4",
        // 2.2.4 names the product token webOSSystem, where 3.x uses wOSSystem, and has no
        // device-class field before "Linux".
        userAgent = "Mozilla/5.0 (Linux; webOS/2.2.4; U; en-US) AppleWebKit/534.6 " +
            "(KHTML, like Gecko) webOSSystem/221.56 Safari/534.6 Pre/3.0",
        carrierCode = "c001-01",
        carrierName = "ATT",
        productLineVersion = "3.0",
        // 2.2.4 answers "no such key" for browserOsName.
        browserOsName = null,
        buildName = "Nova-ATT-Mantaray",
        buildNumber = "2211",
        // One unit's shape: four characters and ten more. Confirm on a second Pre3.
        serialPrefix = "MTRE",
        serialBodyLength = 10,
        keyboardAvailable = true,
        keyboardSlider = true,
        keyboardType = "QWERTY",
        bluetoothAvailable = true,
        carrierAvailable = false,
        // False on the device, though the Pre3 has a gesture area with a light bar.
        coreNaviButton = false,
        swappableBattery = true,
        minimumCardHeight = 318,
        touchableRows = 14,
        naturalLandscape = false,
        positiveSpaceTopPadding = 42,
        // The Pre3's deviceInfo has no carrierAvailable member at all.
        reportsCarrierAvailable = false,
        // Not measured: the Pre3 was not connected when this was written. 0 is LunaSysMgr's
        // other case (its source calls it "For Opal device"), and it is what a phone whose
        // home button is below a portrait screen has. Read luna-platform.conf on a Pre3 and
        // correct this, as the rest of this profile was corrected.
        homeButtonOrientationAngle = 0,
    );

    /**
     * The window's orientation for a screen in [screen], which is what
     * `PalmSystem.windowOrientation` reports.
     *
     * webOS kept the two apart. The *screen's* orientation is the device's, and an app reads
     * it as `PalmSystem.screenOrientation`; the *window's* is the same thing turned by where
     * the home button is, and an app reads it as `PalmSystem.windowOrientation`. On a
     * TouchPad, held in its natural portrait, the screen is "up" and the window is "right".
     *
     * Straight from LunaSysMgr's own `mapScreenOrientationToWindowOrientation`
     * (`Src/js/JsSysObject.cpp`), which switches on `HomeButtonOrientationAngle`: 270 or -90
     * (a TouchPad) turns it a quarter, 0 (a phone) swaps left and right, and anything else is
     * left alone. Both properties are always one of the four names - never "free", which is
     * only something an app can *ask* for.
     */
    fun windowOrientationFor(screen: String): String = when (homeButtonOrientationAngle) {
        270, -90 -> when (screen) {
            "up" -> "right"
            "right" -> "up"
            "left" -> "down"
            "down" -> "left"
            else -> screen
        }
        0 -> when (screen) {
            "right" -> "left"
            "left" -> "right"
            else -> screen
        }
        else -> screen
    }

    val deviceNameShort get() = modelNameAscii
    val platformVersionMajor get() = platformVersion.substringBefore('.').toInt()
    val platformVersionMinor get() = platformVersion.split('.').getOrElse(1) { "0" }.toInt()
    val platformVersionDot get() = platformVersion.split('.').getOrElse(2) { "0" }.toInt()

    /** What systemProperties/Get reports for com.palm.properties.version. */
    val versionString get() = if (this == TOUCHPAD) "webOS CE $platformVersion" else "HP webOS $platformVersion"

    companion object {
        /**
         * A serial in HP's shape for [profile], derived from [nduid]: made up rather than
         * copied, so it says "a TouchPad", not "codepoet's TouchPad". Android's comes from its
         * derived device id (serial, DeviceProfileAndroid.kt).
         */
        fun serialFor(profile: DeviceProfile, nduid: String): String {
            val alphabet = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            val digest = java.security.MessageDigest.getInstance("SHA-1")
                .digest(("lunacy-serial:" + profile.name + ":" + nduid).toByteArray())
            return profile.serialPrefix + (0 until profile.serialBodyLength).map { alphabet[(digest[it].toInt() and 0xff) % alphabet.length] }.joinToString("")
        }

        const val DERIVED = "derived"
        const val USER = "user"
    }
}

/**
 * webOS's *emulated* card: the phone-sized card a tablet gave an app whose appinfo.json never
 * said `"uiRevision": 2`.
 *
 * Such an app was laid out for a Pre-sized screen, and LunaSysMgr ran it at that size - drawn
 * as a little phone in the middle of the tablet, bezel and all - rather than stretching it
 * across the tablet's card (`Window::Type_Emulated_Card`; the sizes are LunaCE's own
 * `Settings` defaults, and the reference device overrides neither in `/etc/palm`).
 *
 * Measured on the reference TouchPad on 2026-09-22 with a probe carrying no `uiRevision`:
 * the page came out 320 x 452, centred in the screen below the status bar, and its
 * `deviceInfo` reported the numbers below. What it does *not* change is who the device is -
 * the model, the version, the serial and the user agent are the TouchPad's, exactly as for
 * any other app.
 */
object EmulatedCard {
    /** emulatedCardWidth, emulatedCardHeight. */
    const val WIDTH = 320
    const val HEIGHT = 480
    /** What deviceInfo reports for a card this size, rather than the device's own. */
    const val MINIMUM_CARD_HEIGHT = 30
    const val TOUCHABLE_ROWS = 8
}

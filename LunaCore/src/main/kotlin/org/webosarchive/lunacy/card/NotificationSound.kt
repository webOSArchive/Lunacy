package org.webosarchive.lunacy.card

/**
 * What a banner's sound or PalmSystem.playSoundNotification plays, as LunaSysMgr's
 * BannerMessageHandler::playSound decided it: the rules, which are the same on every platform.
 * Playing it is each shell's own (Android's Sounds, Luna DE's shell page).
 *
 * - No class and no file, or the class "none": nothing.
 * - A file named by the app plays: an absolute path is a path on the device (served from the
 *   app's origin, like any /usr/palm or /media/internal path; a file:// path or the app's own
 *   URL names one too), a relative one is looked up in the app's resources/<locale>/ and then
 *   its own folder.
 * - Otherwise the class's default: webOS's notification sound for "notifications" (and for
 *   any class it doesn't know), its alert sound for "alerts", "alarm" and "calendar", its
 *   ringtone for "ringtones".
 * - A notification with no duration of its own is cut off at notificationSoundDuration, 5 s.
 */
object NotificationSound {
    /** luna.conf's notificationSoundDuration. */
    const val NOTIFICATION_MS = 5000

    /** The class [soundClass] plays as, or null when it plays nothing (the tablet has no vibrator). */
    fun classOf(soundClass: String): String? = when (soundClass) {
        "alert" -> "alerts"; "notification" -> "notifications"; "ringtone" -> "ringtones"
        "alerts", "alarm", "calendar", "notifications", "ringtones", "feedback" -> soundClass
        "none", "vibrate" -> null
        else -> "notifications"
    }

    /** The file under /usr/palm/sounds (Lunacy's assets/luna/sounds) a class plays when the app names none. */
    fun defaultFile(cls: String): String = when (cls) {
        "alerts", "alarm", "calendar" -> "alert.wav"
        "ringtones" -> "ringtone.mp3"
        else -> "notification.wav"
    }

    /** How long it plays, in ms; 0 is to its end. */
    fun duration(cls: String, durationMs: Int): Int =
        if (cls == "notifications" && durationMs <= 0) NOTIFICATION_MS else durationMs

    /**
     * Where the file an app named may be, as URLs on the app's origin, in the order to try them.
     * [locale] is as webOS wrote it ("en_us"); [dir] is the app's folder (AppInfo.dir).
     */
    fun candidates(appId: String, entry: String, locale: String, dir: String): List<String> {
        val base = "https://$appId${AppServer.HOST_SUFFIX}"
        // Apps that build the path from their own location (enyo.fetchAppRootPath, minus the
        // "file://" a device's had) name it by the app's URL here, as Messaging does its sent
        // and received sounds; a file:// path is a device path too.
        return if (entry.startsWith("$base/")) listOf(entry)
        else if (entry.startsWith("file:///")) listOf(base + entry.removePrefix("file://"))
        else if (entry.startsWith("/")) listOf(base + entry)
        else listOf(AppServer.appUrl(appId, "resources/$locale/$entry", dir), AppServer.appUrl(appId, entry, dir))
    }
}

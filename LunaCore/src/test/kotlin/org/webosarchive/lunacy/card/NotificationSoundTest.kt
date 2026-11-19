package org.webosarchive.lunacy.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a banner's sound or PalmSystem.playSoundNotification plays (NotificationSound), as
 * LunaSysMgr's BannerMessageHandler::playSound decided it: shared by Android's Sounds and Luna
 * DE's shell since 1.1.0, so a change here changes both.
 */
class NotificationSoundTest {
    @Test fun classes() {
        assertEquals("alerts", NotificationSound.classOf("alert"))
        assertEquals("notifications", NotificationSound.classOf("notification"))
        assertEquals("ringtones", NotificationSound.classOf("ringtone"))
        for (c in listOf("alerts", "alarm", "calendar", "notifications", "ringtones", "feedback")) assertEquals(c, NotificationSound.classOf(c))
        // A class LunaSysMgr didn't know played as a notification: Check Mate's "media".
        assertEquals("notifications", NotificationSound.classOf("media"))
        assertNull(NotificationSound.classOf("none")); assertNull(NotificationSound.classOf("vibrate"))
    }

    @Test fun defaultsAndDurations() {
        assertEquals("alert.wav", NotificationSound.defaultFile("alerts"))
        assertEquals("alert.wav", NotificationSound.defaultFile("alarm"))
        assertEquals("alert.wav", NotificationSound.defaultFile("calendar"))
        assertEquals("ringtone.mp3", NotificationSound.defaultFile("ringtones"))
        assertEquals("notification.wav", NotificationSound.defaultFile("notifications"))
        // luna.conf's notificationSoundDuration cuts a notification with no duration of its own.
        assertEquals(5000, NotificationSound.duration("notifications", 0))
        assertEquals(1200, NotificationSound.duration("notifications", 1200))
        assertEquals(0, NotificationSound.duration("alerts", 0))
    }

    @Test fun whereAnAppsFileIs() {
        val origin = "https://com.example.app${AppServer.HOST_SUFFIX}"
        // A relative name: the app's resources for the locale, then its own folder (which isn't always its id).
        assertEquals(listOf(AppServer.appUrl("com.example.app", "resources/en_us/sounds/move.mp3", "folder"), AppServer.appUrl("com.example.app", "sounds/move.mp3", "folder")),
            NotificationSound.candidates("com.example.app", "sounds/move.mp3", "en_us", "folder"))
        // A device path, a file:// path and the app's own URL each name one file.
        assertEquals(listOf("$origin/media/internal/ringtones/a.mp3"), NotificationSound.candidates("com.example.app", "/media/internal/ringtones/a.mp3", "en_us", "x"))
        assertEquals(listOf("$origin/usr/palm/sounds/alert.wav"), NotificationSound.candidates("com.example.app", "file:///usr/palm/sounds/alert.wav", "en_us", "x"))
        assertEquals(listOf("$origin/media/cryptofs/apps/x/s.wav"), NotificationSound.candidates("com.example.app", "$origin/media/cryptofs/apps/x/s.wav", "en_us", "x"))
    }
}

package org.webosarchive.lunacy.shell

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webosarchive.lunacy.card.AppServer

/**
 * The sounds a banner or PalmSystem.playSoundNotification asks for, played as LunaSysMgr's
 * BannerMessageHandler::playSound did: a sound class, an optional file, an optional duration.
 *
 * - No class and no file, or the class "none": nothing.
 * - A file named by the app plays: an absolute path is a path on the device (served from the
 *   app's origin, like any /usr/palm or /media/internal path), a relative one is looked up in
 *   the app's resources/<locale>/ and then its own folder.
 * - Otherwise the class's default: webOS's notification sound for "notifications" (and for
 *   any class it doesn't know), its alert sound for "alerts", "alarm" and "calendar", its
 *   ringtone for "ringtones". Measured on the reference TouchPad: "notifications" played
 *   `sysmgr_notification` and "alerts" `sysmgr_alert`, the preloaded copies of
 *   /usr/palm/sounds/notification.wav and alert.wav, which Lunacy ships (assets/luna/sounds).
 * - A notification with no duration of its own is cut off at notificationSoundDuration, 5 s.
 *
 * Each plays on the Android stream that matches, so the ringer switch and the volumes are
 * Android's: a notification is silent when the tablet is.
 */
class Sounds(private val context: Context, private val server: AppServer) {
    private val main = Handler(Looper.getMainLooper())
    private val playing = HashSet<MediaPlayer>()

    fun play(appId: String, soundClass: String, soundFile: String, durationMs: Int) {
        if (soundClass.isEmpty() && soundFile.isEmpty()) return
        val cls = when (soundClass) {
            "alert" -> "alerts"; "notification" -> "notifications"; "ringtone" -> "ringtones"
            "alerts", "alarm", "calendar", "notifications", "ringtones", "feedback" -> soundClass
            "none", "vibrate" -> return  // the tablet has no vibrator
            else -> "notifications"
        }
        val file = if (soundFile.isEmpty()) null else appFile(appId, soundFile)
        var duration = durationMs
        if (cls == "notifications" && duration <= 0) duration = NOTIFICATION_MS
        val stream = when (cls) {
            "alarm" -> AudioManager.STREAM_ALARM
            "ringtones" -> AudioManager.STREAM_RING
            else -> AudioManager.STREAM_NOTIFICATION
        }
        val player = MediaPlayer()
        try {
            @Suppress("DEPRECATION") player.setAudioStreamType(stream)
            if (file != null) player.setDataSource(file.path)
            else context.assets.openFd("luna/sounds/" + when (cls) {
                "alerts", "alarm", "calendar" -> "alert.wav"
                "ringtones" -> "ringtone.mp3"
                else -> "notification.wav"
            }).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            player.setOnCompletionListener { release(it) }
            player.setOnErrorListener { p, _, _ -> release(p); true }
            player.prepare()
            player.start()
            playing += player
            if (duration > 0) main.postDelayed({ if (player in playing) release(player) }, duration.toLong())
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "[$appId] sound $soundClass $soundFile: $e")
            player.release()
        }
    }

    private fun release(p: MediaPlayer) {
        if (!playing.remove(p)) return
        try { p.stop() } catch (e: Exception) {}
        p.release()
    }

    /** The app's sound file, copied out of the app server into the cache so MediaPlayer can open it. */
    private fun appFile(appId: String, entry: String): java.io.File? {
        val base = "https://$appId${AppServer.HOST_SUFFIX}"
        val candidates = if (entry.startsWith("/")) listOf(base + entry) else {
            val locale = context.resources.configuration.locale.let { it.language + "_" + it.country.lowercase() }
            listOf(AppServer.appUrl(appId, "resources/$locale/$entry"), AppServer.appUrl(appId, entry))
        }
        for (url in candidates) {
            val r = server.serve(android.net.Uri.parse(url)) ?: continue
            if (r.statusCode != 200 || r.data == null) continue
            val out = java.io.File(context.cacheDir, "sound-" + Integer.toHexString(url.hashCode()) + "." + entry.substringAfterLast('.', "snd"))
            r.data.use { i -> out.outputStream().use { i.copyTo(it) } }
            return out
        }
        Log.w(AppServer.TAG, "[$appId] sound not found: $entry")
        return null
    }

    private companion object {
        /** luna.conf's notificationSoundDuration. */
        const val NOTIFICATION_MS = 5000
    }
}

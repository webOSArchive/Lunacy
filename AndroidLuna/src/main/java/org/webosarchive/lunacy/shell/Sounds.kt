package org.webosarchive.lunacy.shell

import org.webosarchive.lunacy.card.serve
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.NotificationSound

/**
 * The sounds a banner or PalmSystem.playSoundNotification asks for, played as LunaSysMgr's
 * BannerMessageHandler::playSound did: a sound class, an optional file, an optional duration.
 * What plays is [NotificationSound]'s to say (the rules, shared with Luna DE), with one thing of
 * Android's own: "ringtones" with no file plays Android's notification sound (see
 * [androidSound]; webOS's ringtone if Android's can't be read). Measured on the reference
 * TouchPad: "notifications" played `sysmgr_notification` and "alerts" `sysmgr_alert`, the
 * preloaded copies of /usr/palm/sounds/notification.wav and alert.wav, which Lunacy ships
 * (assets/luna/sounds).
 *
 * Each plays on the Android stream that matches, so the ringer switch and the volumes are
 * Android's: a notification is silent when the tablet is.
 */
/** dirOf gives an app's folder from its id ([org.webosarchive.lunacy.card.AppInfo.dir]). */
class Sounds(private val context: Context, private val server: AppServer, private val dirOf: (String) -> String = { it }) {
    private val main = Handler(Looper.getMainLooper())
    private val playing = HashSet<MediaPlayer>()
    /** The app sound files copied into the cache this run (appFile). */
    private val copied = HashSet<String>()

    /** The shell is going: whatever is playing stops, and the samples go. */
    fun release() {
        playing.toList().forEach { release(it) }
        if (samples.isNotEmpty()) pool.release()
    }

    /** Sounds & Alerts' master switch is off (webOS's muteSound): the shell plays nothing. */
    var allMuted: () -> Boolean = { false }
    /** System sounds are off (webOS's systemSounds, or the system class muted): no feedback sounds. */
    var feedbackMuted: () -> Boolean = { false }

    fun play(appId: String, soundClass: String, soundFile: String, durationMs: Int) {
        if (soundClass.isEmpty() && soundFile.isEmpty()) return
        if (allMuted()) return
        val cls = NotificationSound.classOf(soundClass) ?: return
        val file = if (soundFile.isEmpty()) null else appFile(appId, soundFile)
        val duration = NotificationSound.duration(cls, durationMs)
        val stream = when (cls) {
            "alarm" -> AudioManager.STREAM_ALARM
            "ringtones" -> AudioManager.STREAM_RING
            else -> AudioManager.STREAM_NOTIFICATION
        }
        val player = start(stream, "[$appId] sound $soundClass $soundFile") { player ->
            val fromAndroid = if (file == null && cls == "ringtones") androidSound() else null
            if (file != null) player.setDataSource(file.path)
            else if (fromAndroid != null) {
                // Android's own sound is "None": a ringtone sound plays nothing, as Android's would.
                if (fromAndroid == android.net.Uri.EMPTY) return@start false
                player.setDataSource(context, fromAndroid)
            }
            else context.assets.openFd("luna/sounds/" + NotificationSound.defaultFile(cls)).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            true
        } ?: return
        if (duration > 0) main.postDelayed({ if (player in playing) release(player) }, duration.toLong())
    }

    /**
     * A player on [stream], given its source by [source] (false: play nothing), started and
     * kept until it finishes or fails; null if it didn't start, with [what] in the log.
     */
    private fun start(stream: Int, what: String, source: (MediaPlayer) -> Boolean): MediaPlayer? {
        val player = MediaPlayer()
        return try {
            @Suppress("DEPRECATION") player.setAudioStreamType(stream)
            if (!source(player)) { player.release(); return null }
            player.setOnCompletionListener { release(it) }
            player.setOnErrorListener { p, _, _ -> release(p); true }
            player.prepare()
            player.start()
            playing += player
            player
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "$what: $e")
            player.release()
            null
        }
    }

    /**
     * The sound Android plays for a notification, which the "ringtones" class plays when the app
     * names no file (Messaging's default "system" sound): the system ringtone stays Android's
     * setting, and webOS's tones are in Android's list (card/Ringtones; codepoet, 2026-11-04).
     * Uri.EMPTY when it is set to none; null if it can't be read, and webOS's ringtone plays.
     */
    private fun androidSound(): android.net.Uri? = try {
        android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_NOTIFICATION)
            ?: android.net.Uri.EMPTY
    } catch (e: Exception) { null }

    /**
     * webOS's battery-charged sound: LunaSysMgr's StatusBarBattery played
     * /usr/palm/sounds/battery_full.mp3 on the notifications class, uncut, when the battery
     * reached 100 % having been below 95.
     */
    fun batteryFull() = playAsset("battery_full.mp3", AudioManager.STREAM_NOTIFICATION)

    /**
     * webOS's startup sound: LunaSysMgr's WindowServer played /usr/palm/sounds/boot.mp3 on the
     * notifications class, uncut, once, as boot finished and the boot screen gave way to the
     * shell. Sounds & Alerts' master switch and its System Sounds switch silence it (codepoet,
     * 2026-11-12), and so does Android's silent mode.
     */
    fun boot() { if (!allMuted() && !feedbackMuted()) playAsset("boot.mp3", AudioManager.STREAM_NOTIFICATION) }

    private fun playAsset(name: String, stream: Int) {
        start(stream, "sound $name") { player ->
            context.assets.openFd("luna/sounds/$name").use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            true
        }
    }

    // ---- feedback ----

    /**
     * LunaSysMgr's feedback sounds (SoundPlayerPool::playFeedback): short samples preloaded
     * into PulseAudio, here into a SoundPool on Android's system stream, whose volume is
     * Android's and which the ringer's silent mode mutes. webOS played them unless its
     * `systemSounds` preference was turned off (Sounds & Alerts' System Sounds), and it isn't
     * set on the reference TouchPad: see [feedbackMuted]. Android's "Touch sounds" is
     * deliberately not read: it is Android's own click, and it is off out of the box on the
     * reference tablet, so reading it would mean never hearing these.
     */
    @Suppress("DEPRECATION")
    private val pool by lazy { android.media.SoundPool(2, AudioManager.STREAM_SYSTEM, 0) }
    private val samples = HashMap<String, Int>()
    private val loaded = HashSet<Int>()
    private val pending = HashMap<Int, Boolean>()

    /** Loads the samples ahead, as LunaSysMgr's were loaded at boot, so the first play isn't late. */
    fun preload() {
        pool.setOnLoadCompleteListener { p, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            loaded += id
            if (pending.remove(id) == true) p.play(id, 1f, 1f, 1, 0, 1f)
        }
        for (name in FEEDBACK) sample(name)
    }

    private fun sample(name: String): Int = samples.getOrPut(name) {
        context.assets.openFd("luna/sounds/feedback/$name.wav").use { pool.load(it, 1) }
    }

    fun feedback(name: String) {
        if (allMuted() || feedbackMuted()) return
        // A name LunaSysMgr has no sample for plays nothing, as on webOS.
        if (name !in FEEDBACK) return
        val id = try { sample(name) } catch (e: Exception) { Log.w(AppServer.TAG, "feedback $name: $e"); return }
        if (id in loaded) pool.play(id, 1f, 1f, 1, 0, 1f) else pending[id] = true
    }

    private fun release(p: MediaPlayer) {
        if (!playing.remove(p)) return
        try { p.stop() } catch (e: Exception) {}
        p.release()
    }

    /** The app's sound file, copied out of the app server into the cache so MediaPlayer can open it. */
    private fun appFile(appId: String, entry: String): java.io.File? {
        val locale = context.resources.configuration.locale.let { it.language + "_" + it.country.lowercase() }
        val candidates = NotificationSound.candidates(appId, entry, locale, dirOf(appId))
        for (url in candidates) {
            // The extension only if it is one: the rest of the name is the app's, and could climb out of the cache.
            val ext = entry.substringAfterLast('.', "snd").takeIf { e -> e.isNotEmpty() && e.length <= 8 && e.all { it.isLetterOrDigit() } } ?: "snd"
            val out = java.io.File(context.cacheDir, "sound-" + Integer.toHexString(url.hashCode()) + "." + ext)
            // Copied once per run: every banner of the app played this on the main thread before.
            if (url in copied && out.isFile) return out
            val r = server.serve(android.net.Uri.parse(url)) ?: continue
            if (r.statusCode != 200 || r.data == null) continue
            r.data.use { i -> out.outputStream().use { i.copyTo(it) } }
            copied += url
            return out
        }
        Log.w(AppServer.TAG, "[$appId] sound not found: $entry")
        return null
    }

    private companion object {
        /** The samples Lunacy has a use for; see assets/luna/sounds/NOTICE. */
        val FEEDBACK = listOf("appclose", "birdappclose", "carddrag", "LauncherOpenApp", "LauncherCloseApp", "AdjustVolume")
    }
}

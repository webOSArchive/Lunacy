package org.webosarchive.lunacy.card

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android apps playing audio, as webOS Music's dashboard (codepoet, 2026-11-04): what is
 * playing, and previous / play-pause / next. Android hands an app that may read its
 * notifications (the owner's Notification access, as for [AndroidNotifications]) every active
 * media session, with its metadata and its transport controls, so nothing here depends on the
 * player's own notification, which Lunacy leaves out of its dashboards.
 *
 * One entry per Android app, its first session that is playing or paused with a title; it goes
 * when the session stops or ends. Kept in memory only, like the notifications.
 */
object AndroidMedia {
    private const val TAG = "LunacyMedia"
    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var appContext: Context? = null
    private var sessions: List<MediaController> = emptyList()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = changed()
        override fun onMetadataChanged(metadata: MediaMetadata?) = changed()
        override fun onSessionDestroyed() = changed()
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> follow(list.orEmpty()) }

    private fun changed() { AndroidNotifications.onChanged?.let { main.post(it) } }

    /** The listener connected: Android now lets Lunacy see the sessions. */
    internal fun start(context: Context) = main.post {
        if (manager != null) return@post
        val c = context.applicationContext
        val m = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val me = ComponentName(c, AndroidNotificationListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsChanged, me, main)
            manager = m; appContext = c
            follow(m.getActiveSessions(me))
        } catch (e: SecurityException) {
            Log.w(TAG, "media sessions refused: $e")
        }
    }

    /** The listener went: so does Lunacy's view of the sessions. */
    internal fun stop() = main.post {
        manager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        manager = null
        follow(emptyList())
    }

    private fun follow(list: List<MediaController>) {
        sessions.forEach { runCatching { it.unregisterCallback(callback) } }
        val mine = appContext?.packageName
        sessions = list.filter { it.packageName != mine }
        sessions.forEach { it.registerCallback(callback, main) }
        changed()
    }

    private fun live(c: MediaController): Boolean {
        val s = c.playbackState?.state ?: return false
        if (s == PlaybackState.STATE_NONE || s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_ERROR) return false
        return !c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
    }

    /** The app's controller to show: its first live session. */
    private fun shown(): List<MediaController> = sessions.filter { live(it) }.distinctBy { it.packageName }

    fun any(): Boolean = shown().isNotEmpty()

    fun has(pkg: String): Boolean = shown().any { it.packageName == pkg }

    /** For the Android Notifications app: `media`, one per app playing or paused. */
    fun snapshot(context: Context, icon: (String) -> String): JSONArray {
        val out = JSONArray()
        val pm = context.packageManager
        for (c in shown()) {
            val m = c.metadata ?: continue
            val st = c.playbackState
            val playing = st?.state == PlaybackState.STATE_PLAYING || st?.state == PlaybackState.STATE_BUFFERING ||
                st?.state == PlaybackState.STATE_CONNECTING
            val artist = listOf(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ALBUM)
                .firstNotNullOfOrNull { k -> m.getString(k)?.takeIf { it.isNotBlank() } }.orEmpty()
            val actions = st?.actions ?: 0L
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault(c.packageName)
            out.put(JSONObject().put("package", c.packageName).put("app", label)
                .put("title", m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()).put("artist", artist)
                .put("playing", playing)
                .put("canPrevious", actions == 0L || (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L)
                .put("canNext", actions == 0L || (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L)
                .put("icon", icon(c.packageName)))
        }
        return out
    }

    /** A dashboard button: `previous`, `playpause`, `next`, or `open` (the player, as its session asks). Null when done. */
    fun control(context: Context, pkg: String, command: String): String? {
        val c = shown().firstOrNull { it.packageName == pkg } ?: return "Nothing is playing in $pkg"
        val t = c.transportControls
        when (command) {
            "previous" -> t.skipToPrevious()
            "next" -> t.skipToNext()
            "playpause" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            "open" -> {
                val sent = c.sessionActivity?.let { pi ->
                    runCatching {
                        if (Build.VERSION.SDK_INT >= 34) {
                            val o = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            pi.send(context, 0, null, null, null, null, o.toBundle())
                        } else pi.send()
                    }.isSuccess
                } ?: false
                if (!sent) {
                    val launch = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?: return "This player can't be opened"
                    if (runCatching { context.startActivity(launch) }.isFailure) return "This player can't be opened"
                }
            }
            else -> return "Unknown command \"$command\""
        }
        return null
    }
}

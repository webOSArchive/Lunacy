package org.webosarchive.lunacy.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.webosarchive.lunacy.R
import org.webosarchive.lunacy.card.AppServer

/**
 * What a webOS app is playing, as Android knows a player (codepoet, 2026-11-04): Music tells
 * com.palm.service.mediabroadcast its song and whether it is paused (MediaBroadcast), as webOS
 * passed them to a car or headset over Bluetooth AVRCP; Lunacy gives them to an Android media
 * session in a foreground service, which keeps Lunacy, and so Music's page, playing with the
 * screen off or another app in front. Android shows its standard media notification for it,
 * with previous / play-pause / next, and its lock screen, headsets and cars control it; each
 * press goes back to the app as webOS's AVRCP key (com.palm.keys media/status, [onKey]).
 *
 * The service runs in the foreground while the song plays; paused, its notification can be
 * swiped away; it ends when the app stops broadcasting (Music closed).
 */
object NowPlaying {
    @Volatile var title = ""; @Volatile var artist = ""; @Volatile var album = ""
    @Volatile var durationMs = 0L
    @Volatile var playing = false
    @Volatile var active = false
    /** An Android control was pressed: webOS's AVRCP key name (play, pause, next, prev, togglePausePlay, stop). */
    @Volatile var onKey: ((String) -> Unit)? = null

    fun update(context: Context) {
        if (!active) return
        val i = Intent(context, NowPlayingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26 && playing) context.startForegroundService(i) else context.startService(i)
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "now playing: $e")
        }
    }

    fun end(context: Context) {
        active = false; playing = false
        context.stopService(Intent(context, NowPlayingService::class.java))
    }
}

class NowPlayingService : Service() {
    private var session: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "Lunacy").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = key("play")
                override fun onPause() = key("pause")
                override fun onSkipToNext() = key("next")
                override fun onSkipToPrevious() = key("prev")
                override fun onStop() = key("stop")
            })
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }

    private fun key(k: String) { NowPlaying.onKey?.invoke(k) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> key("prev")
            ACTION_NEXT -> key("next")
            ACTION_TOGGLE -> key("togglePausePlay")
        }
        if (!NowPlaying.active) { stopSelf(); return START_NOT_STICKY }
        refresh()
        return START_NOT_STICKY
    }

    private fun refresh() {
        val s = session ?: return
        s.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, NowPlaying.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, NowPlaying.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, NowPlaying.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, NowPlaying.durationMs).build())
        s.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
            .setState(if (NowPlaying.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
        val n = notification(s)
        if (NowPlaying.playing) {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(ID, n)
        } else {
            // Paused: the owner may swipe it away, and Android may end the service.
            @Suppress("DEPRECATION") stopForeground(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID, n)
        }
    }

    private fun notification(s: MediaSession): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val b = if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Now playing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "What a webOS app (Music) is playing, with its controls"
                    setShowBadge(false)
                })
            }
            Notification.Builder(this, CHANNEL)
        } else @Suppress("DEPRECATION") Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        fun action(icon: Int, title: String, act: String) = Notification.Action.Builder(icon, title,
            PendingIntent.getService(this, act.hashCode(), Intent(this, NowPlayingService::class.java).setAction(act),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
        val open = PendingIntent.getActivity(this, 0, Intent(this, ShellActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        return b.setSmallIcon(R.drawable.lunacy_logo).setContentTitle(NowPlaying.title).setContentText(NowPlaying.artist)
            .setContentIntent(open).setShowWhen(false).setOngoing(NowPlaying.playing).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV))
            .addAction(if (NowPlaying.playing) action(android.R.drawable.ic_media_pause, "Pause", ACTION_TOGGLE)
                else action(android.R.drawable.ic_media_play, "Play", ACTION_TOGGLE))
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(s.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    override fun onDestroy() {
        session?.run { isActive = false; release() }
        session = null
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(ID)
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "now-playing"
        const val ID = 0x4d555349
        const val ACTION_PREV = "org.webosarchive.lunacy.nowplaying.PREV"
        const val ACTION_NEXT = "org.webosarchive.lunacy.nowplaying.NEXT"
        const val ACTION_TOGGLE = "org.webosarchive.lunacy.nowplaying.TOGGLE"
    }
}

package org.webosarchive.lunacy.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import org.webosarchive.lunacy.R
import org.webosarchive.lunacy.card.AppServer
import org.webosarchive.lunacy.card.Db8

/**
 * Keeps a Lunacy that isn't the home screen running while it has mail to keep up with
 * (codepoet, 2026-11-06).
 *
 * On webOS the system never stopped: mail synced and pushed with the screen off and other
 * apps open. As Android's home app Lunacy is the home process, which Android doesn't freeze.
 * Opened as an ordinary app, it is cached about a minute after the owner leaves it, and
 * Android 12 and later freeze a cached app whole (measured on the Pixel Tablet, Android 17:
 * frozen at 60 to 90 s, with or without the battery exemption): no sync, no IDLE push, no
 * Android account copy, until it is opened again. A foreground service is what Android lets an
 * app keep running with, and it must show a notification: one, silent, at the lowest priority
 * Android has, only while there is a mail account and Lunacy isn't the home app (Zen of Palm:
 * nothing more than that). Swiping Lunacy out of the recent apps still ends it all, as the
 * owner asked; the service ends with the shell.
 */
class BackgroundWork(private val context: Context, private val db8: Db8, private val isHome: () -> Boolean) {
    private var running = false
    private var mail = false

    /** Watches the accounts, and checks once now. */
    fun start() {
        db8.onWrites(ACCOUNT_KIND) { recount() }
        recount()
    }

    /** The shell has come to the front (onStart): Android may have changed the home app meanwhile. */
    fun check() = apply()

    private fun recount() = db8.count(ACCOUNT_KIND, ::isMail) { n -> mail = n > 0; apply() }

    private fun isMail(a: JSONObject): Boolean {
        if (a.optBoolean("beingDeleted")) return false
        val providers = a.optJSONArray("capabilityProviders") ?: return false
        return (0 until providers.length()).any { providers.optJSONObject(it)?.optString("capability") == "MAIL" }
    }

    private fun apply() {
        val want = mail && !isHome()
        if (want == running) return
        running = want
        val i = Intent(context, BackgroundService::class.java)
        try {
            if (want) {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            } else context.stopService(i)
            Log.i(AppServer.TAG, "background: ${if (want) "running, for mail" else "not needed"}")
        } catch (e: Exception) {
            running = false
            Log.w(AppServer.TAG, "background: couldn't ${if (want) "start" else "stop"}: $e")
        }
    }

    /** The shell is going: nothing is left to keep running. */
    fun stop() {
        if (running) runCatching { context.stopService(Intent(context, BackgroundService::class.java)) }
        running = false
    }

    companion object {
        const val ACCOUNT_KIND = "com.palm.account:1"
    }
}

/** The foreground service itself: no work of its own, only Android's leave to keep the process running. */
class BackgroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, notification())
        // Not restarted on its own: after a kill there is no shell, so nothing to keep running.
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val b = if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Running in the background", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown while Lunacy keeps your mail up to date with its window closed"
                    setShowBadge(false)
                    setSound(null, null)
                })
            }
            Notification.Builder(this, CHANNEL)
        } else @Suppress("DEPRECATION") Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        val open = PendingIntent.getActivity(this, 0, Intent(this, ShellActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return b.setSmallIcon(R.drawable.ic_stat_lunacy).setContentTitle("Lunacy")
            .setContentText("Keeping your mail up to date")
            .setContentIntent(open).setShowWhen(false).setOngoing(true).build()
    }

    private companion object {
        const val ID = 0x424b47  // "BKG"
        const val CHANNEL = "background"
    }
}

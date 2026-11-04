package org.webosarchive.lunacy.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.util.Log
import org.webosarchive.lunacy.R
import org.webosarchive.lunacy.card.AppServer

/**
 * Screen & Lock's Blink Notifications, on a device with a notification light (codepoet,
 * 2026-11-04). The TouchPad pulsed its centre button while notifications it had shown waited
 * with the screen off; webOS's `BlinkNotifications` preference turned that on, and it was off
 * out of the box.
 *
 * Android lets an app light the LED only through a notification of its own, and lights it only
 * while the screen is off. So while Blink is on, a webOS notification (a banner or a dashboard)
 * that arrives with the screen off posts one quiet Android notification that asks for the
 * light: no sound, no vibration, no heads-up. It goes as soon as the screen comes on, or when
 * nothing is left in the notification area. A device without a light (Android's
 * config_intrusiveNotificationLed) never gets one: there it would only be a line in the shade.
 */
class NotificationLight(private val context: Context, private val enabled: () -> Boolean, private val screenOn: () -> Boolean) {
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var showing = false

    val hasLight: Boolean by lazy { deviceHasLight() }

    /** A banner or dashboard arrived. */
    fun arrived() {
        if (showing || !hasLight || screenOn() || !enabled()) return
        post()
    }

    /** The owner looked (the screen came on), or nothing is left to look at. */
    fun clear() {
        if (!showing) return
        showing = false
        runCatching { nm.cancel(ID) }
    }

    private fun post() {
        try {
            val open = PendingIntent.getActivity(context, 0, Intent(context, ShellActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val title = context.applicationInfo.loadLabel(context.packageManager).toString()
            val text = "New notifications"
            val n = if (Build.VERSION.SDK_INT >= 26) {
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    // Android lights the LED only above low importance; the default, made silent.
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Notification light", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Blinks the notification light while webOS notifications wait (Screen & Lock, Blink Notifications)"
                        setSound(null, null); enableVibration(false)
                        enableLights(true); lightColor = Color.WHITE
                        setShowBadge(false)
                    })
                }
                Notification.Builder(context, CHANNEL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context).setDefaults(0).setLights(Color.WHITE, ON_MS, OFF_MS)
            }
            nm.notify(ID, n.setSmallIcon(R.drawable.lunacy_logo).setContentTitle(title).setContentText(text)
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS).build())
            showing = true
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "notification light: $e")
        }
    }

    companion object {
        /** Whether this device has a notification light, as Android's own config says (Screen & Lock shows Blink Notifications only then). */
        fun deviceHasLight(): Boolean {
            val id = Resources.getSystem().getIdentifier("config_intrusiveNotificationLed", "bool", "android")
            return id != 0 && runCatching { Resources.getSystem().getBoolean(id) }.getOrDefault(false)
        }

        private const val CHANNEL = "notification-light"
        private const val ID = 0x4c4544
        // Before Android 8: a slow pulse, a short flash every few seconds. From 8 the channel
        // names only the colour, and the pulse is the device's own (the Kyocera A101BM's:
        // 100 ms every 4.75 s).
        private const val ON_MS = 500
        private const val OFF_MS = 4500
    }
}

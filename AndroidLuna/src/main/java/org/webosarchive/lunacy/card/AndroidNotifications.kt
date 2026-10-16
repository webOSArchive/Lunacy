package org.webosarchive.lunacy.card

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android's notifications, shown as webOS dashboards (Docs/luna-deltas.md B14). Android hands
 * them to [AndroidNotificationListener] once the owner allows it on Android's own Notification
 * access screen; nothing else lets an app see another's notifications.
 *
 * They are kept here, in memory only, while Android has them: never written down, never
 * logged, never sent anywhere. Lunacy's Android Notifications app
 * (org.webosarchive.lunacy.notifications) draws them, one dashboard per Android app, through
 * palm://org.webosarchive.lunacy/android/notifications.
 *
 * Only what a webOS device would have shown comes through, so Lunacy stays as quiet as webOS
 * was ("the zen of Palm"): no ongoing notifications ("running in the background", calls, a
 * download's progress), no media players, nothing Android itself ranks as silent. Only what
 * Android ranks high, and Do Not Disturb lets through, gets a banner; the rest only adds to
 * its dashboard. Android has already played the posting app's sound, so Lunacy plays none.
 */
object AndroidNotifications {
    private const val TAG = "LunacyNotifications"

    class Entry(
        val key: String, val pkg: String, val title: String, val text: String, val posted: Long,
        /** Whether Android ranks it high enough to interrupt: a banner as it arrives. */
        val loud: Boolean,
        val autoCancel: Boolean, val contentIntent: PendingIntent?, val group: String?, val summary: Boolean,
    ) {
        /** Arrived since anyone was last told: the Android Notifications app banners it, once. */
        var fresh = false
    }

    private val entries = LinkedHashMap<String, Entry>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var listener: AndroidNotificationListener? = null
    /** Each app's icon as a PNG, by package; null where none could be drawn. */
    private val icons = HashMap<String, ByteArray?>()
    @Volatile private var appContext: Context? = null

    /** The shell's, while it runs: told on the main thread whenever the notifications change. */
    @Volatile var onChanged: (() -> Unit)? = null

    private fun changed() { onChanged?.let { main.post(it) } }

    /** Whether the owner has let Lunacy read Android's notifications, on Android's Notification access screen. */
    fun accessGranted(context: Context): Boolean {
        val mine = ComponentName(context, AndroidNotificationListener::class.java)
        val on = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return on.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    val connected get() = listener != null

    // ---- from the listener ----

    internal fun connected(l: AndroidNotificationListener) {
        listener = l
        appContext = l.applicationContext
        // What was there before Lunacy was listening is shown, but not bannered: it isn't news.
        val now = runCatching { l.activeNotifications.orEmpty().toList() }.getOrDefault(emptyList())
        val ranking = runCatching { l.currentRanking }.getOrNull()
        synchronized(entries) {
            entries.clear()
            for (sbn in now) entryOf(l, sbn, ranking)?.let { entries[it.key] = it }
        }
        changed()
    }

    internal fun disconnected(l: AndroidNotificationListener) {
        if (listener != l) return
        listener = null
        synchronized(entries) { entries.clear() }
        changed()
    }

    internal fun posted(l: AndroidNotificationListener, sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap?) {
        val e = entryOf(l, sbn, ranking)
        synchronized(entries) {
            val before = entries.remove(sbn.key)
            if (e == null) { if (before == null) return } else {
                // An update to one already shown alerts again unless the app asked it not to.
                val once = (sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0
                e.fresh = e.loud && (before == null || !once)
                entries[e.key] = e
            }
        }
        changed()
    }

    internal fun removed(sbn: StatusBarNotification) {
        if (synchronized(entries) { entries.remove(sbn.key) } != null) changed()
    }

    /** One notification as webOS would show it, or null if webOS wouldn't have. */
    private fun entryOf(context: Context, sbn: StatusBarNotification, rankings: NotificationListenerService.RankingMap?): Entry? {
        val n = sbn.notification
        if (sbn.packageName == context.packageName) return null
        if ((n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0) return null
        val extras = n.extras ?: return null
        // A media player's controls, and a download's progress: not yet (Docs/luna-deltas.md B14).
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
        if (extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) return null
        val ranking = rankings?.let { r -> NotificationListenerService.Ranking().takeIf { r.getRanking(sbn.key, it) } }
        val (quiet, loud) = rank(n, ranking)
        if (quiet) return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT) ?: n.tickerText)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        // Do Not Disturb holds back the banner, not the dashboard.
        val interrupts = loud && (ranking?.matchesInterruptionFilter() ?: true)
        return Entry(sbn.key, sbn.packageName, title.trim(), text.trim(), sbn.postTime, interrupts,
            (n.flags and Notification.FLAG_AUTO_CANCEL) != 0, n.contentIntent, n.group,
            (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0)
    }

    /** Whether Android ranks it silent, and whether it ranks it urgent: by its channel from Android 8, its priority before. */
    @Suppress("DEPRECATION")
    private fun rank(n: Notification, ranking: NotificationListenerService.Ranking?): Pair<Boolean, Boolean> =
        if (Build.VERSION.SDK_INT >= 26 && ranking != null)
            (ranking.importance < android.app.NotificationManager.IMPORTANCE_DEFAULT) to (ranking.importance >= android.app.NotificationManager.IMPORTANCE_HIGH)
        else (n.priority < Notification.PRIORITY_DEFAULT) to (n.priority >= Notification.PRIORITY_HIGH)

    // ---- for the Android Notifications app ----

    /**
     * Every notification shown, by app, oldest first in each - a dashboard's top layer is its
     * last - and the apps by their newest. A group's summary only stands in for a group with
     * nothing else showing. `alerts` are the ones to banner; with [consume] each is told once.
     */
    fun snapshot(context: Context, consume: Boolean): JSONObject {
        val (all, fresh) = synchronized(entries) {
            val all = entries.values.toList()
            val fresh = all.filter { it.fresh }
            if (consume) fresh.forEach { it.fresh = false }
            all to fresh
        }
        val shown = all.filter { e ->
            !e.summary || e.group == null || all.none { o -> o.pkg == e.pkg && o.group == e.group && !o.summary }
        }
        val pm = context.packageManager
        val labels = HashMap<String, String>()
        fun label(pkg: String) = labels.getOrPut(pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
        val apps = JSONArray()
        for ((pkg, es) in shown.groupBy { it.pkg }.entries.sortedBy { (_, es) -> es.maxOf { it.posted } }) {
            val notes = JSONArray()
            for (e in es.sortedBy { it.posted }) notes.put(JSONObject().put("key", e.key).put("title", e.title.ifEmpty { label(pkg) }).put("text", e.text))
            apps.put(JSONObject().put("package", pkg).put("title", label(pkg)).put("icon", iconUrl(context, pkg))
                .put("image", ICON_PATH + pkg + ".svg").put("notifications", notes))
        }
        val alerts = JSONArray()
        for (e in fresh) alerts.put(JSONObject().put("key", e.key).put("package", e.pkg).put("title", e.title.ifEmpty { label(e.pkg) })
            .put("text", e.text).put("icon", iconUrl(context, e.pkg)))
        return JSONObject().put("returnValue", true).put("access", accessGranted(context)).put("connected", connected)
            .put("apps", apps).put("alerts", alerts)
    }

    /**
     * An app's launcher icon as a PNG, [ICON_PX] square, filling it as Android's icons do. The
     * bar's mini icon is made from it (ShellActivity.icon), and the dashboard's own from [dashboardIcon].
     */
    fun icon(context: Context, pkg: String): ByteArray? = synchronized(icons) {
        if (icons.containsKey(pkg)) icons[pkg] else runCatching {
            val d = context.packageManager.getApplicationIcon(pkg)
            val b = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, ICON_PX, ICON_PX); d.draw(Canvas(b))
            java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }.getOrNull().also { icons[pkg] = it }
    }

    /**
     * For AppServer: a dashboard's icon for an app with a notification showing, else null. The
     * layers load it from [ICON_PATH] on the Android Notifications app's origin: Enyo's
     * dashboard takes any icon not starting with "/" as relative to the app.
     *
     * Enyo's dashboard shows its icon at its own size, and webOS apps drew theirs 48 px square
     * with the art about 40 to 44 px across (Calendar's, Email's, Messaging's), where Android's
     * fills its square. So it is a 48 px SVG with the icon 42 px across in the middle: a page
     * sizes it as it would a 48 px PNG, and the PNG inside stays sharp on a dense screen.
     */
    fun dashboardIcon(pkg: String): ByteArray? {
        val c = appContext ?: return null
        if (synchronized(entries) { entries.values.none { it.pkg == pkg } }) return null
        val png = icon(c, pkg) ?: return null
        val b64 = android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP)
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"48\" height=\"48\">" +
            "<image x=\"3\" y=\"3\" width=\"42\" height=\"42\" xlink:href=\"data:image/png;base64,$b64\"/></svg>").toByteArray()
    }

    /** The same as a data: URL: the dashboard's and the banner's icon in the bar, which the shell decodes. */
    private fun iconUrl(context: Context, pkg: String): String =
        icon(context, pkg)?.let { "data:image/png;base64," + android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }.orEmpty()

    private const val ICON_PX = 144
    /** Where the Android Notifications app's pages find an app's dashboard icon: [ICON_PATH]<package>.svg. */
    const val ICON_PATH = "/__lunacy/android-icon/"
    const val APP_ID = "org.webosarchive.lunacy.notifications"

    /** An app was updated or removed: its icon is drawn again next time. */
    fun forget(pkg: String) { synchronized(icons) { icons.remove(pkg) } }

    /** Whether any notification is showing: the shell starts the Android Notifications app for them. */
    fun any(): Boolean = synchronized(entries) { entries.isNotEmpty() }

    /**
     * A notification tapped: what Android's own shade does - its intent is sent, and it goes
     * if it asked to - or, with no intent, its app opened. Null when done, else why not.
     */
    fun open(context: Context, key: String): String? {
        val e = synchronized(entries) { entries[key] } ?: return "That notification has gone"
        val sent = e.contentIntent?.let { pi ->
            runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    // The owner tapped it, in Lunacy, on the screen: the app may come forward.
                    val o = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(context, 0, null, null, null, null, o.toBundle())
                } else pi.send()
            }.onFailure { Log.w(TAG, "a notification's intent failed: $it") }.isSuccess
        } ?: false
        if (!sent) {
            val launch = context.packageManager.getLaunchIntentForPackage(e.pkg)?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: return "This notification can't be opened"
            if (runCatching { context.startActivity(launch) }.isFailure) return "This notification can't be opened"
        }
        if (e.autoCancel) dismiss(listOf(key))
        return null
    }

    /**
     * Swiped away: gone from Android too. One Android won't clear goes from here until its app
     * posts it again.
     */
    fun dismiss(keys: List<String>) {
        val l = listener
        val gone = synchronized(entries) { keys.mapNotNull { entries.remove(it) } }
        for (e in gone) runCatching { l?.cancelNotification(e.key) }
        if (gone.isNotEmpty()) changed()
    }
}

/** What Android binds to once the owner allows it; everything it hears goes to [AndroidNotifications]. */
class AndroidNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = AndroidNotifications.connected(this)
    override fun onListenerDisconnected() = AndroidNotifications.disconnected(this)
    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) = AndroidNotifications.posted(this, sbn, rankingMap)
    override fun onNotificationPosted(sbn: StatusBarNotification) = AndroidNotifications.posted(this, sbn, null)
    override fun onNotificationRemoved(sbn: StatusBarNotification) = AndroidNotifications.removed(sbn)
    override fun onDestroy() { AndroidNotifications.disconnected(this); super.onDestroy() }
}

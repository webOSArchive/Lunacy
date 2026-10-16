package org.webosarchive.lunacy.shell

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppInfo

/**
 * A webOS app pinned to Android's own home screen, for when Lunacy isn't the home app: its
 * icon there opens the app straight away, full screen, and leaving it goes back to Android
 * (ShellActivity's direct launch), so someone who keeps Android's launcher never sees
 * Lunacy's. Software Manager asks for it, one app at a time.
 *
 * From Android 8 the request goes through ShortcutManager.requestPinShortcut, and the home app
 * asks the user; before that, through the INSTALL_SHORTCUT broadcast, which the home app takes
 * as it is. Either way the shortcut's intent is [openIntent].
 */
class HomeScreenPins(private val context: Context) {
    /** Whether Lunacy is Android's default home app: then there is nothing to pin to. */
    fun lunacyIsHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val ri = context.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == context.packageName
    }

    /** Whether the home app takes pins. Before Android 8 there is no asking; the broadcast is sent and the launcher may ignore it. */
    fun canPin(): Boolean = if (Build.VERSION.SDK_INT >= 26) Requests.supported(context) else true

    fun status(): JSONObject = JSONObject().put("returnValue", true).put("lunacyIsHome", lunacyIsHome()).put("canPin", canPin())

    /** Asks the home app to pin [app]. Null when asked, else what went wrong. */
    fun pin(app: AppInfo, icon: Bitmap?): String? {
        if (lunacyIsHome()) return "Lunacy is the home screen"
        val intent = openIntent(context, app.id)
        if (Build.VERSION.SDK_INT >= 26) return Requests.pin(context, app, icon, intent)
        @Suppress("DEPRECATION")
        val add = Intent("com.android.launcher.action.INSTALL_SHORTCUT")
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent)
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, app.title)
            .putExtra("duplicate", false)
        if (icon != null) {
            @Suppress("DEPRECATION")
            add.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon)
        }
        context.sendBroadcast(add)
        return null
    }

    @android.annotation.TargetApi(26)
    private object Requests {
        fun supported(context: Context) = runCatching { context.getSystemService(ShortcutManager::class.java).isRequestPinShortcutSupported }.getOrDefault(false)

        fun pin(context: Context, app: AppInfo, icon: Bitmap?, intent: Intent): String? {
            val sm = context.getSystemService(ShortcutManager::class.java)
            if (!sm.isRequestPinShortcutSupported) return "The home screen doesn't take pinned shortcuts"
            val info = ShortcutInfo.Builder(context, "webos:" + app.id)
                .setShortLabel(app.title.ifEmpty { app.id })
                .setIntent(intent)
                .apply { if (icon != null) setIcon(Icon.createWithBitmap(icon)) }
                .build()
            return if (runCatching { sm.requestPinShortcut(info, null) }.getOrDefault(false)) null else "The home screen refused the shortcut"
        }
    }

    companion object {
        /** The action of a pinned webOS app's intent: ShellActivity opens [EXTRA_APP] straight away. */
        const val ACTION_OPEN_APP = "org.webosarchive.lunacy.action.OPEN_APP"
        const val EXTRA_APP = "app"

        fun openIntent(context: Context, appId: String): Intent =
            Intent(ACTION_OPEN_APP).setClass(context, ShellActivity::class.java).putExtra(EXTRA_APP, appId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

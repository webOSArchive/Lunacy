package org.webosarchive.lunacy.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppFiles
import org.webosarchive.lunacy.card.AppInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Proof of concept: Android's own apps as launcher icons, for when Lunacy is the home screen.
 *
 * Each launchable activity becomes an [AppInfo] with the id `android:<package>/<activity>`,
 * so the launcher arranges, groups and renames them like any webOS app. It lands on the
 * favorites page (named "android" in launcher-pages.json); launching one starts Android's
 * activity rather than opening a card.
 */
class AndroidApps(private val context: Context, private val files: AppFiles) {
    private val icons = HashMap<String, ByteArray?>()

    fun list(): List<AppInfo> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                val id = PREFIX + cn.flattenToShortString()
                AppInfo(
                    id = id, title = ri.loadLabel(pm).toString(), main = "", icon = "", noWindow = true,
                    type = "android", version = "", userInstalled = false, visible = true,
                    androidSettings = "", splashIcon = "", uiRevision = 2, category = "android",
                    keywords = emptyList(), appinfo = JSONObject(), files = files,
                    androidComponent = cn,
                    androidIcon = { icons.getOrPut(id) { png(ri) }?.let { ByteArrayInputStream(it) } },
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    /** The activity's icon as a 64 px PNG: the size of a webOS launcher icon, in TouchPad px. */
    private fun png(ri: android.content.pm.ResolveInfo): ByteArray? = runCatching {
        val d = ri.loadIcon(context.packageManager)
        val b = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, ICON_PX, ICON_PX)
        d.draw(Canvas(b))
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }.getOrNull()

    /** Starts the app. False if Android wouldn't. */
    fun launch(app: AppInfo): Boolean {
        val cn = app.androidComponent ?: return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }.isSuccess
    }

    /** Hands the app to Android's own uninstaller, which asks the user. */
    fun uninstall(app: AppInfo) {
        val cn = app.androidComponent ?: return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + cn.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        const val PREFIX = "android:"
        /** Luna decodes an icon at one PNG pixel per TouchPad px, so this is its size on screen. */
        private const val ICON_PX = 64
    }
}

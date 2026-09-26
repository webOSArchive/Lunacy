package org.webosarchive.lunacy.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
 * activity rather than opening a card. Apps the user installed (not part of the system image)
 * are userInstalled, so edit mode gives them the delete decorator, which hands them to
 * Android's uninstaller.
 *
 * [iconPx] is the icon's size on screen: the launcher's 64 TouchPad px at the shell's scale.
 * Icons are drawn at that size so they stay sharp whatever the scale.
 */
class AndroidApps(private val context: Context, private val files: AppFiles, private val iconPx: Int) {
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
                val system = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val version = runCatching { pm.getPackageInfo(cn.packageName, 0).versionName }.getOrNull().orEmpty()
                AppInfo(
                    id = id, title = ri.loadLabel(pm).toString(), main = "", icon = "", noWindow = true,
                    type = "android", version = version, userInstalled = !system, visible = true,
                    androidSettings = "", splashIcon = "", uiRevision = 2, category = "android",
                    keywords = emptyList(), appinfo = JSONObject(), files = files,
                    androidComponent = cn,
                    androidIcon = { icons.getOrPut(id) { png(ri) }?.let { ByteArrayInputStream(it) } },
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    /**
     * The activity's icon as an [iconPx] PNG. A launcher icon is 48 dp, so the art is asked for
     * at the density that makes it at least that big, as a launcher does with
     * ActivityManager's launcherLargeIconDensity; the app's own default otherwise.
     */
    private fun png(ri: android.content.pm.ResolveInfo): ByteArray? = runCatching {
        val pm = context.packageManager
        val dpi = maxOf((iconPx * 160 + 47) / 48, (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).launcherLargeIconDensity)
        val res = ri.activityInfo.iconResource
        @Suppress("DEPRECATION")
        val d = (if (res != 0) runCatching { pm.getResourcesForApplication(ri.activityInfo.applicationInfo).getDrawableForDensity(res, dpi) }.getOrNull() else null)
            ?: ri.loadIcon(pm)
        val b = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, iconPx, iconPx)
        d.draw(Canvas(b))
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }.getOrNull()

    /** A package was added, changed or removed: its icons are drawn again next time. */
    fun forget(packageName: String) { icons.keys.removeAll { it.startsWith("$PREFIX$packageName/") } }

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
    }
}

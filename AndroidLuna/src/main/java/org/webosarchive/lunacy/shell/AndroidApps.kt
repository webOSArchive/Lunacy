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
 * Android's uninstaller. The device's own camera wears webOS's camera icon ([systemCameras]),
 * and its own phone webOS's Phone & Video Calls icon ([systemDialers]).
 *
 * [iconPx] is the icon's size on screen: the launcher's 64 TouchPad px at the shell's scale.
 * Icons are drawn at that size so they stay sharp whatever the scale.
 */
class AndroidApps(private val context: Context, private val files: AppFiles, private val iconPx: Int) {
    /** Each activity's icon as a PNG, or null where none could be drawn; a miss is kept too. */
    private val icons = java.util.Collections.synchronizedMap(HashMap<String, ByteArray?>())

    private fun icon(id: String, ri: android.content.pm.ResolveInfo): ByteArray? = synchronized(icons) {
        if (icons.containsKey(id)) icons[id] else (stand(ri) ?: png(ri)).also { icons[id] = it }
    }

    /** The packages whose icon is webOS's own (see [systemCameras]); found with the list. */
    @Volatile private var cameras: Set<String> = emptySet()
    /** The launcher activities that are the device's own dialer (see [systemDialers]). */
    @Volatile private var dialers: Set<ComponentName> = emptySet()

    /** webOS's icon for an app that stands in for one of its own, drawn at [iconPx] as Android's are. */
    private fun stand(ri: android.content.pm.ResolveInfo): ByteArray? {
        val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
        val asset = when {
            ri.activityInfo.packageName in cameras -> CAMERA_ICON
            cn in dialers -> PHONE_ICON
            else -> return null
        }
        return runCatching {
            val art = context.assets.open(asset).use { android.graphics.BitmapFactory.decodeStream(it) }
            val b = Bitmap.createScaledBitmap(art, iconPx, iconPx, true)
            ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }.getOrNull()
    }

    fun list(): List<AppInfo> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val found = pm.queryIntentActivities(main, 0).filter { it.activityInfo.packageName != context.packageName }
        cameras = systemCameras()
        dialers = systemDialers(found)
        // Drawn ahead, off the main thread: rendering forty icons on the launcher's first
        // draw of the page cost it about half a second on the HP tablet.
        Thread({ for (ri in found) icon(PREFIX + ComponentName(ri.activityInfo.packageName, ri.activityInfo.name).flattenToShortString(), ri) }, "android-icons").start()
        return found
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
                    androidIcon = { icon(id, ri)?.let { ByteArrayInputStream(it) } },
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

    /**
     * The packages of the device's own camera: system apps that take a still picture
     * (MediaStore's STILL_IMAGE_CAMERA, the intent the lock screen's camera uses). Lunacy has no
     * camera app of its own, so the device's wears webOS's camera icon (codepoet, 2026-11-04);
     * a camera the user installed keeps its own.
     */
    private fun systemCameras(): Set<String> = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 0)
            .filter { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            .map { it.activityInfo.packageName }.toSet()
    }.getOrDefault(emptySet())

    /**
     * The device's own phone: the launcher activity of a system app that dials (ACTION_DIAL),
     * which wears webOS's Phone & Video Calls icon (codepoet, 2026-11-04). Where one package
     * holds the dialer and other apps (older Android's contacts package had Phone and People),
     * only the launcher activity that dials does; a package with one launcher activity, that one.
     */
    private fun systemDialers(launchers: List<android.content.pm.ResolveInfo>): Set<ComponentName> = runCatching {
        @Suppress("DEPRECATION")
        val dial = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:")), 0)
            .filter { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
        val dialComponents = dial.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toSet()
        val dialPackages = dial.map { it.activityInfo.packageName }.toSet()
        val byPackage = launchers.groupBy { it.activityInfo.packageName }
        launchers.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.filter { cn ->
            cn in dialComponents || (cn.packageName in dialPackages && byPackage[cn.packageName]?.size == 1)
        }.toSet()
    }.getOrDefault(emptySet())

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
        const val CAMERA_ICON = "luna/images/android/camera.png"
        const val PHONE_ICON = "luna/images/android/phone.png"
    }
}

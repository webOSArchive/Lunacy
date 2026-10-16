package org.webosarchive.lunacy.shell

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppFiles
import org.webosarchive.lunacy.card.AppInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Shortcuts other apps pin to the home screen - a browser's "Add to Home screen" above all - as
 * launcher icons, for when Lunacy is the home screen. They sit with Android's apps (each is an
 * [AppInfo] with [AppInfo.androidShortcut] set), so the launcher arranges them, Software
 * Manager can hide them, and edit mode's delete decorator unpins them.
 *
 * From Android 8 an app asks with ShortcutManager.requestPinShortcut, which reaches the home
 * app's CONFIRM_PIN_SHORTCUT activity ([PinShortcutActivity]); once accepted, Android keeps the
 * pin for the home app, so the list is LauncherApps' and there is nothing of Lunacy's to store.
 * Only the default home app may read or start them; when Lunacy isn't, there are none.
 *
 * Before Android 8 an app sent the INSTALL_SHORTCUT broadcast ([InstallShortcutReceiver]) with
 * the intent to start, a name and an icon, and the launcher kept them: here in
 * files/shortcuts.json. That intent is another app's, started with Lunacy's identity, so one
 * aimed at Lunacy itself is refused and its URI grants are dropped.
 */
class PinnedShortcuts(
    private val context: Context,
    /** The shell's; null for [InstallShortcutReceiver], which only stores. */
    private val files: AppFiles?,
    private val iconPx: Int = (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).launcherLargeIconSize,
) {
    private val store = File(context.filesDir, "shortcuts.json")
    private val icons = java.util.Collections.synchronizedMap(HashMap<String, ByteArray?>())

    fun list(): List<AppInfo> = if (Build.VERSION.SDK_INT >= 26) Pinned.list(this) else legacy().map { tile(it) }

    /** Starts the shortcut. False if Android wouldn't. */
    fun launch(app: AppInfo): Boolean {
        val id = app.androidShortcut ?: return false
        if (Build.VERSION.SDK_INT >= 26) return Pinned.launch(this, app.androidComponent!!.packageName, id)
        val entry = legacy().firstOrNull { it.optString("id") == id } ?: return false
        return runCatching { context.startActivity(safeIntent(entry.optString("intent")) ?: return false) }.isSuccess
    }

    /** Unpins it: the launcher's delete decorator. */
    fun remove(app: AppInfo) {
        val id = app.androidShortcut ?: return
        if (Build.VERSION.SDK_INT >= 26) Pinned.unpin(this, app.androidComponent!!.packageName, id)
        else synchronized(LOCK) { save(legacy().filter { it.optString("id") != id }) }
        icons.remove(PREFIX + app.androidComponent!!.packageName + "/" + id)
        changed()
    }

    /** A package's shortcuts changed: their icons are drawn again next time. */
    fun forget(packageName: String) { icons.keys.removeAll { it.startsWith("$PREFIX$packageName/") } }

    // ---- before Android 8: the INSTALL_SHORTCUT broadcast ----

    private fun legacy(): List<JSONObject> = synchronized(LOCK) {
        runCatching { JSONArray(store.readText()) }.getOrNull()?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
    }

    private fun save(entries: List<JSONObject>) { store.writeText(JSONArray(entries).toString()) }

    /** A shortcut sent with INSTALL_SHORTCUT, kept. False if it can't be: no intent, or one aimed at Lunacy. */
    fun addLegacy(data: Intent): Boolean {
        @Suppress("DEPRECATION")
        val target = data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT) ?: return false
        val uri = target.toUri(Intent.URI_INTENT_SCHEME)
        if (safeIntent(uri) == null) return false
        @Suppress("DEPRECATION")
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME).orEmpty()
        val icon = legacyIcon(data)
        synchronized(LOCK) {
            val entries = legacy().toMutableList()
            // The same intent pinned again is the same shortcut, renamed or re-iconed.
            entries.removeAll { it.optString("intent") == uri }
            entries += JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("intent", uri).put("title", name)
                .put("package", target.component?.packageName ?: target.`package` ?: "")
                .put("icon", icon?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: "")
            save(entries)
        }
        changed()
        return true
    }

    @Suppress("DEPRECATION")
    private fun legacyIcon(data: Intent): ByteArray? {
        data.getParcelableExtra<Bitmap>(Intent.EXTRA_SHORTCUT_ICON)?.let { return png(it) }
        val res = data.getParcelableExtra<Intent.ShortcutIconResource>(Intent.EXTRA_SHORTCUT_ICON_RESOURCE) ?: return null
        return runCatching {
            val r = context.packageManager.getResourcesForApplication(res.packageName)
            png(r.getDrawable(r.getIdentifier(res.resourceName, null, null)) ?: return null)
        }.getOrNull()
    }

    private fun tile(e: JSONObject): AppInfo {
        val id = e.optString("id")
        val pkg = e.optString("package")
        val icon = e.optString("icon").takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.DEFAULT) }
        return tile(pkg, id, e.optString("title"), null) { icon?.let { ByteArrayInputStream(it) } }
    }

    /** The intent to start, or null if it is aimed at Lunacy or can't be read. */
    private fun safeIntent(uri: String): Intent? {
        val i = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return null
        val own = context.packageName
        if (i.component?.packageName == own || i.`package` == own) return null
        if (i.resolveActivity(context.packageManager)?.packageName == own) return null
        i.selector = null
        i.flags = (i.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()) or Intent.FLAG_ACTIVITY_NEW_TASK
        return i
    }

    // ---- shared ----

    /** A shortcut as a launcher tile: an Android tile of the pinning app, with its shortcut id. */
    internal fun tile(pkg: String, id: String, title: String, activity: ComponentName?, icon: () -> java.io.InputStream?): AppInfo {
        val version = runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull().orEmpty()
        return AppInfo(
            id = "$PREFIX$pkg/$id", title = title, main = "", icon = "", noWindow = true,
            type = "android", version = version, userInstalled = true, visible = true,
            androidSettings = "", splashIcon = "", uiRevision = 2, category = "android",
            keywords = emptyList(), appinfo = JSONObject(), files = files!!,
            androidComponent = activity ?: ComponentName(pkg, ""), androidIcon = icon, androidShortcut = id,
        )
    }

    internal fun cachedIcon(key: String, draw: () -> ByteArray?): ByteArray? = synchronized(icons) {
        if (icons.containsKey(key)) icons[key] else draw().also { icons[key] = it }
    }

    internal fun png(d: Drawable): ByteArray {
        val b = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, iconPx, iconPx)
        d.draw(Canvas(b))
        return png(b)
    }

    private fun png(b: Bitmap): ByteArray {
        val scaled = if (b.width == iconPx && b.height == iconPx) b else Bitmap.createScaledBitmap(b, iconPx, iconPx, true)
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    internal val dpi get() = maxOf((iconPx * 160 + 47) / 48, (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).launcherLargeIconDensity)
    internal val launcherApps get() = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    /** From Android 8: the pins LauncherApps keeps for the home app. */
    @TargetApi(26)
    private object Pinned {
        fun pinned(p: PinnedShortcuts, pkg: String? = null): List<ShortcutInfo> = runCatching {
            val la = p.launcherApps
            if (!la.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            if (pkg != null) q.setPackage(pkg)
            la.getShortcuts(q, Process.myUserHandle()).orEmpty()
        }.getOrDefault(emptyList())

        fun list(p: PinnedShortcuts): List<AppInfo> = pinned(p).filter { it.isEnabled }.map { s ->
            val key = PREFIX + s.`package` + "/" + s.id
            p.tile(s.`package`, s.id, (s.shortLabel ?: s.longLabel ?: "").toString(), s.activity) {
                p.cachedIcon(key) { runCatching { p.launcherApps.getShortcutIconDrawable(s, p.dpi)?.let { p.png(it) } }.getOrNull() }
                    ?.let { ByteArrayInputStream(it) }
            }
        }

        fun launch(p: PinnedShortcuts, pkg: String, id: String): Boolean =
            runCatching { p.launcherApps.startShortcut(pkg, id, null, null, Process.myUserHandle()) }.isSuccess

        fun unpin(p: PinnedShortcuts, pkg: String, id: String) {
            runCatching { p.launcherApps.pinShortcuts(pkg, pinned(p, pkg).map { it.id } - id, Process.myUserHandle()) }
        }
    }

    companion object {
        const val PREFIX = "shortcut:"
        private val LOCK = Any()
        /** The shell's, while it runs: told when a shortcut is pinned or unpinned. */
        @Volatile var onChanged: (() -> Unit)? = null
        private fun changed() { onChanged?.let { f -> android.os.Handler(android.os.Looper.getMainLooper()).post(f) } }
        internal fun notifyChanged() = changed()
    }
}

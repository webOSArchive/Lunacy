package org.webosarchive.lunacy.card

import org.json.JSONObject
import org.webosarchive.lunacy.core.Assets

/**
 * The launcher's pages as Lunacy ships them, and LunaCE's rule for where an app goes: from the
 * bundled page map (assets/luna/launcher-pages.json, which says where each value comes from) and
 * the quick-launch bar's default (assets/luna/default-dock-positions.json). Shared by
 * AndroidLuna's launcher (LauncherStore) and Luna DE's shell, so both place an app alike.
 */
class LauncherPages(assets: Assets) {
    private val pageMap = runCatching {
        JSONObject(assets.open("luna/launcher-pages.json").bufferedReader().use { it.readText() })
    }.getOrDefault(JSONObject())

    private val dock = runCatching {
        JSONObject(assets.open("luna/default-dock-positions.json").bufferedReader().use { it.readText() })
    }.getOrDefault(JSONObject())

    /**
     * The pages a launcher starts with, designator to name: LunaCE's four built-in pages,
     * named as the reference TouchPad names them (APPS, DOWNLOADS, GAMES, SETTINGS).
     */
    val initialPages: List<Pair<String, String>> = pageMap.optJSONArray("pages")?.let { a ->
        (0 until a.length()).map { i ->
            val p = a.getJSONObject(i)
            p.optString("designator") to p.optString("name", p.optString("designator"))
        }
    }?.takeIf { it.isNotEmpty() } ?: listOf("apps" to "apps", "downloads" to "downloads", "favorites" to "android", "prefs" to "settings")

    /**
     * Where a bundled app starts, from `layout` in the page map: webOS shipped the same thing as
     * /etc/palm/default-launcher-page-layout.json, which is what put Palm's settings apps on
     * the prefs page. Page designator to app ids, in the order they should appear.
     */
    val defaultLayout: List<Pair<String, List<String>>> =
        pageMap.optJSONArray("layout")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val items = o.optJSONArray("items")
                o.optString("title") to (0 until (items?.length() ?: 0))
                    .map { j -> items!!.optString(j).removeSuffix("_default") }
            }
        }.orEmpty()

    /** keyword (lower case) to page designator. */
    private val keywordPages: Map<String, String> = pageMap.optJSONObject("keywords")?.let { o ->
        // AOSP's keys() is a raw Iterator: each key is a String.
        o.keys().asSequence().map { it.toString() }.associate { it.lowercase() to o.optString(it) }
    }.orEmpty()

    /**
     * The page an app with no place of its own belongs to, as LunaCE's
     * AppMonitor::pageDesignatorForWebOSApp works it out: its category first, then each of its
     * keywords; an app the user installed goes on the downloads page
     * (LauncherObject::pageIndexForAppByPredefinedDesignators). Null: the first page.
     */
    fun designatorFor(app: AppInfo): String? =
        keywordPages[app.category.lowercase()]
            ?: app.keywords.firstNotNullOfOrNull { keywordPages[it.lowercase()] }
            ?: if (app.userInstalled) "downloads" else null

    /**
     * The quick-launch bar's apps before the user arranges them, by app id: "quicklauncher", in
     * the shape of the device's /usr/palm/default-dock-positions.json, or on a phone
     * "quicklauncher-phone" where there is one.
     */
    fun quickLaunch(phone: Boolean = false): List<String> =
        ((if (phone) dock.optJSONArray("quicklauncher-phone") else null) ?: dock.optJSONArray("quicklauncher"))?.let { a ->
            (0 until a.length()).map { a.optString(it).removeSuffix("_default") }
        }.orEmpty()
}

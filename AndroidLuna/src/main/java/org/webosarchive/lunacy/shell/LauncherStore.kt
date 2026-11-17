package org.webosarchive.lunacy.shell

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppInfo
import org.webosarchive.lunacy.card.LauncherLayout
import org.webosarchive.lunacy.card.androidComponent

/**
 * Where the launcher's pages come from and go to: LunaCore's LauncherLayout, the rules Luna DE's
 * launcher keeps too, over the "launcher" preferences the user's tabs have always been kept in.
 * What Android has of its own comes in through LauncherLayout's hooks: Android's apps on their
 * tab (the favorites page, named "android"), and the device's own phone and camera where the
 * default layout names them.
 */
class LauncherStore(private val context: Context) {
    private val bundled = org.webosarchive.lunacy.card.LauncherPages(org.webosarchive.lunacy.card.AndroidAssets(context.assets))

    val layout = LauncherLayout(
        bundled, org.webosarchive.lunacy.card.AndroidKeyedStore(context, "launcher"),
        alias = { id, apps ->
            when (id) {
                // "android:phone" in the default layout is the device's own phone, whatever its package (AndroidApps.dialers).
                Launcher.ANDROID_PHONE -> {
                    val dialers = AndroidApps.dialers(context, apps.mapNotNull { it.androidComponent })
                    apps.firstOrNull { it.androidComponent in dialers }?.id
                }
                // "android:camera" the device's own camera (AndroidApps.cameraPackages).
                Launcher.ANDROID_CAMERA -> {
                    val cams = AndroidApps.cameraPackages(context)
                    apps.firstOrNull { it.androidComponent?.packageName in cams }?.id
                }
                else -> null
            }
        },
        // Proof of concept: Android's apps start on the favorites page, named "android".
        platformPage = { app -> if (app.androidComponent != null) "favorites" else null },
        // The old default name for favorites gives way to "android".
        rename = { d, name -> if (d == "favorites" && name == "games") "android" else name },
    )

    /** The pages a launcher starts with, before the user's own tabs. */
    fun initialPages(): MutableList<Launcher.Page> =
        bundled.initialPages.map { (designator, name) -> Launcher.Page(designator, name) }.toMutableList()

    /**
     * Each page's designator, name and cells, in order: the tabs themselves are the user's now.
     * A hidden app isn't on the page, so LauncherLayout puts it back where the last save had it.
     */
    fun save(pages: List<Launcher.Page>, hidden: Set<String>) {
        layout.saveTabs(JSONArray(pages.map { p ->
            JSONObject().put("designator", p.designator).put("name", p.title).put("apps", JSONArray(p.tiles.map { t ->
                when (t) {
                    is Launcher.Tile.App -> t.app.id
                    is Launcher.Tile.Group -> JSONObject().put("group", t.name).put("uid", t.id).put("apps", JSONArray(t.members.map { it.id }))
                }
            }))
        }), hidden)
    }
}

package org.webosarchive.lunacy.card

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What webOS's configurator did for installed packages: every app and service folder's
 * configuration/ files. The files in db/kinds (putKind parameters) and db/permissions
 * (putPermissions arrays) go to db8, tempdb/ likewise to tempdb. activities/ aren't supported
 * yet and are logged. Runs at startup and after each install; registering again replaces.
 *
 * Apps are read through [AppFiles], so a bundled app's configuration counts too: Palm's Clock
 * ships its alarm and preference kinds that way, and without them it can't store an alarm.
 */
class Configurator(private val files: AppFiles, private val db: Db8, private val tempdb: Db8) {
    fun run() {
        for (id in files.appIds()) {
            configure("${Packages.APPS}/$id/configuration/db", db, id)
            configure("${Packages.APPS}/$id/configuration/tempdb", tempdb, id)
            files.list("${Packages.APPS}/$id/configuration/activities").takeIf { it.isNotEmpty() }?.let {
                Log.i(AppServer.TAG, "configurator: $id has ${it.size} activities, not supported yet")
            }
        }
        // Services are only ever installed from a package, never bundled.
        File(files.root, JsServices.SERVICES).listFiles()?.forEach { dir ->
            val cfg = File(dir, "configuration")
            if (!cfg.isDirectory) return@forEach
            configureFiles(File(cfg, "db"), db, dir.name)
            configureFiles(File(cfg, "tempdb"), tempdb, dir.name)
        }
    }

    private fun configure(path: String, target: Db8, owner: String) {
        val kinds = read("$path/kinds", owner) { JSONObject(it) }
        val permissions = read("$path/permissions", owner) { JSONArray(it) }
        if (kinds.isNotEmpty() || permissions.isNotEmpty()) target.configure(kinds, permissions)
    }

    private fun <T> read(path: String, owner: String, parse: (String) -> T): List<T> =
        files.list(path).mapNotNull { name ->
            val text = files.open("$path/$name")?.use { it.bufferedReader().readText() } ?: return@mapNotNull null
            try { parse(text) } catch (e: Exception) {
                Log.w(AppServer.TAG, "configurator: $owner/$name unreadable: $e"); null
            }
        }

    // ---- the installed tree, for services ----

    private fun configureFiles(dir: File, target: Db8, pkg: String) {
        val kinds = parseFiles(File(dir, "kinds"), pkg) { JSONObject(it) }
        val permissions = parseFiles(File(dir, "permissions"), pkg) { JSONArray(it) }
        if (kinds.isNotEmpty() || permissions.isNotEmpty()) target.configure(kinds, permissions)
    }

    private fun <T> parseFiles(dir: File, pkg: String, parse: (String) -> T): List<T> =
        dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.mapNotNull { f ->
            try { parse(f.readText()) } catch (e: Exception) { Log.w(AppServer.TAG, "configurator: $pkg/${f.name} unreadable: $e"); null }
        }.orEmpty()
}

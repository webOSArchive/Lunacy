package org.webosarchive.lunacy.card

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What webOS's configurator did for installed packages: every app and service folder's
 * configuration/ files. The files in db/kinds (putKind parameters) and db/permissions (putPermissions
 * arrays) go to db8, tempdb/ likewise to tempdb. activities/ aren't supported yet and are
 * logged. Runs at startup and after each install; registering again replaces.
 */
class Configurator(private val installed: File, private val db: Db8, private val tempdb: Db8) {
    fun run() {
        val dirs = listOf(Packages.APPS, JsServices.SERVICES).flatMap { File(installed, it).listFiles()?.toList().orEmpty() }
        for (dir in dirs) {
            val cfg = File(dir, "configuration")
            if (!cfg.isDirectory) continue
            configure(File(cfg, "db"), db, dir.name)
            configure(File(cfg, "tempdb"), tempdb, dir.name)
            File(cfg, "activities").list()?.takeIf { it.isNotEmpty() }?.let {
                Log.i(AppServer.TAG, "configurator: ${dir.name} has ${it.size} activities, not supported yet")
            }
        }
    }

    private fun configure(dir: File, target: Db8, pkg: String) {
        val kinds = files(File(dir, "kinds"), pkg) { JSONObject(it) }
        val permissions = files(File(dir, "permissions"), pkg) { JSONArray(it) }
        if (kinds.isNotEmpty() || permissions.isNotEmpty()) target.configure(kinds, permissions)
    }

    private fun <T> files(dir: File, pkg: String, parse: (String) -> T): List<T> =
        dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.mapNotNull { f ->
            try { parse(f.readText()) } catch (e: Exception) { Log.w(AppServer.TAG, "configurator: $pkg/${f.name} unreadable: $e"); null }
        }.orEmpty()
}

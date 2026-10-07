package org.webosarchive.lunacy.card

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What webOS's configurator did for installed packages: every app and service folder's
 * configuration/ files. The files in db/kinds (putKind parameters) and db/permissions
 * (putPermissions arrays) go to db8, tempdb/ likewise to tempdb, and activities/ to the
 * activity manager ([activities]). Runs at startup and after each install; registering again
 * replaces.
 *
 * Apps are read through [AppFiles], so a bundled app's configuration counts too: Palm's Clock
 * ships its alarm and preference kinds that way, and without them it can't store an alarm.
 */
class Configurator(private val files: AppFiles, private val db: Db8, private val tempdb: Db8, private val system: File? = null,
                   private val bus: Bus? = null) {
    /** File cache types already defined in this run of the shell, by name and definition. */
    private val defined = HashMap<String, String>()

    fun run() {
        // The system's own kinds first, as the device's configurator read /etc/palm: those of
        // the services in the webOS root (accounts, palmprofile), in its ROM.
        if (system != null) {
            configureFiles(File(system, "etc/palm/db"), db, "/etc/palm/db")
            configureFiles(File(system, "etc/palm/tempdb"), tempdb, "/etc/palm/tempdb")
        }
        for (id in files.appIds()) {
            configure("${Packages.APPS}/$id/configuration/db", db, id)
            configure("${Packages.APPS}/$id/configuration/tempdb", tempdb, id)
        }
        // /etc/palm/db_kinds, the device's other kind folder: kind files with no folder for
        // permissions, which the native services own (the mail transports' kinds). After the
        // apps, since they extend kinds an app owns (com.palm.imap.account extends the Email
        // app's com.palm.mail.account).
        if (system != null) {
            val kinds = parseFiles(File(system, "etc/palm/db_kinds"), "/etc/palm/db_kinds") { text, _ -> JSONObject(text) }
            if (kinds.isNotEmpty()) db.configure(kinds, emptyList())
        }
        // The file cache's types, as webOS's configurator defined them at boot ("filecache"):
        // /etc/palm/filecache_types and packages' configuration/filecache, each file a
        // DefineType. The mail services keep message bodies in the "email" type.
        if (system != null) defineTypes(File(system, "etc/palm/filecache_types").listFiles().orEmpty().filter { it.isFile }.mapNotNull { f ->
            runCatching { f.readText() }.getOrNull()
        })
        for (id in files.appIds()) defineTypes(files.list("${Packages.APPS}/$id/configuration/filecache").mapNotNull { name ->
            files.open("${Packages.APPS}/$id/configuration/filecache/$name")?.use { it.bufferedReader().readText() }
        })
        carrierDefaults()
        // Services come from packages, or from the webOS root's /usr/palm/services.
        val activityFiles = ArrayList<Triple<String, String, String>>()
        (File(files.root, JsServices.SERVICES).listFiles().orEmpty().toList() +
            (system?.let { File(it, JsServices.SERVICES).listFiles() }.orEmpty())).forEach { dir ->
            val cfg = File(dir, "configuration")
            if (!cfg.isDirectory) return@forEach
            configureFiles(File(cfg, "db"), db, dir.name)
            configureFiles(File(cfg, "tempdb"), tempdb, dir.name)
            activityFiles += folderFiles(File(cfg, "activities"), SERVICE_ID)
        }
        // Activities last, once the kinds their triggers watch are registered: the apps',
        // the services' and the system's own (/etc/palm/activities).
        for (id in files.appIds()) {
            val dir = "${Packages.APPS}/$id/configuration/activities"
            for (creator in files.list(dir)) for (name in files.list("$dir/$creator")) {
                val text = files.open("$dir/$creator/$name")?.use { it.bufferedReader().readText() } ?: continue
                activityFiles += Triple("$dir/$creator/$name", "$APP_ID:$creator", text)
            }
        }
        system?.let { activityFiles += folderFiles(File(it, "etc/palm/activities"), SERVICE_ID) }
        activities(activityFiles)
    }

    /** A configuration folder's files one folder down, named for their creator: (path, "kind:creator", text). */
    private fun folderFiles(dir: File, kind: String): List<Triple<String, String, String>> =
        dir.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.flatMap { d ->
            d.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.mapNotNull { f ->
                runCatching { Triple(f.path, "$kind:${d.name}", f.readText()) }.getOrNull()
            }
        }

    /** Activity files created in this run of the shell, by path, with their text. */
    private val activitiesDone = HashMap<String, String>()

    /**
     * configuration/activities/<creator>/<file>, as Open webOS's ActivityConfigurator
     * handled them: each file is activitymanager/create's parameters, sent with the activity's
     * creator set to the folder's name (an app's id under an app, a service's otherwise) and
     * the configurator's own "firstUseSafe" removed. Until First Use has run and made the
     * profile account (/var/luna/preferences/ran-first-use and first-use-profile-created),
     * only files marked firstUseSafe are sent. webOS never remembered an activity file as
     * configured, so it created them again at every boot, which the files allow by asking
     * for "replace"; Lunacy does the same at every start, and within one run sends a file again
     * only when it changes. Not built: cancelling a removed package's activities.
     */
    private fun activities(found: List<Triple<String, String, String>>) {
        val bus = bus ?: return
        val prefs = system?.let { File(it, "var/luna/preferences") }
        val firstUseOnly = prefs == null || !File(prefs, "ran-first-use").exists() || !File(prefs, "first-use-profile-created").exists()
        for ((path, creator, text) in found) {
            if (activitiesDone[path] == text) continue
            val params = try { JSONObject(text) } catch (e: Exception) { Log.w(AppServer.TAG, "configurator: $path unreadable: $e"); continue }
            val activity = params.optJSONObject("activity") ?: run { Log.w(AppServer.TAG, "configurator: $path has no activity"); null } ?: continue
            if (firstUseOnly && !params.optBoolean("firstUseSafe")) continue
            val (kind, id) = creator.split(':', limit = 2)
            activity.put("creator", JSONObject().put(kind, id))
            params.remove("firstUseSafe")
            bus.call(CONFIGURATOR, "palm://com.palm.activitymanager/create", params.toString(), privateBus = true) { reply ->
                // An activity that exists already counts as configured, as webOS's took it.
                if (reply.contains("\"returnValue\":true") || reply.contains("\"errorCode\":17")) activitiesDone[path] = text
                else Log.w(AppServer.TAG, "configurator: activity $path: $reply")
            }
        }
    }

    private fun defineTypes(texts: List<String>) {
        val bus = bus ?: return
        for (text in texts) {
            val t = try { JSONObject(text) } catch (e: Exception) { Log.w(AppServer.TAG, "configurator: file cache type unreadable: $e"); continue }
            val name = t.optString("typeName")
            if (name.isEmpty()) continue
            val spec = t.toString()
            if (defined[name] == spec) continue
            bus.call(CONFIGURATOR, "palm://com.palm.filecache/DefineType", spec, privateBus = true) { reply ->
                // Remembered once the cache has answered: before the webOS root is laid down the
                // file cache isn't on the bus yet, and the next run defines the type. A type the
                // cache already has answers that it exists, which is what a reboot did too.
                if (!reply.contains("Service does not exist")) defined[name] = spec
                if (!reply.contains("\"returnValue\":true")) Log.i(AppServer.TAG, "configurator: file cache type $name: $reply")
            }
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
        val kinds = parseFiles(File(dir, "kinds"), pkg) { text, owner ->
            JSONObject(text).apply { if (owner != null && !has("owner")) put("owner", owner) }
        }
        val permissions = parseFiles(File(dir, "permissions"), pkg) { text, _ -> JSONArray(text) }
        if (kinds.isNotEmpty() || permissions.isNotEmpty()) target.configure(kinds, permissions)
    }

    /**
     * The files in dir, and in its folders one level down. /etc/palm/db/kinds keeps a service's
     * kinds in a folder named for it (kinds/com.palm.service.accounts/com.palm.account), and
     * those kinds name no owner: the folder is the owner, which parse is given.
     */
    private fun <T> parseFiles(dir: File, pkg: String, parse: (String, String?) -> T): List<T> {
        val entries = dir.listFiles().orEmpty().sortedBy { it.name }
        val files = entries.filter { it.isFile }.map { it to null } +
            entries.filter { it.isDirectory }.flatMap { d -> d.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.map { it to d.name } }
        return files.mapNotNull { (f, owner) ->
            try { parse(f.readText(), owner) } catch (e: Exception) { Log.w(AppServer.TAG, "configurator: $pkg/${f.name} unreadable: $e"); null }
        }
    }

    /**
     * Email's carrier defaults, which webOS's customization service wrote for a carrier's
     * build (the kind grants it alone besides Email): Email takes its default signature from
     * the record, and "-- Sent from my HP TouchPad" only when there is none. Lunacy's says
     * Lunacy: codepoet's exception to rule 0 (2026-10-07, Docs/luna-deltas.md). Written as the
     * customization service, once Email's kind is registered; the same record each time.
     */
    private fun carrierDefaults() {
        val bus = bus ?: return
        if (EMAIL !in files.appIds()) return
        val sig = JSONObject().put("_id", "lunacy-email-carrier-defaults").put("_kind", "$EMAIL.carrier_defaults:1")
            .put("defaultSignature", "-- Sent from Lunacy")
        bus.call(CUSTOMIZATION, "palm://com.palm.db/merge", JSONObject().put("objects", JSONArray().put(sig)).toString(),
            privateBus = true) { reply ->
            if (!reply.contains("\"returnValue\":true")) Log.w(AppServer.TAG, "configurator: carrier defaults: $reply")
        }
    }

    private companion object {
        const val CONFIGURATOR = "com.palm.configurator"
        const val APP_ID = "appId"
        const val SERVICE_ID = "serviceId"
        const val CUSTOMIZATION = "com.palm.service.customization"
        const val EMAIL = "com.palm.app.email"
    }
}

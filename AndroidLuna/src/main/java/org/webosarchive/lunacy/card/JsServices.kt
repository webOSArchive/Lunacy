package org.webosarchive.lunacy.card

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * webOS JS services. Each service package (usr/palm/services/<id>/, with services.json) runs in
 * its own Node process: nodejs-mobile's Node 12, started through liblunacynode.so, running
 * Lunacy's host.js, which provides what Palm's jsservicelauncher and mojoservice expect. The
 * package's bus names are registered when it is installed; the first call starts the process,
 * and it ends when it exits (mojoservice's activityTimeout) or Lunacy stops it.
 * See Docs/architecture.md, "JS services".
 */
class JsServices(private val bus: Bus, private val installed: File, private val webos: WebosRoot) {
    /** Lunacy's copy of the webOS filesystem that services see (frameworks, /media/internal, curl). */
    val root get() = webos.root
    private val worker = Executors.newSingleThreadExecutor()
    /** Bus name to its package's directory, as a webOS path. Main thread. */
    private val names = HashMap<String, String>()
    /** Running packages, by directory. Main thread. */
    private val running = HashMap<String, Proc>()

    /** What each package's files came to at the last reload, by directory: a change means a restart. */
    private val signatures = HashMap<String, String>()

    /**
     * Registers every service's bus names, the system's (/usr/palm/services, from the ROM or a
     * package's script) and then installed packages' (/media/cryptofs/apps/usr/palm/services),
     * and stops running services whose package changed or went. The others keep running, with
     * their subscriptions: installing one package is no reason to end another's.
     */
    fun reload() {
        names.keys.forEach { bus.unregisterService(it) }
        names.clear()
        val seen = HashMap<String, String>()
        scan(File(webos.root, SERVICES), "/$SERVICES", seen)
        scan(File(installed, SERVICES), "/media/cryptofs/apps/$SERVICES", seen)
        for (p in running.values.toList()) if (seen[p.dir] != signatures[p.dir]) p.stop()
        signatures.clear(); signatures += seen
        if (names.isNotEmpty()) Log.i(AppServer.TAG, "js services: ${names.keys.sorted()}")
    }

    /** Ends every service's process: the shell is going. */
    fun stopAll() { running.values.toList().forEach { it.stop() } }

    private fun scan(parent: File, webosPath: String, seen: HashMap<String, String>) {
        parent.listFiles()?.sortedBy { it.name }?.forEach { dir ->
            val json = File(dir, "services.json").takeIf { it.isFile } ?: return@forEach
            try {
                val text = json.readText()
                val services = JSONObject(text).optJSONArray("services") ?: return@forEach
                val path = "$webosPath/${dir.name}"
                seen[path] = signature(dir, text)
                for (i in 0 until services.length()) {
                    val name = services.getJSONObject(i).optString("name")
                    if (name.isEmpty()) continue
                    names[name] = path
                    bus.registerService(name, Bus.CallHandler { call(it) })
                }
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "services.json of ${dir.name} unreadable: $e")
            }
        }
    }

    /** services.json and the newest change anywhere in the package: what an install or a script would alter. */
    private fun signature(dir: File, servicesJson: String): String {
        var newest = 0L; var count = 0
        dir.walkTopDown().forEach { newest = maxOf(newest, it.lastModified()); count++ }
        return "$count:$newest:${servicesJson.hashCode()}"
    }

    /** The bus names installed packages' services answer to. */
    fun names(): org.json.JSONArray = org.json.JSONArray(names.keys.sorted())

    private fun call(call: Bus.Call) {
        val dir = names[call.service] ?: return call.reply(Bus.error("Service does not exist: ${call.service}."))
        val p = running[dir] ?: Proc(dir, call.service).also { running[dir] = it }
        p.request(call)
    }

    /** One service package's Node process (ServiceProcess carries its bus). */
    private inner class Proc(val dir: String, name: String) {
        private val link = ServiceProcess(bus, name, "js service", worker, start = {
            webos.prepare()
            if (!webos.node.isFile) throw IOException("no Node runtime in this build (${webos.node.path})")
            val pb = ProcessBuilder(webos.node.path, File(webos.host, "host.js").path, root.path, dir).directory(root)
            pb.environment().apply {
                // Its own luna-send calls, and its child processes', come from the service.
                putAll(webos.environment(name))
                put("LD_LIBRARY_PATH", webos.nativeDir)
            }
            pb.start()
        }, onEnded = { if (running[dir] == this) running.remove(dir) })

        fun request(call: Bus.Call) = link.request(call)
        fun stop() = link.stop()
    }

    companion object { const val SERVICES = "usr/palm/services" }
}

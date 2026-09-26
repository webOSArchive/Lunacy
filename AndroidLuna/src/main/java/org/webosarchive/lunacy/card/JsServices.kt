package org.webosarchive.lunacy.card

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
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
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    /** Bus name to its package's directory, as a webOS path. Main thread. */
    private val names = HashMap<String, String>()
    /** Running packages, by directory. Main thread. */
    private val running = HashMap<String, Proc>()

    /**
     * Registers every service's bus names, the system's (/usr/palm/services, from the ROM or a
     * package's script) and then installed packages' (/media/cryptofs/apps/usr/palm/services),
     * and stops running services whose package changed.
     */
    fun reload() {
        running.values.toList().forEach { it.stop() }
        names.keys.forEach { bus.unregisterService(it) }
        names.clear()
        scan(File(webos.root, SERVICES), "/$SERVICES")
        scan(File(installed, SERVICES), "/media/cryptofs/apps/$SERVICES")
        if (names.isNotEmpty()) Log.i(AppServer.TAG, "js services: ${names.keys.sorted()}")
    }

    private fun scan(parent: File, webosPath: String) {
        parent.listFiles()?.sortedBy { it.name }?.forEach { dir ->
            val json = File(dir, "services.json").takeIf { it.isFile } ?: return@forEach
            try {
                val services = JSONObject(json.readText()).optJSONArray("services") ?: return@forEach
                for (i in 0 until services.length()) {
                    val name = services.getJSONObject(i).optString("name")
                    if (name.isEmpty()) continue
                    names[name] = "$webosPath/${dir.name}"
                    bus.registerService(name, Bus.CallHandler { call(it) })
                }
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "services.json of ${dir.name} unreadable: $e")
            }
        }
    }

    /** The bus names installed packages' services answer to. */
    fun names(): org.json.JSONArray = org.json.JSONArray(names.keys.sorted())

    private fun call(call: Bus.Call) {
        val dir = names[call.service] ?: return call.reply(Bus.error("Service does not exist: ${call.service}."))
        val p = running[dir] ?: Proc(dir, call.service).also { running[dir] = it }
        p.request(call)
    }

    /** One service package's Node process. Its stdin and stdout carry the bus, one JSON object per line. */
    private inner class Proc(val dir: String, val name: String) {
        private var process: Process? = null
        private var stdin: OutputStream? = null
        private var ended = false
        private val queue = ArrayList<String>()           // lines waiting for the process to start
        private val requests = HashMap<Int, Bus.Call>()    // calls into the service, by id
        private val outgoing = HashMap<String, Bus.Call>() // the service's own calls, by its id
        private var nextId = 1

        init { worker.execute { start() } }

        private fun start() {
            try {
                webos.prepare()
                if (!webos.node.isFile) throw IOException("no Node runtime in this build (${webos.node.path})")
                val pb = ProcessBuilder(webos.node.path, File(webos.host, "host.js").path, root.path, dir).directory(root)
                pb.environment().apply {
                    // Its own luna-send calls, and its child processes', come from the service.
                    putAll(webos.environment(name))
                    put("LD_LIBRARY_PATH", webos.nativeDir)
                }
                val p = pb.start()
                Log.i(AppServer.TAG, "js service $name started ($dir)")
                Thread({ p.errorStream.bufferedReader().forEachLine { Log.i(AppServer.TAG, "svc [$name] $it") } }, "svc-err").start()
                Thread({
                    p.inputStream.bufferedReader().forEachLine { line -> main.post { receive(line) } }
                    val code = try { p.waitFor() } catch (e: InterruptedException) { -1 }
                    main.post { exited("exit $code") }
                }, "svc-out").start()
                main.post {
                    process = p; stdin = p.outputStream
                    queue.forEach { write(it) }; queue.clear()
                }
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "js service $name can't start: $e")
                main.post { exited("can't start: ${e.message}") }
            }
        }

        fun request(call: Bus.Call) {
            if (ended) return call.reply(Bus.error("Service does not exist: ${call.service}."))
            val id = nextId++
            requests[id] = call
            val slash = call.method.lastIndexOf('/')
            val category = if (slash > 0) "/" + call.method.substring(0, slash) else "/"
            send(JSONObject().put("t", "request").put("id", id).put("service", call.service)
                .put("category", category).put("method", call.method.substring(slash + 1))
                .put("payload", call.params.toString()).put("sender", call.appId)
                .put("subscribe", call.subscribe).put("outside", !call.privateBus))
            call.onCancel { if (requests.remove(id) != null) send(JSONObject().put("t", "cancel").put("id", id)) }
        }

        private fun receive(line: String) {
            val m = try { JSONObject(line) } catch (e: Exception) { Log.i(AppServer.TAG, "svc [$name] $line"); return }
            when (m.optString("t")) {
                "response" -> requests[m.optInt("id")]?.let { c ->
                    if (!c.subscribe) requests.remove(m.optInt("id"))
                    c.reply(m.optString("payload"))
                }
                // The service calls the bus itself (PalmCall): as its own caller, by its service name.
                "call" -> {
                    val id = m.optString("id")
                    val sub = m.optBoolean("subscribe")
                    val c = bus.call(name, m.optString("url"), m.optString("payload"), privateBus = true) { reply ->
                        send(JSONObject().put("t", "callResponse").put("id", id).put("payload", reply).put("subscribe", sub).put("sender", ""))
                    }
                    if (sub && !c.cancelled) outgoing[id] = c
                }
                "cancelCall" -> outgoing.remove(m.optString("id"))?.cancel()
                "ready" -> Log.i(AppServer.TAG, "js service $name ready")
            }
        }

        private fun send(m: JSONObject) {
            val line = m.toString()
            if (stdin == null) queue += line else write(line)
        }

        private fun write(line: String) = try {
            stdin?.apply { write((line + "\n").toByteArray()); flush() }
        } catch (e: IOException) { Log.w(AppServer.TAG, "js service $name: $e") }

        fun stop() {
            try { stdin?.close() } catch (e: IOException) {}
            process?.destroy()
            exited("stopped")
        }

        /** The process is gone: open calls get an error, the service's own calls end. */
        private fun exited(why: String) {
            if (ended) return
            ended = true
            if (running[dir] == this) running.remove(dir)
            Log.i(AppServer.TAG, "js service $name ended: $why")
            requests.values.toList().also { requests.clear() }.forEach { it.reply(Bus.error("Service exited: ${it.service}.")) }
            outgoing.values.toList().also { outgoing.clear() }.forEach { it.cancel() }
            queue.clear()
        }
    }

    companion object { const val SERVICES = "usr/palm/services" }
}

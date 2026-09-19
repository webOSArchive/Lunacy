package org.webosarchive.lunacy.card

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.system.Os
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
 * See docs/architecture.md, "JS services".
 */
class JsServices(private val context: Context, private val bus: Bus, private val installed: File) {
    /** Lunacy's copy of the webOS filesystem that services see (frameworks, /media/internal, curl). */
    val root = File(context.filesDir, "webos")
    private val host = File(context.filesDir, "jshost")
    private val nativeDir: String = context.applicationInfo.nativeLibraryDir
    private val node = File(nativeDir, "liblunacynode.so")
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    /** Bus name to its package's directory, as a webOS path. Main thread. */
    private val names = HashMap<String, String>()
    /** Running packages, by directory. Main thread. */
    private val running = HashMap<String, Proc>()
    @Volatile private var prepared = false

    /** Registers every installed service's bus names; stops running services whose package changed. */
    fun reload() {
        running.values.toList().forEach { it.stop() }
        names.keys.forEach { bus.unregisterService(it) }
        names.clear()
        File(installed, SERVICES).listFiles()?.forEach { dir ->
            val json = File(dir, "services.json").takeIf { it.isFile } ?: return@forEach
            try {
                val services = JSONObject(json.readText()).optJSONArray("services") ?: return@forEach
                for (i in 0 until services.length()) {
                    val name = services.getJSONObject(i).optString("name")
                    if (name.isEmpty()) continue
                    names[name] = "/media/cryptofs/apps/$SERVICES/${dir.name}"
                    bus.registerService(name, Bus.CallHandler { call(it) })
                }
            } catch (e: Exception) {
                Log.w(AppServer.TAG, "services.json of ${dir.name} unreadable: $e")
            }
        }
        if (names.isNotEmpty()) Log.i(AppServer.TAG, "js services: ${names.keys.sorted()}")
    }

    private fun call(call: Bus.Call) {
        val dir = names[call.service] ?: return call.reply(Bus.error("Service does not exist: ${call.service}."))
        val p = running[dir] ?: Proc(dir, call.service).also { running[dir] = it }
        p.request(call)
    }

    /** Builds root/: the frameworks and launcher from the APK (again after each update), plus links and curl. */
    private fun prepare() {
        if (prepared) return
        val stamp = File(root, ".lunacy-apk")
        val apk = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (!stamp.isFile || stamp.readText() != apk) {
            File(root, "usr").deleteRecursively()
            host.deleteRecursively()
            copyAssets("services-fw/frameworks", File(root, "usr/palm/frameworks"))
            copyAssets("services-fw/jsservicelauncher", File(root, "usr/palm/services/jsservicelauncher"))
            copyAssets("lunacy/services", host)
            val curl = "#!/system/bin/sh\nLD_LIBRARY_PATH=$nativeDir exec ${node.path} ${File(host, "curl.js").path} \"$@\"\n"
            for (name in listOf("curl", "curl11")) {
                File(root, "usr/bin/$name").apply { parentFile?.mkdirs(); writeText(curl); setExecutable(true, false) }
            }
            stamp.writeText(apk)
        }
        for (d in listOf("media/internal", "tmp", "var/tmp", "home/root", "bin")) File(root, d).mkdirs()
        link(installed.path, File(root, "media/cryptofs/apps"))
        link("/system/bin/sh", File(root, "bin/sh"))
        prepared = true
    }

    private fun link(target: String, at: File) {
        if (at.exists() || runCatching { Os.readlink(at.path) }.isSuccess) return
        at.parentFile?.mkdirs()
        Os.symlink(target, at.path)
    }

    private fun copyAssets(from: String, to: File) {
        val children = context.assets.list(from).orEmpty()
        if (children.isEmpty()) {
            to.parentFile?.mkdirs()
            context.assets.open(from).use { i -> to.outputStream().use { i.copyTo(it) } }
        } else children.forEach { copyAssets("$from/$it", File(to, it)) }
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
                prepare()
                if (!node.isFile) throw IOException("no Node runtime in this build (${node.path})")
                val pb = ProcessBuilder(node.path, File(host, "host.js").path, root.path, dir).directory(root)
                pb.environment().apply {
                    put("LD_LIBRARY_PATH", nativeDir); put("LUNACY_WEBOS_ROOT", root.path)
                    put("HOME", File(root, "home/root").path); put("TMPDIR", File(root, "tmp").path)
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
                .put("subscribe", call.subscribe).put("outside", true))
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
                    val c = bus.call(name, m.optString("url"), m.optString("payload")) { reply ->
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

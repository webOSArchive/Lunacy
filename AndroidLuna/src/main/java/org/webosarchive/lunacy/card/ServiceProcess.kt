package org.webosarchive.lunacy.card

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executor

/**
 * One service process on Lunacy's bus: a JS service package's Node (host.js) or a native
 * system service linked against Lunacy's liblunaservice.so. Its stdin and stdout carry the
 * bus, one JSON object per line, and its stderr is its log:
 *
 *   to it    {"t":"request","id","service","category","method","payload","sender","fromService","subscribe","outside"}
 *            {"t":"cancel","id"}, {"t":"callResponse","id","payload","subscribe","sender"}
 *            {"t":"extractfs","id","path"?}
 *   from it  {"t":"response","id","payload"}, {"t":"call","id","url","payload","subscribe","appId"?},
 *            {"t":"cancelCall","id"}, {"t":"ready"}, {"t":"extractfs","id","spec"}
 *
 * "extractfs" is webOS's thumbnailing filesystem, which a service read like any file ([Extractfs]):
 * the process asks for the file first and is given the webOS path it has been written to.
 *
 * [start] runs on [worker] and returns the started process. Everything else is on the main thread.
 */
class ServiceProcess(
    private val bus: Bus,
    /** The bus name the process's own calls come from. */
    val name: String,
    /** What the log calls it: "js service", "native service". */
    private val kind: String,
    worker: Executor,
    /** Makes an extractfs read's file, and gives its webOS path (or null) on the main thread. */
    private val extractfs: ((spec: String, reply: (String?) -> Unit) -> Unit)? = null,
    private val start: () -> Process,
    private val onEnded: (ServiceProcess) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var process: Process? = null
    private var stdin: OutputStream? = null
    var ended = false
        private set
    private val queue = ArrayList<String>()           // lines waiting for the process to start
    private val requests = HashMap<Int, Bus.Call>()    // calls into the service, by id
    private val outgoing = HashMap<String, Bus.Call>() // the service's own calls, by its id
    private var nextId = 1

    init { worker.execute { launch() } }

    private fun launch() {
        try {
            val p = start()
            Log.i(AppServer.TAG, "$kind $name started")
            // Ending the process closes its streams under these readers: that is the end of the
            // log or the link, not an error.
            Thread({ try { p.errorStream.bufferedReader().forEachLine { line -> log(line) } } catch (e: IOException) {} }, "svc-err").start()
            Thread({
                try { p.inputStream.bufferedReader().forEachLine { line -> main.post { receive(line) } } } catch (e: IOException) {}
                val code = try { p.waitFor() } catch (e: InterruptedException) { -1 }
                main.post { exited("exit $code") }
            }, "svc-out").start()
            main.post {
                // Stopped while it was still starting: the process must not be left running.
                if (ended) { p.destroy(); return@post }
                process = p; stdin = p.outputStream
                queue.forEach { write(it) }; queue.clear()
            }
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "$kind $name can't start: $e")
            main.post { exited("can't start: ${e.message}") }
        }
    }

    /**
     * A line of the service's stderr. A native service's syslog() arrives as
     * "<priority>ident: message" (the runtime's preload), and goes where webOS's syslogd put
     * it, /var/log/messages, for palm-log; everything goes to Android's log too.
     */
    private fun log(line: String) {
        Log.i(AppServer.TAG, "svc [$name] ${Bus.redact(line)}")
        SYSLOG.find(line)?.let { m ->
            SysLog.log(PRIORITIES.getOrElse(m.groupValues[1].toInt()) { "info" }, m.groupValues[2], m.groupValues[3])
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
            // A service's call carries its service name; an app's, its app id (LS2 tells them apart).
            .put("fromService", bus.has(call.appId))
            .put("subscribe", call.subscribe).put("outside", !call.privateBus))
        call.onCancel { if (requests.remove(id) != null) send(JSONObject().put("t", "cancel").put("id", id)) }
    }

    private fun receive(line: String) {
        val m = try { JSONObject(line) } catch (e: Exception) { Log.i(AppServer.TAG, "svc [$name] ${Bus.redact(line)}"); return }
        when (m.optString("t")) {
            "response" -> requests[m.optInt("id")]?.let { c ->
                if (!c.subscribe) requests.remove(m.optInt("id"))
                c.reply(m.optString("payload"))
            }
            // The service calls the bus itself: as its own caller, by its service name, or (a
            // system service's LSCallFromApplication) on behalf of the app it names.
            "call" -> {
                val id = m.optString("id")
                val sub = m.optBoolean("subscribe")
                val caller = m.optString("appId").ifEmpty { name }
                val c = bus.call(caller, m.optString("url"), m.optString("payload"), privateBus = true) { reply ->
                    send(JSONObject().put("t", "callResponse").put("id", id).put("payload", reply).put("subscribe", sub).put("sender", ""))
                }
                if (sub && !c.cancelled) outgoing[id] = c
            }
            "cancelCall" -> outgoing.remove(m.optString("id"))?.cancel()
            "ready" -> Log.i(AppServer.TAG, "$kind $name ready")
            "extractfs" -> {
                val id = m.optString("id")
                val made = extractfs ?: return send(JSONObject().put("t", "extractfs").put("id", id))
                made(m.optString("spec")) { path ->
                    if (!ended) send(JSONObject().put("t", "extractfs").put("id", id).apply { path?.let { put("path", it) } })
                }
            }
        }
    }

    private fun send(m: JSONObject) {
        val line = m.toString()
        if (stdin == null) queue += line else write(line)
    }

    private fun write(line: String) = try {
        stdin?.apply { write((line + "\n").toByteArray()); flush() }
    } catch (e: IOException) { Log.w(AppServer.TAG, "$kind $name: $e") }

    fun stop() = exited("stopped")

    /** The process is gone (or is to go): open calls get an error, the service's own calls end. */
    private fun exited(why: String) {
        if (ended) return
        ended = true
        try { stdin?.close() } catch (e: IOException) {}
        process?.destroy()
        onEnded(this)
        Log.i(AppServer.TAG, "$kind $name ended: $why")
        requests.values.toList().also { requests.clear() }.forEach { it.reply(Bus.error("Service exited: ${it.service}.")) }
        outgoing.values.toList().also { outgoing.clear() }.forEach { it.cancel() }
        queue.clear()
    }

    private companion object {
        val SYSLOG = Regex("^<([0-7])>([^:]{1,63}): (.*)$")
        val PRIORITIES = listOf("emerg", "alert", "crit", "err", "warning", "notice", "info", "debug")
    }
}

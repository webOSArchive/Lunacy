package org.webosarchive.lunacy.card

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A window's hybrid plugins (Docs/pdk.md, "Hybrid apps"): the PDK binaries its page embeds as
 * `<object type="application/x-palm-remote" exe="…">`. On webOS the browser's RemoteAdapter
 * plugin started the binary when the object went into the page and ended it when the object
 * left; the binary's JS handlers became the object's methods, called synchronously, and
 * PDL_CallJS called functions the page had set on the object. The page's side of that is the
 * bridge (assets/lunacy/bridge.js, "Plugins"); this is the shell's, with the binary run by the
 * PDK runtime as a PDK app's is ([PdkHost]).
 *
 * The object's status reaches the page as RemoteAdapter gave it, through the object's
 * `__PDL_PluginStatusChange__`: "connected" when the plugin has started, "ready" once it has
 * registered its handlers, "disconnected" when it ends. Measured on the reference TouchPad with
 * Adobe Reader (2026-10-07): connected, then the plugin's own first PDL_CallJS, then ready.
 *
 * What a plugin draws isn't shown yet: Quick Office's and Adobe Reader's are 1 px or 0 px
 * objects that render documents into files, with OpenGL ES where they use it (replayed
 * offscreen, as for a PDK card).
 */
class HybridPlugins(private val window: AppWindow, private val app: AppInfo, private val runtime: PdkRuntime,
                    /** Readies the webOS root, whose busybox runs a plugin's commands (WebosRoot.prepare). */
                    private val prepareRoot: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val plugins = ConcurrentHashMap<Int, Plugin>()

    private inner class Plugin(val id: Int, val exe: String) : PdkHost.Listener, PdkHost.JsLink {
        val host = PdkHost(window.context, app.id, app, runtime, this, PdkHost.Plugin(exe, this))
        private val calls = AtomicInteger(0)
        private val answers = ConcurrentHashMap<Int, LinkedBlockingQueue<Pair<Int, String>>>()
        @Volatile var connected = false
        @Volatile var ended = false

        /** The page's call of a handler; waits for the answer, as the page's script did on webOS. */
        fun call(name: String, args: List<String>): JSONObject {
            if (!connected || ended) return JSONObject().put("exception", "plugin not connected")
            val n = calls.incrementAndGet()
            val q = LinkedBlockingQueue<Pair<Int, String>>(1)
            answers[n] = q
            try {
                if (!host.jsCall(n, name, args)) return JSONObject().put("exception", "plugin not connected")
                while (true) {
                    val a = q.poll(1, TimeUnit.SECONDS)
                    if (a != null) return when (a.first) {
                        0 -> JSONObject().put("value", a.second)
                        1 -> JSONObject().put("exception", a.second)
                        else -> JSONObject()
                    }
                    if (ended) return JSONObject().put("exception", "plugin disconnected")
                }
            } finally { answers.remove(n) }
        }

        fun end() {
            ended = true
            host.stop()
            synchronized(early) { gl?.destroy(); gl = null }
            answers.values.forEach { it.offer(1 to "plugin disconnected") }
        }

        override fun onConnected() { connected = true; event("status", "connected") }
        override fun onReady(names: List<String>) { event("ready", JSONArray(names)) }
        override fun onReply(id: Int, kind: Int, value: String) { answers[id]?.offer(kind to value) }
        override fun onCallJs(name: String, args: List<String>) { event("call", name, JSONArray(args)) }
        override fun onExit(code: Int) {
            Log.i(AppServer.TAG, "[${app.id}] plugin $exe ended ($code)")
            val was = connected
            ended = true; connected = false
            answers.values.forEach { it.offer(1 to "plugin disconnected") }
            plugins.remove(id)
            synchronized(early) { gl?.destroy(); gl = null }
            if (was) event("status", "disconnected")
        }

        // OpenGL ES: replayed in the shell as a PDK card's is (PdkGl), with no view to show it
        // in. Quick Office's plugin draws a presentation's slides with GLES 2 and reads them
        // back, so it waits on the replay's answers.
        private var gl: PdkGl? = null
        private var glMode: Pair<Int, Int>? = null
        private var glVersion = 0
        private val early = ArrayList<ByteArray>()

        override fun onMode(width: Int, height: Int, gl: Boolean) { if (gl) synchronized(early) { glMode = width to height; startGl() } }
        override fun onGl(batch: ByteArray, version: Int) {
            synchronized(early) {
                gl?.let { it.replay(batch); return }
                early.add(batch)
                if (glVersion == 0) glVersion = version
                startGl()
            }
        }
        /** Under the lock: once the mode and the first batch's version are known. */
        private fun startGl() {
            val (w, h) = glMode ?: return
            if (gl != null || glVersion == 0 || ended || !PdkGl.available) return
            val stream = PdkGl(app.id, maxOf(1, w), maxOf(1, h), glVersion, host::glAnswer) {}
            early.forEach { stream.replay(it) }
            early.clear()
            gl = stream
        }
        override fun onFrame(frame: Bitmap) {}
        override fun onCaption(title: String) {}
        override fun onPdl(request: JSONObject) {}

        /** Tells the page, in order: every event goes through the main thread's queue. */
        private fun event(kind: String, vararg values: Any) {
            val args = values.joinToString(",") { if (it is String) JSONObject.quote(it) else it.toString() }
            main.post { if (!ended || kind == "status") window.evaluateJavascript("window.__lunacyPlugin&&__lunacyPlugin($id,${JSONObject.quote(kind)},$args)", null) }
        }
    }

    private val nextId = AtomicInteger(0)

    /** The page put a plugin object in: starts its binary. The object's id, or -1 if it can't run. */
    fun start(exe: String): Int {
        if (!app.appinfo.optBoolean("plug-ins", false)) {
            Log.w(AppServer.TAG, "[${app.id}] a plugin object, but appinfo.json hasn't \"plug-ins\": true"); return -1
        }
        if (!runtime.available || exe.isEmpty() || exe.startsWith("/") || exe.split('/').any { it == ".." }) return -1
        val p = Plugin(nextId.incrementAndGet(), exe)
        plugins[p.id] = p
        // A binary the package hasn't got never connects, as on webOS (Adobe Reader's page also
        // embeds Quick Office's plugin, from code the two apps share).
        if (!java.io.File(java.io.File(runtime.appsRoot, app.dir), exe).isFile) {
            Log.i(AppServer.TAG, "[${app.id}] plugin $exe isn't in the package"); return p.id
        }
        // Off the main thread: the root may still be being laid down after an update.
        Thread({
            prepareRoot()
            if (p.ended) return@Thread
            if (!p.host.start()) { Log.w(AppServer.TAG, "[${app.id}] plugin $exe couldn't start"); plugins.remove(p.id); p.ended = true }
            else Log.i(AppServer.TAG, "[${app.id}] plugin $exe started")
        }, "plugin-start").start()
        return p.id
    }

    /** A handler call from the page, on the bridge's thread: {"value"}, {"exception"} or {} for no reply. */
    fun call(id: Int, name: String, args: String): String {
        val p = plugins[id] ?: return JSONObject().put("exception", "plugin not connected").toString()
        val list = runCatching { JSONArray(args) }.getOrNull()?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        return p.call(name, list).toString()
    }

    /** The object left the page: its plugin ends, as RemoteAdapter's did. */
    fun stop(id: Int) { plugins.remove(id)?.end() }

    /** The page went away. */
    fun stopAll() { plugins.values.toList().forEach { stop(it.id) } }   // values: keys is Java 8's KeySetView, not on API 21
}

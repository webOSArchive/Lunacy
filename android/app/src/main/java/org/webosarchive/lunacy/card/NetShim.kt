package org.webosarchive.lunacy.card

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The native half of the network shim (assets/lunacy/net.js). webOS apps ran from file:// and
 * could reach any server with no CORS check; pages here are on https origins, so their
 * cross-origin XHRs come here instead. Requests go out as the reference TouchPad sent them
 * (measured with spike/probe 0.0.7, spike/results/touchpad-net.txt). One per window.
 */
class NetShim(private val appId: String, private val userAgent: String, private val deliver: (Int) -> Unit) {
    // Typed as Map: ConcurrentHashMap.keySet() compiles to a KeySetView call Android 5 lacks.
    private val results: MutableMap<Int, String> = ConcurrentHashMap()
    private val inFlight: MutableMap<Int, HttpURLConnection> = ConcurrentHashMap()
    private val aborted: MutableSet<Int> = java.util.Collections.newSetFromMap(ConcurrentHashMap())

    /** Queues a request; deliver(id) is called when netResult(id) has its answer. */
    fun send(id: Int, request: String) {
        pool.execute {
            val r = run(id, request)
            if (id in aborted) { aborted.remove(id); return@execute }
            results[id] = r
            deliver(id)
        }
    }

    fun sendSync(request: String): String = run(-1, request)

    fun result(id: Int): String { aborted.remove(id); return results.remove(id) ?: error("no result", "error") }

    fun abort(id: Int) {
        aborted += id
        // Off the pool, which may be full of the very requests being cancelled.
        inFlight.remove(id)?.let { c -> Thread { c.disconnect() }.start() }
    }

    /** A new page in the window: earlier pages' requests are dropped. */
    fun reset() {
        inFlight.keys.toList().forEach { abort(it) }
        results.clear()
    }

    private fun run(id: Int, request: String): String {
        val j = JSONObject(request)
        val method = j.getString("method").uppercase()
        val url = j.getString("url")
        try {
            val headers = ArrayList<Pair<String, String>>()
            val set = HashSet<String>()
            j.optJSONArray("headers")?.let { a ->
                for (i in 0 until a.length()) { val h = a.getJSONArray(i); headers += h.getString(0) to h.getString(1); set += h.getString(0).lowercase() }
            }
            fun default(k: String, v: String) { if (k.lowercase() !in set) headers += k to v }
            // What the TouchPad added. It also sent X-Palm-Carrier, which Lunacy has no value for.
            default("User-Agent", userAgent)
            default("Accept", "*/*")
            default("Accept-Language", "en-us,en;q=0.5")
            default("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.3")
            // The origin of a file:// page on the TouchPad, sent with everything but GET and HEAD.
            if (method != "GET" && method != "HEAD") default("Origin", "file://.media.cryptofs.apps.usr.palm.applications.$appId")
            if (j.has("user")) {
                val auth = j.optString("user") + ":" + j.optString("password")
                default("Authorization", "Basic " + Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP))
            }
            val body = when {
                j.isNull("body") -> null
                j.optBoolean("bodyBase64") -> Base64.decode(j.getString("body"), Base64.DEFAULT)
                else -> j.getString("body").toByteArray(Charsets.UTF_8)
            }
            val timeout = j.optInt("timeout", 0)
            val hops = ArrayList<List<Pair<String, String>>>()
            val c = Http.connect(Http.Request(method, url, headers, body,
                connectTimeoutMs = if (timeout > 0) timeout else CONNECT_TIMEOUT_MS, readTimeoutMs = if (timeout > 0) timeout else 300_000),
                object : Http.Tracker {
                    override fun opened(c: HttpURLConnection) { inFlight[id] = c }
                    override fun redirected(headers: List<Pair<String, String>>) { hops += headers }
                })
            try {
                val status = c.responseCode
                val bytes = (if (status >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
                Log.i(AppServer.TAG, "net [$appId] $method $url $status ${bytes.size}b")
                val out = JSONObject().put("status", status).put("url", c.url.toString())
                    .put("headers", JSONArray(merged(hops + listOf(Http.headersOf(c))).map { JSONArray(listOf(it.first, it.second)) }))
                if (j.optBoolean("binary")) out.put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                else out.put("text", String(bytes, charsetOf(j.optString("charset"), c.contentType, bytes)))
                return out.toString()
            } finally { c.disconnect() }
        } catch (e: Exception) {
            if (id !in aborted) Log.w(AppServer.TAG, "[$appId] net $method $url failed: $e")
            // "timeout" only for the page's own xhr.timeout; a server that never answers is an
            // "error" on the TouchPad (probe 0.0.9).
            val timedOut = e is java.net.SocketTimeoutException && j.optInt("timeout", 0) > 0
            return error(e.toString(), if (timedOut) "timeout" else "error")
        } finally { inFlight.remove(id) }
    }

    /**
     * The TouchPad's response headers after redirects: every hop's headers merged by name, a
     * later hop's value replacing an earlier one (so a redirect's Set-Cookie and Location stay).
     */
    private fun merged(hops: List<List<Pair<String, String>>>): List<Pair<String, String>> {
        val byName = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        for (hop in hops) {
            val names = hop.map { it.first.lowercase() }.toSet()
            names.forEach { byName.remove(it) }
            hop.forEach { byName.getOrPut(it.first.lowercase()) { ArrayList() } += it }
        }
        return byName.values.flatten()
    }

    private fun error(text: String, kind: String) = JSONObject().put("error", kind).put("errorText", text).toString()

    companion object {
        private val pool = Executors.newFixedThreadPool(6)
        /**
         * How long a server that never answers takes to fail: about 9 s on the reference
         * TouchPad, for synchronous and asynchronous requests alike (spike/probe 0.0.9). A
         * synchronous XHR blocks its page that long, as it did on webOS.
         */
        private const val CONNECT_TIMEOUT_MS = 9_000
        private val CHARSET = Regex("charset\\s*=\\s*\"?([\\w.:-]+)", RegexOption.IGNORE_CASE)
        private val XML_DECL = Regex("^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([\\w.:-]+)")

        /** The page's override, then the response's charset, then an XML declaration, then UTF-8. */
        fun charsetOf(override: String, contentType: String?, bytes: ByteArray): Charset {
            val name = override.ifEmpty { null }
                ?: contentType?.let { CHARSET.find(it)?.groupValues?.get(1) }
                ?: XML_DECL.find(String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1))?.groupValues?.get(1)
            return try { if (name != null) Charset.forName(name) else Charsets.UTF_8 } catch (e: Exception) { Charsets.UTF_8 }
        }
    }
}

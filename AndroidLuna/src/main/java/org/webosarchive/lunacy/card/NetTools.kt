package org.webosarchive.lunacy.card

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * palm://com.palm.nettools: findMxRecords, which Email's account wizard asks before it guesses
 * a domain's mail servers. Android has no MX lookup an app can use below API 29, so the query
 * goes to the active network's own resolvers over UDP, as webOS's c-ares did. Replies
 * measured on the reference TouchPad (webOS CE 3.1.0):
 *
 *   {"returnValue":true,"mxRecords":[{"mxServer":"in1-smtp.messagingengine.com","mxPreference":10},…]}
 *     in the order the server gave them
 *   {"returnValue":false,"errorCode":1,"errorText":"Domain name exists but there were no mx records returned"}
 *   {"returnValue":false,"errorCode":4,"errorText":"The domain name provided was not found"}
 *   {"returnValue":false,"errorCode":8,"errorText":"Error encoding domain name"}   (no domainName)
 *
 * The device's service had other methods; they answer as unknown here until something needs one.
 */
class NetTools(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newCachedThreadPool()

    fun register(bus: Bus) {
        bus.register(SERVICE, "findMxRecords") { _, p, reply ->
            val domain = p.optString("domainName").trim().trimEnd('.')
            if (domain.isEmpty() || domain.length > 253 || domain.split('.').any { it.isEmpty() || it.length > 63 })
                return@register reply(error(8, "Error encoding domain name"))
            worker.execute {
                val r = try { lookup(domain) } catch (e: Exception) { error(4, "The domain name provided was not found") }
                main.post { reply(r) }
            }
        }
    }

    private fun lookup(domain: String): String {
        val servers = resolvers()
        var last: Exception? = null
        for (server in servers) {
            try {
                val (rcode, records) = query(server, domain)
                return when {
                    rcode == 3 -> error(4, "The domain name provided was not found")
                    rcode != 0 -> throw IllegalStateException("rcode $rcode")
                    records.isEmpty() -> error(1, "Domain name exists but there were no mx records returned")
                    else -> JSONObject().put("returnValue", true).put("mxRecords", JSONArray(records.map {
                        JSONObject().put("mxServer", it.first).put("mxPreference", it.second)
                    })).toString()
                }
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("no resolver")
    }

    @Suppress("DEPRECATION")
    private fun resolvers(): List<InetAddress> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = if (android.os.Build.VERSION.SDK_INT >= 23) cm.activeNetwork else {
            val type = cm.activeNetworkInfo?.takeIf { it.isConnected }?.type
            cm.allNetworks.firstOrNull { cm.getNetworkInfo(it)?.let { i -> i.isConnected && i.type == type } == true }
        }
        return net?.let { cm.getLinkProperties(it)?.dnsServers }.orEmpty().ifEmpty { listOf(InetAddress.getByName("8.8.8.8")) }
    }

    /** One MX query: the response code and the (exchange, preference) records, in answer order. */
    private fun query(server: InetAddress, domain: String): Pair<Int, List<Pair<String, Int>>> {
        val id = (Math.random() * 65535).toInt()
        val q = ByteArrayOutputStream()
        q.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in domain.split('.')) { val b = label.toByteArray(); q.write(b.size); q.write(b) }
        q.write(byteArrayOf(0, 0, 15, 0, 1))
        val out = q.toByteArray()
        DatagramSocket().use { s ->
            s.soTimeout = 4000
            s.send(DatagramPacket(out, out.size, server, 53))
            val buf = ByteArray(4096)
            val pkt = DatagramPacket(buf, buf.size)
            while (true) {
                s.receive(pkt)
                val d = buf.copyOf(pkt.length)
                if (d.size < 12 || ((d[0].toInt() and 0xff) shl 8 or (d[1].toInt() and 0xff)) != id) continue
                return parse(d)
            }
        }
    }

    private fun parse(d: ByteArray): Pair<Int, List<Pair<String, Int>>> {
        fun u16(i: Int) = (d[i].toInt() and 0xff) shl 8 or (d[i + 1].toInt() and 0xff)
        val rcode = d[3].toInt() and 0x0f
        val qd = u16(4); val an = u16(6)
        var i = 12
        repeat(qd) { i = skipName(d, i) + 4 }
        val out = ArrayList<Pair<String, Int>>()
        repeat(an) {
            i = skipName(d, i)
            val type = u16(i); val len = u16(i + 8)
            val rdata = i + 10
            if (type == 15) out += name(d, rdata + 2) to u16(rdata)
            i = rdata + len
        }
        return rcode to out
    }

    private fun skipName(d: ByteArray, start: Int): Int {
        var i = start
        while (true) {
            val len = d[i].toInt() and 0xff
            if (len == 0) return i + 1
            if (len and 0xc0 == 0xc0) return i + 2
            i += len + 1
        }
    }

    private fun name(d: ByteArray, start: Int): String {
        val parts = ArrayList<String>()
        var i = start; var jumps = 0
        while (jumps < 20) {
            val len = d[i].toInt() and 0xff
            if (len == 0) break
            if (len and 0xc0 == 0xc0) { i = (len and 0x3f) shl 8 or (d[i + 1].toInt() and 0xff); jumps++; continue }
            parts += String(d, i + 1, len, Charsets.US_ASCII)
            i += len + 1
        }
        return parts.joinToString(".")
    }

    private fun error(code: Int, text: String) =
        JSONObject().put("returnValue", false).put("errorCode", code).put("errorText", text).toString()

    companion object { const val SERVICE = "com.palm.nettools" }
}

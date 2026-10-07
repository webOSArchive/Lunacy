package org.webosarchive.lunacy.card

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkRequest
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * The two files of a webOS root that describe the network, written from Android's own:
 *
 * - `/etc/ssl/certs/ca-certificates.crt`, the CA bundle the TouchPad's native services verify
 *   servers against (the mail services' `SSL_CERT_FILE`), made from Android's trust store
 *   ("AndroidCAStore": the system's roots and any the user added), so it is as current as the
 *   device rather than as the TouchPad's 2011 bundle (codepoet: rely on what Android provides).
 * - `/etc/resolv.conf`, which a glibc binary's resolver (c-ares, under libpalmsocket) reads for
 *   its name servers: Android keeps them in its link properties, not in a file, so the active
 *   network's are written there and again whenever they change.
 *
 * Native binaries reach both through the runtime's preload, which maps /etc/ssl and
 * /etc/resolv.conf into the root.
 */
class HostNetwork(private val context: Context, private val root: File) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun start() {
        writeResolvConf(active()?.let { cm.getLinkProperties(it) })
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                if (network == active()) writeResolvConf(lp)
            }
            override fun onAvailable(network: Network) { writeResolvConf(active()?.let { cm.getLinkProperties(it) }) }
            override fun onLost(network: Network) { writeResolvConf(active()?.let { cm.getLinkProperties(it) }) }
        })
        Thread({ writeTrustStore() }, "ca-bundle").start()
    }

    /** The network Android routes through: activeNetwork from API 23, the connected one of the active type before. */
    @Suppress("DEPRECATION")
    private fun active(): Network? {
        if (android.os.Build.VERSION.SDK_INT >= 23) return cm.activeNetwork
        val type = cm.activeNetworkInfo?.takeIf { it.isConnected }?.type ?: return null
        return cm.allNetworks.firstOrNull { cm.getNetworkInfo(it)?.let { i -> i.isConnected && i.type == type } == true }
    }

    @Synchronized private fun writeResolvConf(lp: LinkProperties?) {
        val servers = lp?.dnsServers.orEmpty().mapNotNull { it.hostAddress?.substringBefore('%') }
        val text = buildString {
            append("# Written by Lunacy from Android's active network\n")
            lp?.domains?.takeIf { it.isNotBlank() }?.let { append("search $it\n") }
            // With no network, the public resolvers a TouchPad fell back to are as good as none.
            (servers.ifEmpty { listOf("8.8.8.8") }).forEach { append("nameserver $it\n") }
        }
        write(File(root, "etc/resolv.conf"), text)
    }

    private fun writeTrustStore() {
        try {
            val ks = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val text = StringBuilder()
            var n = 0
            for (alias in ks.aliases().toList().sorted()) {
                val cert = ks.getCertificate(alias) as? X509Certificate ?: continue
                text.append("# ").append(cert.subjectX500Principal.name).append('\n')
                text.append("-----BEGIN CERTIFICATE-----\n")
                text.append(Base64.encodeToString(cert.encoded, Base64.DEFAULT))
                text.append("-----END CERTIFICATE-----\n")
                n++
            }
            if (write(File(root, "etc/ssl/certs/ca-certificates.crt"), text.toString()))
                Log.i(AppServer.TAG, "webOS root: CA bundle from Android's trust store, $n certificates")
        } catch (e: Exception) {
            Log.w(AppServer.TAG, "webOS root: can't read Android's trust store: $e")
        }
    }

    /** Writes a file if its text changed; true if it did. */
    private fun write(f: File, text: String): Boolean {
        if (f.isFile && f.readText() == text) return false
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, ".${f.name}.new")
        tmp.writeText(text)
        return tmp.renameTo(f)
    }
}

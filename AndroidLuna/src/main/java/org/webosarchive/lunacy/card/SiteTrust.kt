package org.webosarchive.lunacy.card

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/**
 * Whether a browser view goes on past a certificate error: a certificate the owner chose to
 * Trust Always on that host, or one that holds up under Lunacy's own trust store, goes on;
 * anything else asks the page (dialogSSLConfirm), as the TouchPad's browser asked.
 */
object SiteTrust {
    /**
     * Trust Always: each host with the fingerprint of the certificate the owner trusted,
     * kept across runs. Another certificate on that host is asked about again.
     */
    private const val TRUSTED_CERTIFICATES = "trustedcertificates"
    /** Host and certificate pairs that held up under Lunacy's own trust store. */
    private val verified = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())

    private fun trustKey(host: String, certificate: ByteArray) = host + "/" +
        java.security.MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }

    private fun store(context: Context) = context.getSharedPreferences(TRUSTED_CERTIFICATES, Context.MODE_PRIVATE)

    /** The owner answered Trust Always for [key], as [check] handed it to [ask]. */
    fun trustAlways(context: Context, key: String) { store(context).edit().putBoolean(key, true).apply() }

    /**
     * [error]'s certificate: [proceed] if it is trusted, else [ask] with the host and the key
     * Trust Always would keep it under. Main thread; [worker] checks the store off it.
     */
    fun check(context: Context, error: SslError, worker: Executor, proceed: () -> Unit, ask: (host: String, always: String?) -> Unit) {
        val uri = Uri.parse(error.url)
        val host = uri.host.orEmpty()
        val seen = android.net.http.SslCertificate.saveState(error.certificate)?.getByteArray("x509-certificate")
        val always = seen?.let { trustKey(host, it) }
        if (always != null && store(context).contains(always)) { proceed(); return }
        // An older Android's store lacks today's roots (Android 5 refuses Wikipedia's).
        // Lunacy's own requests trust the bundled roots as well (Http); if the site's
        // certificate holds up there and is the one this view was shown, it is good.
        // (Expiry and the host name are checked there too.)
        if (error.primaryError == SslError.SSL_UNTRUSTED) {
            val key = host + "/" + seen?.contentHashCode()
            if (key in verified) { proceed(); return }
            val port = if (uri.port > 0) uri.port else 443
            worker.execute {
                val good = seen != null && Http.verifiedLeaf(host, port)?.contentEquals(seen) == true
                main.post { if (good) { verified.add(key); proceed() } else ask(host, always) }
            }
            return
        }
        ask(host, always)
    }
}

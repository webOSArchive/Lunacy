package org.webosarchive.lunacy.card

import android.content.res.AssetManager
import android.webkit.CookieManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Lunacy's own HTTP: the network shim and package downloads. Cookies live in the WebView's
 * jar, so pages and native requests share them. TLS trusts Android's store and the bundled
 * Mozilla roots (assets/certs), since Android 5's store lacks current roots.
 */
object Http {
    class Request(
        val method: String,
        val url: String,
        /** In the order set; names as given. */
        val headers: List<Pair<String, String>> = emptyList(),
        val body: ByteArray? = null,
        val connectTimeoutMs: Int = 30_000,
        val readTimeoutMs: Int = 300_000,
    )

    /**
     * Called with each connection as it opens (one per redirect hop), so a caller can cancel it,
     * and with each redirect's response headers.
     */
    interface Tracker {
        fun opened(c: HttpURLConnection)
        fun redirected(headers: List<Pair<String, String>>) {}
    }

    private const val MAX_REDIRECTS = 20
    private lateinit var assets: AssetManager
    private val tls: SSLSocketFactory by lazy { tlsFactory() }

    fun init(assets: AssetManager) { this.assets = assets }

    /**
     * Sends the request and follows redirects as the TouchPad's WebKit did: 301/302/303 turn a
     * POST into a GET without its body (headers stay), and each hop sends the previous URL as
     * Referer. Returns the final connection, its response code already read.
     */
    fun connect(req: Request, tracker: Tracker? = null): HttpURLConnection {
        var url = URL(req.url)
        var method = req.method.uppercase()
        var body = req.body
        var referer: String? = null
        repeat(MAX_REDIRECTS + 1) {
            if (url.protocol != "http" && url.protocol != "https") throw IOException("can't fetch ${url.protocol} URLs")
            val c = url.openConnection() as HttpURLConnection
            tracker?.opened(c)
            if (c is HttpsURLConnection) c.sslSocketFactory = tls
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.connectTimeout = req.connectTimeoutMs
            c.readTimeout = req.readTimeoutMs
            c.requestMethod = method
            val names = HashSet<String>()
            for ((k, v) in req.headers) { c.setRequestProperty(k, v); names += k.lowercase() }
            if (referer != null && "referer" !in names) c.setRequestProperty("Referer", referer)
            if ("cookie" !in names) CookieManager.getInstance().getCookie(url.toString())?.let { c.setRequestProperty("Cookie", it) }
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            c.headerFields["Set-Cookie"]?.forEach { CookieManager.getInstance().setCookie(url.toString(), it) }
            val location = c.getHeaderField("Location")
            if (code in 300..399 && code != 304 && location != null) {
                tracker?.redirected(headersOf(c))
                c.disconnect()
                referer = url.toString()
                url = URL(url, location)
                if (code in 301..303 && method != "GET" && method != "HEAD") { method = "GET"; body = null }
                return@repeat
            }
            return c
        }
        throw IOException("too many redirects for ${req.url}")
    }

    /** Response headers as sent, minus the ones Android's client adds for itself. */
    fun headersOf(c: HttpURLConnection): List<Pair<String, String>> =
        c.headerFields.flatMap { (k, vs) ->
            if (k == null || k.startsWith("X-Android-", ignoreCase = true)) emptyList() else vs.map { k to it }
        }

    private fun tlsFactory(): SSLSocketFactory {
        val system = trustManager(null)
        val bundled = trustManager(KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            assets.open("certs/cacerts.pem").use { s ->
                CertificateFactory.getInstance("X.509").generateCertificates(s).forEachIndexed { i, cert -> setCertificateEntry("mozilla-$i", cert) }
            }
        })
        val either = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try { system.checkServerTrusted(chain, authType) } catch (e: CertificateException) { bundled.checkServerTrusted(chain, authType) }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers + bundled.acceptedIssuers
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(either), null) }.socketFactory
    }

    private fun trustManager(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
}

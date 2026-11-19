package org.webosarchive.lunacy.core

/**
 * The cookie jar native requests share (Http): on Android the WebViews' own (CookieManager), so a
 * page and its XHRs through the network shim see the same cookies, as on a device. Luna DE keeps
 * one in its host ([MemoryCookies]) until it reads the Electron session's (Docs/luna-de.md).
 */
interface Cookies {
    /** The Cookie header for [url], or null. */
    fun get(url: String): String?
    /** A Set-Cookie header [url] answered with. */
    fun set(url: String, setCookie: String)
}

/** A cookie jar in memory, by java.net's rules: Luna DE's host's for now. */
class MemoryCookies : Cookies {
    private val manager = java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)
    override fun get(url: String): String? = runCatching {
        manager.get(java.net.URI(url), emptyMap())["Cookie"]?.joinToString("; ")?.takeIf { it.isNotEmpty() }
    }.getOrNull()
    override fun set(url: String, setCookie: String) {
        runCatching { manager.put(java.net.URI(url), mapOf("Set-Cookie" to listOf(setCookie))) }
    }
}

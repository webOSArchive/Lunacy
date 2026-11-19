package org.webosarchive.lunacy.core

/**
 * Base64 (RFC 4648), for LunaCore: java.util.Base64 is Android 8's (API 26), and android.util's
 * isn't on the desktop. Encodes without line breaks (Base64.NO_WRAP's form); decodes as
 * Base64.DEFAULT did, passing over white space, padding optional.
 */
object Base64Codec {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val VALUES = IntArray(128) { -1 }.also { v ->
        ALPHABET.forEachIndexed { i, c -> v[c.code] = i }
        v['-'.code] = 62; v['_'.code] = 63   // the URL-safe alphabet, which Base64.DEFAULT also read
    }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xff
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else -1
            out.append(ALPHABET[b0 shr 2])
            out.append(ALPHABET[((b0 and 3) shl 4) or (if (b1 < 0) 0 else b1 shr 4)])
            out.append(if (b1 < 0) '=' else ALPHABET[((b1 and 15) shl 2) or (if (b2 < 0) 0 else b2 shr 6)])
            out.append(if (b2 < 0) '=' else ALPHABET[b2 and 63])
            i += 3
        }
        return out.toString()
    }

    /** Throws IllegalArgumentException on a character that isn't Base64. */
    fun decode(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(text.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in text) {
            if (c == '=' ) break
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') continue
            val v = if (c.code < 128) VALUES[c.code] else -1
            require(v >= 0) { "bad base64 character '$c'" }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) { bits -= 8; out.write((buffer shr bits) and 0xff) }
        }
        return out.toByteArray()
    }
}

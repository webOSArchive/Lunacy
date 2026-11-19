package org.webosarchive.lunacy.card

import org.json.JSONObject

/**
 * Text pasted into a script for evaluateJavascript. JSON allows U+2028 and U+2029 raw in a
 * string, and org.json leaves them so, but Chromium 37's JavaScript reads them as line ends:
 * the script is a SyntaxError and is dropped, with the bus reply, paste or event it carried.
 */
fun jsSafe(script: String): String = script.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

/** [s] as a JavaScript string literal. */
fun jsString(s: String): String = jsSafe(JSONObject.quote(s))

/**
 * A served file's text, by its byte-order mark, as WebKit read a script, a stylesheet or a page
 * from a file: UTF-16 (either order) or UTF-8 when it carries one, UTF-8 when it doesn't. The
 * mark itself isn't part of the text. Apps written on Windows shipped UTF-16 scripts (Glimpse's
 * functions.js, converter.js, weather.js and pix.js); read as UTF-8 they reached the page as a
 * syntax error on their first character, and the app ran without them.
 */
fun servedText(s: java.io.InputStream): String {
    val b = s.readBytes()
    fun at(i: Int) = if (i < b.size) b[i].toInt() and 0xff else -1
    return when {
        at(0) == 0xff && at(1) == 0xfe -> String(b, 2, b.size - 2, Charsets.UTF_16LE)
        at(0) == 0xfe && at(1) == 0xff -> String(b, 2, b.size - 2, Charsets.UTF_16BE)
        at(0) == 0xef && at(1) == 0xbb && at(2) == 0xbf -> String(b, 3, b.size - 3, Charsets.UTF_8)
        else -> String(b, Charsets.UTF_8)
    }
}

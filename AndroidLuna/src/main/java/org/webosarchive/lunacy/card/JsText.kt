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

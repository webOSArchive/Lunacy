package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject

/**
 * What another system's "send this by email" becomes for webOS's Email: the launch parameters
 * Palm documented for apps that wanted a message composed (`summary`, `text`, `recipients`;
 * Email's nowindow/source/Launch.js and compose/source/Composition.js), or a mailto: target.
 * The shell opens Email with them as any app did through applicationManager/open; nothing
 * here is Email's alone to know.
 */
object ShareToEmail {
    const val EMAIL = "com.palm.app.email"

    // EmailRecipient's roles for an outside app's recipients.
    private const val TO = 1
    private const val CC = 2
    private const val BCC = 3

    /**
     * A message to compose: [subject], [text] as plain text, and whoever it is for. Null when
     * there is nothing to put in it, which Email would take for an ordinary launch.
     */
    fun compose(subject: String?, text: String?, to: List<String> = emptyList(), cc: List<String> = emptyList(),
                bcc: List<String> = emptyList()): JSONObject? {
        val recipients = JSONArray()
        for ((role, list) in listOf(TO to to, CC to cc, BCC to bcc)) for (a in list) {
            val addr = a.trim()
            if (addr.isNotEmpty()) recipients.put(JSONObject().put("value", addr).put("contactDisplay", addr).put("role", role))
        }
        if (subject.isNullOrEmpty() && text.isNullOrEmpty() && recipients.length() == 0) return null
        val p = JSONObject()
        if (!subject.isNullOrEmpty()) p.put("summary", subject)
        // Plain text: Email escapes it and keeps its line breaks.
        if (!text.isNullOrEmpty()) p.put("text", text).put("isHtml", false)
        if (recipients.length() > 0) p.put("recipients", recipients)
        return p
    }

    /**
     * A mailto: link to open, as Email takes one from the Web app. A [subject] or [text] sent
     * beside it goes into the link where the link has none of its own.
     */
    fun mailto(uri: String, subject: String? = null, text: String? = null): JSONObject? {
        if (!uri.startsWith("mailto:", ignoreCase = true)) return null
        var target = uri
        fun add(name: String, value: String?) {
            if (value.isNullOrEmpty() || Regex("[?&]$name=", RegexOption.IGNORE_CASE).containsMatchIn(target)) return
            target += (if ('?' in target) "&" else "?") + name + "=" + java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        }
        add("subject", subject)
        add("body", text)
        return JSONObject().put("target", target)
    }
}

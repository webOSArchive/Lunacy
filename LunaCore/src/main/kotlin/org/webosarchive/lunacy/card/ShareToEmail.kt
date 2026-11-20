package org.webosarchive.lunacy.card

import org.json.JSONArray
import org.json.JSONObject

/**
 * What another system's "send this by email" becomes for webOS's Email: the launch parameters
 * Palm documented for apps that wanted a message composed (`summary`, `text`, `recipients`, `attachments`;
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

    /** A file to attach: its webOS path, the name it goes by, and its type if known. */
    class Attachment(val path: String, val name: String, val mimeType: String?)

    /** Where copies of files shared from outside are kept, in /media/internal: hidden, so no index lists them. */
    const val SHARED_DIR = ".lunacy-shared"

    /** How long a copy is kept: a message can wait in the outbox, which reads the file when it sends. */
    const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * A message to compose: [subject], [text] as plain text, whoever it is for, and the files
     * to attach. Null when there is nothing to put in it, which Email would take for an
     * ordinary launch.
     */
    fun compose(subject: String?, text: String?, to: List<String> = emptyList(), cc: List<String> = emptyList(),
                bcc: List<String> = emptyList(), attachments: List<Attachment> = emptyList()): JSONObject? {
        val recipients = JSONArray()
        for ((role, list) in listOf(TO to to, CC to cc, BCC to bcc)) for (a in list) {
            val addr = a.trim()
            if (addr.isNotEmpty()) recipients.put(JSONObject().put("value", addr).put("contactDisplay", addr).put("role", role))
        }
        if (subject.isNullOrEmpty() && text.isNullOrEmpty() && recipients.length() == 0 && attachments.isEmpty()) return null
        val p = JSONObject()
        if (!subject.isNullOrEmpty()) p.put("summary", subject)
        // Plain text: Email escapes it and keeps its line breaks.
        if (!text.isNullOrEmpty()) p.put("text", text).put("isHtml", false)
        if (recipients.length() > 0) p.put("recipients", recipients)
        if (attachments.isNotEmpty()) p.put("attachments", JSONArray().also { list ->
            for (a in attachments) list.put(JSONObject().put("fullPath", a.path).put("displayName", a.name).apply { if (a.mimeType != null) put("mimeType", a.mimeType) })
        })
        return p
    }

    /**
     * The name a shared file is kept under: [name] without anything that would make it a path
     * or a hidden file, or "attachment" when nothing is left.
     */
    fun safeName(name: String?): String {
        val n = name.orEmpty().substringAfterLast('/').substringAfterLast('\\').filter { it >= ' ' && it != ':' }.trim().trimStart('.')
        return (if (n.isEmpty()) "attachment" else n).take(120)
    }

    /**
     * A new, empty folder in [media] (Lunacy's own /media/internal) for one share's files, so
     * each keeps its own name; copies older than [KEEP_MS] go first.
     */
    fun newSharedFolder(media: java.io.File, now: Long = System.currentTimeMillis()): java.io.File {
        val dir = java.io.File(media, SHARED_DIR)
        dir.listFiles()?.forEach { if (now - it.lastModified() > KEEP_MS) it.deleteRecursively() }
        var n = now
        while (true) { val f = java.io.File(dir, n.toString(36)); if (f.mkdirs()) return f; n++ }
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

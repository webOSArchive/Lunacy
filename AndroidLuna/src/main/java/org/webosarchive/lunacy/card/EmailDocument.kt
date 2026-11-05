package org.webosarchive.lunacy.card

/**
 * The text work behind showing an email in a browser view (BrowserViews): the fonts it names
 * as the TouchPad drew them, and the escaping, unescaping and linking a plain-text message
 * and the email object's attributes need. Pure functions.
 */
object EmailDocument {
    /** Fonts the reference TouchPad had besides Prelude (/usr/share/fonts), by the names pages use. */
    private val DEVICE_FONTS = setOf("arial", "verdana", "georgia", "times new roman", "times", "courier new", "courier",
        "lucida console", "monospace")

    /**
     * The fonts an email names, as the TouchPad's QtWebKit drew them: only the first family
     * of a list counts, and a name the device hasn't got - or a generic one, or a system-ui
     * that WebKit 534 didn't know - draws Prelude (tools/gen-fonts-css.py, rule 3). Fastmail's
     * "'Proxima Nova', system-ui, ..., Arial, sans-serif" is Prelude on the reference
     * TouchPad, and was Android's Roboto here. Fonts the device had (Arial, Verdana,
     * Georgia, Times, Courier, Lucida Console) keep their names, drawn by Android's own
     * stand-ins for them. Prelude itself comes from fonts.css, which the document links.
     */
    fun touchpadFonts(doc: String): String {
        fun resolve(list: String): String {
            val important = Regex("\\s*!important\\s*$", RegexOption.IGNORE_CASE).find(list)?.value.orEmpty()
            val first = list.removeSuffix(important).split(',').firstOrNull()?.trim()?.trim('\'', '"').orEmpty()
            val keep = first.lowercase().let { f -> f.startsWith("prelude") || f in DEVICE_FONTS }
            return (if (keep) "'$first'" else "Prelude") + important
        }
        var out = Regex("(font-family\\s*:\\s*)([^;}\"<>]+)", RegexOption.IGNORE_CASE).replace(doc) { m ->
            m.groupValues[1] + resolve(m.groupValues[2])
        }
        // The shorthand: the families follow the size (and line height).
        out = Regex("(font\\s*:\\s*[^;}\"<>]*?\\d[\\w.%]*(?:\\s*/\\s*[\\d.]+[\\w%]*)?\\s+)([^;}\"<>]+)", RegexOption.IGNORE_CASE).replace(out) { m ->
            m.groupValues[1] + resolve(m.groupValues[2])
        }
        return out
    }

    fun insertAfter(doc: String, tag: String, what: String): String? =
        Regex(tag, RegexOption.IGNORE_CASE).find(doc)?.let { m -> doc.substring(0, m.range.last + 1) + what + doc.substring(m.range.last + 1) }

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value }.replace("&amp;", "&")
    fun linkify(s: String) = s.replace(Regex("\\b(https?://[^\\s<>\"]+)")) { "<a href=\"${it.value}\">${it.value}</a>" }
}

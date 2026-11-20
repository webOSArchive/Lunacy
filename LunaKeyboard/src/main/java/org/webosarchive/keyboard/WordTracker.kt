package org.webosarchive.keyboard

/**
 * What the keyboard knows of the field around the cursor: where the cursor is, and the text
 * just before it. The word Text Assist may correct is read off that text - the letters that
 * stand before the cursor - and never off the keys that were pressed.
 *
 * The keys were the first design, and it went wrong whenever the two parted (codepoet,
 * 2026-11-20): a first letter typed, erased and typed over left the keyboard correcting the
 * rest of the word on its own; so did a word gone back into with backspace, a selection typed
 * over, and a word taken up again after a tap. And the cursor was taken from the field's own
 * reports, which come back after the keys that caused them: a letter typed before the report
 * of the edit before it was placed one off, and the next report then looked like the cursor
 * being moved by someone else, which dropped the word so far.
 *
 * So the keyboard keeps its own account. Every edit it makes moves [selStart] and [selEnd]
 * and the text before the cursor here at once, and leaves the position it expects the field
 * to report; a report that matches one is the keyboard's own and changes nothing. One that
 * matches none is the cursor moved from outside (a tap, the app): the account of the text is
 * dropped, and read again from the field at the next key ([needsContext], [context]).
 *
 * Plain Kotlin with no Android in it, so the tests run it directly.
 */
class WordTracker(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {

    /** The selection as the keyboard's own edits leave it; -1 while the field hasn't said. */
    var selStart = -1; private set
    var selEnd = -1; private set

    /** The text just before the cursor (before the selection, when there is one); null when unread. */
    private var before: StringBuilder? = null
    /** Whether [before] starts where the field's text starts. */
    private var whole = false
    /** Whether a letter or digit stands straight after the cursor: the cursor is inside a word. */
    private var wordFollows = false

    /**
     * Whether the word before the cursor is one the owner is writing: a letter of it was typed
     * or erased since the cursor came to it. A word the cursor was only put after is left be.
     */
    var touched = false; private set

    /** Where the keyboard's own edits have put the cursor lately, with when: what the field will report, perhaps more than once. */
    private val flights = ArrayList<LongArray>()

    /** A field starts: its selection, as the field gave it. */
    fun start(start: Int, end: Int) {
        selStart = start; selEnd = end
        flights.clear()
        forget()
    }

    /** The text around the cursor is no longer known. */
    fun forget() { before = null; touched = false }

    /** Where the cursor is, counted from 0 while the field hasn't said. */
    val cursor: Int get() = if (selStart >= 0) Math.min(selStart, selEnd) else 0

    /** Whether the text before the cursor must be read from the field before the next key is acted on. */
    val needsContext: Boolean get() = before.let { it == null || (!whole && it.length < KEEP) }

    /** The text before the cursor as the field has it, [asked] characters at most, and what follows the cursor. */
    fun context(text: CharSequence, asked: Int, after: CharSequence) {
        before = StringBuilder(text)
        whole = text.length < asked
        wordFollows = after.isNotEmpty() && after[0].isLetterOrDigit()
    }

    /** The text before the cursor, if it is known far enough back to judge a sentence's start by. */
    val textBefore: CharSequence? get() = before?.takeIf { whole || it.length >= KEEP }

    /**
     * The word that stands before the cursor: letters and digits, and apostrophes inside it.
     * Empty when there is none, or the text is unread.
     */
    val word: String get() {
        val t = before ?: return ""
        var i = t.length
        while (i > 0 && inWord(t[i - 1])) i--
        while (i < t.length && !t[i].isLetterOrDigit()) i++
        // A word that runs back past what was read is not known whole.
        if (i == 0 && !whole) return ""
        return t.substring(i)
    }

    /**
     * Whether [word] is to be left as typed whatever it is: the cursor is inside a longer word,
     * or the word is a piece of an address or a name written without spaces ("you@example.com",
     * "example.com", "/usr/lib", "#lunacy").
     */
    val plain: Boolean get() {
        val t = before ?: return true
        val w = word
        if (w.isEmpty() || wordFollows) return true
        var i = t.length - w.length - 1
        // Past the apostrophes that open it.
        while (i >= 0 && (t[i] == '\'' || t[i] == '’')) i--
        return i >= 0 && t[i] in JOINERS
    }

    // --- The keyboard's own edits -----------------------------------------------------------

    private fun arrive(pos: Int) {
        val p = Math.max(0, pos)
        selStart = p; selEnd = p
        flights += longArrayOf(p.toLong(), now())
    }

    private fun append(text: String) {
        val t = before ?: return
        t.append(text)
        if (t.length > KEEP_MOST) { t.delete(0, t.length - ASK); whole = false }
    }

    /** [text] was typed: it replaces the selection, and the cursor stands after it. */
    fun inserted(text: String) {
        if (text.isEmpty()) return
        append(text)
        touched = inWord(text[text.length - 1]) && word.isNotEmpty()
        arrive(cursor + text.length)
    }

    /**
     * Backspace, as the Delete key does it: the selection goes, or the character before the
     * cursor (both halves of a surrogate pair). Returns how many characters that is.
     */
    fun deleted(): Int {
        if (selStart != selEnd) { touched = false; arrive(cursor); return 0 }
        val t = before
        var n = 1
        if (t != null && t.isNotEmpty()) {
            if (t.length >= 2 && Character.isLowSurrogate(t[t.length - 1]) && Character.isHighSurrogate(t[t.length - 2])) n = 2
            t.setLength(t.length - n)
            touched = word.isNotEmpty()
        } else {
            if (t != null && whole) n = 0       // the start of the field: nothing goes
            touched = false
        }
        if (n > 0 && selStart >= 0) arrive(selStart - n)
        return n
    }

    /** The [n] characters before the cursor were removed. */
    fun removedBefore(n: Int) = replacedBefore(n, "")

    /** The [n] characters before the cursor became [text], and the cursor stands after it. */
    fun replacedBefore(n: Int, text: String) {
        val at = cursor
        val t = before
        if (t != null) { if (t.length >= n) { t.setLength(t.length - n); append(text) } else before = null }
        touched = false
        arrive(at - n + text.length)
    }

    /** The text that began at [start] became [text], and the cursor stands after it. */
    fun replacedAt(start: Int, text: String) {
        forget()
        arrive(start + text.length)
    }

    /** A key the field acts on by itself went out (an arrow, Return, Tab): where the cursor lands is the field's to say. */
    fun adrift() = forget()

    /**
     * The field reports its selection. False when it is one of the keyboard's own edits coming
     * back, or nothing new; true when the cursor was moved from outside, and the account starts
     * again from here.
     *
     * The keyboard's own is any place its edits put the cursor a moment ago, not only one still
     * to be reported: a field reports the same place twice (Chromium says it again when a
     * correction's composing text is committed), folds several reports into one, and a place
     * crossed off at its first report made the second look like a tap, and every report behind
     * it too (measured on the Galaxy Tab A7 Lite, 2026-11-20: corrections typed quickly were
     * skipped). The cost is a tap that lands, within [FLIGHT_MS] of typing, exactly where the
     * cursor has just been: the text is then read wrong until the next report, and a
     * correction made on it is refused by the check that the word is really there.
     */
    fun update(start: Int, end: Int): Boolean {
        val t = now()
        flights.removeAll { t - it[1] > FLIGHT_MS }
        if (start == end && flights.any { it[0] == start.toLong() }) return false
        if (start == selStart && end == selEnd) return false
        selStart = start; selEnd = end
        forget()
        return true
    }

    companion object {
        /** How much text before the cursor is asked for. */
        const val ASK = 64
        /** With less than this left, and more in the field, it is read again: a word and the sentence end before it. */
        const val KEEP = 28
        private const val KEEP_MOST = 160
        /** A report this long after its edit is no longer taken for the keyboard's own. */
        private const val FLIGHT_MS = 700L
        /** What joins the pieces of an address, a path or a tag: a word straight after one is part of it. */
        private const val JOINERS = "@./\\_#"

        fun inWord(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'
    }
}

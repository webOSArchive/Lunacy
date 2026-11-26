package org.webosarchive.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.RectF
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout

/**
 * The Android side of the keyboard: it owns the view and turns what the view reports into
 * edits on whatever field has focus. How the keyboard looks and behaves lives in
 * [KeyboardView]; this class speaks to Android, and does webOS's Text Assist ([TextAssist]):
 *
 * - the word being typed goes in as plain text, as webOS's did; the keyboard keeps its own
 *   account of the cursor and the text before it ([WordTracker]), and the word is whatever
 *   letters stand before the cursor;
 * - a space or punctuation ends the word, and Auto-Correction swaps it for the word it most
 *   likely meant ([Corrector], webOS's SmartKey `auto-accept`) or a shortcut's expansion, with the plop,
 *   and the swapped word is underlined in gray dots ([AssistOverlay]). Only the swap is
 *   composing text, for a moment, because that is what Android says the position of;
 * - backspace straight after a correction puts the word back as it was typed; moving the
 *   cursor into a corrected word opens the bubble of its original and alternates;
 * - Auto-Capitalization follows the field's own caps mode, and two spaces after a word make a
 *   period and a space.
 */
class KeyboardService : InputMethodService(), KeyboardView.Host {

    private var view: KeyboardView? = null
    private var overlay: AssistOverlay? = null
    private var root: View? = null
    private var editor: EditorInfo? = null
    private val main = Handler(Looper.getMainLooper())

    /** The word list and its matcher, read in the background: null until it is ready. */
    @Volatile private var corrector: Corrector? = null
    @Volatile private var personal: Set<String> = emptySet()
    private lateinit var shortcuts: Shortcuts
    private lateinit var notifier: TextAssist.Notifier
    private lateinit var feedback: KeyFeedback

    override fun onCreate() {
        super.onCreate()
        Thread {
            // The word list, then Lunacy's own additions to it (assist/words.txt).
            val c = runCatching {
                assets.open("assist/dictionary.txt").bufferedReader().use { list ->
                    assets.open("assist/words.txt").bufferedReader().use { more -> Corrector(list.lineSequence() + more.lineSequence()) }
                }
            }.getOrNull()
            c?.learnAll(personal)
            corrector = c
        }.start()
        shortcuts = Shortcuts(this) { words -> personal = words; corrector?.learnAll(words) }
        notifier = TextAssist.Notifier(this)
        feedback = KeyFeedback(this)
        for (name in LIVE_PREFS) getSharedPreferences(name, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(settingsChanged)
    }

    /**
     * The setup screen's changes, shown at once, even with the keyboard up over its try-it
     * field: a new size rebuilds the keyboard, and Auto-Capitalization relights shift. The rest
     * (sounds, the buzz, Text Assist's switches) are read as they are used. Kept in a field:
     * SharedPreferences holds its listeners weakly.
     */
    private val settingsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        main.post {
            when (key) {
                KeyboardView.Scale.KEY -> if (view != null && view?.scale != KeyboardView.Scale.current(this)) {
                    setInputView(onCreateInputView())
                    view?.reset()
                    updateCaps()
                }
                "autoCapitalization" -> updateCaps()
            }
        }
    }

    override fun onDestroy() {
        for (name in LIVE_PREFS) getSharedPreferences(name, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(settingsChanged)
        shortcuts.close(); notifier.release(); feedback.release()
        super.onDestroy()
    }

    /**
     * The input view is the whole height Android allows: the keyboard at the bottom, and above
     * it the transparent overlay where corrections are drawn over the app's text. Only the
     * keyboard and an open bubble take touches ([onComputeInsets]).
     */
    override fun onCreateInputView(): View {
        val kb = KeyboardView(this, this)
        val ov = AssistOverlay(this, Art(this), kb.scale) { m, pick -> picked(m, pick) }
        val frame = object : FrameLayout(this) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                val h = MeasureSpec.getSize(heightSpec).takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            }
        }
        frame.addView(ov, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(kb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        // Built for Android 15, the keyboard's window reaches under the navigation bar, whose
        // buttons (back, the keyboard switcher) the system draws over the bottom row. So the
        // keyboard stands on the bar, and the strip under it is the background's own bottom
        // colour, as the system painted it before.
        val strip = View(this).apply { setBackgroundColor(android.graphics.Color.rgb(84, 84, 84)) }
        frame.addView(strip, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM))
        if (Build.VERSION.SDK_INT >= 30) frame.setOnApplyWindowInsetsListener { _, insets ->
            val bar = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            (kb.layoutParams as FrameLayout.LayoutParams).bottomMargin = bar
            strip.layoutParams.height = bar
            kb.requestLayout(); strip.requestLayout()
            insets
        }
        view = kb; overlay = ov; root = frame
        return frame
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editor = info
        // A size chosen while the keyboard wasn't up (or its process wasn't running).
        if (view?.scale != KeyboardView.Scale.current(this)) setInputView(onCreateInputView())
        view?.reset()
        resetAssist()
        // The new field's cursor, as it starts. Kept from the last field, it made the first
        // letter of the first word look typed somewhere else: the field's next selection update
        // dropped it from the word, and the rest was corrected on its own, "esting" to "eating"
        // after the "T" (codepoet, 2026-11-14: "Testing" became "Teating", "Keyboard"
        // "Kkeyboard"). Chromium sends no update for where a field starts.
        tracker.start(info?.initialSelStart ?: -1, info?.initialSelEnd ?: -1)
        requestPositions()
        updateCaps()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        flushPending()
        resetAssist()
        super.onFinishInputView(finishingInput)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * The input view is taller than the keyboard - the room above it is the balloon's and the
     * overlay's, and transparent. So the insets say where the keyboard really starts, and only
     * the keyboard, and the correction bubble when it is open, take touches. The balloon still
     * gets the finger that opened it, because a gesture stays with the window that caught it.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val v = view ?: return
        if (v.width == 0 || v.height == 0) return
        val at = IntArray(2)
        v.getLocationInWindow(at)
        val top = at[1] + v.keyboardTop()
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, v.width, at[1] + v.height)
        val ov = overlay
        val b = ov?.bubbleFrame
        if (ov != null && b != null) {
            val o = IntArray(2); ov.getLocationInWindow(o)
            outInsets.touchableRegion.op(b.left + o[0], b.top + o[1], b.right + o[0], b.bottom + o[1], android.graphics.Region.Op.UNION)
        }
    }

    // --- Text Assist ------------------------------------------------------------------------

    /** The cursor, and the text and word before it, by the keyboard's own account. */
    private val tracker = WordTracker { SystemClock.uptimeMillis() }
    /** Words put back by the owner in this field: not corrected again. */
    private val ignored = HashSet<String>()
    /** The last correction and what ended it, while backspace can still undo it. */
    private var undo: Pair<AssistOverlay.Marked, String>? = null
    private var lastOwnEdit = 0L
    private var lastSpaceAt = 0L

    private var anchor: CursorAnchorInfo? = null
    private var anchorSeen = false
    /** A correction waiting one frame for Android to say where it is on screen. */
    private var pending: Triple<AssistOverlay.Marked, String, Runnable>? = null
    /** Each mark's box in the field's own coordinates, kept to follow the field around. */
    private val local = HashMap<AssistOverlay.Marked, RectF>()

    private fun resetAssist() {
        tracker.forget(); ignored.clear(); undo = null; pending = null
        main.removeCallbacks(capsLater)
        anchor = null; anchorSeen = false; local.clear()
        overlay?.clear()
    }

    /** Whether this field is one webOS corrected: ordinary text, and not one that asks not to be. */
    private fun assisted(): Boolean {
        val t = editor?.inputType ?: return false
        if (t and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (t and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        // A web page's field says "don't correct" by leaving AUTO_CORRECT out: Chromium sets it
        // on every text field except one marked autocorrect="off" (Enyo's autocorrect: false,
        // and Mojo's autoReplace: false through Lunacy's compat layer). Measured on the Kyocera:
        // 0xc0a1 for a plain field, 0x40a1 with autocorrect="off"; NO_SUGGESTIONS is never set.
        // A native Android field keeps being corrected without the flag, as other keyboards do.
        if (t and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT &&
            t and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT == 0) return false
        return when (t and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER, InputType.TYPE_TEXT_VARIATION_PHONETIC -> false
            else -> true
        }
    }

    private fun tracking() = assisted() && TextAssist.autoCorrect(this)

    /**
     * A character that is part of a word: a letter, a digit ("mp3", "3rd": a word with one in
     * it is never corrected, so a digit must not end the word before it), or an apostrophe
     * inside one.
     */
    private fun isWordChar(s: String) = s.length == 1 && (s[0].isLetterOrDigit() || (tracker.word.isNotEmpty() && (s[0] == '\'' || s[0] == '’')))

    /** Reads the text around the cursor when the keyboard's account of it has lapsed. */
    private fun readContext(ic: InputConnection) {
        if (!tracker.needsContext) return
        val before = ic.getTextBeforeCursor(WordTracker.ASK, 0) ?: return
        tracker.context(before, WordTracker.ASK, ic.getTextAfterCursor(1, 0) ?: "")
    }

    private fun own() { lastOwnEdit = SystemClock.uptimeMillis() }

    /**
     * Whether the next letter starts a sentence, by the field's own caps mode. Judged on the
     * keyboard's account of the text where it has one: the field's answer runs behind a Delete
     * key still on its way, so a capital typed and erased came back in lower case.
     */
    private fun updateCaps() {
        main.removeCallbacks(capsLater)
        val ic = currentInputConnection
        val type = editor?.inputType ?: 0
        val on = TextAssist.autoCap(this) && tracker.word.isEmpty() && ic != null &&
            (tracker.textBefore?.let { android.text.TextUtils.getCapsMode(it, it.length, type) } ?: ic.getCursorCapsMode(type)) != 0
        view?.setAutoCap(on)
    }

    private val capsLater = Runnable { updateCaps() }

    private fun correctionFor(w: String): String? {
        if (!TextAssist.autoCorrect(this) || w in ignored || w.any { it.isDigit() }) return null
        return corrector?.correct(w)
    }

    /** Ends the word before the cursor with [sep], correcting it first if Text Assist would. */
    private fun endWord(sep: String) {
        val ic = currentInputConnection ?: return
        val typed = tracker.word
        // A word the cursor was only put after, a piece of an address, a word with more of it
        // after the cursor: left as it is.
        val assist = typed.isNotEmpty() && tracker.touched && !tracker.plain && sep !in JOINS
        if (!assist) {
            if (sep.isNotEmpty()) { own(); ic.commitText(sep, 1); tracker.inserted(sep) }
            undo = null
            return
        }
        val start = tracker.cursor - typed.length
        val short = if (typed !in ignored) shortcuts.expand(typed) else null
        val fix = short ?: correctionFor(typed)
        own()
        // The field must still hold the word just before the cursor, as typed.
        val there = ic.getTextBeforeCursor(typed.length, 0)?.toString() == typed
        if (fix == null || fix == typed || !there) {
            if (!there) tracker.forget()
            if (sep.isNotEmpty()) { ic.commitText(sep, 1); tracker.inserted(sep) }
            undo = null
            return
        }
        val alternates = (corrector?.alternates(typed, ALTERNATES) ?: emptyList()).filter { it != fix }
        val mark = AssistOverlay.Marked(start, fix, typed, alternates)
        // The replacement goes in as composing text first, so that Android says where it is.
        // The typed word is taken out by its place before the cursor, which the field has just
        // confirmed, never by a position: the keyboard's count of where the cursor is can run a
        // report behind, and three corrections typed quickly ("i teh adn") then replaced the
        // wrong letters (measured on the Galaxy Tab A7 Lite at 30 ms a key, 2026-11-20).
        ic.beginBatchEdit()
        ic.deleteSurroundingText(typed.length, 0)
        ic.setComposingText(fix, 1)
        ic.endBatchEdit()
        tracker.replacedBefore(typed.length, fix)
        if (Build.VERSION.SDK_INT >= 21 && anchorSeen) {
            val timeout = Runnable { completePending() }
            pending = Triple(mark, sep, timeout)
            main.postDelayed(timeout, ANCHOR_WAIT_MS)
        } else {
            pending = Triple(mark, sep, Runnable {})
            completePending()
        }
    }

    private fun completePending(bounds: RectF? = null) {
        val (mark, sep, timeout) = pending ?: return
        pending = null
        main.removeCallbacks(timeout)
        val ic = currentInputConnection ?: return
        own()
        ic.finishComposingText()
        if (sep.isNotEmpty()) { ic.commitText(sep, 1); tracker.inserted(sep) }
        if (bounds != null) { local[mark] = bounds; mark.screen = toScreen(bounds) }
        overlay?.marks?.add(mark)
        overlay?.invalidate()
        undo = mark to sep
        notifier.corrected()
    }

    private fun flushPending() { if (pending != null) completePending() }

    /** Asks the field to say where its text is on screen, and to go on saying so. */
    private fun requestPositions() {
        if (Build.VERSION.SDK_INT >= 21) currentInputConnection?.requestCursorUpdates(
            InputConnection.CURSOR_UPDATE_MONITOR or InputConnection.CURSOR_UPDATE_IMMEDIATE)
    }

    private fun toScreen(r: RectF): RectF? {
        val m = anchor?.matrix ?: return null
        val out = RectF(r); m.mapRect(out); return out
    }

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        anchor = info; anchorSeen = true
        val p = pending
        if (p != null && info.composingText?.toString() == p.first.text) {
            var b: RectF? = null
            for (i in 0 until p.first.text.length) {
                val r = info.getCharacterBounds(info.composingTextStart + i) ?: continue
                if (b == null) b = RectF(r) else b.union(r)
            }
            // The word's baseline. A native field reports it; Chrome and the WebView report the
            // line's bottom as its "baseline" (measured on the Fold3: 7 px below the letters), and
            // then it is where fonts put it, a fifth of the line up from the bottom.
            if (b != null) {
                val base = info.insertionMarkerBaseline
                val reported = !base.isNaN() && base < info.insertionMarkerBottom - 0.5f
                val line = b.height()
                b.bottom = if (reported) base else b.bottom - line * DESCENT_SHARE
                // How big its dots are: a web page's CSS pixels are TouchPad pixels in Lunacy,
                // so the field's own scale to the screen is the dots' too; a native field's text
                // is measured by its line against the TouchPad's (about 22 px for its 18 px text).
                val v = FloatArray(9); info.matrix.getValues(v)
                val m = Math.abs(v[android.graphics.Matrix.MSCALE_Y])
                p.first.dotScale = if (m > 1.01f) m else Math.max(1f, line / TOUCHPAD_LINE)
            }
            completePending(b)
            return
        }
        // The field moved or scrolled: the marks follow it.
        var moved = false
        for ((m, r) in local) { val s = toScreen(r); if (s != m.screen) { m.screen = s; moved = true } }
        if (moved) overlay?.invalidate()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
                                   candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // One of the keyboard's own edits coming back changes nothing; anything else is the
        // cursor moved from outside, and the text around it is read again at the next key.
        tracker.update(newSelStart, newSelEnd)
        // Asked for while the field was starting, the positions often never come (measured on
        // the Kyocera's Android 12: the request was accepted and nothing followed), so the
        // first selection update, when the field is surely there, asks again.
        if (!anchorSeen) requestPositions()
        val ov = overlay ?: return
        // A tap that puts the cursor in a corrected word opens its bubble; anything else closes it.
        val ours = SystemClock.uptimeMillis() - lastOwnEdit < OWN_EDIT_MS
        if (!ours && newSelStart == newSelEnd) checkMarks(newSelStart)
        val m = if (!ours && newSelStart == newSelEnd && !tracker.touched)
            ov.marks.lastOrNull { newSelStart >= it.start && newSelStart <= it.end } else null
        if (m != null && stillThere(m, newSelStart)) { undo = null; ov.showBubble(m) }
        else if (ov.bubbleShowing && !ours) ov.hideBubble()
        if (!ours) undo = null
        updateCaps()
    }

    /** Whether the field still holds [m]'s word where it was; a mark that isn't is dropped. */
    private fun stillThere(m: AssistOverlay.Marked, cursor: Int): Boolean {
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(cursor - m.start, 0)?.toString()
        val after = ic.getTextAfterCursor(m.end - cursor, 0)?.toString()
        val ok = before != null && after != null && before + after == m.text
        if (!ok) { overlay?.marks?.remove(m); local.remove(m); overlay?.invalidate() }
        return ok
    }

    /**
     * Drops every mark whose word is no longer where it was. The field's text can change
     * without the keyboard - the app clearing or setting it, a send emptying it - and a mark
     * was only checked when the cursor came into it, so its dots stayed under whatever took
     * the word's place (codepoet, 2026-11-14: "Teating"'s dots under Email's "Tap Here To
     * Type" once the subject was emptied). A mark too far from the cursor to read cheaply stays.
     */
    private fun checkMarks(cursor: Int) {
        val ov = overlay ?: return
        if (ov.marks.isEmpty() || cursor < 0) return
        val ic = currentInputConnection ?: return
        var dropped = false
        for (m in ov.marks.toList()) {
            val here: String? = when {
                m.end <= cursor -> if (cursor - m.start > MARK_REACH) continue
                    else ic.getTextBeforeCursor(cursor - m.start, 0)?.toString()?.let { if (it.length == cursor - m.start) it.take(m.text.length) else null }
                m.start >= cursor -> if (m.end - cursor > MARK_REACH) continue
                    else ic.getTextAfterCursor(m.end - cursor, 0)?.toString()?.let { if (it.length == m.end - cursor) it.drop(m.start - cursor) else null }
                else -> (ic.getTextBeforeCursor(cursor - m.start, 0)?.toString() ?: "") + (ic.getTextAfterCursor(m.end - cursor, 0)?.toString() ?: "")
            }
            if (here != m.text) { ov.marks.remove(m); local.remove(m); dropped = true }
        }
        if (dropped) ov.invalidate()
    }

    /** A word from the bubble replaces the corrected one; its original comes back unmarked. */
    private fun picked(m: AssistOverlay.Marked, pick: String) {
        val ic = currentInputConnection ?: return
        own()
        ic.beginBatchEdit()
        ic.setComposingRegion(m.start, m.end)
        ic.commitText(pick, 1)
        ic.endBatchEdit()
        tracker.replacedAt(m.start, pick)
        overlay?.marks?.remove(m)
        val was = local.remove(m)
        if (pick == m.original) ignored += pick
        // The words after it on its line move by the difference: their offsets by its length,
        // their dots by its width, estimated from the old word's.
        val delta = pick.length - m.text.length
        if (delta != 0) {
            val dx = was?.let { it.width() * delta / Math.max(1, m.text.length) } ?: 0f
            for (o in overlay?.marks ?: emptyList<AssistOverlay.Marked>()) {
                if (o.start < m.end) continue
                o.start += delta
                val r = local[o] ?: continue
                if (was != null && Math.abs(r.bottom - was.bottom) < 1f) { r.offset(dx, 0f); o.screen = toScreen(r) }
                else { local.remove(o); o.screen = null }
            }
        }
        overlay?.invalidate()
    }

    /** Marks at or past the cursor can't be trusted once the text before them changes. */
    private fun dropMarksFrom(pos: Int) {
        val ov = overlay ?: return
        if (ov.marks.removeAll { it.end > pos }) { local.keys.retainAll(ov.marks.toSet()); ov.invalidate() }
    }

    // --- What the keyboard reports ----------------------------------------------------------

    /**
     * webOS CE's emoji key (TabletKeyboard::handleKey): whatever was being typed is left as it
     * is - the shortcuts and corrections forget it rather than act on it - and the emoji goes
     * in whole, surrogate pairs and variation selector together.
     */
    override fun onEmoji(text: String) {
        val ic = currentInputConnection ?: return
        flushPending()
        overlay?.hideBubble()
        dropMarksFrom(tracker.cursor)
        own()
        ic.commitText(text, 1)
        tracker.inserted(text)
        undo = null
        updateCaps()
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        flushPending()
        overlay?.hideBubble()
        val track = tracking()
        if (track) readContext(ic)
        if (!tracker.touched) dropMarksFrom(tracker.cursor)
        if (track && isWordChar(text)) {
            undo = null
            own()
            ic.commitText(text, 1)
            tracker.inserted(text)
            view?.setAutoCap(false)
            return
        }
        // webOS's Double Space For Period: a second space straight after a word's makes ". ".
        if (text == " " && assisted() && TextAssist.doubleSpace(this) && tracker.word.isEmpty()) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit() &&
                SystemClock.uptimeMillis() - lastSpaceAt < DOUBLE_SPACE_MS) {
                own()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                tracker.replacedBefore(1, ". ")
                lastSpaceAt = 0; undo = null
                updateCaps()
                return
            }
        }
        if (track) endWord(text) else { own(); ic.commitText(text, 1); tracker.inserted(text); undo = null }
        if (text == " ") lastSpaceAt = SystemClock.uptimeMillis()
        updateCaps()
    }

    override fun onBackspace(word: Boolean) {
        val ic = currentInputConnection ?: return
        flushPending()
        overlay?.hideBubble()
        // Straight after a correction, backspace puts the word back as it was typed.
        val u = undo
        if (u != null && !tracker.touched) {
            val (m, sep) = u
            val before = ic.getTextBeforeCursor(m.text.length + sep.length, 0)?.toString()
            undo = null
            if (before == m.text + sep) {
                own()
                ic.deleteSurroundingText(before.length, 0)
                ic.commitText(m.original + sep, 1)
                tracker.replacedBefore(before.length, m.original + sep)
                ignored += m.original
                lastSpaceAt = 0     // the space that comes back wasn't typed just now
                overlay?.marks?.remove(m); local.remove(m); overlay?.invalidate()
                updateCaps()
                return
            }
        }
        undo = null
        if (tracker.cursor > 0) dropMarksFrom(tracker.cursor - 1)
        if (!word) {
            // The Delete key, as a webOS keyboard sent it: pages act on the key itself (Email's
            // recipients go one at a time on a backspace in an empty field). The keyboard's
            // account of the text moves with it, so the word before the cursor is right at the
            // next key whether or not the field has caught up.
            own()
            val known = tracker.textBefore != null
            tracker.deleted()
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            // Where the text isn't known the field is asked, once the key has reached it.
            if (known) updateCaps() else main.postDelayed(capsLater, CAPS_AFTER_DELETE_MS)
            return
        }
        // Held down long enough and webOS started taking whole words.
        val before = ic.getTextBeforeCursor(64, 0) ?: return
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        own()
        ic.deleteSurroundingText(before.length - i, 0)
        tracker.removedBefore(before.length - i)
        updateCaps()
    }

    override fun onEnter() {
        endWord("")
        flushPending()
        val action = editor?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val unspecified = action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        val multiline = (editor?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (unspecified || multiline) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else currentInputConnection?.performEditorAction(action)
        tracker.adrift()
        undo = null
        updateCaps()
    }

    override fun onTab() {
        endWord("")
        flushPending()
        sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
        tracker.adrift()
    }

    override fun onHide() {
        endWord("")
        flushPending()
        requestHideSelf(0)
    }

    override fun onPress(sound: KeyFeedback.Sound) = feedback.press(sound)

    override fun onCursor(dx: Int, dy: Int, select: Boolean) {
        val ic = currentInputConnection ?: return
        flushPending()
        tracker.adrift()
        val code = when {
            dx > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
            dx < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
            dy > 0 -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> KeyEvent.KEYCODE_DPAD_UP
        }
        // Holding shift while the ball moves selects, the way it did on webOS.
        val meta = if (select) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    /**
     * What the field wants its Return key to say. webOS's key said "Enter" (LunaCE's
     * `m_localized__Enter`) unless the field gave it a label, and a single-line field's was
     * "Done" (codepoet, 2026-11-04). A label or action the field asks for comes first.
     */
    override fun enterLabel(): String {
        val info = editor
        info?.actionLabel?.let { if (it.isNotEmpty()) return it.toString() }
        if ((info?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return "Enter"
        return when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
            else -> "Done"
        }
    }

    private companion object {
        /** How long a correction waits for Android to say where it landed before going without. */
        const val ANCHOR_WAIT_MS = 120L
        /** A selection change this soon after the keyboard's own edit is the keyboard's. */
        const val OWN_EDIT_MS = 300L
        /** How far from the cursor a mark is read back to check it: farther, it is left be. */
        const val MARK_REACH = 4000
        /** Two spaces this close together make a period. */
        const val DOUBLE_SPACE_MS = 1500L
        /** How much of a line's box is below its baseline, where the field doesn't say. */
        const val DESCENT_SHARE = 0.2f
        /** A line of the TouchPad's Memos text (18 px Prelude), top to bottom. */
        const val TOUCHPAD_LINE = 22f
        /** Other spellings offered in the bubble beside the original. */
        const val ALTERNATES = 4
        /** How long a Delete key is given to reach the field before its caps mode is asked. */
        const val CAPS_AFTER_DELETE_MS = 80L
        /** What joins a word to more of an address or a path: the word before one is left as typed. */
        val JOINS = setOf("@", "_", "/", "\\")
        /** The settings files whose changes the keyboard shows straight away: its size's and Text Assist's. */
        val LIVE_PREFS = listOf(KeyboardView.Scale.PREFS, "textassist")
    }
}

package org.webosarchive.keyboard

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
 * - the word being typed goes in as plain text, as webOS's did, and the keyboard keeps track of
 *   it;
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

    override fun onCreate() {
        super.onCreate()
        Thread {
            val c = runCatching { assets.open("assist/dictionary.txt").bufferedReader().useLines { Corrector(it) } }.getOrNull()
            c?.learnAll(personal)
            corrector = c
        }.start()
        shortcuts = Shortcuts(this) { words -> personal = words; corrector?.learnAll(words) }
        notifier = TextAssist.Notifier(this)
    }

    override fun onDestroy() {
        shortcuts.close(); notifier.release()
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
        // A size chosen in the setup screen takes effect the next time the keyboard comes up.
        if (view?.scale != KeyboardView.Scale.current(this)) setInputView(onCreateInputView())
        view?.reset()
        resetAssist()
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

    /** The word being typed, and where in the field it ends (the cursor, as the keyboard left it). */
    private val word = StringBuilder()
    private var wordEnd = -1
    private var selStart = -1
    private var selEnd = -1
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
        word.setLength(0); wordEnd = -1; ignored.clear(); undo = null; pending = null
        anchor = null; anchorSeen = false; local.clear()
        overlay?.clear()
    }

    /** Whether this field is one webOS corrected: ordinary text, and not one that asks not to be. */
    private fun assisted(): Boolean {
        val t = editor?.inputType ?: return false
        if (t and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (t and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        return when (t and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER, InputType.TYPE_TEXT_VARIATION_PHONETIC -> false
            else -> true
        }
    }

    private fun tracking() = assisted() && TextAssist.autoCorrect(this)

    private fun isWordChar(s: String) = s.length == 1 && (s[0].isLetter() || (word.isNotEmpty() && (s[0] == '\'' || s[0] == '’')))

    private fun own() { lastOwnEdit = SystemClock.uptimeMillis() }

    private fun updateCaps() {
        val ic = currentInputConnection
        val on = TextAssist.autoCap(this) && word.isEmpty() && ic != null &&
            ic.getCursorCapsMode(editor?.inputType ?: 0) != 0
        view?.setAutoCap(on)
    }

    private fun correctionFor(w: String): String? {
        if (!TextAssist.autoCorrect(this) || w in ignored || w.any { it.isDigit() }) return null
        return corrector?.correct(w)
    }

    /** Ends the word being typed with [sep], correcting it first if Text Assist would. */
    private fun endWord(sep: String) {
        val ic = currentInputConnection ?: return
        if (word.isEmpty()) { if (sep.isNotEmpty()) { own(); ic.commitText(sep, 1) }; return }
        val typed = word.toString()
        val start = wordEnd - typed.length
        val short = if (typed !in ignored) shortcuts.expand(typed) else null
        val fix = short ?: correctionFor(typed)
        word.setLength(0); wordEnd = -1
        own()
        // The field must still hold the word just before the cursor, as typed.
        val there = ic.getTextBeforeCursor(typed.length, 0)?.toString() == typed
        if (fix == null || fix == typed || !there) {
            if (sep.isNotEmpty()) ic.commitText(sep, 1)
            undo = null
            return
        }
        val alternates = (corrector?.alternates(typed, ALTERNATES) ?: emptyList()).filter { it != fix }
        val mark = AssistOverlay.Marked(start, fix, typed, alternates)
        // The replacement goes in as composing text first, so that Android says where it is.
        if (ic.setComposingRegion(start, start + typed.length)) ic.setComposingText(fix, 1)
        else { ic.deleteSurroundingText(typed.length, 0); ic.setComposingText(fix, 1) }
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
        if (sep.isNotEmpty()) ic.commitText(sep, 1)
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
        selStart = newSelStart; selEnd = newSelEnd
        // Asked for while the field was starting, the positions often never come (measured on
        // the Kyocera's Android 12: the request was accepted and nothing followed), so the
        // first selection update, when the field is surely there, asks again.
        if (!anchorSeen) requestPositions()
        // The cursor went somewhere the keyboard didn't put it: the word is finished as it stands.
        // (An update on the way to where it did - typing outruns them - is not that.)
        if (word.isNotEmpty() && (newSelStart != newSelEnd || newSelEnd > wordEnd || newSelEnd < wordEnd - word.length)) {
            word.setLength(0); wordEnd = -1
        }
        val ov = overlay ?: return
        // A tap that puts the cursor in a corrected word opens its bubble; anything else closes it.
        val ours = SystemClock.uptimeMillis() - lastOwnEdit < OWN_EDIT_MS
        val m = if (!ours && newSelStart == newSelEnd && word.isEmpty())
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

    /** A word from the bubble replaces the corrected one; its original comes back unmarked. */
    private fun picked(m: AssistOverlay.Marked, pick: String) {
        val ic = currentInputConnection ?: return
        own()
        ic.beginBatchEdit()
        ic.setComposingRegion(m.start, m.end)
        ic.commitText(pick, 1)
        ic.endBatchEdit()
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

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        flushPending()
        overlay?.hideBubble()
        if (selEnd >= 0 && word.isEmpty()) dropMarksFrom(selEnd)
        if (tracking() && isWordChar(text)) {
            undo = null
            if (word.isEmpty()) wordEnd = if (selEnd >= 0) selEnd else 0
            word.append(text); wordEnd += text.length
            own()
            ic.commitText(text, 1)
            view?.setAutoCap(false)
            return
        }
        // webOS's Double Space For Period: a second space straight after a word's makes ". ".
        if (text == " " && assisted() && TextAssist.doubleSpace(this) && word.isEmpty()) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit() &&
                SystemClock.uptimeMillis() - lastSpaceAt < DOUBLE_SPACE_MS) {
                own()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceAt = 0; undo = null
                updateCaps()
                return
            }
        }
        if (word.isNotEmpty()) endWord(text) else { own(); ic.commitText(text, 1); undo = null }
        if (text == " ") lastSpaceAt = SystemClock.uptimeMillis()
        updateCaps()
    }

    override fun onBackspace(word: Boolean) {
        val ic = currentInputConnection ?: return
        flushPending()
        overlay?.hideBubble()
        // Straight after a correction, backspace puts the word back as it was typed.
        val u = undo
        if (u != null && this.word.isEmpty()) {
            val (m, sep) = u
            val before = ic.getTextBeforeCursor(m.text.length + sep.length, 0)?.toString()
            undo = null
            if (before == m.text + sep) {
                own()
                ic.deleteSurroundingText(before.length, 0)
                ic.commitText(m.original + sep, 1)
                ignored += m.original
                lastSpaceAt = 0     // the space that comes back wasn't typed just now
                overlay?.marks?.remove(m); local.remove(m); overlay?.invalidate()
                updateCaps()
                return
            }
        }
        undo = null
        // Inside the word being typed, it gets shorter; a whole-word delete ends it.
        if (this.word.isNotEmpty()) {
            if (word) { this.word.setLength(0); wordEnd = -1 }
            else { this.word.setLength(this.word.length - 1); wordEnd -= 1; if (this.word.isEmpty()) wordEnd = -1 }
        }
        if (selEnd > 0) dropMarksFrom(selEnd - 1)
        if (!word) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); updateCaps(); return }
        // Held down long enough and webOS started taking whole words.
        val before = ic.getTextBeforeCursor(64, 0) ?: return
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        ic.deleteSurroundingText(before.length - i, 0)
        updateCaps()
    }

    override fun onEnter() {
        if (word.isNotEmpty()) endWord("")
        flushPending()
        val action = editor?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val unspecified = action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        val multiline = (editor?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (unspecified || multiline) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else currentInputConnection?.performEditorAction(action)
        undo = null
        updateCaps()
    }

    override fun onTab() {
        if (word.isNotEmpty()) endWord("")
        flushPending()
        sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
    }

    override fun onHide() {
        if (word.isNotEmpty()) endWord("")
        flushPending()
        requestHideSelf(0)
    }

    override fun onCursor(dx: Int, dy: Int, select: Boolean) {
        val ic = currentInputConnection ?: return
        word.setLength(0); wordEnd = -1
        flushPending()
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
        /** Two spaces this close together make a period. */
        const val DOUBLE_SPACE_MS = 1500L
        /** How much of a line's box is below its baseline, where the field doesn't say. */
        const val DESCENT_SHARE = 0.2f
        /** A line of the TouchPad's Memos text (18 px Prelude), top to bottom. */
        const val TOUCHPAD_LINE = 22f
        /** Other spellings offered in the bubble beside the original. */
        const val ALTERNATES = 4
    }
}

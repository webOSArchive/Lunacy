package org.webosarchive.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * The Android side of the keyboard: it owns the view and turns what the view reports into
 * edits on whatever field has focus. Everything about how the keyboard looks and behaves
 * lives in [KeyboardView]; this class only speaks to Android.
 */
class KeyboardService : InputMethodService(), KeyboardView.Host {

    private var view: KeyboardView? = null
    private var editor: EditorInfo? = null

    override fun onCreateInputView(): View =
        KeyboardView(this, this).also { view = it }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editor = info
        view?.reset()
    }

    /**
     * webOS's keyboard never took over the screen, and the tablet layout leaves room for the
     * field above it, so the extracted full-screen editor is off.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * The input view is taller than the keyboard: it carries transparent room above it for the
     * press-and-hold balloon (see [KeyboardView]). Android would otherwise push the app up by
     * the whole view and send every tap in that room to the keyboard, so the insets say where
     * the keyboard really starts, and only that part takes touches. The balloon still gets the
     * finger that opened it, because a gesture stays with the window that caught it.
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
    }

    // --- What the keyboard reports ----------------------------------------------------------

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onBackspace(word: Boolean) {
        val ic = currentInputConnection ?: return
        if (!word) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        // Held down long enough and webOS started taking whole words.
        val before = ic.getTextBeforeCursor(64, 0) ?: return
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        ic.deleteSurroundingText(before.length - i, 0)
    }

    override fun onEnter() {
        val action = editor?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val unspecified = action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        val multiline = (editor?.inputType ?: 0) and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (unspecified || multiline) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else currentInputConnection?.performEditorAction(action)
    }

    override fun onTab() = sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)

    override fun onHide() = requestHideSelf(0)

    override fun onCursor(dx: Int, dy: Int, select: Boolean) {
        val code = when {
            dx > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
            dx < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
            dy > 0 -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> KeyEvent.KEYCODE_DPAD_UP
        }
        val ic = currentInputConnection ?: return
        // Holding shift while the ball moves selects, the way it did on webOS.
        val meta = if (select) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    /** What the field wants its Return key to say. webOS wrote "Done" by default. */
    override fun enterLabel(): String {
        val info = editor
        info?.actionLabel?.let { if (it.isNotEmpty()) return it.toString() }
        return when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
            else -> "Done"
        }
    }
}

package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * The "Just type..." pill above the card view (reference §3.3). Width is 0.75 of the screen's
 * short side; placement is done by the shell.
 *
 * It takes text but does nothing with it yet: webOS's Just Type searched, launched, and handed
 * what was typed to an app, and none of that exists here. What it is good for today is
 * somewhere to type - which is how the companion keyboard gets tried on. Return is swallowed,
 * deliberately, until there is something for it to do.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
class JustType(context: Context, private val luna: Luna) : EditText(context) {
    companion object {
        const val HEIGHT = 50        // launcher3/search-field-bg-launcher.png height
        const val TOP_GAP = 9        // below the status bar
        const val WIDTH_OF_SHORT_SIDE = 0.75f
    }

    /** The prompt is italic on a TouchPad; what the user types is not. */
    private val promptFace = Typeface.create(luna.fontMedium, Typeface.ITALIC)
    private val typedFace = luna.fontMedium

    init {
        // The pill is drawn here, so the widget brings no background and no padding of its
        // own: only the text sits inside it, clear of the search button on the right.
        background = null
        setPadding(luna.px(20f).toInt(), 0, luna.px(45f).toInt(), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, luna.px(18f))
        setTextColor(Color.WHITE)
        setHintTextColor(Color.argb((0.8f * 255).toInt(), 255, 255, 255))
        hint = "Just type..."
        typeface = promptFace
        includeFontPadding = false
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        setOnEditorActionListener { _, _, _ -> true }   // nothing to search yet
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val face = if (s.isEmpty()) promptFace else typedFace
                if (typeface !== face) typeface = face
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    override fun onDraw(c: Canvas) {
        luna.nine(c, "launcher3/search-field-bg-launcher.png", RectF(0f, 0f, width.toFloat(), height.toFloat()), 40, 0, 40, 0)
        luna.image("launcher3/search-button-launcher.png")?.let { b ->
            c.drawBitmap(b, width - luna.px(15f) - b.width, (height - b.height) / 2f, null)
        }
        super.onDraw(c)
    }

    /** Fading the pill out (a card opens, the launcher opens) puts the keyboard away with it. */
    override fun onVisibilityChanged(changed: View, visibility: Int) {
        super.onVisibilityChanged(changed, visibility)
        if (changed === this && visibility != View.VISIBLE && isFocused) {
            clearFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(windowToken, 0)
        }
    }
}

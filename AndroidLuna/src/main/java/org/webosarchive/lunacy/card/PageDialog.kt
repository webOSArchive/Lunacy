package org.webosarchive.lunacy.card

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.AbsoluteLayout
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * A web page's alert, confirm or prompt, as the Web app's AcceptCancelPopup draws them on the
 * reference TouchPad (Browser.js; the art is Enyo's Onyx theme), in the card, over a dimmed page.
 *
 * It is drawn here because it can't be drawn in the app's page. The page that called waits for
 * the answer with its renderer stopped, and a WebView has one renderer for every page in the
 * process, the Web app's own included: asking that page to open its popup, as webOS's separate
 * browser server could, left the page and every card waiting for each other for good. That
 * wait is also why other cards stand still until the dialog is answered: Docs/luna-deltas.md.
 */
@SuppressLint("ViewConstructor")
class PageDialog private constructor(
    private val window: AppWindow, kind: Kind, message: String, hint: String, private val answer: (Boolean, String) -> Unit,
) : FrameLayout(window.context) {
    enum class Kind { ALERT, CONFIRM, PROMPT }

    private val scale = window.pageScale
    private fun px(css: Float) = css * scale
    private var answered = false
    private val text = TextView(context)
    private val input: EditText? = if (kind == Kind.PROMPT) EditText(context) else null

    init {
        setBackgroundColor(Color.argb(166, 0, 0, 0))  // Enyo's enyo-scrim-dim, 0.65
        isClickable = true
        val medium = Typeface.createFromAsset(context.assets, "luna/fonts/Prelude-Medium.ttf")
        val panel = Panel(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(INSET_LEFT).toInt(), px(INSET_TOP).toInt(), px(INSET_RIGHT).toInt(), px(INSET_BOTTOM).toInt())
        }
        text.apply {
            this.text = message; setTextColor(Color.rgb(51, 51, 51)); typeface = medium
            setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16f))
            includeFontPadding = false; setLineSpacing(px(4f), 1f)
            // A long message scrolls, so that it can't push the buttons off the card.
            movementMethod = ScrollingMovementMethod()
        }
        panel.addView(text, LinearLayout.LayoutParams(-1, -2))
        input?.apply {
            this.hint = hint; setSingleLine(); typeface = medium
            setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_PX, px(18f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = Slices(art("input-focus.png"), 14f, 14f, 14f, 14f, scale)
            setPadding(px(11f).toInt(), 0, px(11f).toInt(), 0)
            panel.addView(this, LinearLayout.LayoutParams(-1, px(INPUT_H).toInt()).apply { topMargin = px(MESSAGE_GAP).toInt() })
        }
        fun button(caption: String, dark: Boolean, accept: Boolean) = Button(context, caption, dark, medium, scale).apply {
            setOnClickListener { finish(accept) }
        }
        val row = LinearLayout.LayoutParams(-1, px(ROW_H).toInt())
        // alert and prompt: the OK of an AcceptCancelPopup with no cancelCaption. confirm: a
        // VerticalAcceptCancelPopup, OK over Cancel.
        panel.addView(button("OK", dark = true, accept = true), row)
        if (kind == Kind.CONFIRM) panel.addView(button("Cancel", dark = false, accept = false), LinearLayout.LayoutParams(row))
        // Browser.showPopup: no wider than the app's width less 100.
        val width = min(px(WIDTH), window.width - px(100f))
        addView(panel, LayoutParams(width.toInt(), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun art(name: String): Bitmap = context.assets.open(IMAGES + name).use { BitmapFactory.decodeStream(it) }

    /**
     * In the middle of what the keyboard leaves of the card, as on the TouchPad. The card itself
     * doesn't move for it: the Web app's page, which would say so, is the one that is waiting.
     */
    private val keyboardWatch = ViewTreeObserver.OnGlobalLayoutListener {
        val visible = Rect()
        getWindowVisibleDisplayFrame(visible)
        val at = IntArray(2)
        getLocationOnScreen(at)
        val covered = (at[1] + height - visible.bottom).coerceIn(0, height)
        if (covered != paddingBottom) setPadding(0, 0, 0, covered)
        // A long message gets what is left of that.
        val around = px(INSET_TOP + INSET_BOTTOM + MESSAGE_GAP + ROW_H * 2 + (if (input != null) INPUT_H else 0f))
        val room = (height - covered - around).toInt().coerceAtLeast(px(20f).toInt())
        if (text.maxHeight != room) text.maxHeight = room
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(keyboardWatch)
    }

    private fun finish(accept: Boolean) {
        if (answered) return
        answered = true
        val value = input?.text?.toString().orEmpty().ifEmpty { input?.hint?.toString().orEmpty() }
        input?.let { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0) }
        window.removeView(this)
        answer(accept, value)
    }

    /** The card closing with the question open: as far as the page knows, it was cancelled. */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalLayoutListener(keyboardWatch)
        if (!answered) { answered = true; answer(false, "") }
    }

    private fun show() {
        @Suppress("DEPRECATION") window.addView(this, AbsoluteLayout.LayoutParams(-1, -1, 0, 0))
        input?.let { f ->
            f.requestFocus()
            f.post { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(f, 0) }
        }
    }

    /** dialog.png, as `border-image: 42 24 24` draws it. */
    private inner class Panel(context: Context) : LinearLayout(context) {
        private val back = Slices(art("dialog.png"), 42f, 24f, 24f, 24f, scale)
        init { setWillNotDraw(false) }
        override fun onDraw(canvas: Canvas) { back.setBounds(0, 0, width, height); back.draw(canvas) }
    }

    /** An Enyo button: 35 px high in a row of 41, on its colour under button-up.png, or button-down.png pressed. */
    private inner class Button(context: Context, private val caption: String, private val dark: Boolean, face: Typeface, private val s: Float) : View(context) {
        private val up = Slices(art("button-up.png"), 4f, 4f, 4f, 4f, s)
        private val down = Slices(art("button-down.png"), 4f, 4f, 4f, 4f, s)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) Color.argb(128, 23, 23, 23) else Color.argb(204, 255, 255, 255) }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face; textSize = 16f * s; textAlign = Paint.Align.CENTER
            color = if (dark) Color.WHITE else Color.rgb(41, 41, 41)
        }
        init { isClickable = true }
        override fun setPressed(pressed: Boolean) { super.setPressed(pressed); invalidate() }
        override fun onDraw(canvas: Canvas) {
            val m = 3f * s
            val box = RectF(m, m, width - m, height - m)
            canvas.drawRoundRect(box, 5f * s, 5f * s, fill)
            (if (isPressed) down else up).let { it.setBounds(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt()); it.draw(canvas) }
            canvas.drawText(caption, width / 2f, height / 2f - (label.ascent() + label.descent()) / 2f, label)
        }
    }

    /** A CSS border-image with `fill`: the source's edges in px, drawn at [scale]. */
    private class Slices(private val b: Bitmap, private val top: Float, private val right: Float, private val bottom: Float, private val left: Float, private val scale: Float) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        override fun draw(c: Canvas) {
            val d = bounds
            val xs = floatArrayOf(0f, left, b.width - right, b.width.toFloat())
            val ys = floatArrayOf(0f, top, b.height - bottom, b.height.toFloat())
            val dx = floatArrayOf(d.left.toFloat(), d.left + left * scale, d.right - right * scale, d.right.toFloat())
            val dy = floatArrayOf(d.top.toFloat(), d.top + top * scale, d.bottom - bottom * scale, d.bottom.toFloat())
            for (i in 0..2) for (j in 0..2) {
                val src = Rect(xs[j].toInt(), ys[i].toInt(), xs[j + 1].toInt(), ys[i + 1].toInt())
                c.drawBitmap(b, src, RectF(dx[j], dy[i], dx[j + 1], dy[i + 1]), paint)
            }
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val IMAGES = "fw/enyo/1.0/framework/build/palm/themes/Onyx/images/"
        // Measured in the app's CSS px, and against the TouchPad's prompt (2026-11-01). The width
        // has the art's shadow in it: 300 of it shows.
        private const val WIDTH = 320f
        private const val INSET_LEFT = 19f
        private const val INSET_RIGHT = 20f
        private const val INSET_TOP = 21f
        private const val INSET_BOTTOM = 20f
        private const val MESSAGE_GAP = 9f
        private const val INPUT_H = 46f
        private const val ROW_H = 41f

        fun show(window: AppWindow, kind: Kind, message: String, hint: String, answer: (Boolean, String) -> Unit) =
            PageDialog(window, kind, message, hint, answer).show()
    }
}

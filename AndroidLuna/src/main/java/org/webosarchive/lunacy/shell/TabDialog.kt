package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout

/**
 * [LunaCE] The launcher's Rename Tab / New Tab dialog (renamedialog.cpp), over the launcher:
 * the launcher dimmed black at α120, a 520 × 150 panel (rgba 25,25,25,235, 1.5 px white α90
 * border, radius 12) centred and then lifted 140 px clear of the keyboard, the prompt in
 * Prelude 18 px white α170 in a 44 px row, and the name in Prelude 24 px bold in a box with a
 * blue 1.5 px border - up to 24 characters. A tab past the first four gets the trash can from
 * tab-delete-icon.png beside the box; it deletes at once, with no confirmation, as LunaCE's does.
 *
 * Return commits. Tapping away commits a rename but throws a new tab away, as LunaCE decided.
 */
@SuppressLint("ViewConstructor")
class TabDialog(context: Context, private val luna: Luna) : FrameLayout(context) {
    companion object {
        const val PANEL_W = 520f
        const val PANEL_H = 150f
        const val PROMPT_H = 44f
        const val PADDING = 18f
        const val SHIFT_UP = 140f
        const val MAX_LEN = 24
        const val DELETE = 40f
    }

    private var prompt = ""
    private var deletable = false
    private var commitOnTapAway = true
    private var onDone: (String?, Boolean) -> Unit = { _, _ -> }

    private val field = object : EditText(context) {
        // The box is drawn by the dialog; the field only holds the text.
        init { background = null }
    }.apply {
        setTextColor(Color.argb(235, 255, 255, 255))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, luna.px(24f))
        typeface = luna.fontBold
        gravity = Gravity.CENTER
        setSingleLine()
        includeFontPadding = false
        filters = arrayOf(InputFilter.LengthFilter(MAX_LEN))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        setOnEditorActionListener { _, _, _ -> finish(commit = true, delete = false); true }
    }

    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255); textSize = luna.px(18f); typeface = luna.fontMedium; textAlign = Paint.Align.CENTER
    }
    private val panelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 25, 25, 25) }
    private val panelEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = luna.px(1.5f); color = Color.argb(90, 255, 255, 255) }
    private val boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 0, 0, 0) }
    private val boxEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = luna.px(1.5f); color = Color.argb(220, 140, 180, 255) }

    init {
        setWillNotDraw(false)
        visibility = GONE
        addView(field, LayoutParams(0, 0))
    }

    val showing get() = visibility == VISIBLE

    /** [done] gets the trimmed name (null to leave it as it was) and whether to delete the tab. */
    fun show(prompt: String, text: String, deletable: Boolean, commitOnTapAway: Boolean, done: (String?, Boolean) -> Unit) {
        this.prompt = prompt; this.deletable = deletable; this.commitOnTapAway = commitOnTapAway; onDone = done
        field.setText(text.take(MAX_LEN))
        field.setSelection(field.text.length)
        visibility = VISIBLE
        requestLayout()
        field.requestFocus()
        post { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, 0) }
    }

    private fun finish(commit: Boolean, delete: Boolean) {
        if (!showing) return
        val name = field.text.toString().trim()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
        field.clearFocus()
        visibility = GONE
        onDone(if (commit && name.isNotEmpty()) name else null, delete)
    }

    /** Leaving it any other way (the home button) is tapping away. */
    fun dismiss() = finish(commit = commitOnTapAway, delete = false)

    // ---- geometry (renamedialog.cpp's recalculateLayout) ----

    private fun panel(): RectF {
        val w = luna.px(PANEL_W); val h = luna.px(PANEL_H)
        val cx = width / 2f; val cy = height / 2f - luna.px(SHIFT_UP)
        return RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }
    private fun promptRect(p: RectF) = RectF(p.left + luna.px(PADDING), p.top + luna.px(PADDING) / 2, p.right - luna.px(PADDING), p.top + luna.px(PADDING) / 2 + luna.px(PROMPT_H))
    private fun deleteRect(p: RectF): RectF {
        val right = p.right - luna.px(PADDING); val top = promptRect(p).bottom + luna.px(4f)
        return RectF(right - luna.px(DELETE), top, right, top + luna.px(DELETE))
    }
    private fun editRect(p: RectF): RectF {
        val right = if (deletable) deleteRect(p).left - luna.px(10f) else p.right - luna.px(PADDING)
        return RectF(p.left + luna.px(PADDING), promptRect(p).bottom, right, p.bottom - luna.px(PADDING))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val e = editRect(panel())
        field.layout(e.left.toInt(), e.top.toInt(), e.right.toInt(), e.bottom.toInt())
    }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, h)
        val e = editRect(panel())
        field.measure(MeasureSpec.makeMeasureSpec(e.width().toInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(e.height().toInt(), MeasureSpec.EXACTLY))
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.argb(120, 0, 0, 0))
        val p = panel()
        val r12 = luna.px(12f)
        c.drawRoundRect(p, r12, r12, panelFill)
        c.drawRoundRect(p, r12, r12, panelEdge)
        val pr = promptRect(p)
        c.drawText(prompt, pr.centerX(), pr.centerY() - (promptPaint.ascent() + promptPaint.descent()) / 2, promptPaint)
        val box = RectF(editRect(p)).apply { inset(luna.px(4f), luna.px(4f)) }
        val r6 = luna.px(6f)
        c.drawRoundRect(box, r6, r6, boxFill)
        c.drawRoundRect(box, r6, r6, boxEdge)
        if (deletable) luna.image("launcher3/tab-delete-icon.png")?.let { b ->
            c.drawBitmap(b, Rect(0, 0, b.width, b.height / 2), deleteRect(p), null)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Every touch is the dialog's while it is up; a tap decides.
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val p = panel()
            when {
                deletable && deleteRect(p).contains(e.x, e.y) -> finish(commit = false, delete = true)
                !p.contains(e.x, e.y) -> finish(commit = commitOnTapAway, delete = false)
            }
        }
        return true
    }
}

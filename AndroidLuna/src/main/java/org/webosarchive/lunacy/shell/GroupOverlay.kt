package org.webosarchive.lunacy.shell

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.text.InputFilter
import android.text.InputType
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import org.webosarchive.lunacy.card.AppInfo

/** [LunaCE] GroupIcon's composite: the art a launcher group shows in place of an app icon. */
object GroupArt {
    const val EDGE = 68
    const val SUB = 26
    const val MARGIN = 6

    /**
     * groupicon.cpp's renderCompositePmo: a rounded backplate (radius 8) with a soft shadow
     * 1.5 px down, a vertical gradient and a 2 px white α95 border, and up to four members
     * as 26 px thumbnails, 2 by 2 - past four, the fourth place says "+N".
     */
    fun composite(luna: Luna, members: List<AppInfo>): Bitmap {
        val size = luna.px(EDGE)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val k = luna.density
        val plate = RectF(1f * k, 1f * k, size - 1f * k, size - 2f * k)
        val r = 8f * k
        c.drawRoundRect(RectF(plate).apply { offset(0f, 1.5f * k) }, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) })
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, plate.top, 0f, plate.bottom,
                intArrayOf(Color.argb(160, 111, 111, 116), Color.argb(150, 61, 61, 66), Color.argb(170, 36, 36, 38)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(plate, r, r, fill)
        c.drawRoundRect(plate, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * k; color = Color.argb(95, 255, 255, 255) })
        val sub = SUB * k; val margin = MARGIN * k
        val gap = size - 2 * sub - 2 * margin
        val overflow = members.size > 4
        val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until minOf(members.size, 4)) {
            val x = margin + (i % 2) * (sub + gap); val y = margin + (i / 2) * (sub + gap)
            if (overflow && i == 3) {
                val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 255, 255, 255); textSize = 14f * k; typeface = luna.fontBold; textAlign = Paint.Align.CENTER }
                c.drawText("+${members.size - 3}", x + sub / 2, y + sub / 2 - (t.ascent() + t.descent()) / 2, t)
                continue
            }
            (luna.appIcon(members[i]) ?: luna.image("default-app-icon.png"))?.let { c.drawBitmap(it, null, RectF(x, y, x + sub, y + sub), filter) }
        }
        return b
    }
}

/**
 * [LunaCE] A launcher group opened (groupoverlay.cpp): the launcher dimmed, and a panel of the
 * group's members - cells of at least 110 px (here the 128 px icon cell and 8 more), up to
 * four across and sixteen shown - under the group's name in Prelude 22 px bold. It grows out
 * of the group's icon, 0.85 to full size and faded in, over 160 ms OutCubic, and shrinks back
 * over 130 ms InCubic. A tap on a member launches it; holding one puts it back on the page;
 * a tap on the name renames the group (the panel lifts 140 px for the keyboard); a tap
 * outside closes it.
 */
@SuppressLint("ViewConstructor")
class GroupOverlay(context: Context, private val luna: Luna) : FrameLayout(context) {
    companion object {
        const val CELL = 110f
        const val TITLE_H = 44f
        const val PADDING = 18f
        const val MAX_COLUMNS = 4
        const val MAX_SHOWN = 16
        const val EDIT_SHIFT = 140f
        const val MAX_NAME = 24
        const val OPEN_MS = 160L
        const val CLOSE_MS = 130L
        const val MIN_SCALE = 0.85f
        const val HOLD_MS = 700L
        // The launcher's own icon geometry (Launcher.Params).
        const val ICON = 64f
        const val ICON_DY = -11f
        const val LABEL_W = 100f
        const val LABEL_GAP = 2f
    }

    var onLaunch: (AppInfo) -> Unit = {}
    var onPopOut: (Launcher.Tile.Group, AppInfo) -> Unit = { _, _ -> }
    var onRenamed: (Launcher.Tile.Group) -> Unit = {}

    private var group: Launcher.Tile.Group? = null
    private var origin = PointF()
    private var progress = 0f
    private var anim: ValueAnimator? = null
    private var editing = false
    val showing get() = group != null

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255); textSize = luna.px(22f); typeface = luna.fontBold; textAlign = Paint.Align.CENTER
    }
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(14f); typeface = luna.fontBold; color = Color.WHITE }
    private val panelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 25, 25, 25) }
    private val panelEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = luna.px(1.5f); color = Color.argb(90, 255, 255, 255) }
    private val boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 0, 0, 0) }
    private val boxEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = luna.px(1.5f); color = Color.argb(220, 140, 180, 255) }

    private val field = object : EditText(context) { init { background = null } }.apply {
        setTextColor(Color.argb(235, 255, 255, 255))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, luna.px(22f))
        typeface = luna.fontBold
        gravity = Gravity.CENTER
        setSingleLine()
        includeFontPadding = false
        filters = arrayOf(InputFilter.LengthFilter(MAX_NAME))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        setOnEditorActionListener { _, _, _ -> endEdit(commit = true); true }
        visibility = GONE
    }

    init {
        setWillNotDraw(false)
        visibility = GONE
        addView(field, LayoutParams(0, 0))
    }

    fun show(g: Launcher.Tile.Group, from: PointF) {
        group = g; origin = from; editing = false
        visibility = VISIBLE
        animateTo(1f, OPEN_MS, Easing.OutCubic)
    }

    fun close() {
        if (group == null) return
        if (editing) endEdit(commit = true)
        animateTo(0f, CLOSE_MS, android.animation.TimeInterpolator { it * it * it }) {
            group = null; visibility = GONE
        }
    }

    private fun animateTo(to: Float, ms: Long, easing: android.animation.TimeInterpolator, done: () -> Unit = {}) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(progress, to).apply {
            duration = ms; interpolator = easing
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) { if (!cancelled) done() }
            })
            start()
        }
    }

    // ---- layout (relayoutMembers) ----

    private fun shown() = group?.members?.take(MAX_SHOWN).orEmpty()
    private fun cell() = maxOf(luna.px(CELL), luna.px(128f + 8f))
    private fun panel(): RectF {
        val n = shown().size
        val cols = n.coerceIn(1, MAX_COLUMNS); val rows = maxOf(1, (n + cols - 1) / cols)
        val w = cols * cell() + 2 * luna.px(PADDING)
        val h = rows * cell() + luna.px(TITLE_H) + 2 * luna.px(PADDING)
        val cy = height / 2f - if (editing) luna.px(EDIT_SHIFT) else 0f
        return RectF(width / 2f - w / 2, cy - h / 2, width / 2f + w / 2, cy + h / 2)
    }
    private fun titleRect(p: RectF) = RectF(p.left + luna.px(PADDING), p.top + luna.px(PADDING) / 2, p.right - luna.px(PADDING), p.top + luna.px(PADDING) / 2 + luna.px(TITLE_H))
    private fun cellRect(p: RectF, i: Int): RectF {
        val cols = shown().size.coerceIn(1, MAX_COLUMNS)
        val left = p.left + luna.px(PADDING) + (i % cols) * cell(); val top = titleRect(p).bottom + (i / cols) * cell()
        return RectF(left, top, left + cell(), top + cell())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val tr = titleRect(panel())
        field.layout(tr.left.toInt(), tr.top.toInt(), tr.right.toInt(), tr.bottom.toInt())
    }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, h)
        val tr = titleRect(panel())
        field.measure(MeasureSpec.makeMeasureSpec(tr.width().toInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(tr.height().toInt(), MeasureSpec.EXACTLY))
    }

    // ---- drawing (GroupOverlay::paint) ----

    override fun onDraw(c: Canvas) {
        val g = group ?: return
        c.drawColor(Color.argb((120 * progress).toInt(), 0, 0, 0))
        val p = panel()
        val scale = MIN_SCALE + (1 - MIN_SCALE) * progress
        val o = if (editing) PointF(origin.x, origin.y - luna.px(EDIT_SHIFT)) else origin
        c.save()
        c.scale(scale, scale, o.x, o.y)
        val alpha = (progress * 255).toInt()
        c.saveLayerAlpha(0f, 0f, width / scale + width, height / scale + height, alpha, Canvas.ALL_SAVE_FLAG)
        val r12 = luna.px(12f)
        c.drawRoundRect(p, r12, r12, panelFill)
        c.drawRoundRect(p, r12, r12, panelEdge)
        val tr = titleRect(p)
        if (editing) {
            val box = RectF(tr).apply { inset(luna.px(4f), luna.px(4f)) }
            val r6 = luna.px(6f)
            c.drawRoundRect(box, r6, r6, boxFill); c.drawRoundRect(box, r6, r6, boxEdge)
        } else c.drawText(g.name, tr.centerX(), tr.centerY() - (titlePaint.ascent() + titlePaint.descent()) / 2, titlePaint)
        shown().forEachIndexed { i, app -> drawMember(c, app, cellRect(p, i)) }
        c.restore()
        c.restore()
    }

    private val labels = HashMap<String, StaticLayout>()

    @Suppress("DEPRECATION")
    private fun drawMember(c: Canvas, app: AppInfo, cell: RectF) {
        val half = luna.px(ICON) / 2
        val cx = cell.centerX(); val iy = cell.centerY() + luna.px(ICON_DY)
        (luna.appIcon(app) ?: luna.image("default-app-icon.png"))?.let { c.drawBitmap(it, null, RectF(cx - half, iy - half, cx + half, iy + half), null) }
        val layout = labels.getOrPut(app.id) {
            val w = luna.px(LABEL_W).toInt()
            val full = StaticLayout(app.title, label, w, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)
            if (full.lineCount <= 2) full else {
                val second = TextUtils.ellipsize(app.title.substring(full.getLineStart(1)), label, w.toFloat(), TextUtils.TruncateAt.END)
                StaticLayout(app.title.substring(0, full.getLineStart(1)) + second, label, w, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)
            }
        }
        c.save(); c.translate(cx - layout.width / 2f, iy + half + luna.px(LABEL_GAP)); layout.draw(c); c.restore()
    }

    // ---- the name ----

    private fun startEdit() {
        val g = group ?: return
        editing = true
        field.setText(g.name.take(MAX_NAME)); field.setSelection(field.text.length)
        field.visibility = VISIBLE
        requestLayout(); invalidate()
        field.requestFocus()
        post { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, 0) }
    }

    private fun endEdit(commit: Boolean) {
        if (!editing) return
        val g = group
        val name = field.text.toString().trim()
        editing = false
        field.visibility = GONE
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
        if (commit && g != null && name.isNotEmpty() && name != g.name) { g.name = name; onRenamed(g) }
        requestLayout(); invalidate()
    }

    // ---- touch ----

    private var downMember: AppInfo? = null
    private var held = false
    private val hold = Runnable {
        val g = group ?: return@Runnable
        val app = downMember ?: return@Runnable
        held = true
        onPopOut(g, app)
        // The group is gone once it is down to one member.
        if (g.members.size < 2) close() else invalidate()
    }

    private fun memberAt(x: Float, y: Float): AppInfo? {
        val p = panel()
        return shown().withIndex().firstOrNull { (i, _) -> cellRect(p, i).contains(x, y) }?.value
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (group == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                held = false
                downMember = if (editing) null else memberAt(e.x, e.y)
                if (downMember != null) postDelayed(hold, HOLD_MS)
            }
            MotionEvent.ACTION_MOVE -> {}
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                if (held) return true
                val p = panel()
                when {
                    editing -> if (!titleRect(p).contains(e.x, e.y)) endEdit(commit = true)
                    !p.contains(e.x, e.y) -> close()
                    titleRect(p).contains(e.x, e.y) -> startEdit()
                    else -> memberAt(e.x, e.y)?.let { onLaunch(it); close() }
                }
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(hold)
        }
        return true
    }
}

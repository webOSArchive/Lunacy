package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Exhibition mode's app menu: what the title in the status bar drops down while the device is
 * exhibiting, so its owner can change face without leaving the mode.
 *
 * webOS drew this itself (LunaSysMgr's DockModeAppMenu, in the same MenuContainer frame as the
 * notification drop-down), listing each dock-mode launch point by its icon and its dock title.
 * Lunacy's list is the same, with the shell's own Time face at the top: Palm's Exhibition app
 * treats Time as an entry that is always there and can't be switched off, and so does this.
 *
 * Measured on the reference TouchPad's own screenshot of it (1024 x 768, so TouchPad px
 * directly): the panel is flush to the left edge of the screen, 320 px wide, and starts
 * immediately under the status bar - no frame, no inset, square top corners. Rows are 70 px
 * (LunaCE's ITEM_HEIGHT), the icon sits at x 12 in a 48 px box centred in its row, and the
 * title starts at x 68. The panel is a single vertical gradient from #424242 at the top to
 * #1c1c1c at the bottom - what looked like a highlighted row is just that gradient - with a
 * groove between rows and a light edge along the bottom.
 *
 */
@SuppressLint("ViewConstructor")
class ExhibitionMenu(context: Context, private val luna: Luna) : View(context) {
    class Entry(val appId: String?, val title: String, val icon: Bitmap?)

    companion object {
        const val ROW = 70
        const val WIDTH = 320
        const val ICON_X = 12
        const val ICON = 48
        const val TITLE_X = 68
        /** The light line along the bottom, and the panel's own gradient. */
        val TOP_COLOUR = Color.rgb(0x42, 0x42, 0x42)
        val BOTTOM_COLOUR = Color.rgb(0x1c, 0x1c, 0x1c)
        val EDGE_COLOUR = Color.rgb(0x40, 0x40, 0x40)
        val GROOVE_DARK = Color.rgb(0x1c, 0x1c, 0x1c)
        val GROOVE_LIGHT = Color.rgb(0x3e, 0x3e, 0x3e)
    }

    /** Chosen: null appId means the shell's own Time face. */
    var onChoose: (String?) -> Unit = {}

    var entries: List<Entry> = emptyList()
        set(v) { field = v; requestLayout(); invalidate() }
    private var pressed = -1

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = luna.px(18f); typeface = luna.fontMedium
    }
    private val panel = Paint()
    private val line = Paint()

    init { setWillNotDraw(false) }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(luna.px(WIDTH), entries.size * luna.px(ROW) + luna.px(2f).toInt())

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        panel.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, TOP_COLOUR, BOTTOM_COLOUR,
            android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), h, panel)

        entries.forEachIndexed { i, entry ->
            val top = i * luna.px(ROW).toFloat()
            val row = RectF(0f, top, width.toFloat(), top + luna.px(ROW))
            if (i == pressed) c.drawRect(row, Paint().apply { color = Color.argb(50, 255, 255, 255) })
            entry.icon?.let { icon ->
                val size = luna.px(ICON.toFloat())
                val x = luna.px(ICON_X.toFloat())
                c.drawBitmap(icon, null,
                    RectF(x, row.centerY() - size / 2, x + size, row.centerY() + size / 2), null)
            }
            c.drawText(entry.title, luna.px(TITLE_X.toFloat()),
                row.centerY() - (label.ascent() + label.descent()) / 2, label)
            // The groove between rows: a dark line with a lighter one under it.
            if (i > 0) {
                line.color = GROOVE_DARK; c.drawRect(0f, top - luna.px(1f), width.toFloat(), top, line)
                line.color = GROOVE_LIGHT; c.drawRect(0f, top, width.toFloat(), top + luna.px(1f), line)
            }
        }
        line.color = EDGE_COLOUR
        c.drawRect(0f, h - luna.px(1f), width.toFloat(), h, line)
    }

    private fun rowAt(y: Float): Int {
        val i = (y / luna.px(ROW)).toInt()
        return if (i in entries.indices) i else -1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { pressed = rowAt(e.y); invalidate() }
            MotionEvent.ACTION_UP -> {
                val i = rowAt(e.y)
                pressed = -1; invalidate()
                if (i >= 0) onChoose(entries[i].appId)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }
}

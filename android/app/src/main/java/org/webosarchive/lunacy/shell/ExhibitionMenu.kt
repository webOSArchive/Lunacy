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
 * Rows are LunaCE's 70 px (DockModeAppMenuContainer's ITEM_HEIGHT).
 */
@SuppressLint("ViewConstructor")
class ExhibitionMenu(context: Context, private val luna: Luna) : View(context) {
    class Entry(val appId: String?, val title: String, val icon: Bitmap?)

    companion object {
        const val ROW = 70
        const val WIDTH = 320
        /** menu-dropdown-bg.png's borders, as the dashboard drop-down uses them. */
        const val EDGE_L = 30; const val EDGE_T = 10; const val EDGE_R = 30; const val EDGE_B = 30
        const val INSET = 11
    }

    /** Chosen: null appId means the shell's own Time face. */
    var onChoose: (String?) -> Unit = {}

    var entries: List<Entry> = emptyList()
        set(v) { field = v; requestLayout(); invalidate() }
    /** Which entry is exhibiting now, so the menu can show it as the current one. */
    var current: String? = null
        set(v) { field = v; invalidate() }

    private var pressed = -1

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = luna.px(18f); typeface = luna.fontMedium
    }

    init { setWillNotDraw(false) }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(
        luna.px(WIDTH + 2 * INSET),
        luna.px(EDGE_T + EDGE_B) + entries.size * luna.px(ROW))

    override fun onDraw(c: Canvas) {
        luna.nine(c, "menu-dropdown-bg.png", RectF(0f, 0f, width.toFloat(), height.toFloat()),
            EDGE_L, EDGE_T, EDGE_R, EDGE_B)
        val left = luna.px(INSET).toFloat()
        val right = width - luna.px(INSET).toFloat()
        var y = luna.px(EDGE_T).toFloat()
        entries.forEachIndexed { i, entry ->
            val row = RectF(left, y, right, y + luna.px(ROW))
            if (i == pressed) {
                c.drawRect(row, Paint().apply { color = Color.argb(60, 255, 255, 255) })
            }
            entry.icon?.let { icon ->
                val size = luna.px(48f)
                val ix = row.left + luna.px(10f)
                c.drawBitmap(icon, null, RectF(ix, row.centerY() - size / 2, ix + size, row.centerY() + size / 2), null)
            }
            // The face that is exhibiting now reads brighter, the rest plain.
            label.alpha = if (entry.appId == current) 255 else 180
            c.drawText(entry.title, row.left + luna.px(68f),
                row.centerY() - (label.ascent() + label.descent()) / 2, label)
            if (i > 0) luna.tile(c, "menu-divider.png", RectF(row.left, y - luna.px(1f), row.right, y + luna.px(1f)))
            y += luna.px(ROW)
        }
    }

    private fun rowAt(y: Float): Int {
        val top = luna.px(EDGE_T).toFloat()
        if (y < top) return -1
        val i = ((y - top) / luna.px(ROW)).toInt()
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

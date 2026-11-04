package org.webosarchive.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * What webOS drew over the text itself when it corrected a word (codepoet, 2026-11-04: "it makes
 * a little plop sound, then underlines the replacement with a gray dotted line. tapping it shows
 * the alternates in a pop-up bubble (the original text, plus any other possibilities beside
 * it)"). On webOS the text field drew it; an Android keyboard can't draw in another app's
 * field, so the keyboard's window reaches up over the screen, transparent, and draws it there,
 * where Android says the words are ([android.view.inputmethod.CursorAnchorInfo]). Only the
 * bubble takes touches; everything else goes on to the app.
 *
 * The art is WebKit's, from the reference TouchPad (`/usr/palm/webkit/images/ate-*`), and the
 * geometry was measured on its screen: Prelude Medium 17 px, the text's baseline 29 px into the
 * 60 px art and starting 16 px in from the left, the pointer's tip 52 px down, and the dotted
 * lines a 5 px dot every 6 px starting 3 px under the baseline - gray under a corrected word,
 * orange under the original in the bubble.
 */
@SuppressLint("ViewConstructor")
class AssistOverlay(context: Context, private val art: Art, private val scale: Int, private val onPick: (Marked, String) -> Unit) : View(context) {

    /** A corrected word: where it is in the text, what it was, and where it is drawn. */
    class Marked(var start: Int, var text: String, val original: String, val alternates: List<String>) {
        /** Its box on screen: left, top, right and baseline, in screen pixels. */
        var screen: RectF? = null
        /** Screen pixels per TouchPad pixel for its text: how big its dots are drawn. */
        var dotScale = 1f
        val end get() = start + text.length
    }

    val marks = ArrayList<Marked>()
    private var bubble: Marked? = null
    private var items: List<String> = emptyList()
    private val itemRects = ArrayList<RectF>()
    /** The bubble's frame, in this view's pixels: Android is told it alone takes touches. */
    var bubbleFrame: Rect? = null
        private set

    private fun image(name: String): Bitmap? = art.assist(name)

    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = art.font("Prelude-Medium.ttf")
        color = 0xffffffff.toInt()
        textSize = FONT * scale.toFloat()
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun clear() { marks.clear(); hideBubble(); invalidate() }

    fun showBubble(m: Marked) {
        bubble = m
        // The original first, wearing its orange dots, then the other possibilities.
        items = (listOf(m.original) + m.alternates).filter { !it.equals(m.text, ignoreCase = false) }.distinct().take(MAX_ITEMS)
        if (items.isEmpty()) { hideBubble(); return }
        invalidate()
    }

    fun hideBubble() {
        val had = bubbleFrame != null
        bubble = null; bubbleFrame = null; itemRects.clear(); invalidate()
        if (had) insetsChanged()
    }
    val bubbleShowing get() = bubble != null

    private val here = IntArray(2)
    private fun toLocal(r: RectF): RectF { getLocationOnScreen(here); return RectF(r.left - here[0], r.top - here[1], r.right - here[0], r.bottom - here[1]) }

    override fun onDraw(c: Canvas) {
        val dot = image("ate-underline-corrected.png")
        if (dot != null) for (m in marks) m.screen?.let { dots(c, dot, toLocal(it), m.dotScale) }
        drawBubble(c)
    }

    /** A row of [dot]s under a word, drawn at [s] times their own size: [r] is left, top, right and baseline. */
    private fun dots(c: Canvas, dot: Bitmap, r: RectF, s: Float = scale.toFloat()) {
        // Measured in the TouchPad's Memos: the dots' 5 px tile starts a pixel below the
        // baseline, so their ink begins two rows under the letters.
        val y = r.bottom + DOT_DROP * s
        var x = r.left
        val step = 6f * s
        val dst = RectF()
        while (x + dot.width * s <= r.right + step / 2) {
            dst.set(x, y, x + dot.width * s, y + dot.height * s)
            c.drawBitmap(dot, null, dst, paint)
            x += step
        }
    }

    private fun drawBubble(c: Canvas) {
        val m = bubble ?: return
        val target = m.screen?.let { toLocal(it) } ?: return
        val left = image("ate-left.png") ?: return
        val middle = image("ate-middle.png") ?: return
        val right = image("ate-right.png") ?: return
        val down = image("ate-arrow-down.png") ?: return
        val up = image("ate-arrow-up.png") ?: return
        val divider = image("ate-divider.png")
        val s = scale.toFloat()

        val widths = items.map { text.measureText(it) }
        val gap = (ITEM_GAP * 2 + (divider?.width ?: 4)) * s
        val inner = widths.sum() + gap * (items.size - 1)
        // Never narrower than its two ends and the pointer between them.
        val w = Math.max(inner + (TEXT_LEFT + TEXT_RIGHT) * s, (left.width + right.width + down.width) * s)
        val h = ART_HEIGHT * s
        val cx = (target.left + target.right) / 2
        var x0 = (cx - w / 2).coerceIn(0f, Math.max(0f, width - w))
        // Above the word, its pointer's tip just over it; below it, pointing up, if there is no room.
        val above = target.top - (POINTER_TIP + TIP_GAP) * s >= 0
        val y0 = if (above) target.top - (POINTER_TIP + TIP_GAP) * s else target.bottom + (DESCENT + TIP_GAP) * s - POINTER_TOP * s
        val bodyY = if (above) y0 else y0 + (POINTER_HEIGHT) * s
        fun piece(b: Bitmap, x: Float, wPx: Float, y: Float) = c.drawBitmap(b, null, RectF(x, y, x + wPx, y + h), paint)
        piece(left, x0, left.width * s, bodyY)
        piece(middle, x0 + left.width * s, w - (left.width + right.width) * s, bodyY)
        piece(right, x0 + w - right.width * s, right.width * s, bodyY)
        val arrow = if (above) down else up
        val ax = (cx - arrow.width * s / 2).coerceIn(x0 + left.width * s, Math.max(x0 + left.width * s, x0 + w - right.width * s - arrow.width * s))
        piece(arrow, ax, arrow.width * s, y0)

        itemRects.clear()
        // The words sit in the middle when the bubble is wider than they are (one short word).
        var x = x0 + Math.max(TEXT_LEFT * s, (w - inner) / 2)
        val baseline = bodyY + BASELINE * s
        val miss = image("ate-underline-mispelled.png")
        for ((i, item) in items.withIndex()) {
            if (i > 0) {
                divider?.let { d ->
                    val dx = x - gap / 2 - d.width * s / 2
                    c.drawBitmap(d, null, RectF(dx, bodyY + DIVIDER_TOP * s, dx + d.width * s, bodyY + (DIVIDER_TOP + d.height) * s), paint)
                }
            }
            c.drawText(item, x, baseline, text)
            if (i == 0 && item == m.original && miss != null) dots(c, miss, RectF(x, baseline, x + widths[i], baseline + 2 * s), s)
            itemRects += RectF(x - gap / 2, bodyY, x + widths[i] + gap / 2, bodyY + BODY_BOTTOM * s)
            x += widths[i] + gap
        }
        val frame = Rect(x0.toInt(), y0.toInt(), (x0 + w).toInt(), (y0 + h).toInt())
        if (frame != bubbleFrame) { bubbleFrame = frame; post { insetsChanged() } }
    }

    /** What takes touches changed: Android asks the keyboard for its insets on the next layout. */
    private fun insetsChanged() { (parent as? View)?.requestLayout() ?: requestLayout() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = bubble ?: return false
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val i = itemRects.indexOfFirst { it.contains(e.x, e.y) }
            if (i >= 0) { val pick = items[i]; hideBubble(); onPick(m, pick) }
        }
        return true
    }

    companion object {
        // Measured on the reference TouchPad's screen and its ate-* art, in TouchPad pixels.
        private const val FONT = 17f
        private const val ART_HEIGHT = 60f
        private const val BASELINE = 29f
        private const val TEXT_LEFT = 16f
        private const val TEXT_RIGHT = 15f
        private const val ITEM_GAP = 12f
        private const val DIVIDER_TOP = 3f
        private const val BODY_BOTTOM = 43f
        private const val POINTER_TIP = 52f     // the down pointer's tip, from the art's top
        private const val POINTER_TOP = 2f      // the up pointer's tip
        private const val POINTER_HEIGHT = 8f   // how far the up pointer pushes the body down
        private const val TIP_GAP = 2f
        private const val DESCENT = 5f
        private const val MAX_ITEMS = 5
        private const val DOT_DROP = 1f
    }
}

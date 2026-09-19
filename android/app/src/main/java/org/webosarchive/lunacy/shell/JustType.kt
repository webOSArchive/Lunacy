package org.webosarchive.lunacy.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * The "Just type..." pill above the card view (reference §3.3). Search itself comes later.
 * Width is 0.75 of the screen's short side; placement is done by the shell.
 */
@SuppressLint("ViewConstructor")
class JustType(context: Context, private val luna: Luna) : View(context) {
    companion object {
        const val HEIGHT = 50        // launcher3/search-field-bg-launcher.png height
        const val TOP_GAP = 9        // below the status bar
        const val WIDTH_OF_SHORT_SIDE = 0.75f
    }

    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((0.8f * 255).toInt(), 255, 255, 255); textSize = luna.px(18f)
        typeface = Typeface.create(luna.fontMedium, Typeface.ITALIC)
    }

    override fun onDraw(c: Canvas) {
        luna.nine(c, "launcher3/search-field-bg-launcher.png", RectF(0f, 0f, width.toFloat(), height.toFloat()), 40, 0, 40, 0)
        luna.image("launcher3/search-button-launcher.png")?.let { b ->
            c.drawBitmap(b, width - luna.px(15f) - b.width, (height - b.height) / 2f, null)
        }
        val base = height / 2f - (hint.ascent() + hint.descent()) / 2 - luna.px(1f)
        c.drawText("Just type...", luna.px(20f), base, hint)
    }
}

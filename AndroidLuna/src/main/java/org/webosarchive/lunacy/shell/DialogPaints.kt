package org.webosarchive.lunacy.shell

import android.graphics.Color
import android.graphics.Paint

/**
 * GroupOverlay::paint's rounded rects, which TabDialog draws with the same paints: the panel
 * (brush 25,25,25,235; pen 255,255,255,90, 1.5 px) and the name's edit box (brush 0,0,0,180;
 * pen 140,180,255,220). The reference TouchPad's screen shows them so. (Its takeScreenShot
 * doesn't: the capture fills each shape with the pen colour, a grey panel and a light-blue
 * box. Compare by eye.)
 */
class DialogPaints(luna: Luna) {
    val panelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 25, 25, 25) }
    val panelEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = luna.px(1.5f); color = Color.argb(90, 255, 255, 255) }
    val boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 0, 0, 0) }
    val boxEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = luna.px(1.5f); color = Color.argb(220, 140, 180, 255) }
}

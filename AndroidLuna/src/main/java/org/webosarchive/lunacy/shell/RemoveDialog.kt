package org.webosarchive.lunacy.shell

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import org.webosarchive.lunacy.card.AppInfo

/**
 * The launcher's remove dialog, LunaCE's AppInfoDialog: "Remove Application?" or, for a
 * shortcut, "Remove Shortcut?", with Cancel and Remove, over a scrim on [view]. Up while [app]
 * is set; Remove hands it to [onRemove].
 */
class RemoveDialog(
    private val view: View,
    private val luna: Luna,
    /** An app's title by id, for a shortcut's message. */
    private val appTitle: (String) -> String?,
    private val onRemove: (AppInfo) -> Unit,
) {
    /** The app asked about; null when the dialog isn't up. */
    var app: AppInfo? = null
    private var pressed = -1  // 0 Cancel, 1 Remove
    private val dialogTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(18f); typeface = luna.fontBold; color = Color.WHITE }
    private val dialogMessage = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(14f); typeface = luna.fontBold; color = Color.WHITE }
    private val buttonText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(16f); typeface = luna.fontBold; color = Color.WHITE; textAlign = Paint.Align.CENTER }

    @Suppress("DEPRECATION")
    private fun dialogText(t: String, p: TextPaint) =
        StaticLayout(t, p, luna.px(Launcher.Params.DIALOG_W - 2 * (Launcher.Params.DIALOG_EDGE + Launcher.Params.DIALOG_MARGIN)).toInt(), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)

    private class DialogLayout(val frame: RectF, val title: StaticLayout, val titleY: Float, val message: StaticLayout, val messageY: Float, val buttons: List<RectF>)

    /** An Android app's own name, for the shortcut it pinned; its package if it has none. */
    private fun androidAppTitle(pkg: String): String = runCatching {
        val pm = view.context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun dialogLayout(app: AppInfo): DialogLayout {
        val edge = luna.px(Launcher.Params.DIALOG_EDGE); val margin = luna.px(Launcher.Params.DIALOG_MARGIN)
        // LauncherObject::appDeleteDecoratorActivated: a launch point an app added is a shortcut,
        // and so is one an Android app pinned (PinnedShortcuts), named by the app that pinned it.
        val lp = app.launchPoint
        val pinnedBy = app.androidShortcut?.let { androidAppTitle(app.androidComponent!!.packageName) }
        val title = dialogText(if (lp != null || pinnedBy != null) "Remove Shortcut?" else "Remove Application?", dialogTitle)
        val message = dialogText(when {
            lp != null -> app.title + " (" + (appTitle(lp.appId) ?: lp.appId) + ")"
            pinnedBy != null -> "${app.title} ($pinnedBy)"
            else -> app.title + " - v." + app.version
        }, dialogMessage)
        val bh = luna.px(Launcher.Params.DIALOG_BUTTON_H)
        val w = luna.px(Launcher.Params.DIALOG_W)
        val h = title.height + message.height + 2 * bh + 2 * edge + 4 * margin + luna.px(Launcher.Params.DIALOG_TOP)
        val left = (view.width - w) / 2; val top = (view.height - h) / 2
        val titleY = top + edge + margin + luna.px(Launcher.Params.DIALOG_TOP)
        val messageY = titleY + title.height + margin
        val bx = left + edge + margin + luna.px(1f); val bw = w - 2 * (edge + margin) - luna.px(1f)
        val cancel = RectF(bx, messageY + message.height + margin, bx + bw, messageY + message.height + margin + bh)
        val remove = RectF(bx, cancel.bottom, bx + bw, cancel.bottom + bh)
        return DialogLayout(RectF(left, top, left + w, top + h), title, titleY, message, messageY, listOf(cancel, remove))
    }

    fun draw(c: Canvas) {
        val app = app ?: return
        luna.image("scrim.png")?.let { c.drawBitmap(it, null, RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()), null) }
        val d = dialogLayout(app)
        luna.nine(c, "popup-bg.png", d.frame, 35, 40, 35, 40)
        c.save(); c.translate(d.frame.centerX() - d.title.width / 2f, d.titleY); d.title.draw(c); c.restore()
        c.save(); c.translate(d.frame.centerX() - d.message.width / 2f, d.messageY); d.message.draw(c); c.restore()
        listOf("Cancel", "Remove").forEachIndexed { i, caption ->
            val r = d.buttons[i]
            luna.nine(c, if (pressed == i) "pin/button-black-press.png" else "pin/button-black.png", r, 10, 10, 10, 10)
            c.drawText(caption, r.centerX(), buttonText.centredBaseline(r.centerY()), buttonText)
        }
    }

    fun touch(e: MotionEvent) {
        val app = app ?: return
        val hit = dialogLayout(app).buttons.indexOfFirst { it.contains(e.x, e.y) }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { pressed = hit; view.invalidate() }
            MotionEvent.ACTION_UP -> {
                pressed = -1
                if (hit >= 0) { this.app = null; if (hit == 1) onRemove(app) }
                view.invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; view.invalidate() }
        }
    }
}

package org.webosarchive.keyboard

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * A sideloaded keyboard has to be switched on in Settings and then chosen, and neither step is
 * easy to find. This is both the launcher entry and the gear beside the keyboard's entry in
 * Language & input: those two steps, the keyboard's settings, and a field to try it in that
 * stays above the keys while you type.
 */
class SetupActivity : Activity() {

    private val unit get() = (16 * resources.displayMetrics.density).toInt()
    private val refreshers = ArrayList<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(unit * 2, unit * 2, unit * 2, unit * 2)
        }
        column.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        column.addView(note("The TouchPad's keyboard. Switch it on, then choose it."))
        column.addView(wideButton("1. Switch it on") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        column.addView(wideButton("2. Choose it") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        // The size: automatic from the screen, or a whole number of device pixels per art pixel.
        column.addView(heading("Size"))
        column.addView(choices(listOf(0, 1, 2, 3), { if (it == 0) "Auto (${KeyboardView.Scale.auto(this)}x)" else "${it}x" },
            { KeyboardView.Scale.setting(this) }) { KeyboardView.Scale.setSetting(this, it) })

        // The keys' own feedback: webOS's tap sounds, and a buzz of Android's.
        column.addView(heading("Keys"))
        column.addView(toggle("Tap sounds", "The TouchPad's click on each key.",
            { KeyFeedback.tapSounds(this) }) { KeyFeedback.setTapSounds(this, it) })
        column.addView(toggle("Vibrate on keys", "A light buzz on each key.",
            { KeyFeedback.vibrate(this) }) { KeyFeedback.setVibrate(this, it) })
        // Its strength, felt as it is chosen; greyed out while the buzz is off.
        val strength = choices((1..KeyFeedback.LEVELS).toList(), { KeyFeedback.LEVEL_NAMES[it - 1] },
            { KeyFeedback.vibrateLevel(this) }) { v ->
            KeyFeedback.setVibrateLevel(this, v)
            KeyFeedback.buzz(this, v)
        }
        column.addView(strength)
        refreshers += { strength.alpha = if (KeyFeedback.vibrate(this)) 1f else 0.45f }

        // webOS's Text Assist, as its settings app had it (com.palm.app.textassist).
        column.addView(heading("Text Assist"))
        column.addView(toggle("Auto-Capitalization", "Capitals at the start of a sentence.",
            { TextAssist.autoCap(this) }) { TextAssist.set(this, "autoCapitalization", it) })
        column.addView(toggle("Auto-Correction", "Fixes typos as you type. Tap a dotted word for others.",
            { TextAssist.autoCorrect(this) }) { TextAssist.set(this, "autoCorrection", it) })
        column.addView(toggle("Double Space For Period", "Two spaces type \". \".",
            { TextAssist.doubleSpace(this) }) { TextAssist.set(this, "doubleSpaceForPeriod", it) })
        // What a correction does to say so - only a correction, not every key (that's Keys, above).
        column.addView(note("When a word is corrected:"))
        column.addView(choices(listOf(TextAssist.Alert.SOUND, TextAssist.Alert.VIBRATE, TextAssist.Alert.MUTE),
            { when (it) { TextAssist.Alert.SOUND -> "Sound"; TextAssist.Alert.VIBRATE -> "Vibrate"; else -> "Mute" } },
            { TextAssist.alert(this) }) { TextAssist.setAlert(this, it) })

        // Shortcuts and learned words are Android's personal dictionary (Shortcuts.kt).
        column.addView(heading("Shortcuts"))
        column.addView(note("Shortcuts and learned words."))
        column.addView(wideButton("Edit User Dictionary") {
            runCatching { startActivity(Intent(Settings.ACTION_USER_DICTIONARY_SETTINGS)) }
        })

        // Somewhere to try it, so the two steps above can be checked without leaving the app.
        val tryIt = EditText(this).apply {
            hint = "Try it here"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(0x80, 0x80, 0x80))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = unit * 2 }
        }
        column.addView(tryIt)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(PAGE)
            isFillViewport = true
            addView(column)
        }
        // Android 15 lays an app out under its status and navigation bars, and (from Android 11)
        // tells it where the keyboard is: keep the content clear of all three. Earlier Android
        // resizes the window for the keyboard itself (adjustResize in the manifest).
        // The field is brought up above the keys when the keyboard first comes up for it, and
        // whenever something is typed in it while it is out of sight - never just because the
        // keyboard changed size (a new Size above), which would yank the page away from the
        // buttons being tapped.
        if (Build.VERSION.SDK_INT >= 30) {
            var keyboardUp = false
            scroll.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                val up = insets.getInsets(WindowInsets.Type.ime()).bottom > 0
                if (up && !keyboardUp && tryIt.hasFocus()) v.post { scrollToTryIt(scroll, tryIt) }
                keyboardUp = up
                insets
            }
        } else {
            scroll.fitsSystemWindows = true
            // The window shrinks when the keyboard comes up: follow it the first time it shrinks.
            var fullHeight = 0
            scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                val h = bottom - top; val old = oldBottom - oldTop
                fullHeight = Math.max(fullHeight, h)
                if (h < old && old == fullHeight && tryIt.hasFocus()) scroll.post { scrollToTryIt(scroll, tryIt) }
            }
        }
        tryIt.setOnFocusChangeListener { _, focused -> if (focused) scroll.post { scrollToTryIt(scroll, tryIt) } }
        tryIt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(t: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(t: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(t: Editable?) { if (!inSight(scroll, tryIt)) scroll.post { scrollToTryIt(scroll, tryIt) } }
        })
        setContentView(scroll)
    }

    /**
     * Scrolls until the bottom of [field] (the last thing on the screen) sits just above the
     * keyboard. [field]'s place is in the page's column, which starts below the top padding
     * (the status bar), and the bottom padding is the navigation bar or the keyboard.
     */
    private fun scrollToTryIt(scroll: ScrollView, field: View) {
        scroll.smoothScrollTo(0, Math.max(0, field.bottom + unit - visibleHeight(scroll)))
    }

    /** All of [field] is on screen, between the status bar and the keyboard. */
    private fun inSight(scroll: ScrollView, field: View) =
        field.top >= scroll.scrollY && field.bottom <= scroll.scrollY + visibleHeight(scroll)

    private fun visibleHeight(scroll: ScrollView) = scroll.height - scroll.paddingTop - scroll.paddingBottom

    override fun onResume() {
        super.onResume()
        refreshers.forEach { it() }
    }

    private fun heading(s: String) = TextView(this).apply {
        text = s
        textSize = 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(0, unit * 2, 0, unit / 2)
    }

    private fun note(s: String) = TextView(this).apply {
        text = s
        setTextColor(GRAY_TEXT)
        gravity = Gravity.CENTER
        setPadding(0, unit / 2, 0, unit / 2)
    }

    /** A row of buttons, the current one lit. */
    private fun <T> choices(values: List<T>, label: (T) -> String, current: () -> T, set: (T) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        lateinit var refresh: () -> Unit
        val buttons = values.map { v ->
            Button(this).apply {
                text = label(v)
                isAllCaps = false
                // One line, however narrow the row: "Auto (2x)" wrapped on a phone.
                isSingleLine = true
                setPadding(unit / 4, paddingTop, unit / 4, paddingBottom)
                setOnClickListener { set(v); refresh() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }.also { row.addView(it) }
        }
        refresh = {
            val cur = current()
            buttons.forEachIndexed { i, b -> if (values[i] == cur) paint(b, CHOSEN, Color.WHITE) else paint(b, UNCHOSEN, GRAY_TEXT) }
        }
        refreshers += refresh
        refresh()
        return row
    }

    /** A switch with a line of explanation under it. */
    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun toggle(title: String, detail: String, current: () -> Boolean, set: (Boolean) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, unit / 2, 0, unit / 2)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val sw = Switch(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        val listener = CompoundButton.OnCheckedChangeListener { _, on -> set(on); refreshers.forEach { it() } }
        box.addView(sw)
        box.addView(TextView(this).apply { text = detail; setTextColor(GRAY_TEXT) })
        refreshers += {
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = current()
            sw.setOnCheckedChangeListener(listener)
        }
        sw.isChecked = current()
        sw.setOnCheckedChangeListener(listener)
        return box
    }

    private fun wideButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        paint(this, ACTION, Color.WHITE)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = unit / 2 }
    }

    /** Buttons in this screen's greys, whatever the phone's own theme would draw (Samsung's are near black). */
    private fun paint(b: Button, back: Int, text: Int) {
        if (Build.VERSION.SDK_INT >= 21) b.backgroundTintList = ColorStateList.valueOf(back)
        b.setTextColor(text)
    }

    companion object {
        private val PAGE = Color.rgb(0x28, 0x28, 0x28)
        private val GRAY_TEXT = Color.rgb(0xb4, 0xb4, 0xb4)
        private val ACTION = Color.rgb(0x5a, 0x5a, 0x5a)
        private val CHOSEN = Color.rgb(0x8a, 0x8a, 0x8a)
        private val UNCHOSEN = Color.rgb(0x3c, 0x3c, 0x3c)
    }
}

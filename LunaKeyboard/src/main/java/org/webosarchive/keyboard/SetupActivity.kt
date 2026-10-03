package org.webosarchive.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A sideloaded keyboard has to be switched on in Settings and then chosen, and neither step is
 * easy to find. This is both the launcher entry and the gear beside the keyboard's entry in
 * Language & input, and it does nothing but open those two screens.
 */
class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(0x28, 0x28, 0x28))
            setPadding(pad * 2, pad * 2, pad * 2, pad * 2)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Two steps, both in Android's own settings: switch the keyboard on, " +
                "then choose it while a text field has focus."
            setTextColor(Color.rgb(0xb4, 0xb4, 0xb4))
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad * 2)
        })
        root.addView(button("1. Turn it on in Settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("2. Choose the keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        // The size: automatic from the screen, or a whole number of device pixels per art pixel.
        root.addView(TextView(this).apply {
            text = "Size"
            setTextColor(Color.rgb(0xb4, 0xb4, 0xb4))
            gravity = Gravity.CENTER
            setPadding(0, pad * 2, 0, 0)
        })
        val sizes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val choices = listOf(0 to "Auto (${KeyboardView.Scale.auto(this)}x)", 1 to "1x", 2 to "2x", 3 to "3x")
        val buttons = ArrayList<Button>()
        fun refresh() { val cur = KeyboardView.Scale.setting(this); buttons.forEachIndexed { i, b -> b.alpha = if (choices[i].first == cur) 1f else 0.5f } }
        for ((v, label) in choices) {
            val b = Button(this).apply {
                text = label
                setOnClickListener { KeyboardView.Scale.setSetting(this@SetupActivity, v); refresh() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            buttons += b; sizes.addView(b)
        }
        refresh()
        root.addView(sizes)
        // Somewhere to try it, so the two steps above can be checked without leaving the app.
        root.addView(EditText(this).apply {
            hint = "Try it here"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(0x80, 0x80, 0x80))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad * 2 }
        })
        setContentView(root)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
    }
}


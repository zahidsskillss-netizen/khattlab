package com.ghoragraphics.khattlab

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** Full color chooser: hue / saturation / brightness sliders plus a #HEX box. */
object ColorPicker {

    fun show(a: Activity, initial: Int, onPick: (Int) -> Unit) {
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(4))
        }
        val preview = View(a)
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        val hex = EditText(a).apply {
            setSingleLine()
            textDirection = View.TEXT_DIRECTION_LTR
            hint = "#FFFFFF"
        }
        var updating = false

        fun current() = Color.HSVToColor(hsv)

        fun refresh(updateHex: Boolean) {
            preview.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(current())
                setStroke(dp(1), 0x44FFFFFF)
            }
            if (updateHex) {
                updating = true
                hex.setText(String.format("#%06X", 0xFFFFFF and current()))
                updating = false
            }
        }

        val labels = listOf(
            a.getString(R.string.cp_hue) to 360,
            a.getString(R.string.cp_saturation) to 100,
            a.getString(R.string.cp_brightness) to 100
        )
        val bars = mutableListOf<SeekBar>()
        fun progressOf(i: Int) = if (i == 0) hsv[0].toInt() else (hsv[i] * 100).toInt()

        labels.forEachIndexed { i, pair ->
            val tv = TextView(a).apply {
                text = pair.first
                setPadding(0, dp(12), 0, 0)
            }
            root.addView(tv)
            val sb = SeekBar(a).apply {
                max = pair.second
                progress = progressOf(i)
            }
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    hsv[i] = if (i == 0) p.toFloat() else p / 100f
                    refresh(true)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
            bars.add(sb)
            root.addView(sb)
        }

        root.addView(hex)
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val raw = s?.toString()?.trim() ?: return
                val str = if (raw.startsWith("#")) raw else "#$raw"
                if (str.length != 7) return
                try {
                    val c = Color.parseColor(str)
                    Color.colorToHSV(c, hsv)
                    bars.forEachIndexed { i, b -> b.progress = progressOf(i) }
                    refresh(false)
                } catch (e: IllegalArgumentException) {
                    // not a color yet
                }
            }
        })
        refresh(true)

        val scroll = ScrollView(a)
        scroll.addView(root)
        AlertDialog.Builder(a)
            .setTitle(R.string.cp_title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ -> onPick(current()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

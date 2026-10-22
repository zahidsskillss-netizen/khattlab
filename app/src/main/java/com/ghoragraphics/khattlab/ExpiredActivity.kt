package com.ghoragraphics.khattlab

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** Shown instead of the editor once the test days are over. */
class ExpiredActivity : Activity() {

    private val bg = 0xFF141416.toInt()
    private val gold = 0xFFE8B547.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bold = try { Typeface.createFromAsset(assets, "ui/bahij_bold.ttf") } catch (e: Exception) { Typeface.DEFAULT_BOLD }
        val body = try { Typeface.createFromAsset(assets, "fonts/Bahij_TheSansArabic-SemiBold.ttf") } catch (e: Exception) { Typeface.DEFAULT }
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }
        root.addView(ImageView(this).apply { setImageResource(R.drawable.app_logo) }, LinearLayout.LayoutParams(dp(110), dp(110)))
        root.addView(text("NawakhtGar", 28f, 0xFFFFFFFF.toInt(), bold).apply { setPadding(0, dp(14), 0, dp(18)) })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(22))
            background = GradientDrawable().apply {
                setColor(0xFF22222A.toInt()); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0x99E8B547.toInt())
            }
        }
        card.addView(text(getString(R.string.trial_end_t), 20f, gold, bold).apply { setPadding(0, 0, 0, dp(12)) })
        card.addView(text(getString(R.string.trial_end_text), 16f, 0xFFFFFFFF.toInt(), body).apply { setLineSpacing(0f, 1.5f) })
        card.addView(text(getString(R.string.trial_feedback), 15f, 0xD9FFFFFF.toInt(), body).apply {
            setLineSpacing(0f, 1.5f); setPadding(0, dp(14), 0, 0)
        })
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(button(getString(R.string.tg_channel) + "\nt.me/pashtoapp", true, bold) { open(AboutActivity.CHANNEL) }, topMargin(20))
        root.addView(button(getString(R.string.tg_ghora) + "\nt.me/GhoraGraphicsProduction", false, bold) { open(AboutActivity.GHORA_TG) }, topMargin(10))
        root.addView(button(getString(R.string.trial_close), false, bold) { finishAffinity() }, topMargin(10))

        setContentView(ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true; addView(root) })
    }

    private fun text(s: String, size: Float, color: Int, tf: Typeface) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); typeface = tf; gravity = Gravity.CENTER
    }

    private fun button(s: String, filled: Boolean, tf: Typeface, onClick: () -> Unit) = text(s, 15f, if (filled) bg else gold, tf).apply {
        setPadding(dp(14), dp(12), dp(14), dp(14))
        background = RippleDrawable(ColorStateList.valueOf(0x33000000),
            GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                if (filled) setColor(gold) else setStroke(dp(1), gold)
            }, null)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun topMargin(m: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(m) }

    private fun open(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: ActivityNotFoundException) {
            android.widget.Toast.makeText(this, url, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { finishAffinity() }
}

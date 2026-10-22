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

/** About the app and about its developer (same idea as in Zikr Yad). */
class AboutActivity : Activity() {

    companion object {
        // only Telegram for contact (for now)
        const val CHANNEL = "https://t.me/pashtoapp"
        const val GHORA_TG = "https://t.me/GhoraGraphicsProduction"
        const val SHARE_LINK = "https://github.com/zahidsskillss-netizen/khattlab/releases/latest"

        private const val BG = 0xFFF4F4F7.toInt()
        private const val INK = 0xFF1E1E24.toInt()
        private const val MUTED = 0xFF6B6D78.toInt()
        private const val GOLD = 0xFFE8B547.toInt()
        private const val GOLD_D = 0xFFB9822A.toInt()
        private const val LINE = 0xFFE3E3EA.toInt()
        private const val SOFT = 0xFFF8F8FB.toInt()
        private const val DARK1 = 0xFF2A2A30.toInt()
        private const val DARK2 = 0xFF141416.toInt()
    }

    private lateinit var bold: Typeface
    private lateinit var body: Typeface

    private fun dp(v: Float) = (v * resources.displayMetrics.density).roundToInt()
    private fun dp(v: Int) = dp(v.toFloat())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bold = try { Typeface.createFromAsset(assets, "ui/bahij_bold.ttf") } catch (e: Exception) { Typeface.DEFAULT_BOLD }
        body = try { Typeface.createFromAsset(assets, "fonts/Bahij_TheSansArabic-SemiBold.ttf") } catch (e: Exception) { Typeface.DEFAULT }
        window.statusBarColor = DARK1
        window.navigationBarColor = BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, 0, 0, dp(32))
        }
        build(root)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(root)
        })
    }

    private fun build(root: LinearLayout) {
        root.addView(header())

        // ---- about the app ----
        val app = card("✦", getString(R.string.ab_app_t))
        app.addView(para(getString(R.string.ab_app_text)))
        val feats = listOf(R.string.ab_f1, R.string.ab_f2, R.string.ab_f3, R.string.ab_f4, R.string.ab_f5, R.string.ab_f6)
        feats.forEachIndexed { i, r -> app.addView(bullet(pashtoNum(i + 1), getString(r))) }
        root.addView(app)

        // ---- test version ----
        val test = card("⏳", getString(R.string.trial_left, Trial.daysLeft(this)))
        test.addView(para(getString(R.string.ab_test_text)))
        root.addView(test)

        // ---- feedback + contact: Telegram only ----
        val contact = card("☎", getString(R.string.ab_feedback_t))
        contact.addView(para(getString(R.string.trial_feedback)))
        contact.addView(contactRow(getString(R.string.tg_channel), "t.me/pashtoapp", CHANNEL))
        contact.addView(contactRow(getString(R.string.ab_telegram), "t.me/GhoraGraphicsProduction", GHORA_TG))
        root.addView(contact)

        // ---- Ghora Graphics Production ----
        root.addView(ghoraAd())

        // ---- share ----
        val share = card("↗", getString(R.string.ab_share_t))
        share.addView(para(getString(R.string.ab_share_text)))
        share.addView(button(getString(R.string.ab_share_now), true) { shareApp() }, topMargin(12))
        root.addView(share)

        root.addView(text("© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Ghora Graphics Production", 12f, MUTED, body).apply {
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(0, dp(22), 0, 0)
        }, matchWrap())
    }

    // ================= pieces =================

    private fun header(): View {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(DARK1, DARK2)).apply {
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat())
            }
        }
        val back = text("→  " + getString(R.string.ab_back), 14f, 0xE6FFFFFF.toInt(), bold).apply {
            setPaddingRelative(dp(14), dp(6), dp(16), dp(8))
            background = RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), rounded(0x26FFFFFF, 18, 0x40FFFFFF), null)
            setOnClickListener { finish() }
        }
        h.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.START; bottomMargin = dp(12)
        })
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            elevation = dp(6).toFloat()
        }
        h.addView(logo, LinearLayout.LayoutParams(dp(104), dp(104)))
        h.addView(text("NawakhtGar", 30f, 0xFFFFFFFF.toInt(), bold).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.08f; setPadding(0, dp(14), 0, dp(2))
        })
        h.addView(text(getString(R.string.ab_tagline), 14f, 0xD9FFFFFF.toInt(), body).apply { gravity = Gravity.CENTER })
        h.addView(text(getString(R.string.ab_version) + " " + version(), 13f, GOLD, bold).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(4), dp(14), dp(6))
            background = rounded(0x22E8B547, 14, 0x66E8B547)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        return h
    }

    private fun ghoraAd(): View {
        val ad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(DARK1, DARK2)).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1.5f), 0x99E8B547.toInt())
            }
            elevation = dp(3).toFloat()
        }
        ad.addView(text(getString(R.string.ab_ghora), 26f, GOLD, bold).apply { gravity = Gravity.CENTER })
        ad.addView(text("GHORA GRAPHICS PRODUCTION", 11f, 0xB3F3E3A5.toInt(), bold).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.18f; setPadding(0, dp(6), 0, 0)
        })
        ad.addView(text(getString(R.string.ab_ad_tag), 15f, 0xFFFFFFFF.toInt(), body).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
        })
        listOf(R.string.ab_s1, R.string.ab_chip2, R.string.ab_s3, R.string.ab_s4, R.string.ab_s5).forEach { r ->
            val line = row().apply { setPadding(0, dp(5), 0, dp(5)) }
            line.addView(text("✦", 13f, GOLD, bold))
            line.addView(text(getString(r), 15.5f, 0xFFFFFFFF.toInt(), body).apply { setPadding(dp(10), 0, dp(10), 0) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            ad.addView(line)
        }
        ad.addView(text(getString(R.string.ab_contact_now), 15f, GOLD, bold).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(10))
        })
        ad.addView(button(getString(R.string.ab_telegram) + ": t.me/GhoraGraphicsProduction", true) { open(GHORA_TG) }, matchWrap())
        ad.layoutParams = matchWrap().apply { setMargins(dp(16), dp(14), dp(16), 0) }
        return ad
    }

    private fun card(glyph: String?, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = rounded(0xFFFFFFFF.toInt(), 24, LINE)
            elevation = dp(1.5f).toFloat()
            layoutParams = matchWrap().apply { setMargins(dp(16), dp(14), dp(16), 0) }
        }
        val head = row().apply { setPadding(0, 0, 0, dp(10)) }
        if (glyph != null) head.addView(badge(glyph, 34))
        head.addView(text(title, 18f, INK, bold).apply { setPadding(dp(10), 0, dp(10), 0) })
        c.addView(head)
        return c
    }

    private fun badge(s: String, size: Int) = text(s, 15f, GOLD_D, bold).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x22E8B547) }
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun bullet(mark: String, s: String): View {
        val r = row().apply { setPadding(0, dp(6), 0, dp(6)) }
        r.addView(badge(mark, 30))
        r.addView(text(s, 15f, INK, body).apply { setPadding(dp(12), 0, dp(12), 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return r
    }

    private fun chip(s: String) = text(s, 12.5f, GOLD_D, bold).apply {
        setPadding(dp(12), dp(4), dp(12), dp(6))
        background = rounded(0x1AE8B547, 14, 0)
    }

    private fun contactRow(label: String, value: String, url: String): View {
        val r = row().apply {
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = RippleDrawable(ColorStateList.valueOf(0x22E8B547), rounded(SOFT, 16, LINE), null)
            isClickable = true
            setOnClickListener { open(url) }
            layoutParams = topMargin(8)
        }
        r.addView(text(label, 15f, INK, bold), LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
        r.addView(text(value, 15f, GOLD_D, body).apply { textDirection = View.TEXT_DIRECTION_LTR; gravity = Gravity.END },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(text("‹", 20f, MUTED, bold).apply { setPadding(dp(10), 0, 0, 0) })
        return r
    }

    private fun button(s: String, filled: Boolean, onClick: () -> Unit) = text(s, 15f, if (filled) DARK2 else GOLD_D, bold).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(12), dp(14), dp(14))
        background = RippleDrawable(ColorStateList.valueOf(0x33000000),
            if (filled) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFFF6D27A.toInt(), GOLD_D)).apply { cornerRadius = dp(16).toFloat() }
            else rounded(0x00000000, 16, GOLD), null)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun para(s: String) = text(s, 15.5f, INK, body).apply { setLineSpacing(0f, 1.45f) }

    private fun text(s: String, size: Float, color: Int, tf: Typeface) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); typeface = tf
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun rounded(color: Int, radiusDp: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun topMargin(m: Int) = matchWrap().apply { topMargin = dp(m) }

    private fun pashtoNum(n: Int) = n.toString().map { "۰۱۲۳۴۵۶۷۸۹"[it - '0'] }.joinToString("")

    private fun version(): String = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    private fun open(url: String) {
        try {
            startActivity(Intent(if (url.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            android.widget.Toast.makeText(this, url, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun shareApp() {
        val msg = getString(R.string.ab_share_msg) + "\n" + SHARE_LINK + "\n\n" + CHANNEL
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, msg)
        }, getString(R.string.ab_share_now)))
    }
}

package com.ghoragraphics.khattlab

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** First-launch guide: pick a language and see the key features. */
class OnboardingActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    private val bg = 0xFF141416.toInt()
    private val card = 0xFF1F1F27.toInt()
    private val gold = 0xFFE8B547.toInt()
    private lateinit var bold: Typeface

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bold = try { Typeface.createFromAsset(assets, "ui/bahij_bold.ttf") } catch (e: Exception) { Typeface.DEFAULT_BOLD }
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(16))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // logo + welcome
        body.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_fg)
            background = GradientDrawable().apply { setColor(0xFF23231C.toInt()); cornerRadius = dp(22).toFloat() }
        }, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        body.addView(TextView(this).apply {
            setText(R.string.ob_welcome); typeface = bold; textSize = 24f; gravity = Gravity.CENTER
            setTextColor(gold); setPadding(0, dp(16), 0, dp(2))
        })
        body.addView(TextView(this).apply {
            setText(R.string.ob_subtitle); textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xB3FFFFFF.toInt())
        })

        // language chips
        body.addView(section(R.string.ob_pick_lang))
        val (lscroll, lrow) = run {
            val sv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            sv.addView(r); sv to r
        }
        val cur = LocaleManager.lang(this)
        for (code in LocaleManager.LANGUAGES) {
            val on = code == cur
            lrow.addView(TextView(this).apply {
                text = langName(code)
                typeface = bold; textSize = 15f; gravity = Gravity.CENTER
                setTextColor(if (on) bg else Color.WHITE)
                setPadding(dp(18), dp(10), dp(18), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    if (on) setColor(gold) else { setColor(0x1AFFFFFF); setStroke(dp(1), 0x44FFFFFF) }
                }
                setOnClickListener {
                    if (code != LocaleManager.lang(this@OnboardingActivity)) {
                        LocaleManager.setLang(this@OnboardingActivity, code)
                        recreate()
                    }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        body.addView(lscroll)

        // feature cards
        val feats = listOf(
            Triple("✨", R.string.ob_f_smart_t, R.string.ob_f_smart_d),
            Triple("Aa", R.string.ob_f_text_t, R.string.ob_f_text_d),
            Triple("🖼", R.string.ob_f_photo_t, R.string.ob_f_photo_d),
            Triple("▦", R.string.ob_f_templates_t, R.string.ob_f_templates_d),
            Triple("＋", R.string.ob_f_more_t, R.string.ob_f_more_d)
        )
        for (ft in feats) body.addView(featureCard(ft.first, ft.second, ft.third))

        // start button
        val start = TextView(this).apply {
            setText(R.string.ob_start); typeface = bold; textSize = 18f; gravity = Gravity.CENTER
            setTextColor(bg)
            background = GradientDrawable().apply { setColor(gold); cornerRadius = dp(26).toFloat() }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                LocaleManager.setOnboarded(this@OnboardingActivity)
                setResult(RESULT_OK); finish()
            }
        }
        root.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(22), dp(8), dp(22), dp(18))
        })
        setContentView(root)
    }

    private fun langName(code: String): String {
        val id = resources.getIdentifier("lang_$code", "string", packageName)
        return if (id != 0) getString(id) else code
    }

    private fun section(res: Int) = TextView(this).apply {
        setText(res); typeface = bold; textSize = 15f; setTextColor(gold); setPadding(dp(2), dp(22), dp(2), dp(8))
    }

    private fun featureCard(glyph: String, titleRes: Int, descRes: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(card); cornerRadius = dp(14).toFloat() }
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        row.layoutParams = lp
        row.addView(TextView(this).apply {
            text = glyph; textSize = 24f; gravity = Gravity.CENTER
            setTextColor(gold)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x22E8B547) }
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(14) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { setText(titleRes); typeface = bold; textSize = 16f; setTextColor(Color.WHITE) })
        col.addView(TextView(this).apply { setText(descRes); textSize = 13f; setTextColor(0xB3FFFFFF.toInt()); setPadding(0, dp(2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }
}

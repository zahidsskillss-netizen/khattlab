package com.ghoragraphics.khattlab

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's own language, independent of the phone's language.
 * Default is English; the user picks their language in onboarding or Settings.
 */
object LocaleManager {
    private const val PREFS = "settings"
    private const val KEY = "lang"
    const val DEFAULT = "en"

    /** The 10 languages offered, in the order shown. */
    val LANGUAGES = listOf("en", "ps", "fa", "ar", "ur", "tr", "hi", "es", "fr", "de")

    fun lang(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

    fun setLang(c: Context, code: String) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    fun hasChosen(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("onboarded")

    fun setOnboarded(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
    }

    /** Wraps a context so its resources use the chosen language. */
    fun wrap(base: Context): Context {
        val code = lang(base)
        val loc = Locale(code)
        Locale.setDefault(loc)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        // force the right writing direction (Android may not infer RTL from a bare code like "ps")
        val rtl = code in setOf("ps", "fa", "ar", "ur")
        cfg.setLayoutDirection(if (rtl) Locale("ar") else Locale("en"))
        return base.createConfigurationContext(cfg)
    }
}

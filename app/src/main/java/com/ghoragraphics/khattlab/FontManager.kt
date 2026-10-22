package com.ghoragraphics.khattlab

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Keeps the list of fonts the user can pick:
 *  - phone (system) fonts
 *  - fonts shipped inside the app (put .ttf/.otf files in app/src/main/assets/fonts/)
 *  - fonts the user imports from their phone (saved in the app's private folder)
 */
class FontManager(private val ctx: Context) {

    class FontItem(val key: String, val label: String, val typeface: Typeface)

    companion object {
        const val DEFAULT_KEY = "sys:default"
    }

    val fonts = mutableListOf<FontItem>()

    /** Changes whenever the list changes, so text layers re-measure themselves. */
    var version = 0
        private set

    private val userDir = File(ctx.filesDir, "fonts")

    init {
        fonts.add(FontItem(DEFAULT_KEY, ctx.getString(R.string.font_default), Typeface.DEFAULT))
        fonts.add(FontItem("sys:bold", ctx.getString(R.string.font_bold), Typeface.DEFAULT_BOLD))
        fonts.add(FontItem("sys:light", ctx.getString(R.string.font_light), Typeface.create("sans-serif-light", Typeface.NORMAL)))
        fonts.add(FontItem("sys:condensed", ctx.getString(R.string.font_condensed), Typeface.create("sans-serif-condensed", Typeface.BOLD)))
        fonts.add(FontItem("sys:serif", ctx.getString(R.string.font_serif), Typeface.SERIF))
        fonts.add(FontItem("sys:serif_bold", ctx.getString(R.string.font_serif_bold), Typeface.create(Typeface.SERIF, Typeface.BOLD)))
        fonts.add(FontItem("sys:mono", ctx.getString(R.string.font_mono), Typeface.MONOSPACE))

        // Fonts bundled with the app
        val assetNames = try { ctx.assets.list("fonts") ?: emptyArray() } catch (e: Exception) { emptyArray() }
        for (name in assetNames.sorted()) {
            if (!isFontName(name)) continue
            try {
                val tf = Typeface.createFromAsset(ctx.assets, "fonts/$name")
                fonts.add(FontItem("asset:$name", prettyName(name), tf))
            } catch (e: Exception) {
                // broken font file: skip it
            }
        }

        // Fonts the user imported earlier
        userDir.mkdirs()
        val files = userDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        for (f in files) {
            val tf = loadFile(f) ?: continue
            fonts.add(FontItem("file:${f.name}", prettyName(f.name), tf))
        }
    }

    /**
     * Font used for new text: Bahij TheSansArabic (Light if present, otherwise any weight),
     * or the phone's normal font when it is not in assets/fonts.
     */
    val defaultKey: String
        get() {
            val sans = fonts.filter {
                val k = it.key.lowercase()
                k.startsWith("asset:") && k.contains("thesansarabic")
            }
            return (sans.firstOrNull { it.key.lowercase().contains("light") } ?: sans.firstOrNull())?.key ?: DEFAULT_KEY
        }

    fun typeface(key: String): Typeface = fonts.firstOrNull { it.key == key }?.typeface ?: Typeface.DEFAULT

    private fun isFontName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".ttf") || n.endsWith(".otf")
    }

    private fun prettyName(file: String) = file.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')

    private fun loadFile(f: File): Typeface? = try {
        Typeface.createFromFile(f)
    } catch (e: Exception) {
        null
    }

    /** Copies a font the user picked into the app. Returns null if it is not a usable font. */
    fun importFont(uri: Uri): FontItem? {
        var name = "font_${System.currentTimeMillis()}.ttf"
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) name = n
                }
            }
        } catch (e: Exception) {
            // keep generated name
        }
        name = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (!isFontName(name)) return null

        val target = File(userDir, name)
        try {
            userDir.mkdirs()
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
        } catch (e: Exception) {
            target.delete()
            return null
        }
        val tf = loadFile(target)
        if (tf == null) {
            target.delete()
            return null
        }
        val key = "file:$name"
        fonts.removeAll { it.key == key }
        val item = FontItem(key, prettyName(name), tf)
        fonts.add(item)
        version++
        return item
    }

    /** Adds a font file (copied into the app) under [fileName]. Returns null if it is not a usable font. */
    fun installFont(src: File, fileName: String): FontItem? {
        var name = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (!isFontName(name)) name += ".ttf"
        val key = "file:$name"
        fonts.firstOrNull { it.key == key }?.let { existing ->
            val f = File(userDir, name)
            if (f.exists() && f.length() == src.length()) return existing
        }
        val target = File(userDir, name)
        try {
            userDir.mkdirs()
            src.inputStream().use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
        } catch (e: Exception) {
            target.delete()
            return null
        }
        val tf = loadFile(target)
        if (tf == null) {
            target.delete()
            return null
        }
        fonts.removeAll { it.key == key }
        val item = FontItem(key, prettyName(name), tf)
        fonts.add(item)
        version++
        return item
    }

    /** A font whose file name (without .ttf / .otf) is [name], if the app has it. */
    fun findByFileName(name: String): FontItem? {
        val want = name.substringAfterLast('/').substringBeforeLast('.').trim().lowercase()
        if (want.isEmpty()) return null
        return fonts.firstOrNull { it.key.substringAfter(':').substringBeforeLast('.').trim().lowercase() == want }
            ?: fonts.firstOrNull { it.label.trim().lowercase() == want.replace('_', ' ').replace('-', ' ') }
    }
}

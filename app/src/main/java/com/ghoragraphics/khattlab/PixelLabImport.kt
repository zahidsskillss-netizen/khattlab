package com.ghoragraphics.khattlab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Opens PixelLab projects (.plp): a zip with data.plab (JSON: page and objects),
 * the pictures and fonts it uses in /res, and a thumbnail.
 *
 * Step 1 [read] runs in the background: unzip, read the JSON, prepare every picture.
 * Step 2 [build] runs on the main thread: add the fonts, make NawakhtGar layers.
 *
 * Fonts PixelLab took from its own fonts folder are not inside the .plp; they are looked up in
 * NawakhtGar's fonts by file name, and listed in [Result.missingFonts] when not found.
 */
object PixelLabImport {

    /** Everything prepared in the background. */
    class Read(
        val name: String,
        val width: Int,
        val height: Int,
        val bgBitmap: Bitmap?,
        val bgColor: Int,
        val items: List<Item>,
        val dir: File
    )

    sealed class Item(val o: JSONObject)
    class ImageItem(o: JSONObject, val bitmap: Bitmap) : Item(o)
    class TextItem(o: JSONObject) : Item(o)
    class ShapeItem(o: JSONObject) : Item(o)

    class Result(val doc: DocBackground, val layers: List<Layer>, val name: String, val missingFonts: List<String>)

    /** Is this file a PixelLab project? */
    fun isPixelLab(f: File): Boolean = try {
        java.util.zip.ZipFile(f).use { z -> z.getEntry("data.plab") != null || z.getEntry("/data.plab") != null }
    } catch (e: Exception) { false }

    private fun entryName(n: String) = n.trimStart('/').substringAfterLast('/')

    /** Background step. [work] is an empty folder the files are unpacked into. */
    fun read(plp: File, work: File, fallbackName: String): Read? {
        work.deleteRecursively()
        work.mkdirs()
        val safe = Regex("^[A-Za-z0-9._-]{1,80}$")
        java.util.zip.ZipFile(plp).use { z ->
            var total = 0L
            for (e in z.entries()) {
                if (e.isDirectory) continue
                val n = entryName(e.name)
                if (!safe.matches(n)) continue
                total += e.size.coerceAtLeast(0)
                if (total > 400L * 1024 * 1024) return null
                z.getInputStream(e).use { inp -> File(work, n).outputStream().use { inp.copyTo(it) } }
            }
        }
        val data = File(work, "data.plab")
        if (!data.exists()) return null
        val j = JSONObject(data.readText())

        // page size: PixelLab keeps the shape of the page (1080 wide here)
        val bw = j.optInt("bgWidth", 1).coerceAtLeast(1)
        val bh = j.optInt("bgHeight", 1).coerceAtLeast(1)
        val w = 1080
        val h = (w * bh.toFloat() / bw).roundToInt().coerceIn(200, 4000)

        val bgType = j.optInt("bgType", 0)
        var bgBitmap: Bitmap? = null
        val bgColor = j.optInt("bgColor", Color.WHITE)
        when (bgType) {
            1 -> bgBitmap = loadPic(work, j.optJSONObject("bgPhotoSrc"), 2560)?.let { crop(it, j.optJSONObject("bgPhotoCrop")) }
            2 -> bgBitmap = gradientBitmap(j.optString("bgGradientV2", ""), w, h)
        }

        val bundle = j.optJSONObject("objectsBundle") ?: JSONObject()
        val ids = j.optJSONArray("objectsIds")
        val order = mutableListOf<String>()
        if (ids != null) for (i in 0 until ids.length()) order.add(ids.optString(i))
        else bundle.keys().forEach { order.add(it) }

        val items = mutableListOf<Item>()
        for (id in order) {
            val o = bundle.optJSONObject(id) ?: continue
            if (id.startsWith("text")) {
                items.add(TextItem(o))
            } else {
                val pic = loadPic(work, o.optJSONObject("shapeImageSrc"), 2048)
                if (pic == null) { items.add(ShapeItem(o)); continue }
                var b = crop(pic, o.optJSONObject("shapeImageCropInfo"))
                if (o.optBoolean("shapeRemoveColorEnabled")) {
                    val ct = o.optJSONArray("shapeRemoveColorCT")
                    val col = ct?.optString(0)?.toLongOrNull()?.toInt() ?: Color.WHITE
                    val tol = ct?.optString(1)?.toFloatOrNull() ?: 100f
                    b = removeColor(b, col, tol)
                }
                if (o.optBoolean("shapeColorOverlayEnabled")) b = overlay(b, o.optInt("shapeColorOverlayColor", Color.RED))
                items.add(ImageItem(o, b))
            }
        }
        return Read(fallbackName, w, h, bgBitmap, bgColor, items, work)
    }

    // ---------- pictures ----------

    private fun loadPic(work: File, src: JSONObject?, maxSide: Int): Bitmap? {
        val path = src?.optString("imgSrcPath", "") ?: return null
        if (path.isEmpty()) return null
        val f = File(work, path.substringAfterLast('/'))
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > maxSide) sample *= 2
        return try {
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } catch (e: OutOfMemoryError) { null }
    }

    /** PixelLab crops are in 1/100000 of the picture. */
    private fun crop(b: Bitmap, c: JSONObject?): Bitmap {
        if (c == null) return b
        val l = c.optInt("cropInfoPorLeft", 0); val t = c.optInt("cropInfoPorTop", 0)
        val r = c.optInt("cropInfoPorRight", 100000); val bo = c.optInt("cropInfoPorBottom", 100000)
        if (l <= 0 && t <= 0 && r >= 100000 && bo >= 100000) return b
        val x0 = (b.width * l / 100000f).roundToInt().coerceIn(0, b.width - 1)
        val y0 = (b.height * t / 100000f).roundToInt().coerceIn(0, b.height - 1)
        val x1 = (b.width * r / 100000f).roundToInt().coerceIn(x0 + 1, b.width)
        val y1 = (b.height * bo / 100000f).roundToInt().coerceIn(y0 + 1, b.height)
        return Bitmap.createBitmap(b, x0, y0, x1 - x0, y1 - y0)
    }

    /** Makes one color (and colors near it) see-through, with a soft edge. */
    private fun removeColor(src: Bitmap, color: Int, tolerance: Float): Bitmap {
        val b = if (src.isMutable && src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, true)
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val rr = Color.red(color); val gg = Color.green(color); val bb = Color.blue(color)
        val t1 = tolerance * 0.6f
        val t2 = tolerance * 0.8f
        for (i in px.indices) {
            val c = px[i]
            val dr = Color.red(c) - rr; val dg = Color.green(c) - gg; val db = Color.blue(c) - bb
            val d = sqrt((dr * dr + dg * dg + db * db).toFloat())
            if (d >= t2) continue
            val k = if (d <= t1) 0f else (d - t1) / (t2 - t1)
            val a = (Color.alpha(c) * k).roundToInt()
            px[i] = (c and 0x00FFFFFF) or (a shl 24)
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    /** Paints the whole picture in one color, keeping its shape. */
    private fun overlay(src: Bitmap, color: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(color or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN) }
        Canvas(out).drawBitmap(src, 0f, 0f, p)
        return out
    }

    /** "v2;type;x1/y1/x2/y2;color/color...;pos/pos..." (positions in %); type 4 = round, otherwise straight. */
    private fun parseGradient(s: String): Triple<Int, FloatArray, Pair<IntArray, FloatArray>>? {
        val p = s.split(';')
        if (p.size < 5) return null
        return try {
            val type = p[1].toInt()
            val pts = p[2].split('/').map { it.toFloat() / 100f }.toFloatArray()
            val cols = p[3].split('/').map { it.toLong().toInt() }.toIntArray()
            val pos = p[4].split('/').map { it.toFloat() }.toFloatArray()
            if (pts.size < 4 || cols.size < 2 || pos.size != cols.size) null else Triple(type, pts, Pair(cols, pos))
        } catch (e: Exception) { null }
    }

    private fun gradientShader(s: String, w: Float, h: Float): Shader? {
        val g = parseGradient(s) ?: return null
        val (type, p, cp) = g
        return if (type == 4) {
            val r = max(1f, kotlin.math.hypot((p[2] - p[0]) * w, (p[3] - p[1]) * h))
            RadialGradient(p[0] * w, p[1] * h, r, cp.first, cp.second, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(p[0] * w, p[1] * h, p[2] * w, p[3] * h, cp.first, cp.second, Shader.TileMode.CLAMP)
        }
    }

    private fun gradientBitmap(s: String, w: Int, h: Int): Bitmap? {
        val sh = gradientShader(s, w.toFloat(), h.toFloat()) ?: return null
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = sh })
        return b
    }

    // ---------- main thread: layers ----------

    /** PixelLab font path of a text ("fonts/پښتو/لمر.ttf", or a font inside the .plp). */
    fun fontPathOf(o: JSONObject): String {
        val f = o.optJSONObject("textTextFont") ?: return ""
        val k = f.keys()
        while (k.hasNext()) {
            val v = f.optJSONObject(k.next()) ?: continue
            val p = v.optString("textsIntervalsFont", "")
            if (p.isNotEmpty()) return p
        }
        return ""
    }

    /** Font files PixelLab took from its own fonts folder (not inside the .plp). */
    fun neededFonts(r: Read): Set<String> =
        r.items.filterIsInstance<TextItem>().map { fontPathOf(it.o) }
            .filter { it.isNotEmpty() && !it.contains("/res/") }
            .map { it.substringAfterLast('/') }
            .toSet()

    fun build(r: Read, fm: FontManager): Result {
        val layers = mutableListOf<Layer>()
        val missing = linkedSetOf<String>()
        val fallback = fm.findByFileName("Bahij_TheSansArabic-SemiBold")?.key ?: FontManager.DEFAULT_KEY

        for (it in r.items) {
            val o = it.o
            val cw = o.optDouble("shapeOldContainerWidth", 1058.0).toFloat().coerceAtLeast(1f)
            val ch = o.optDouble("shapeOldContainerHeight", cw.toDouble()).toFloat().coerceAtLeast(1f)
            val kx = r.width / cw
            val ky = r.height / ch
            val k = (kx + ky) / 2f
            val left = o.optDouble("shapeX", 0.0).toFloat() * kx
            val top = o.optDouble("shapeY", 0.0).toFloat() * ky
            val l: Layer = when (it) {
                is ImageItem -> imageLayer(it, left, top, kx, ky, k)
                is ShapeItem -> shapeLayer(o, left, top, kx, ky, k)
                is TextItem -> {
                    // the font: inside the .plp, or one the app has with the same file name
                    val path = fontPathOf(o)
                    var key: String? = null
                    if (path.contains("/res/")) {
                        val f = File(r.dir, path.substringAfterLast('/'))
                        if (f.exists()) {
                            val nice = fontFileName(f) ?: "pixellab_${path.substringAfterLast('/')}"
                            key = fm.installFont(f, nice)?.key
                        }
                    } else if (path.isNotEmpty()) {
                        key = fm.findByFileName(path)?.key
                    }
                    val t = textLayer(o, kx, k, key ?: fallback)
                    if (key == null && path.isNotEmpty()) {
                        missing.add(path.substringAfterLast('/'))
                        // a symbols / ornament font shows only letters without the font: hide it
                        if (path.contains("Symbol", true) || path.contains("Logo", true) || path.contains("Ornament", true)) t.hidden = true
                    }
                    val sz = t.contentSize(fm)
                    t.x = left + sz[0] / 2f
                    t.y = top + sz[1] / 2f
                    t
                }
            }
            l.hidden = l.hidden || o.optBoolean("objectHidden", false)
            l.locked = o.optBoolean("objectLocked", false)
            layers.add(l)
        }
        val doc = DocBackground(r.bgBitmap, r.bgColor, r.width, r.height)
        return Result(doc, layers, r.name, missing.toList())
    }

    private fun f(o: JSONObject, k: String, d: Float): Float {
        val v = o.opt(k) ?: return d
        return when (v) {
            is Number -> v.toFloat()
            is String -> v.toFloatOrNull() ?: d
            else -> d
        }
    }

    private fun imageLayer(it: ImageItem, left: Float, top: Float, kx: Float, ky: Float, k: Float): ImageLayer {
        val o = it.o
        val w = f(o, "shapeWidth", it.bitmap.width.toFloat()) * kx
        val h = f(o, "shapeHeight", it.bitmap.height.toFloat()) * ky
        var b = it.bitmap
        // PixelLab can stretch a picture; do the same so it fills the same box
        val want = w / h.coerceAtLeast(1f)
        val has = b.width / b.height.toFloat()
        if (kotlin.math.abs(want / has - 1f) > 0.02f) {
            val nh = (b.width / want).roundToInt().coerceIn(1, 6000)
            b = Bitmap.createScaledBitmap(b, b.width, nh, true)
        }
        val l = ImageLayer(b, left + w / 2f, top + h / 2f)
        l.scale = w / b.width.coerceAtLeast(1)
        l.rotation = f(o, "shapeRotation", 0f)
        l.opacity = (f(o, "shapeOpacity", 100f) * 2.55f).roundToInt().coerceIn(0, 255)
        if (o.optBoolean("shapeShadowEnabled")) {
            l.shadowRadius = f(o, "shapeShadowRadius", 10f) * k
            l.shadowDx = f(o, "shapeShadowDx", 0f) * k
            l.shadowDy = f(o, "shapeShadowDy", 0f) * k
            l.shadowColor = o.optInt("shapeShadowColor", 0x99000000.toInt())
        }
        if (o.optBoolean("shapeStrokeEnabled")) {
            l.borderWidth = f(o, "shapeStrokeWidth", 2f) * k
            l.borderColor = o.optInt("shapeStrokeColor", Color.WHITE)
        }
        return l
    }

    /** A PixelLab shape without a picture: a filled box. */
    private fun shapeLayer(o: JSONObject, left: Float, top: Float, kx: Float, ky: Float, k: Float): ShapeLayer {
        val w = f(o, "shapeWidth", 200f) * kx
        val h = f(o, "shapeHeight", 200f) * ky
        val l = ShapeLayer(ShapeLayer.RECT, left + w / 2f, top + h / 2f)
        l.w = w; l.h = h
        val g = parseGradient(o.optString("sCOG", ""))
        if (g != null) {
            l.fillColor = g.third.first[0]
            if (o.optInt("sCOfT", 1) == 2) {
                l.fillMode = 1
                l.gradColor1 = g.third.first[0]
                l.gradColor2 = g.third.first.last()
            }
        }
        l.rotation = f(o, "shapeRotation", 0f)
        l.opacity = (f(o, "shapeOpacity", 100f) * 2.55f).roundToInt().coerceIn(0, 255)
        if (o.optBoolean("shapeStrokeEnabled")) {
            l.strokeWidth = f(o, "shapeStrokeWidth", 2f) * k
            l.strokeColor = o.optInt("shapeStrokeColor", Color.BLACK)
        }
        return l
    }

    private fun textLayer(o: JSONObject, kx: Float, k: Float, fontKey: String): TextLayer {
        val t = TextLayer(o.optString("textTextString", ""), 0f, 0f)
        t.fontKey = fontKey
        t.textSize = f(o, "textTextSize", 40f) * k
        t.rotation = f(o, "textRotation", 0f)
        t.opacity = (f(o, "textOpacity", 100f) * 2.55f).roundToInt().coerceIn(0, 255)

        // alignment from Android gravity
        val g = o.optInt("textAlignment", 17)
        t.align = when (g and 7) {
            1 -> 1
            5 -> 2
            else -> 0
        }
        val maxW = f(o, "textMaxWidth", -1f)
        if (maxW > 0f && maxW < 5000f) t.wrapWidth = maxW * kx
        t.lineSpacing = (1f + f(o, "textLineSpacing", 0f) / 500f).coerceIn(0.5f, 3f)
        t.letterSpacing = f(o, "textLetterSpacing", 0f) / 100f

        // colors: the first part gives the main color, the others become colored parts
        val cols = o.optJSONObject("textTextColor")
        val parts = mutableListOf<IntArray>()
        if (cols != null) {
            val ks = cols.keys().asSequence().toList().sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (key in ks) {
                val v = cols.optJSONObject(key) ?: continue
                parts.add(intArrayOf(v.optInt("textsIntervalsStart"), v.optInt("textsIntervalsEnd"), v.optInt("textsIntervalsColor", Color.BLACK)))
            }
        }
        if (parts.isNotEmpty()) {
            t.color = parts[0][2]
            val covers = parts[0][0] <= 0 && parts[0][1] >= t.text.length
            t.spans = (if (covers) parts.drop(1) else parts).toMutableList()
        } else t.color = Color.BLACK

        if (o.optInt("textTextFillType", 1) == 2) {
            parseGradient(o.optString("textTextGradientV2", ""))?.let { gr ->
                t.fillMode = 1
                t.gradColor1 = gr.third.first[0]
                t.gradColor2 = gr.third.first.last()
                t.gradAngle = Math.toDegrees(kotlin.math.atan2((gr.second[3] - gr.second[1]).toDouble(), (gr.second[2] - gr.second[0]).toDouble())).toFloat()
            }
        }
        if (o.optBoolean("textStrokeEnabled")) {
            t.strokeWidth = f(o, "textStrokeWidth", 4f) * k
            t.strokeColor = o.optInt("textStrokeColor", Color.BLACK)
        }
        if (o.optBoolean("textOuterEnabled")) {
            t.shadowRadius = f(o, "textOuterRadius", 10f) * k
            t.shadowDx = f(o, "textOuterDx", 0f) * k
            t.shadowDy = f(o, "textOuterDy", 0f) * k
            t.shadowColor = o.optInt("textOuterColor", 0x99000000.toInt())
        }
        if (o.optBoolean("textInnerEnabled")) {
            t.innerRadius = f(o, "textInnerRadius", 3f) * k
            t.innerDx = f(o, "textInnerDx", 0f) * k
            t.innerDy = f(o, "textInnerDy", 0f) * k
            t.innerColor = o.optInt("textInnerColor", 0xAA000000.toInt())
        }
        if (o.optBoolean("textBgEnabled")) {
            t.highlightColor = o.optInt("textBgColor", 0xB4000000.toInt())
            val pads = listOf("Left", "Top", "Right", "Bottom").map { f(o, "textBgPadding$it", 0f) }
            t.highlightPadding = t.textSize * 0.25f + pads.average().toFloat() * k
            t.highlightRound = (f(o, "textBgBorderRadius", 0f) / 1.4f).coerceIn(0f, 100f)
        }
        if (o.optBoolean("textEmbossEnabled")) t.emboss = f(o, "textEmbossIntensity", 60f).coerceIn(0f, 100f)
        if (o.optBoolean("textThreeDEnabled")) {
            t.depth = f(o, "textThreeDDepth", 25f) * k
            t.depthAngle = f(o, "textThreeDObAn", 45f)
        }
        if (o.optBoolean("textReflectionEnabled")) {
            t.reflectSize = 60f
            t.reflectGap = f(o, "textReflectionOffset", 0f) * k
        }
        val curve = f(o, "textTextCurve", 0f)
        if (curve != 0f) t.curve = curve.coerceIn(-100f, 100f)
        fun any(key: String) = (o.optJSONObject(key)?.length() ?: 0) > 0
        t.bold = any("textBold")
        t.italic = any("textItalic")
        t.underline = any("textUnder")
        return t
    }

    /** The font's own name from its 'name' table, as a file name ("Some Font.ttf"), or null. */
    fun fontFileName(f: File): String? = try {
        val d = f.readBytes()
        fun u16(p: Int) = ((d[p].toInt() and 0xFF) shl 8) or (d[p + 1].toInt() and 0xFF)
        fun u32(p: Int) = (u16(p).toLong() shl 16) or u16(p + 2).toLong()
        val n = u16(4)
        var nameOff = -1
        for (i in 0 until n) {
            val r = 12 + i * 16
            if (String(d, r, 4, Charsets.ISO_8859_1) == "name") { nameOff = u32(r + 8).toInt(); break }
        }
        if (nameOff < 0) null else {
            val count = u16(nameOff + 2)
            val strOff = nameOff + u16(nameOff + 4)
            var best: String? = null
            for (i in 0 until count) {
                val r = nameOff + 6 + i * 12
                val platform = u16(r); val nameId = u16(r + 6)
                val len = u16(r + 8); val off = u16(r + 10)
                if (nameId != 4 && nameId != 1) continue
                val s = if (platform == 3 || platform == 0) String(d, strOff + off, len, Charsets.UTF_16BE)
                else String(d, strOff + off, len, Charsets.ISO_8859_1)
                if (s.isNotBlank() && (best == null || nameId == 4)) best = s.trim()
            }
            best?.replace(Regex("[\\\\/:*?\"<>|]"), "_")?.take(60)?.let { "$it.ttf" }
        }
    } catch (e: Exception) { null }
}

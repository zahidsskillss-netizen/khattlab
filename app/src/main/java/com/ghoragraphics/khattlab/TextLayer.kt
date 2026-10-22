package com.ghoragraphics.khattlab

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.EmbossMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A piece of text with all its effects. Sizes are in page pixels. */
class TextLayer(var text: String, px: Float, py: Float) : Layer() {

    init {
        x = px
        y = py
    }

    // ---- look ----
    var textSize = 90f
    var fontKey = FontManager.DEFAULT_KEY
    var color = Color.WHITE
    var fillMode = 0                // 0 solid, 1 gradient, 2 texture
    var gradColor1 = 0xFFFFEB3B.toInt()
    var gradColor2 = 0xFFF44336.toInt()
    var gradAngle = 90f
    var texture: Bitmap? = null

    var bold = false
    var italic = false
    var underline = false
    var strike = false

    var align = 1                   // 0 start, 1 center, 2 end
    var letterSpacing = 0f
    var lineSpacing = 1f
    var curve = 0f                  // -100..100
    /** Longest line in page pixels before the text wraps (0 = no wrapping). */
    var wrapWidth = 0f
    /** Parts of the text in another color: start, end, color (from PixelLab projects). */
    var spans = mutableListOf<IntArray>()

    var strokeWidth = 0f
    var strokeColor = Color.BLACK

    var shadowRadius = 0f
    var shadowDx = 0f
    var shadowDy = 0f
    var shadowColor = 0x99000000.toInt()

    var highlightColor = Color.TRANSPARENT
    var highlightPadding = 24f
    var highlightRound = 50f        // 0..100

    var innerRadius = 0f
    var innerDx = 0f
    var innerDy = 0f
    var innerColor = 0xAA000000.toInt()

    var emboss = 0f                 // 0..100

    var depth = 0f                  // 3D text
    var depthAngle = 45f
    var depthColor = 0xFF5A5A5A.toInt()
    var d3On = true                 // 3D switched on (keeps values when off)
    var d3Persp = false             // false = oblique (parallel), true = perspective
    var depthMode = 1               // 0 color, 1 auto (from text color), 2 gradient
    var depthColor2 = 0xFF1A1A1A.toInt()
    var depthDarken = 30f           // 0..100, how much darker the far end is
    var lightOn = false
    var lightAngle = 72f            // where the light comes from, degrees
    var lightStrength = 50f         // 0..100

    var floorLength = 0f            // 3D shadow, 0..100
    var floorSkew = 40f             // -100..100
    var floorBlur = 6f
    var floorColor = 0x88000000.toInt()

    // ---- drawing helpers ----
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layerPaint = Paint()
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var staticLayout: StaticLayout? = null
    private var spanLayout: StaticLayout? = null
    private val curvePath = Path()
    private var curveText = ""
    private var curveVOff = 0f
    private var curved = false
    private var cacheKey = ""

    override fun copy(): TextLayer {
        val c = TextLayer(text, x, y)
        c.copyBaseFrom(this)
        c.copyStyleFrom(this)
        c.wrapWidth = wrapWidth
        c.spans = spans.map { it.copyOf() }.toMutableList()
        return c
    }

    fun copyStyleFrom(o: TextLayer) {
        textSize = o.textSize; fontKey = o.fontKey; color = o.color
        fillMode = o.fillMode; gradColor1 = o.gradColor1; gradColor2 = o.gradColor2; gradAngle = o.gradAngle; texture = o.texture
        bold = o.bold; italic = o.italic; underline = o.underline; strike = o.strike
        align = o.align; letterSpacing = o.letterSpacing; lineSpacing = o.lineSpacing; curve = o.curve
        strokeWidth = o.strokeWidth; strokeColor = o.strokeColor
        shadowRadius = o.shadowRadius; shadowDx = o.shadowDx; shadowDy = o.shadowDy; shadowColor = o.shadowColor
        highlightColor = o.highlightColor; highlightPadding = o.highlightPadding; highlightRound = o.highlightRound
        innerRadius = o.innerRadius; innerDx = o.innerDx; innerDy = o.innerDy; innerColor = o.innerColor
        emboss = o.emboss
        depth = o.depth; depthAngle = o.depthAngle; depthColor = o.depthColor
        d3On = o.d3On; d3Persp = o.d3Persp; depthMode = o.depthMode; depthColor2 = o.depthColor2; depthDarken = o.depthDarken
        lightOn = o.lightOn; lightAngle = o.lightAngle; lightStrength = o.lightStrength
        floorLength = o.floorLength; floorSkew = o.floorSkew; floorBlur = o.floorBlur; floorColor = o.floorColor
        opacity = o.opacity
        perspH = o.perspH; perspV = o.perspV; rotX = o.rotX; rotY = o.rotY
        reflectSize = o.reflectSize; reflectGap = o.reflectGap; reflectAlpha = o.reflectAlpha
    }

    /** Remove all effects, keeping text, font, size and place. */
    fun resetEffects() {
        val keep = TextLayer(text, x, y)
        keep.textSize = textSize
        keep.fontKey = fontKey
        keep.highlightPadding = textSize * 0.25f
        keep.reflectGap = 0f
        val sx = x; val sy = y; val r = rotation; val s = scale
        copyStyleFrom(keep)
        x = sx; y = sy; rotation = r; scale = s
    }

    override fun rescale(k: Float) {
        super.rescale(k)
        textSize *= k
        wrapWidth *= k
        strokeWidth *= k
        shadowRadius *= k; shadowDx *= k; shadowDy *= k
        highlightPadding *= k
        innerRadius *= k; innerDx *= k; innerDy *= k
        depth *= k
        floorBlur *= k
    }

    // ---- saving styles ----

    fun styleToJson(): JSONObject = JSONObject().apply {
        put("textSize", textSize.toDouble()); put("fontKey", fontKey); put("color", color)
        put("fillMode", if (fillMode == 2) 0 else fillMode)
        put("grad1", gradColor1); put("grad2", gradColor2); put("gradAngle", gradAngle.toDouble())
        put("bold", bold); put("italic", italic); put("underline", underline); put("strike", strike)
        put("align", align); put("letterSpacing", letterSpacing.toDouble()); put("lineSpacing", lineSpacing.toDouble())
        put("curve", curve.toDouble())
        put("strokeWidth", strokeWidth.toDouble()); put("strokeColor", strokeColor)
        put("shadowRadius", shadowRadius.toDouble()); put("shadowDx", shadowDx.toDouble()); put("shadowDy", shadowDy.toDouble()); put("shadowColor", shadowColor)
        put("hlColor", highlightColor); put("hlPad", highlightPadding.toDouble()); put("hlRound", highlightRound.toDouble())
        put("innerRadius", innerRadius.toDouble()); put("innerDx", innerDx.toDouble()); put("innerDy", innerDy.toDouble()); put("innerColor", innerColor)
        put("emboss", emboss.toDouble())
        put("depth", depth.toDouble()); put("depthAngle", depthAngle.toDouble()); put("depthColor", depthColor)
        put("d3On", d3On); put("d3Persp", d3Persp); put("depthMode", depthMode); put("depthColor2", depthColor2)
        put("depthDarken", depthDarken.toDouble()); put("lightOn", lightOn); put("lightAngle", lightAngle.toDouble())
        put("lightStrength", lightStrength.toDouble())
        put("floorLength", floorLength.toDouble()); put("floorSkew", floorSkew.toDouble()); put("floorBlur", floorBlur.toDouble()); put("floorColor", floorColor)
        put("opacity", opacity)
        put("perspH", perspH.toDouble()); put("perspV", perspV.toDouble()); put("rotX", rotX.toDouble()); put("rotY", rotY.toDouble())
        put("reflectSize", reflectSize.toDouble()); put("reflectGap", reflectGap.toDouble()); put("reflectAlpha", reflectAlpha)
    }

    /** Apply a saved style, keeping this text's own size. */
    fun applyStyleJson(j: JSONObject) {
        val keepSize = textSize
        fun f(k: String, d: Float) = j.optDouble(k, d.toDouble()).toFloat()
        textSize = f("textSize", textSize); fontKey = j.optString("fontKey", fontKey); color = j.optInt("color", color)
        fillMode = j.optInt("fillMode", 0)
        gradColor1 = j.optInt("grad1", gradColor1); gradColor2 = j.optInt("grad2", gradColor2); gradAngle = f("gradAngle", gradAngle)
        bold = j.optBoolean("bold"); italic = j.optBoolean("italic"); underline = j.optBoolean("underline"); strike = j.optBoolean("strike")
        align = j.optInt("align", align); letterSpacing = f("letterSpacing", 0f); lineSpacing = f("lineSpacing", 1f)
        curve = f("curve", 0f)
        strokeWidth = f("strokeWidth", 0f); strokeColor = j.optInt("strokeColor", strokeColor)
        shadowRadius = f("shadowRadius", 0f); shadowDx = f("shadowDx", 0f); shadowDy = f("shadowDy", 0f); shadowColor = j.optInt("shadowColor", shadowColor)
        highlightColor = j.optInt("hlColor", Color.TRANSPARENT); highlightPadding = f("hlPad", highlightPadding); highlightRound = f("hlRound", 50f)
        innerRadius = f("innerRadius", 0f); innerDx = f("innerDx", 0f); innerDy = f("innerDy", 0f); innerColor = j.optInt("innerColor", innerColor)
        emboss = f("emboss", 0f)
        depth = f("depth", 0f); depthAngle = f("depthAngle", 45f); depthColor = j.optInt("depthColor", depthColor)
        d3On = j.optBoolean("d3On", true); d3Persp = j.optBoolean("d3Persp", false)
        // styles saved before depth modes existed used the chosen depth color
        depthMode = j.optInt("depthMode", if (j.has("depthColor")) 0 else 1)
        depthColor2 = j.optInt("depthColor2", depthColor2); depthDarken = f("depthDarken", 30f)
        lightOn = j.optBoolean("lightOn", false); lightAngle = f("lightAngle", 72f); lightStrength = f("lightStrength", 50f)
        floorLength = f("floorLength", 0f); floorSkew = f("floorSkew", 40f); floorBlur = f("floorBlur", 6f); floorColor = j.optInt("floorColor", floorColor)
        opacity = j.optInt("opacity", 255)
        perspH = f("perspH", 0f); perspV = f("perspV", 0f); rotX = f("rotX", 0f); rotY = f("rotY", 0f)
        reflectSize = f("reflectSize", 0f); reflectGap = f("reflectGap", 0f); reflectAlpha = j.optInt("reflectAlpha", 130)
        if (textSize > 0f) rescale(keepSize / textSize)
    }

    // ---- projects ----

    /** Everything about this text, for saving a project (the texture picture is saved separately). */
    fun toJson(): JSONObject = styleToJson().apply {
        put("type", "text"); put("text", text)
        put("fillMode", fillMode)
        if (wrapWidth > 0f) put("wrapWidth", wrapWidth.toDouble())
        if (spans.isNotEmpty()) put("spans", org.json.JSONArray().apply {
            spans.forEach { sp -> put(org.json.JSONArray().apply { put(sp[0]); put(sp[1]); put(sp[2]) }) }
        })
        putBase(this)
    }

    companion object {
        fun fromJson(j: JSONObject, texture: android.graphics.Bitmap?): TextLayer {
            val l = TextLayer(j.optString("text", ""), 0f, 0f)
            l.textSize = j.optDouble("textSize", 90.0).toFloat()
            l.applyStyleJson(j)
            l.fillMode = j.optInt("fillMode", 0)
            l.texture = texture
            if (l.fillMode == 2 && texture == null) l.fillMode = 0
            l.wrapWidth = j.optDouble("wrapWidth", 0.0).toFloat()
            j.optJSONArray("spans")?.let { a ->
                for (i in 0 until a.length()) a.optJSONArray(i)?.let { sp ->
                    if (sp.length() >= 3) l.spans.add(intArrayOf(sp.optInt(0), sp.optInt(1), sp.optInt(2)))
                }
            }
            l.readBase(j)
            return l
        }
    }

    // ---- measuring ----

    override fun prepare(fm: FontManager) {
        val key = "$text|$textSize|$fontKey|$letterSpacing|$lineSpacing|$align|$bold|$italic|$underline|$strike|$curve|$wrapWidth|${spans.joinToString { it.joinToString(",") }}|${fm.version}"
        if (key == cacheKey) return
        cacheKey = key

        paint.reset()
        paint.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        paint.textSize = textSize
        paint.typeface = fm.typeface(fontKey)
        paint.letterSpacing = letterSpacing
        paint.isFakeBoldText = bold
        paint.textSkewX = if (italic) -0.25f else 0f
        paint.isUnderlineText = underline
        paint.isStrikeThruText = strike

        curved = abs(curve) >= 1f
        if (!curved) {
            var w = 0f
            for (line in text.split('\n')) w = max(w, Layout.getDesiredWidth(line, paint))
            var width = ceil(w).toInt().coerceAtLeast(1) + 4
            if (wrapWidth > 1f) width = kotlin.math.min(width, ceil(wrapWidth).toInt().coerceAtLeast(1))
            val alignment = when (align) {
                0 -> Layout.Alignment.ALIGN_NORMAL
                2 -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_CENTER
            }
            val l = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
                .setLineSpacing(0f, lineSpacing)
                .setIncludePad(true)
                .build()
            staticLayout = l
            // the same layout with the colored parts, used only for the fill
            spanLayout = if (spans.isEmpty()) null else {
                val ss = android.text.SpannableString(text)
                for (sp in spans) {
                    val a = sp[0].coerceIn(0, text.length)
                    val b = sp[1].coerceIn(a, text.length)
                    if (b > a) ss.setSpan(android.text.style.ForegroundColorSpan(sp[2]), a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                StaticLayout.Builder.obtain(ss, 0, ss.length, paint, width)
                    .setAlignment(alignment)
                    .setLineSpacing(0f, lineSpacing)
                    .setIncludePad(true)
                    .build()
            }
            cW = l.width + if (italic) textSize * 0.25f else 0f
            cH = l.height.toFloat()
        } else {
            curveText = text.replace('\n', ' ')
            val tw = max(paint.measureText(curveText), 1f)
            val sweep = curve / 100f * 330f
            val theta = Math.toRadians(abs(sweep).toDouble()).toFloat()
            val r = tw / theta
            val oval = RectF(-r, -r, r, r)
            curvePath.reset()
            if (sweep > 0f) curvePath.addArc(oval, -90f - sweep / 2f, sweep)
            else curvePath.addArc(oval, 90f + abs(sweep) / 2f, sweep)
            val fmx = paint.fontMetrics
            curveVOff = -(fmx.ascent + fmx.descent) / 2f
            val b = RectF()
            curvePath.computeBounds(b, true)
            b.inset(-((fmx.descent - fmx.ascent) / 2f + 6f), -((fmx.descent - fmx.ascent) / 2f + 6f))
            curvePath.offset(-b.left, -b.top)
            cW = b.width()
            cH = b.height()
        }
    }

    private fun drawContent(canvas: Canvas, colored: Boolean = false) {
        if (curved) canvas.drawTextOnPath(curveText, curvePath, 0f, curveVOff, paint)
        else if (colored && fillMode == 0 && spanLayout != null) spanLayout?.draw(canvas)
        else staticLayout?.draw(canvas)
    }

    private fun hasHighlight() = Color.alpha(highlightColor) > 0

    override fun padding(): Float {
        var p = strokeWidth / 2f + 6f
        if (hasHighlight()) p += highlightPadding
        return p
    }

    /** Reset the paint between drawing passes. */
    private fun pass(c: Int, style: Paint.Style = Paint.Style.FILL) {
        paint.style = style
        paint.color = c
        paint.shader = null
        paint.maskFilter = null
        paint.xfermode = null
        paint.clearShadowLayer()
    }

    private fun configureFill() {
        pass(color)
        when (fillMode) {
            1 -> {
                val a = Math.toRadians(gradAngle.toDouble())
                val hx = cos(a).toFloat() * cW / 2f
                val hy = sin(a).toFloat() * cH / 2f
                paint.color = Color.BLACK
                paint.shader = LinearGradient(
                    cW / 2f - hx, cH / 2f - hy, cW / 2f + hx, cH / 2f + hy,
                    gradColor1, gradColor2, Shader.TileMode.CLAMP
                )
            }
            2 -> {
                val b = texture
                if (b != null && !b.isRecycled) {
                    val s = max(cW / b.width, cH / b.height)
                    val mm = Matrix()
                    mm.setScale(s, s)
                    mm.postTranslate((cW - b.width * s) / 2f, (cH - b.height * s) / 2f)
                    val sh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    sh.setLocalMatrix(mm)
                    paint.color = Color.BLACK
                    paint.shader = sh
                }
            }
        }
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    /** Main color of the text face, used for the "auto" depth color. */
    private fun frontColor(): Int = when (fillMode) {
        1 -> lerpColor(gradColor1, gradColor2, 0.5f)
        2 -> 0xFF808080.toInt()
        else -> if (Color.alpha(color) < 40) (if (strokeWidth > 0f) strokeColor else Color.GRAY) else color
    }

    /** -1..1: how much the sides of the 3D text face the light. */
    private fun sideLight(): Float {
        if (!lightOn || lightStrength <= 0f) return 0f
        val diff = Math.toRadians((lightAngle - depthAngle).toDouble())
        return cos(diff).toFloat() * lightStrength / 100f
    }

    private fun shade(c: Int, k: Float): Int = when {
        k > 0f -> lerpColor(c, Color.WHITE, 0.45f * k)
        k < 0f -> lerpColor(c, Color.BLACK, 0.55f * -k)
        else -> c
    }

    override fun drawLocal(canvas: Canvas, fm: FontManager) {
        prepare(fm)

        // 3D shadow lying behind the text
        if (floorLength > 0f) {
            canvas.save()
            canvas.translate(0f, cH)
            canvas.skew(-floorSkew / 100f * 1.5f, 0f)
            canvas.scale(1f, floorLength / 100f)
            canvas.translate(0f, -cH)
            pass(floorColor)
            if (floorBlur > 0.5f) paint.maskFilter = BlurMaskFilter(floorBlur, BlurMaskFilter.Blur.NORMAL)
            drawContent(canvas)
            canvas.restore()
        }

        // 3D text (extrusion)
        if (d3On && depth > 0.5f) {
            val a = Math.toRadians(depthAngle.toDouble())
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            val steps = depth.toInt().coerceIn(1, 90)
            val stepLen = depth / steps
            // perspective: the far end gets smaller, as if going away to a point
            val shrink = if (d3Persp) (depth / max(max(cW, cH), 1f) * 1.2f).coerceIn(0f, 0.6f) else 0f
            val base = when (depthMode) {
                1 -> lerpColor(frontColor(), Color.BLACK, 0.25f)
                else -> depthColor
            }
            val side = sideLight()
            for (i in steps downTo 1) {
                val t = i / steps.toFloat()
                val d = i * stepLen
                canvas.save()
                canvas.translate(dx * d, dy * d)
                if (shrink > 0f) {
                    val k = 1f - shrink * t
                    canvas.scale(k, k, cW / 2f, cH / 2f)
                }
                var c = if (depthMode == 2) lerpColor(depthColor, depthColor2, t) else base
                c = lerpColor(c, Color.BLACK, depthDarken / 100f * t)
                c = shade(c, side)
                pass(c, if (strokeWidth > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL)
                paint.strokeWidth = strokeWidth
                paint.strokeJoin = Paint.Join.ROUND
                drawContent(canvas)
                canvas.restore()
            }
        }

        // background box
        if (hasHighlight()) {
            val hp = highlightPadding
            val rr = hp * highlightRound / 100f * 1.4f
            bgPaint.color = highlightColor
            canvas.drawRoundRect(RectF(-hp, -hp * 0.5f, cW + hp, cH + hp * 0.5f), rr, rr, bgPaint)
        }

        val wantShadow = shadowRadius > 0f || shadowDx != 0f || shadowDy != 0f
        val shadowR = max(shadowRadius, 0.5f)

        // outline
        if (strokeWidth > 0f) {
            pass(strokeColor, Paint.Style.STROKE)
            paint.strokeWidth = strokeWidth
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeMiter = 10f
            if (wantShadow) paint.setShadowLayer(shadowR, shadowDx, shadowDy, shadowColor)
            drawContent(canvas)
        }

        // fill (+ emboss, + inner shadow, + light)
        val innerOn = innerRadius > 0f || innerDx != 0f || innerDy != 0f
        val lightFront = d3On && lightOn && lightStrength > 0f
        val layered = innerOn || lightFront
        if (wantShadow && strokeWidth <= 0f && layered) {
            // the shadow is drawn under the separate layer, which then covers the text copy
            configureFill()
            paint.setShadowLayer(shadowR, shadowDx, shadowDy, shadowColor)
            drawContent(canvas)
        }
        val sc = if (layered) canvas.saveLayer(null, null) else -1
        configureFill()
        if (wantShadow && strokeWidth <= 0f && !layered) paint.setShadowLayer(shadowR, shadowDx, shadowDy, shadowColor)
        if (emboss > 0f) {
            @Suppress("DEPRECATION")
            paint.maskFilter = EmbossMaskFilter(floatArrayOf(-1f, -1f, 1f), 0.45f, 10f, max(1f, textSize * 0.03f * emboss / 50f))
        }
        drawContent(canvas, true)

        if (lightFront) {
            // light side brighter, the other side darker
            val a = Math.toRadians(lightAngle.toDouble())
            val hx = cos(a).toFloat() * cW / 2f
            val hy = sin(a).toFloat() * cH / 2f
            val k = lightStrength / 100f
            lightPaint.shader = LinearGradient(
                cW / 2f + hx, cH / 2f + hy, cW / 2f - hx, cH / 2f - hy,
                Color.argb((150 * k).toInt(), 255, 255, 255), Color.argb((140 * k).toInt(), 0, 0, 0),
                Shader.TileMode.CLAMP
            )
            lightPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            val pad = strokeWidth + 8f
            canvas.drawRect(-pad, -pad, cW + pad, cH + pad, lightPaint)
        }

        if (innerOn) {
            layerPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            val sc2 = canvas.saveLayer(null, layerPaint)
            canvas.drawColor(innerColor)
            pass(Color.BLACK)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            if (innerRadius > 0.5f) paint.maskFilter = BlurMaskFilter(innerRadius, BlurMaskFilter.Blur.NORMAL)
            canvas.save()
            canvas.translate(innerDx, innerDy)
            drawContent(canvas)
            canvas.restore()
            canvas.restoreToCount(sc2)
        }
        if (sc >= 0) canvas.restoreToCount(sc)
        pass(color)
    }
}

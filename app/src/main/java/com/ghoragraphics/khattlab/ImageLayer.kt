package com.ghoragraphics.khattlab

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/** A picture placed on the page as its own layer (sticker, logo, photo...). */
class ImageLayer(var bitmap: Bitmap, px: Float, py: Float) : Layer() {

    init {
        x = px
        y = py
    }

    var flipH = false
    var flipV = false
    var round = 0f                  // corner roundness 0..100: the "all corners" setting
    // each corner on its own (top-left, top-right, bottom-right, bottom-left), 0..100
    val radii = FloatArray(4)

    fun setAllCorners(v: Float) {
        round = v
        radii.fill(v)
    }

    private val rr = FloatArray(8)
    private val roundPath = android.graphics.Path()
    var borderWidth = 0f
    var borderColor = Color.WHITE
    // a second line around the picture at a distance (gap > 0 outside, gap < 0 inside), in page pixels
    var outlineWidth = 0f
    var outlineGap = 18f
    var outlineColor = Color.WHITE
    var outlineDashed = false
    private val outlinePath = android.graphics.Path()
    private val orr = FloatArray(8)

    var shadowRadius = 0f
    var shadowDx = 0f
    var shadowDy = 0f
    var shadowColor = 0x99000000.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var mask: Bitmap? = null
    private var maskOf: Bitmap? = null

    private fun alphaMask(): Bitmap {
        val m = mask
        if (m != null && maskOf === bitmap) return m
        val n = bitmap.extractAlpha()
        mask = n
        maskOf = bitmap
        return n
    }

    override fun copy(): ImageLayer {
        val c = ImageLayer(bitmap, x, y)
        c.copyBaseFrom(this)
        c.flipH = flipH; c.flipV = flipV; c.round = round
        radii.copyInto(c.radii)
        c.borderWidth = borderWidth; c.borderColor = borderColor
        c.outlineWidth = outlineWidth; c.outlineGap = outlineGap; c.outlineColor = outlineColor; c.outlineDashed = outlineDashed
        c.shadowRadius = shadowRadius; c.shadowDx = shadowDx; c.shadowDy = shadowDy; c.shadowColor = shadowColor
        return c
    }

    /** Settings of this picture for a project; the picture itself is saved as a file. */
    fun toJson(file: String): org.json.JSONObject = org.json.JSONObject().apply {
        put("type", "image"); put("file", file)
        put("flipH", flipH); put("flipV", flipV); put("round", round.toDouble())
        put("radii", org.json.JSONArray().apply { radii.forEach { put(it.toDouble()) } })
        put("borderWidth", borderWidth.toDouble()); put("borderColor", borderColor)
        put("outlineWidth", outlineWidth.toDouble()); put("outlineGap", outlineGap.toDouble())
        put("outlineColor", outlineColor); put("outlineDashed", outlineDashed)
        put("shadowRadius", shadowRadius.toDouble()); put("shadowDx", shadowDx.toDouble()); put("shadowDy", shadowDy.toDouble())
        put("shadowColor", shadowColor)
        putBase(this)
    }

    companion object {
        fun fromJson(j: org.json.JSONObject, b: Bitmap): ImageLayer {
            val l = ImageLayer(b, 0f, 0f)
            fun f(k: String, d: Float) = j.optDouble(k, d.toDouble()).toFloat()
            l.flipH = j.optBoolean("flipH"); l.flipV = j.optBoolean("flipV"); l.round = f("round", 0f)
            val ra = j.optJSONArray("radii")
            for (i in 0 until 4) l.radii[i] = ra?.optDouble(i, l.round.toDouble())?.toFloat() ?: l.round
            l.borderWidth = f("borderWidth", 0f); l.borderColor = j.optInt("borderColor", Color.WHITE)
            l.outlineWidth = f("outlineWidth", 0f); l.outlineGap = f("outlineGap", 18f)
            l.outlineColor = j.optInt("outlineColor", Color.WHITE); l.outlineDashed = j.optBoolean("outlineDashed", false)
            l.shadowRadius = f("shadowRadius", 0f); l.shadowDx = f("shadowDx", 0f); l.shadowDy = f("shadowDy", 0f)
            l.shadowColor = j.optInt("shadowColor", 0x99000000.toInt())
            l.readBase(j)
            return l
        }
    }

    override fun rescale(k: Float) {
        super.rescale(k)
        scale *= k
    }

    override fun prepare(fm: FontManager) {
        cW = bitmap.width.toFloat()
        cH = bitmap.height.toFloat()
    }

    override fun padding(): Float {
        val out = if (outlineWidth > 0f) max(0f, outlineGap + outlineWidth / 2f) else 0f
        return max(borderWidth / 2f, out) / max(scale, 0.01f) + 4f
    }

    override fun drawLocal(canvas: Canvas, fm: FontManager) {
        prepare(fm)
        if (bitmap.isRecycled) return
        rect.set(0f, 0f, cW, cH)
        // the canvas is flipped below, so a corner the user sees comes from the mirrored corner
        val maxR = min(cW, cH) / 2f
        var anyRound = false
        for (i in 0 until 4) {
            var k = i
            if (flipH) k = intArrayOf(1, 0, 3, 2)[k]
            if (flipV) k = intArrayOf(3, 2, 1, 0)[k]
            val r = maxR * radii[k].coerceIn(0f, 100f) / 100f
            if (r > 0f) anyRound = true
            rr[2 * i] = r; rr[2 * i + 1] = r
        }
        roundPath.reset()
        roundPath.addRoundRect(rect, rr, android.graphics.Path.Direction.CW)
        // border and shadow are set in screen size, so divide by this layer's scale
        val inv = 1f / max(scale, 0.01f)

        canvas.save()
        if (flipH || flipV) canvas.scale(if (flipH) -1f else 1f, if (flipV) -1f else 1f, cW / 2f, cH / 2f)

        val wantShadow = shadowRadius > 0f || shadowDx != 0f || shadowDy != 0f
        if (wantShadow) {
            // a solid copy of the picture's shape casts the shadow; the picture covers it
            shapePaint.color = shadowColor
            shapePaint.setShadowLayer(max(shadowRadius, 0.5f) * inv, shadowDx * inv, shadowDy * inv, shadowColor)
            if (bitmap.hasAlpha() && !anyRound) {
                canvas.drawBitmap(alphaMask(), null, rect, shapePaint)
            } else {
                canvas.drawPath(roundPath, shapePaint)
            }
        }

        paint.color = Color.BLACK
        if (anyRound) {
            paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            canvas.drawPath(roundPath, paint)
            paint.shader = null
        } else {
            canvas.drawBitmap(bitmap, null, rect, paint)
        }

        if (borderWidth > 0f) {
            borderPaint.style = Paint.Style.STROKE
            borderPaint.color = borderColor
            borderPaint.strokeWidth = borderWidth * inv
            canvas.drawPath(roundPath, borderPaint)
        }

        if (outlineWidth > 0f) {
            val g = max(outlineGap * inv, -min(cW, cH) / 2f + 1f)
            for (i in 0 until 8) orr[i] = if (rr[i] > 0f) max(0f, rr[i] + g) else 0f
            outlinePath.reset()
            outlinePath.addRoundRect(RectF(-g, -g, cW + g, cH + g), orr, android.graphics.Path.Direction.CW)
            borderPaint.style = Paint.Style.STROKE
            borderPaint.color = outlineColor
            borderPaint.strokeWidth = outlineWidth * inv
            borderPaint.strokeCap = Paint.Cap.ROUND
            if (outlineDashed) borderPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(outlineWidth * inv * 2.5f, outlineWidth * inv * 1.8f), 0f)
            canvas.drawPath(outlinePath, borderPaint)
            borderPaint.pathEffect = null
        }
        canvas.restore()
    }
}

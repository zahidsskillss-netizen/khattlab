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
    var round = 0f                  // corner roundness 0..100
    var borderWidth = 0f
    var borderColor = Color.WHITE
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
        c.borderWidth = borderWidth; c.borderColor = borderColor
        c.shadowRadius = shadowRadius; c.shadowDx = shadowDx; c.shadowDy = shadowDy; c.shadowColor = shadowColor
        return c
    }

    /** Settings of this picture for a project; the picture itself is saved as a file. */
    fun toJson(file: String): org.json.JSONObject = org.json.JSONObject().apply {
        put("type", "image"); put("file", file)
        put("flipH", flipH); put("flipV", flipV); put("round", round.toDouble())
        put("borderWidth", borderWidth.toDouble()); put("borderColor", borderColor)
        put("shadowRadius", shadowRadius.toDouble()); put("shadowDx", shadowDx.toDouble()); put("shadowDy", shadowDy.toDouble())
        put("shadowColor", shadowColor)
        putBase(this)
    }

    companion object {
        fun fromJson(j: org.json.JSONObject, b: Bitmap): ImageLayer {
            val l = ImageLayer(b, 0f, 0f)
            fun f(k: String, d: Float) = j.optDouble(k, d.toDouble()).toFloat()
            l.flipH = j.optBoolean("flipH"); l.flipV = j.optBoolean("flipV"); l.round = f("round", 0f)
            l.borderWidth = f("borderWidth", 0f); l.borderColor = j.optInt("borderColor", Color.WHITE)
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

    override fun padding(): Float = borderWidth / 2f / max(scale, 0.01f) + 4f

    override fun drawLocal(canvas: Canvas, fm: FontManager) {
        prepare(fm)
        if (bitmap.isRecycled) return
        rect.set(0f, 0f, cW, cH)
        val r = min(cW, cH) / 2f * round / 100f
        // border and shadow are set in screen size, so divide by this layer's scale
        val inv = 1f / max(scale, 0.01f)

        canvas.save()
        if (flipH || flipV) canvas.scale(if (flipH) -1f else 1f, if (flipV) -1f else 1f, cW / 2f, cH / 2f)

        val wantShadow = shadowRadius > 0f || shadowDx != 0f || shadowDy != 0f
        if (wantShadow) {
            // a solid copy of the picture's shape casts the shadow; the picture covers it
            shapePaint.color = shadowColor
            shapePaint.setShadowLayer(max(shadowRadius, 0.5f) * inv, shadowDx * inv, shadowDy * inv, shadowColor)
            if (bitmap.hasAlpha() && r <= 0f) {
                canvas.drawBitmap(alphaMask(), null, rect, shapePaint)
            } else {
                canvas.drawRoundRect(rect, r, r, shapePaint)
            }
        }

        paint.color = Color.BLACK
        if (r > 0f) {
            paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, r, r, paint)
            paint.shader = null
        } else {
            canvas.drawBitmap(bitmap, null, rect, paint)
        }

        if (borderWidth > 0f) {
            borderPaint.style = Paint.Style.STROKE
            borderPaint.color = borderColor
            borderPaint.strokeWidth = borderWidth * inv
            canvas.drawRoundRect(rect, r, r, borderPaint)
        }
        canvas.restore()
    }
}

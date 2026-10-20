package com.ghoragraphics.khattlab

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** One piece of text on the canvas. All sizes are in document pixels. */
class TextLayer(var text: String, var x: Float, var y: Float) {

    var textSize = 90f
    var color = Color.WHITE
    var fontKey = FontManager.DEFAULT_KEY
    var strokeWidth = 0f
    var strokeColor = Color.BLACK
    var shadowRadius = 0f
    var shadowDx = 0f
    var shadowDy = 0f
    var shadowColor = 0x99000000.toInt()
    var opacity = 255
    var rotation = 0f
    var scale = 1f
    /** 0 = start, 1 = center, 2 = end (start is the right side for Pashto). */
    var align = 1
    var letterSpacing = 0f
    var lineSpacing = 1f
    var highlightColor = Color.TRANSPARENT
    var highlightPadding = 24f

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cached: StaticLayout? = null
    private var cacheKey = ""

    fun copy(): TextLayer {
        val c = TextLayer(text, x, y)
        c.textSize = textSize
        c.color = color
        c.fontKey = fontKey
        c.strokeWidth = strokeWidth
        c.strokeColor = strokeColor
        c.shadowRadius = shadowRadius
        c.shadowDx = shadowDx
        c.shadowDy = shadowDy
        c.shadowColor = shadowColor
        c.opacity = opacity
        c.rotation = rotation
        c.scale = scale
        c.align = align
        c.letterSpacing = letterSpacing
        c.lineSpacing = lineSpacing
        c.highlightColor = highlightColor
        c.highlightPadding = highlightPadding
        return c
    }

    fun layout(fm: FontManager): StaticLayout {
        val key = "$text|$textSize|$fontKey|$letterSpacing|$lineSpacing|$align|${fm.version}"
        val old = cached
        if (old != null && key == cacheKey) return old

        paint.textSize = textSize
        paint.typeface = fm.typeface(fontKey)
        paint.letterSpacing = letterSpacing
        paint.style = Paint.Style.FILL

        var w = 0f
        for (line in text.split('\n')) {
            w = max(w, Layout.getDesiredWidth(line, paint))
        }
        val width = ceil(w).toInt().coerceAtLeast(1) + 4
        val alignment = when (align) {
            0 -> Layout.Alignment.ALIGN_NORMAL
            2 -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }
        val built = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(true)
            .build()
        cached = built
        cacheKey = key
        return built
    }

    private fun hasHighlight() = Color.alpha(highlightColor) > 0

    /** Extra space around the text box taken by stroke and highlight. */
    fun padding(): Float {
        var p = strokeWidth / 2f + 6f
        if (hasHighlight()) p += highlightPadding
        return p
    }

    fun draw(canvas: Canvas, fm: FontManager) {
        val l = layout(fm)
        val w = l.width.toFloat()
        val h = l.height.toFloat()

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        canvas.scale(scale, scale)
        canvas.translate(-w / 2f, -h / 2f)

        if (opacity < 255) {
            val ext = padding() + shadowRadius * 2f + max(abs(shadowDx), abs(shadowDy)) + 8f
            canvas.saveLayerAlpha(-ext, -ext, w + ext, h + ext, opacity)
        }

        if (hasHighlight()) {
            val hp = highlightPadding
            bgPaint.color = highlightColor
            canvas.drawRoundRect(RectF(-hp, -hp * 0.5f, w + hp, h + hp * 0.5f), hp * 0.6f, hp * 0.6f, bgPaint)
        }

        val wantShadow = shadowRadius > 0f || shadowDx != 0f || shadowDy != 0f
        val shadowR = max(shadowRadius, 0.5f)

        if (strokeWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeMiter = 10f
            paint.color = strokeColor
            if (wantShadow) paint.setShadowLayer(shadowR, shadowDx, shadowDy, shadowColor) else paint.clearShadowLayer()
            l.draw(canvas)
            paint.clearShadowLayer()
        }

        paint.style = Paint.Style.FILL
        paint.color = color
        if (wantShadow && strokeWidth <= 0f) paint.setShadowLayer(shadowR, shadowDx, shadowDy, shadowColor) else paint.clearShadowLayer()
        l.draw(canvas)
        paint.clearShadowLayer()

        if (opacity < 255) canvas.restore()
        canvas.restore()
    }

    /** Is the document point (px, py) on this layer? */
    fun contains(px: Float, py: Float, fm: FontManager, slop: Float): Boolean {
        val l = layout(fm)
        val dx = px - x
        val dy = py - y
        val r = Math.toRadians(-rotation.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val lx = (dx * c - dy * s) / scale + l.width / 2f
        val ly = (dx * s + dy * c) / scale + l.height / 2f
        val pad = padding() + slop / scale
        return lx >= -pad && lx <= l.width + pad && ly >= -pad && ly <= l.height + pad
    }

    /** Fills [out] with the 4 corners of the selection box, in document coordinates. */
    fun corners(fm: FontManager, out: FloatArray) {
        val l = layout(fm)
        val w = l.width.toFloat()
        val h = l.height.toFloat()
        val p = padding()
        val m = Matrix()
        m.setTranslate(-w / 2f, -h / 2f)
        m.postScale(scale, scale)
        m.postRotate(rotation)
        m.postTranslate(x, y)
        val pts = floatArrayOf(-p, -p, w + p, -p, w + p, h + p, -p, h + p)
        m.mapPoints(out, pts)
    }

    /** Used when the canvas size changes, so the design keeps its proportions. */
    fun rescale(k: Float) {
        textSize *= k
        strokeWidth *= k
        shadowRadius *= k
        shadowDx *= k
        shadowDy *= k
        highlightPadding *= k
    }
}

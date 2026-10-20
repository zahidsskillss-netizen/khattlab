package com.ghoragraphics.khattlab

import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

/**
 * Anything placed on the page (text or picture).
 * Content is drawn in its own box (0..cW, 0..cH); [buildMatrix] places that box on the page
 * with position, size, rotation, perspective and 3D rotation.
 */
abstract class Layer {

    var x = 0f
    var y = 0f
    var rotation = 0f
    var scale = 1f
    var opacity = 255

    // perspective (percent, -80..80) and 3D rotation (degrees)
    var perspH = 0f
    var perspV = 0f
    var rotX = 0f
    var rotY = 0f

    // reflection under the layer
    var reflectSize = 0f   // 0..100 (% of height), 0 = off
    var reflectGap = 0f
    var reflectAlpha = 130 // 0..255

    protected var cW = 1f
    protected var cH = 1f

    private val m = Matrix()
    private val inv = Matrix()
    private val cam = Camera()
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Measure the content and set cW / cH. */
    protected abstract fun prepare(fm: FontManager)

    /** Extra space around the content box (stroke, background...). */
    abstract fun padding(): Float

    /** Draw the content inside its box (0..cW, 0..cH). */
    protected abstract fun drawLocal(canvas: Canvas, fm: FontManager)

    abstract fun copy(): Layer

    /** Scale every size in pixels (used when the page size changes). */
    open fun rescale(k: Float) {
        reflectGap *= k
    }

    protected fun copyBaseFrom(o: Layer) {
        x = o.x; y = o.y; rotation = o.rotation; scale = o.scale; opacity = o.opacity
        perspH = o.perspH; perspV = o.perspV; rotX = o.rotX; rotY = o.rotY
        reflectSize = o.reflectSize; reflectGap = o.reflectGap; reflectAlpha = o.reflectAlpha
    }

    private fun buildMatrix(out: Matrix) {
        out.reset()
        out.setTranslate(-cW / 2f, -cH / 2f)
        if (perspH != 0f || perspV != 0f) {
            val hw = cW / 2f
            val hh = cH / 2f
            val src = floatArrayOf(-hw, -hh, hw, -hh, hw, hh, -hw, hh)
            val dst = src.copyOf()
            val v = perspV / 100f
            if (v > 0f) { dst[0] *= 1f - v; dst[2] *= 1f - v } else { dst[4] *= 1f + v; dst[6] *= 1f + v }
            val h = perspH / 100f
            if (h > 0f) { dst[3] *= 1f - h; dst[5] *= 1f - h } else { dst[1] *= 1f + h; dst[7] *= 1f + h }
            val p = Matrix()
            p.setPolyToPoly(src, 0, dst, 0, 4)
            out.postConcat(p)
        }
        if (rotX != 0f || rotY != 0f) {
            cam.save()
            cam.setLocation(0f, 0f, -max(8f, max(cW, cH) / 72f * 2.5f))
            cam.rotateX(rotX)
            cam.rotateY(rotY)
            val c = Matrix()
            cam.getMatrix(c)
            cam.restore()
            out.postConcat(c)
        }
        out.postScale(scale, scale)
        out.postRotate(rotation)
        out.postTranslate(x, y)
    }

    fun draw(canvas: Canvas, fm: FontManager) {
        prepare(fm)
        buildMatrix(m)
        canvas.save()
        canvas.concat(m)
        if (opacity < 255) canvas.saveLayerAlpha(null, opacity)

        if (reflectSize > 0f) {
            val top = cH + reflectGap
            val len = max(1f, cH * reflectSize / 100f)
            val sc = canvas.saveLayer(null, null)
            canvas.save()
            canvas.translate(0f, 2f * cH + reflectGap)
            canvas.scale(1f, -1f)
            drawLocal(canvas, fm)
            canvas.restore()
            fadePaint.shader = LinearGradient(
                0f, top, 0f, top + len,
                Color.argb(reflectAlpha, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            fadePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect(-cW * 2f, top - cH, cW * 3f, top + cH * 2f, fadePaint)
            canvas.restoreToCount(sc)
        }

        drawLocal(canvas, fm)

        if (opacity < 255) canvas.restore()
        canvas.restore()
    }

    /** Is the page point (px, py) on this layer? */
    fun contains(px: Float, py: Float, fm: FontManager, slop: Float): Boolean {
        prepare(fm)
        buildMatrix(m)
        if (!m.invert(inv)) return false
        val p = floatArrayOf(px, py)
        inv.mapPoints(p)
        val pad = padding() + slop / max(scale, 0.01f)
        return p[0] >= -pad && p[0] <= cW + pad && p[1] >= -pad && p[1] <= cH + pad
    }

    /** The 4 corners of the selection box on the page: top-left, top-right, bottom-right, bottom-left. */
    fun corners(fm: FontManager, out: FloatArray) {
        prepare(fm)
        buildMatrix(m)
        val p = padding()
        val pts = floatArrayOf(-p, -p, cW + p, -p, cW + p, cH + p, -p, cH + p)
        m.mapPoints(out, pts)
    }

    /** Box of the layer on the page (for aligning to the page edges). */
    fun pageBounds(fm: FontManager): RectF {
        val c = FloatArray(8)
        corners(fm, c)
        val r = RectF(c[0], c[1], c[0], c[1])
        for (i in 1 until 4) r.union(c[2 * i], c[2 * i + 1])
        return r
    }
}

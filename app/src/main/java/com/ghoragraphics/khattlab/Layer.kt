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
import kotlin.math.abs
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

    // free perspective: how far each corner of the box is pulled by finger
    // (top-left, top-right, bottom-right, bottom-left; x, y), in parts of the box width / height
    val warp = FloatArray(8)
    fun hasWarp() = warp.any { it != 0f }

    // reflection under the layer
    var reflectSize = 0f   // 0..100 (% of height), 0 = off
    var reflectGap = 0f
    var reflectAlpha = 130 // 0..255

    // layers list
    var locked = false     // can't be moved or picked on the page
    var hidden = false     // not drawn, not saved in the picture

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
        o.warp.copyInto(warp)
        reflectSize = o.reflectSize; reflectGap = o.reflectGap; reflectAlpha = o.reflectAlpha
        locked = o.locked; hidden = o.hidden
    }

    /** Place, size and shared effects, for saving a project. */
    fun putBase(j: org.json.JSONObject) {
        j.put("x", x.toDouble()); j.put("y", y.toDouble()); j.put("rotation", rotation.toDouble()); j.put("scale", scale.toDouble())
        j.put("opacity", opacity)
        j.put("perspH", perspH.toDouble()); j.put("perspV", perspV.toDouble()); j.put("rotX", rotX.toDouble()); j.put("rotY", rotY.toDouble())
        if (hasWarp()) j.put("warp", org.json.JSONArray().apply { warp.forEach { put(it.toDouble()) } })
        j.put("reflectSize", reflectSize.toDouble()); j.put("reflectGap", reflectGap.toDouble()); j.put("reflectAlpha", reflectAlpha)
        j.put("locked", locked); j.put("hidden", hidden)
    }

    fun readBase(j: org.json.JSONObject) {
        fun f(k: String, d: Float) = j.optDouble(k, d.toDouble()).toFloat()
        x = f("x", 0f); y = f("y", 0f); rotation = f("rotation", 0f); scale = f("scale", 1f)
        opacity = j.optInt("opacity", 255)
        perspH = f("perspH", 0f); perspV = f("perspV", 0f); rotX = f("rotX", 0f); rotY = f("rotY", 0f)
        warp.fill(0f)
        j.optJSONArray("warp")?.let { a -> for (i in 0 until minOf(8, a.length())) warp[i] = a.optDouble(i, 0.0).toFloat() }
        reflectSize = f("reflectSize", 0f); reflectGap = f("reflectGap", 0f); reflectAlpha = j.optInt("reflectAlpha", 130)
        locked = j.optBoolean("locked", false); hidden = j.optBoolean("hidden", false)
    }

    /** Where the 4 box corners go (box centered on 0,0), from the sliders and, if [withWarp], the dragged corners. */
    private fun perspDst(out: FloatArray, withWarp: Boolean) {
        val hw = cW / 2f
        val hh = cH / 2f
        out[0] = -hw; out[1] = -hh; out[2] = hw; out[3] = -hh; out[4] = hw; out[5] = hh; out[6] = -hw; out[7] = hh
        val v = perspV / 100f
        if (v > 0f) { out[0] *= 1f - v; out[2] *= 1f - v } else if (v < 0f) { out[4] *= 1f + v; out[6] *= 1f + v }
        val h = perspH / 100f
        if (h > 0f) { out[3] *= 1f - h; out[5] *= 1f - h } else if (h < 0f) { out[1] *= 1f + h; out[7] *= 1f + h }
        if (withWarp) for (i in 0 until 4) {
            out[2 * i] += warp[2 * i] * cW
            out[2 * i + 1] += warp[2 * i + 1] * cH
        }
    }

    /** 3D turn, size, rotation and place: everything that comes after the perspective. */
    private fun buildPost(out: Matrix) {
        out.reset()
        if (rotX != 0f || rotY != 0f) {
            cam.save()
            cam.setLocation(0f, 0f, -max(8f, max(cW, cH) / 72f * 2.5f))
            cam.rotateX(rotX)
            cam.rotateY(rotY)
            cam.getMatrix(out)
            cam.restore()
        }
        out.postScale(scale, scale)
        out.postRotate(rotation)
        out.postTranslate(x, y)
    }

    private val post = Matrix()
    private val pm = Matrix()
    private val srcPts = FloatArray(8)
    private val dstPts = FloatArray(8)

    private fun buildMatrix(out: Matrix) {
        out.reset()
        out.setTranslate(-cW / 2f, -cH / 2f)
        if (perspH != 0f || perspV != 0f || hasWarp()) {
            val hw = cW / 2f
            val hh = cH / 2f
            srcPts[0] = -hw; srcPts[1] = -hh; srcPts[2] = hw; srcPts[3] = -hh
            srcPts[4] = hw; srcPts[5] = hh; srcPts[6] = -hw; srcPts[7] = hh
            perspDst(dstPts, true)
            if (pm.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) out.postConcat(pm)
        }
        buildPost(post)
        out.postConcat(post)
    }

    /** Page positions of the 4 corners of the content (no padding): top-left, top-right, bottom-right, bottom-left. */
    fun contentCorners(fm: FontManager, out: FloatArray) {
        prepare(fm)
        buildMatrix(m)
        val pts = floatArrayOf(0f, 0f, cW, 0f, cW, cH, 0f, cH)
        m.mapPoints(out, pts)
    }

    /**
     * Move content corner [i] (0..3) to the page point (px, py). Returns false (and changes nothing)
     * when the shape would fold over itself.
     */
    fun moveCorner(fm: FontManager, i: Int, px: Float, py: Float): Boolean {
        prepare(fm)
        buildPost(post)
        if (!post.invert(inv)) return false
        val q = floatArrayOf(px, py)
        inv.mapPoints(q)
        val base = FloatArray(8)
        perspDst(base, false)
        val ox = warp[2 * i]
        val oy = warp[2 * i + 1]
        warp[2 * i] = (q[0] - base[2 * i]) / cW
        warp[2 * i + 1] = (q[1] - base[2 * i + 1]) / cH
        perspDst(base, true)
        if (!convex(base)) {
            warp[2 * i] = ox
            warp[2 * i + 1] = oy
            return false
        }
        return true
    }

    /** True when the 4 points make a quad that does not cross or fold. */
    private fun convex(p: FloatArray): Boolean {
        var sign = 0
        for (k in 0 until 4) {
            val ax = p[2 * ((k + 1) % 4)] - p[2 * k]
            val ay = p[2 * ((k + 1) % 4) + 1] - p[2 * k + 1]
            val bx = p[2 * ((k + 2) % 4)] - p[2 * ((k + 1) % 4)]
            val by = p[2 * ((k + 2) % 4) + 1] - p[2 * ((k + 1) % 4) + 1]
            val c = ax * by - ay * bx
            if (abs(c) < 1e-3f) return false
            val sg = if (c > 0f) 1 else -1
            if (sign == 0) sign = sg else if (sg != sign) return false
        }
        return true
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

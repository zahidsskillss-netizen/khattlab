package com.ghoragraphics.khattlab

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A ready-made shape (square, circle, star, heart, arrow...). Sizes are in page pixels. */
class ShapeLayer(var kind: Int, px: Float, py: Float) : Layer() {

    companion object {
        const val RECT = 0
        const val ROUND_RECT = 1
        const val ELLIPSE = 2
        const val TRIANGLE = 3
        const val RIGHT_TRIANGLE = 4
        const val DIAMOND = 5
        const val POLYGON = 6
        const val STAR = 7
        const val HEART = 8
        const val ARROW = 9
        const val LINE = 10
        const val LINE_ARROW = 11
        const val DOUBLE_ARROW = 12
        const val BUBBLE = 13
        const val CROSS = 14
        const val RING = 15
        const val HALF_CIRCLE = 16
        const val BURST = 17
        const val COUNT = 18

        fun isLine(k: Int) = k == LINE || k == LINE_ARROW || k == DOUBLE_ARROW

        fun fromJson(j: JSONObject, image: Bitmap? = null): ShapeLayer {
            fun f(k: String, d: Float) = j.optDouble(k, d.toDouble()).toFloat()
            val l = ShapeLayer(j.optInt("kind", RECT), 0f, 0f)
            l.w = f("w", 400f); l.h = f("h", 400f)
            l.fillMode = j.optInt("fillMode", 0); l.fillColor = j.optInt("fillColor", l.fillColor)
            l.gradColor1 = j.optInt("grad1", l.gradColor1); l.gradColor2 = j.optInt("grad2", l.gradColor2); l.gradAngle = f("gradAngle", 90f)
            l.strokeWidth = f("strokeWidth", 0f); l.strokeColor = j.optInt("strokeColor", l.strokeColor)
            l.dashed = j.optBoolean("dashed", false)
            l.corner = f("corner", 20f)
            val ra = j.optJSONArray("radii")
            for (i in 0 until 4) l.radii[i] = ra?.optDouble(i, l.corner.toDouble())?.toFloat() ?: l.corner
            l.points = j.optInt("points", 5); l.inner = f("inner", 45f)
            l.shadowRadius = f("shadowRadius", 0f); l.shadowDx = f("shadowDx", 0f); l.shadowDy = f("shadowDy", 0f)
            l.shadowColor = j.optInt("shadowColor", l.shadowColor)
            l.outlineWidth = f("outlineWidth", 0f); l.outlineGap = f("outlineGap", 18f)
            l.outlineColor = j.optInt("outlineColor", Color.WHITE); l.outlineDashed = j.optBoolean("outlineDashed", false)
            l.image = image
            l.imgScale = f("imgScale", 1f); l.imgX = f("imgX", 0f); l.imgY = f("imgY", 0f)
            l.readBase(j)
            return l
        }
    }

    init {
        x = px
        y = py
    }

    var w = 400f
    var h = 400f

    var fillMode = 0                 // 0 color, 1 gradient, 2 none
    var fillColor = 0xFFE8B547.toInt()
    var gradColor1 = 0xFFFFEB3B.toInt()
    var gradColor2 = 0xFFF44336.toInt()
    var gradAngle = 90f

    var strokeWidth = 0f
    var strokeColor = Color.BLACK
    var dashed = false

    var corner = 20f                 // 0..100 (rounded square, bubble): the "all corners" setting
    // each corner on its own (top-left, top-right, bottom-right, bottom-left), 0..100
    val radii = floatArrayOf(20f, 20f, 20f, 20f)

    /** Sets every corner at once (the "all corners" slider). */
    fun setAllCorners(v: Float) {
        corner = v
        radii.fill(v)
    }

    private val rr = FloatArray(8)

    /** Adds a rectangle whose corners are rounded one by one. */
    private fun roundRect(W: Float, H: Float) {
        val maxR = min(W, H) / 2f
        for (i in 0 until 4) {
            val r = maxR * radii[i].coerceIn(0f, 100f) / 100f
            rr[2 * i] = r; rr[2 * i + 1] = r
        }
        path.addRoundRect(RectF(0f, 0f, W, H), rr, Path.Direction.CW)
    }
    var points = 5                   // star / polygon / burst
    var inner = 45f                  // 10..90, inner size of star / ring

    // a second line that follows the shape at a distance (gap > 0 outside, gap < 0 inside)
    var outlineWidth = 0f
    var outlineGap = 18f
    var outlineColor = Color.WHITE
    var outlineDashed = false

    var shadowRadius = 0f
    var shadowDx = 0f
    var shadowDy = 0f
    var shadowColor = 0x88000000.toInt()

    // a picture inside the shape (it fills the shape like a frame)
    var image: Bitmap? = null
    var imgScale = 1f                // 1 = just covers the shape
    var imgX = 0f                    // move inside the shape, in parts of the shape size
    var imgY = 0f

    fun hasImage() = image?.isRecycled == false && !isLine(kind)

    private val imgMatrix = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val head = Path()

    override fun copy(): ShapeLayer {
        val c = ShapeLayer(kind, x, y)
        c.copyBaseFrom(this)
        c.w = w; c.h = h
        c.fillMode = fillMode; c.fillColor = fillColor; c.gradColor1 = gradColor1; c.gradColor2 = gradColor2; c.gradAngle = gradAngle
        c.strokeWidth = strokeWidth; c.strokeColor = strokeColor; c.dashed = dashed
        c.corner = corner; c.points = points; c.inner = inner
        radii.copyInto(c.radii)
        c.outlineWidth = outlineWidth; c.outlineGap = outlineGap; c.outlineColor = outlineColor; c.outlineDashed = outlineDashed
        c.shadowRadius = shadowRadius; c.shadowDx = shadowDx; c.shadowDy = shadowDy; c.shadowColor = shadowColor
        c.image = image; c.imgScale = imgScale; c.imgX = imgX; c.imgY = imgY
        return c
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", "shape"); put("kind", kind)
        put("w", w.toDouble()); put("h", h.toDouble())
        put("fillMode", fillMode); put("fillColor", fillColor)
        put("grad1", gradColor1); put("grad2", gradColor2); put("gradAngle", gradAngle.toDouble())
        put("strokeWidth", strokeWidth.toDouble()); put("strokeColor", strokeColor); put("dashed", dashed)
        put("radii", org.json.JSONArray().apply { radii.forEach { put(it.toDouble()) } })
        put("corner", corner.toDouble()); put("points", points); put("inner", inner.toDouble())
        put("shadowRadius", shadowRadius.toDouble()); put("shadowDx", shadowDx.toDouble()); put("shadowDy", shadowDy.toDouble())
        put("shadowColor", shadowColor)
        put("outlineWidth", outlineWidth.toDouble()); put("outlineGap", outlineGap.toDouble())
        put("outlineColor", outlineColor); put("outlineDashed", outlineDashed)
        put("imgScale", imgScale.toDouble()); put("imgX", imgX.toDouble()); put("imgY", imgY.toDouble())
        putBase(this)
    }

    override fun rescale(k: Float) {
        super.rescale(k)
        w *= k; h *= k
        strokeWidth *= k
        outlineWidth *= k; outlineGap *= k
        shadowRadius *= k; shadowDx *= k; shadowDy *= k
    }

    override fun prepare(fm: FontManager) {
        cW = max(w, 4f)
        cH = max(h, 4f)
    }

    override fun padding(): Float {
        val out = if (outlineWidth > 0f && !isLine(kind)) max(0f, outlineGap + outlineWidth / 2f) else 0f
        return max(strokeWidth / 2f, out) + 6f
    }

    private val outlinePath = Path()
    private val outlineMatrix = Matrix()

    /** The shape's outline moved [g] pixels out (or in, when negative). */
    private fun buildOutline(g0: Float) {
        outlinePath.reset()
        val g = max(g0, -min(cW, cH) / 2f + 1f)
        if (kind == RECT || kind == ROUND_RECT) {
            val maxR = min(cW, cH) / 2f
            for (i in 0 until 4) {
                val r0 = if (kind == RECT) 0f else maxR * radii[i].coerceIn(0f, 100f) / 100f
                val r = if (r0 > 0f) max(0f, r0 + g) else 0f
                rr[2 * i] = r; rr[2 * i + 1] = r
            }
            outlinePath.addRoundRect(RectF(-g, -g, cW + g, cH + g), rr, Path.Direction.CW)
            return
        }
        // other shapes: grow the outline around the middle so its box is g bigger on every side
        outlineMatrix.setScale((cW + 2f * g) / cW, (cH + 2f * g) / cH, cW / 2f, cH / 2f)
        path.transform(outlineMatrix, outlinePath)
    }

    /** Builds the outline of the shape inside 0..cW, 0..cH. */
    private fun buildPath() {
        path.reset()
        val W = cW
        val H = cH
        when (kind) {
            RECT -> path.addRect(0f, 0f, W, H, Path.Direction.CW)
            ROUND_RECT -> roundRect(W, H)
            ELLIPSE -> path.addOval(RectF(0f, 0f, W, H), Path.Direction.CW)
            TRIANGLE -> { path.moveTo(W / 2f, 0f); path.lineTo(W, H); path.lineTo(0f, H); path.close() }
            RIGHT_TRIANGLE -> { path.moveTo(0f, 0f); path.lineTo(W, H); path.lineTo(0f, H); path.close() }
            DIAMOND -> { path.moveTo(W / 2f, 0f); path.lineTo(W, H / 2f); path.lineTo(W / 2f, H); path.lineTo(0f, H / 2f); path.close() }
            POLYGON -> regular(points.coerceIn(3, 12), 1f, W, H)
            STAR -> star(points.coerceIn(3, 16), inner / 100f, W, H)
            BURST -> star((points * 3).coerceIn(8, 48), 0.78f, W, H)
            HEART -> {
                path.moveTo(W / 2f, H * 0.28f)
                path.cubicTo(W * 0.5f, H * 0.05f, W * 0.05f, -H * 0.02f, W * 0.02f, H * 0.32f)
                path.cubicTo(0f, H * 0.6f, W * 0.3f, H * 0.78f, W / 2f, H)
                path.cubicTo(W * 0.7f, H * 0.78f, W, H * 0.6f, W * 0.98f, H * 0.32f)
                path.cubicTo(W * 0.95f, -H * 0.02f, W * 0.5f, H * 0.05f, W / 2f, H * 0.28f)
                path.close()
            }
            ARROW -> {
                val t = H * 0.32f
                val hw = min(W * 0.45f, H)
                path.moveTo(0f, H / 2f - t / 2f); path.lineTo(W - hw, H / 2f - t / 2f); path.lineTo(W - hw, 0f)
                path.lineTo(W, H / 2f); path.lineTo(W - hw, H); path.lineTo(W - hw, H / 2f + t / 2f)
                path.lineTo(0f, H / 2f + t / 2f); path.close()
            }
            BUBBLE -> {
                val bh = H * 0.78f
                roundRect(W, bh)
                val tail = Path()
                tail.moveTo(W * 0.22f, bh - 1f); tail.lineTo(W * 0.18f, H); tail.lineTo(W * 0.42f, bh - 1f); tail.close()
                path.op(tail, Path.Op.UNION)
            }
            CROSS -> {
                val t = min(W, H) * 0.34f
                val a = Path(); a.addRect((W - t) / 2f, 0f, (W + t) / 2f, H, Path.Direction.CW)
                val b = Path(); b.addRect(0f, (H - t) / 2f, W, (H + t) / 2f, Path.Direction.CW)
                path.addPath(a); path.op(b, Path.Op.UNION)
            }
            RING -> {
                path.addOval(RectF(0f, 0f, W, H), Path.Direction.CW)
                val k = inner.coerceIn(10f, 95f) / 100f
                val ix = W * (1f - k) / 2f
                val iy = H * (1f - k) / 2f
                val hole = Path(); hole.addOval(RectF(ix, iy, W - ix, H - iy), Path.Direction.CW)
                path.op(hole, Path.Op.DIFFERENCE)
            }
            HALF_CIRCLE -> { path.arcTo(RectF(0f, 0f, W, H * 2f), 180f, 180f); path.close() }
            else -> { // lines: drawn along the middle
                path.moveTo(0f, H / 2f); path.lineTo(W, H / 2f)
            }
        }
    }

    private fun regular(n: Int, k: Float, W: Float, H: Float) {
        for (i in 0 until n) {
            val a = -PI / 2 + i * 2 * PI / n
            val px = W / 2f + (W / 2f * k * cos(a)).toFloat()
            val py = H / 2f + (H / 2f * k * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    private fun star(n: Int, innerK: Float, W: Float, H: Float) {
        for (i in 0 until n * 2) {
            val a = -PI / 2 + i * PI / n
            val k = if (i % 2 == 0) 1f else innerK.coerceIn(0.1f, 0.95f)
            val px = W / 2f + (W / 2f * k * cos(a)).toFloat()
            val py = H / 2f + (H / 2f * k * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    private fun setFill() {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        when (fillMode) {
            1 -> {
                val a = Math.toRadians(gradAngle.toDouble())
                val hx = cos(a).toFloat() * cW / 2f
                val hy = sin(a).toFloat() * cH / 2f
                paint.shader = LinearGradient(cW / 2f - hx, cH / 2f - hy, cW / 2f + hx, cH / 2f + hy, gradColor1, gradColor2, Shader.TileMode.CLAMP)
            }
            else -> paint.color = fillColor
        }
    }

    private fun lineWidth() = if (strokeWidth > 0f) strokeWidth else max(4f, min(cW, cH) * 0.25f)

    override fun drawLocal(canvas: Canvas, fm: FontManager) {
        prepare(fm)
        buildPath()
        val wantShadow = shadowRadius > 0f || shadowDx != 0f || shadowDy != 0f

        if (isLine(kind)) {
            // a line uses the fill color, so one color setting works for every shape
            val lw = lineWidth()
            val headLen = lw * 3.2f
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = lw
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = if (fillMode == 1) gradColor1 else fillColor
            if (dashed) paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(lw * 2.5f, lw * 1.8f), 0f)
            if (wantShadow) paint.setShadowLayer(max(shadowRadius, 0.5f), shadowDx, shadowDy, shadowColor)
            val y0 = cH / 2f
            val x0 = if (kind == DOUBLE_ARROW) headLen * 0.8f else 0f
            val x1 = if (kind == LINE) cW else cW - headLen * 0.8f
            canvas.drawLine(x0, y0, x1, y0, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
            if (kind != LINE) {
                head.reset()
                head.moveTo(cW, y0); head.lineTo(cW - headLen, y0 - headLen * 0.6f); head.lineTo(cW - headLen, y0 + headLen * 0.6f); head.close()
                if (kind == DOUBLE_ARROW) {
                    head.moveTo(0f, y0); head.lineTo(headLen, y0 - headLen * 0.6f); head.lineTo(headLen, y0 + headLen * 0.6f); head.close()
                }
                canvas.drawPath(head, paint)
            }
            paint.clearShadowLayer()
            return
        }

        val img = image
        if (hasImage() && img != null) {
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.style = Paint.Style.FILL
            val k = max(cW / img.width, cH / img.height) * imgScale.coerceAtLeast(0.1f)
            imgMatrix.setScale(k, k)
            imgMatrix.postTranslate(
                (cW - img.width * k) / 2f + imgX * cW,
                (cH - img.height * k) / 2f + imgY * cH
            )
            val sh = BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            sh.setLocalMatrix(imgMatrix)
            paint.shader = sh
            if (wantShadow) paint.setShadowLayer(max(shadowRadius, 0.5f), shadowDx, shadowDy, shadowColor)
            canvas.drawPath(path, paint)
            paint.clearShadowLayer()
            paint.shader = null
        } else if (fillMode != 2) {
            setFill()
            if (wantShadow) paint.setShadowLayer(max(shadowRadius, 0.5f), shadowDx, shadowDy, shadowColor)
            canvas.drawPath(path, paint)
            paint.clearShadowLayer()
        }
        if (strokeWidth > 0f) {
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = strokeColor
            if (dashed) paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(strokeWidth * 2.5f, strokeWidth * 1.8f), 0f)
            if (wantShadow && fillMode == 2) paint.setShadowLayer(max(shadowRadius, 0.5f), shadowDx, shadowDy, shadowColor)
            canvas.drawPath(path, paint)
            paint.clearShadowLayer()
            paint.pathEffect = null
        }
        if (outlineWidth > 0f) {
            buildOutline(outlineGap)
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = outlineWidth
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = outlineColor
            if (outlineDashed) paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(outlineWidth * 2.5f, outlineWidth * 1.8f), 0f)
            canvas.drawPath(outlinePath, paint)
            paint.pathEffect = null
        }
    }
}

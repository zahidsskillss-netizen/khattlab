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

    // paper effects: 0 none, 1 torn paper edges, 2 page curl (a folded corner)
    var edge = EDGE_NONE
    var tornSides = SIDE_BOTTOM   // which sides are torn (SIDE_* bits)
    var tornDepth = 40f           // 0..100: how deep the tear goes
    var tornRim = 14f             // white paper border along the tear, in page pixels
    var tornSeed = 7              // another number = another tear
    var paperColor = Color.WHITE
    var curlCorner = 2            // 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left
    var curlSize = 28f            // 5..70 (% of the shorter side)

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
        c.edge = edge; c.tornSides = tornSides; c.tornDepth = tornDepth; c.tornRim = tornRim; c.tornSeed = tornSeed
        c.paperColor = paperColor; c.curlCorner = curlCorner; c.curlSize = curlSize
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
        put("edge", edge); put("tornSides", tornSides); put("tornDepth", tornDepth.toDouble()); put("tornRim", tornRim.toDouble())
        put("tornSeed", tornSeed); put("paperColor", paperColor); put("curlCorner", curlCorner); put("curlSize", curlSize.toDouble())
        putBase(this)
    }

    companion object {
        const val EDGE_NONE = 0
        const val EDGE_TORN = 1
        const val EDGE_CURL = 2
        const val SIDE_TOP = 1
        const val SIDE_RIGHT = 2
        const val SIDE_BOTTOM = 4
        const val SIDE_LEFT = 8

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
            l.edge = j.optInt("edge", EDGE_NONE); l.tornSides = j.optInt("tornSides", SIDE_BOTTOM)
            l.tornDepth = f("tornDepth", 40f); l.tornRim = f("tornRim", 14f); l.tornSeed = j.optInt("tornSeed", 7)
            l.paperColor = j.optInt("paperColor", Color.WHITE); l.curlCorner = j.optInt("curlCorner", 2); l.curlSize = f("curlSize", 28f)
            l.readBase(j)
            return l
        }
    }

    override fun rescale(k: Float) {
        super.rescale(k)
        scale *= k
    }

    // ---------- paper effects ----------

    private val edgePath = android.graphics.Path()
    private val paperPath = android.graphics.Path()
    private val flapPath = android.graphics.Path()
    private val flipM = android.graphics.Matrix()
    private val effPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * A torn outline around the box: the flagged sides are ripped inward by [depthOf] (+ [extra]).
     * Same [seed] = same tear, so the image and its paper border follow each other.
     */
    private fun tornPath(out: android.graphics.Path, inset: Float, jag: Float, seed: Int) {
        out.reset()
        val minSide = min(cW, cH)
        val depth = minSide * 0.12f * tornDepth.coerceIn(0f, 100f) / 100f
        val step = max(minSide / 90f, 2f)
        // first the depth of the tear at every point of every side
        val offs = Array(4) { side ->
            val bit = intArrayOf(SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM, SIDE_LEFT)[side]
            val len = if (side % 2 == 0) cW else cH
            if (tornSides and bit == 0) FloatArray(0) else {
                val n = max(2, (len / step).toInt())
                val rnd = java.util.Random(tornSeed * 7919L + side * 131L + seed)
                val ph1 = rnd.nextFloat() * 6.283f
                val ph2 = rnd.nextFloat() * 6.283f
                val f1 = 2f + rnd.nextFloat() * 2f
                val f2 = 7f + rnd.nextFloat() * 5f
                val jr = java.util.Random(tornSeed * 104729L + side * 17L + seed * 3L)
                FloatArray(n + 1) { i ->
                    val t = i / n.toFloat()
                    val wave = 0.55f + 0.28f * kotlin.math.sin(t * f1 * 6.283f + ph1) + 0.12f * kotlin.math.sin(t * f2 * 6.283f + ph2)
                    (depth * wave + inset + (jr.nextFloat() - 0.5f) * 2f * jag).coerceAtLeast(0f)
                }
            }
        }
        var first = true
        fun pt(x: Float, y: Float) { if (first) { out.moveTo(x, y); first = false } else out.lineTo(x, y) }
        // sides in order: top (left -> right), right (top -> bottom), bottom (right -> left), left (bottom -> top)
        for (side in 0 until 4) {
            val o = offs[side]
            val len = if (side % 2 == 0) cW else cH
            if (o.isEmpty()) {
                // a straight side starts where the torn side before it ended, so no corner sticks out
                val prev = offs[(side + 3) % 4]
                val along = if (prev.isEmpty()) 0f else prev[prev.size - 1]
                when (side) {
                    0 -> pt(along, 0f)
                    1 -> pt(cW, along)
                    2 -> pt(cW - along, cH)
                    else -> pt(0f, cH - along)
                }
                continue
            }
            val n = o.size - 1
            for (i in 0..n) {
                val t = i / n.toFloat()
                val off = o[i]
                when (side) {
                    0 -> pt(t * len, off)
                    1 -> pt(cW - off, t * len)
                    2 -> pt(cW - t * len, cH - off)
                    else -> pt(off, cH - t * len)
                }
            }
        }
        out.close()
    }

    /** Corner of the page curl and the two points where it folds. */
    private fun curlPoints(): FloatArray {
        val s = min(cW, cH) * curlSize.coerceIn(5f, 70f) / 100f
        val kx = if (curlCorner == 1 || curlCorner == 2) cW else 0f
        val ky = if (curlCorner >= 2) cH else 0f
        val dx = if (kx > 0f) -1f else 1f
        val dy = if (ky > 0f) -1f else 1f
        // corner, fold point on the top/bottom side, fold point on the left/right side, folded corner
        return floatArrayOf(kx, ky, kx + dx * s, ky, kx, ky + dy * s, kx + dx * s, ky + dy * s)
    }

    /** The outline the picture is cut to (in what the user sees, before any flip). */
    private fun effectShape(out: android.graphics.Path) {
        when (edge) {
            EDGE_TORN -> tornPath(out, tornRim / max(scale, 0.01f), min(cW, cH) * 0.004f, 1)
            EDGE_CURL -> {
                val p = curlPoints()
                out.reset()
                out.addRect(0f, 0f, cW, cH, android.graphics.Path.Direction.CW)
                val tri = android.graphics.Path()
                tri.moveTo(p[0], p[1]); tri.lineTo(p[2], p[3]); tri.lineTo(p[4], p[5]); tri.close()
                out.op(tri, android.graphics.Path.Op.DIFFERENCE)
            }
            else -> out.reset()
        }
    }

    /** Paper under a torn picture: same tear, a little less deep, so a white fibre border shows. */
    private fun drawPaper(canvas: Canvas, inv: Float) {
        tornPath(paperPath, 0f, min(cW, cH) * 0.006f, 2)
        effPaint.reset()
        effPaint.isAntiAlias = true
        effPaint.style = Paint.Style.FILL
        effPaint.color = paperColor
        effPaint.setShadowLayer(max(3f, tornRim * 0.6f) * inv, 0f, 2f * inv, 0x66000000)
        canvas.drawPath(paperPath, effPaint)
        effPaint.clearShadowLayer()
    }

    /** The folded-over corner, with its shadow and a soft shine. */
    private fun drawFlap(canvas: Canvas, inv: Float) {
        val p = curlPoints()
        val ax = p[2]; val ay = p[3]; val bx = p[4]; val by = p[5]; val fx = p[6]; val fy = p[7]
        val mx = (ax + bx) / 2f; val my = (ay + by) / 2f
        flapPath.reset()
        flapPath.moveTo(ax, ay)
        // slightly rounded sides, like real paper
        flapPath.quadTo((ax + fx) / 2f + (mx - fx) * 0.12f, (ay + fy) / 2f + (my - fy) * 0.12f, fx, fy)
        flapPath.quadTo((bx + fx) / 2f + (mx - fx) * 0.12f, (by + fy) / 2f + (my - fy) * 0.12f, bx, by)
        flapPath.close()
        effPaint.reset()
        effPaint.isAntiAlias = true
        effPaint.style = Paint.Style.FILL
        effPaint.color = paperColor
        val size = kotlin.math.hypot(ax - bx, ay - by)
        effPaint.setShadowLayer(max(4f, size * 0.06f), (fx - mx) * 0.05f, (fy - my) * 0.05f, 0x77000000)
        canvas.drawPath(flapPath, effPaint)
        effPaint.clearShadowLayer()
        // darker at the fold, lighter at the tip
        effPaint.shader = android.graphics.LinearGradient(
            mx, my, fx, fy,
            intArrayOf(0x55000000, 0x18000000, 0x22FFFFFF), floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(flapPath, effPaint)
        effPaint.shader = null
    }

    override fun prepare(fm: FontManager) {
        cW = bitmap.width.toFloat()
        cH = bitmap.height.toFloat()
    }

    override fun padding(): Float {
        val out = if (outlineWidth > 0f && edge == EDGE_NONE) max(0f, outlineGap + outlineWidth / 2f) else 0f
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

        // paper effects: the outline is made as the user sees it, then mirrored into the flipped picture
        val fx = edge != EDGE_NONE
        if (fx) {
            effectShape(edgePath)
            flipM.reset()
            if (flipH || flipV) flipM.setScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f, cW / 2f, cH / 2f)
            if (edge == EDGE_TORN) drawPaper(canvas, inv)
            edgePath.transform(flipM)
            roundPath.set(edgePath)
            anyRound = true
        }

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

        if (outlineWidth > 0f && !fx) {
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
        if (edge == EDGE_CURL) drawFlap(canvas, inv)
    }
}

package com.ghoragraphics.khattlab

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * One line drawn by hand or with the vector (bezier) pen.
 * Points are x,y pairs. In a finished [PathLayer] they are relative to the layer box.
 */
class Stroke(
    var pts: FloatArray,
    var color: Int,
    var width: Float,
    var brush: Int,          // see BRUSH_* below
    var smooth: Boolean,     // vector pen: a smooth curve through the points
    var closed: Boolean,     // vector pen: join the last point to the first
    var fill: Int            // vector pen: inside color (transparent = none)
) {
    /**
     * Bezier pen: for every point, its two handles (in x, in y, out x, out y) measured from the point.
     * null = an older vector line (a smooth curve through the points).
     */
    var ctrl: FloatArray? = null
    var cap = CAP_ROUND
    var grad = 0             // second color of a gradient line (0 = one color)

    val isBezier get() = ctrl != null

    fun copy() = Stroke(pts.copyOf(), color, width, brush, smooth, closed, fill).also {
        it.ctrl = ctrl?.copyOf(); it.cap = cap; it.grad = grad
    }

    /** Keeps the handles array the same length as the points (4 numbers per point). */
    fun fixCtrl() {
        val c = ctrl ?: return
        val need = pts.size * 2
        if (c.size != need) ctrl = c.copyOf(need)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        val a = JSONArray()
        for (v in pts) a.put(v.toDouble())
        put("pts", a); put("color", color); put("width", width.toDouble()); put("brush", brush)
        put("smooth", smooth); put("closed", closed); put("fill", fill)
        ctrl?.let { c -> put("ctrl", JSONArray().apply { for (v in c) put(v.toDouble()) }) }
        put("cap", cap); put("grad", grad)
    }

    companion object {
        const val BRUSH_PEN = 0
        const val BRUSH_MARKER = 1
        const val BRUSH_NEON = 2
        const val BRUSH_DASH = 3
        const val BRUSH_SPRAY = 4
        const val BRUSH_CALLIGRAPHY = 5
        const val BRUSH_COUNT = 6

        const val CAP_ROUND = 0
        const val CAP_BUTT = 1
        const val CAP_SQUARE = 2

        fun fromJson(j: JSONObject): Stroke {
            val a = j.optJSONArray("pts") ?: JSONArray()
            val p = FloatArray(a.length()) { a.optDouble(it, 0.0).toFloat() }
            val s = Stroke(
                p, j.optInt("color", Color.BLACK), j.optDouble("width", 8.0).toFloat(), j.optInt("brush", 0),
                j.optBoolean("smooth"), j.optBoolean("closed"), j.optInt("fill", Color.TRANSPARENT)
            )
            j.optJSONArray("ctrl")?.let { c -> s.ctrl = FloatArray(c.length()) { c.optDouble(it, 0.0).toFloat() }; s.fixCtrl() }
            s.cap = j.optInt("cap", CAP_ROUND)
            s.grad = j.optInt("grad", 0)
            return s
        }

        /** Builds the path of a stroke: soft curve for hand drawing, smooth curve through points for the vector pen. */
        fun buildPath(s: Stroke, out: Path) {
            out.reset()
            val p = s.pts
            val n = p.size / 2
            if (n == 0) return
            out.moveTo(p[0], p[1])
            if (n == 1) {
                out.lineTo(p[0] + 0.1f, p[1])
                return
            }
            val c = s.ctrl
            if (c != null && c.size >= n * 4) {
                // bezier pen: from each point's "out" handle to the next point's "in" handle
                val count = if (s.closed) n else n - 1
                for (i in 0 until count) {
                    val j = (i + 1) % n
                    out.cubicTo(
                        p[2 * i] + c[4 * i + 2], p[2 * i + 1] + c[4 * i + 3],
                        p[2 * j] + c[4 * j], p[2 * j + 1] + c[4 * j + 1],
                        p[2 * j], p[2 * j + 1]
                    )
                }
                if (s.closed) out.close()
                return
            }
            if (s.smooth) {
                // Catmull-Rom through every point, turned into bezier curves
                val count = if (s.closed) n else n - 1
                for (i in 0 until count) {
                    fun px(k: Int) = p[2 * idx(k, n, s.closed)]
                    fun py(k: Int) = p[2 * idx(k, n, s.closed) + 1]
                    val c1x = px(i) + (px(i + 1) - px(i - 1)) / 6f
                    val c1y = py(i) + (py(i + 1) - py(i - 1)) / 6f
                    val c2x = px(i + 1) - (px(i + 2) - px(i)) / 6f
                    val c2y = py(i + 1) - (py(i + 2) - py(i)) / 6f
                    out.cubicTo(c1x, c1y, c2x, c2y, px(i + 1), py(i + 1))
                }
                if (s.closed) out.close()
            } else {
                for (i in 1 until n - 1) {
                    val mx = (p[2 * i] + p[2 * i + 2]) / 2f
                    val my = (p[2 * i + 1] + p[2 * i + 3]) / 2f
                    out.quadTo(p[2 * i], p[2 * i + 1], mx, my)
                }
                out.lineTo(p[2 * n - 2], p[2 * n - 1])
            }
        }

        private fun idx(k: Int, n: Int, closed: Boolean): Int =
            if (closed) ((k % n) + n) % n else k.coerceIn(0, n - 1)

        private val tmp = Path()
        private val gradBox = RectF()
        private val sprayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        /** Draws one stroke with its brush. [alphaMul] is used for the eraser-free preview. */
        fun draw(c: Canvas, s: Stroke, paint: Paint) {
            buildPath(s, tmp)
            paint.reset()
            paint.isAntiAlias = true
            if (s.smooth && Color.alpha(s.fill) > 0) {
                paint.style = Paint.Style.FILL
                paint.color = s.fill
                c.drawPath(tmp, paint)
            }
            if (s.width <= 0f) return
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s.width
            paint.strokeCap = when (s.cap) {
                CAP_BUTT -> Paint.Cap.BUTT
                CAP_SQUARE -> Paint.Cap.SQUARE
                else -> Paint.Cap.ROUND
            }
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = s.color
            if (s.grad != 0 && s.brush == BRUSH_PEN) {
                // gradient along the line, from the first color to the second
                tmp.computeBounds(gradBox, true)
                val horiz = gradBox.width() >= gradBox.height()
                paint.shader = android.graphics.LinearGradient(
                    gradBox.left, gradBox.top,
                    if (horiz) gradBox.right else gradBox.left, if (horiz) gradBox.top else gradBox.bottom,
                    s.color, s.grad, android.graphics.Shader.TileMode.CLAMP
                )
                c.drawPath(tmp, paint)
                paint.shader = null
                return
            }
            when (s.brush) {
                BRUSH_MARKER -> {
                    paint.strokeCap = Paint.Cap.SQUARE
                    paint.alpha = (Color.alpha(s.color) * 0.55f).toInt()
                    c.drawPath(tmp, paint)
                }
                BRUSH_NEON -> {
                    paint.maskFilter = BlurMaskFilter(s.width * 1.6f, BlurMaskFilter.Blur.NORMAL)
                    paint.strokeWidth = s.width * 1.6f
                    c.drawPath(tmp, paint)
                    paint.maskFilter = null
                    paint.strokeWidth = s.width * 0.45f
                    paint.color = blendWhite(s.color)
                    c.drawPath(tmp, paint)
                }
                BRUSH_DASH -> {
                    paint.pathEffect = DashPathEffect(floatArrayOf(s.width * 2.2f, s.width * 1.8f), 0f)
                    c.drawPath(tmp, paint)
                    paint.pathEffect = null
                }
                BRUSH_SPRAY -> {
                    // fixed random dots along the path, so it looks the same every time
                    val pm = android.graphics.PathMeasure(tmp, false)
                    val rnd = java.util.Random(s.pts.size.toLong() * 31 + s.width.toLong())
                    sprayPaint.color = s.color
                    sprayPaint.style = Paint.Style.FILL
                    val pos = FloatArray(2)
                    do {
                        val len = pm.length
                        var d = 0f
                        val step = max(1f, s.width / 4f)
                        while (d <= len) {
                            pm.getPosTan(d, pos, null)
                            repeat(3) {
                                val ang = rnd.nextDouble() * Math.PI * 2
                                val r = rnd.nextFloat() * s.width
                                c.drawCircle(
                                    pos[0] + (Math.cos(ang) * r).toFloat(), pos[1] + (Math.sin(ang) * r).toFloat(),
                                    max(0.8f, s.width / 14f), sprayPaint
                                )
                            }
                            d += step
                        }
                    } while (pm.nextContour())
                }
                BRUSH_CALLIGRAPHY -> {
                    // a flat nib: several thin lines side by side at an angle
                    paint.strokeWidth = max(1f, s.width / 6f)
                    paint.strokeCap = Paint.Cap.BUTT
                    val k = s.width / 2f
                    for (i in -3..3) {
                        c.save()
                        c.translate(i * k / 3f * 0.7f, -i * k / 3f * 0.7f)
                        c.drawPath(tmp, paint)
                        c.restore()
                    }
                }
                else -> c.drawPath(tmp, paint)
            }
        }

        private fun blendWhite(c: Int): Int = Color.argb(
            Color.alpha(c),
            (Color.red(c) + 255 * 2) / 3, (Color.green(c) + 255 * 2) / 3, (Color.blue(c) + 255 * 2) / 3
        )

        fun fromJsonLayer(j: JSONObject): PathLayer {
            val a = j.optJSONArray("strokes") ?: JSONArray()
            val list = mutableListOf<Stroke>()
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { list.add(fromJson(it)) }
            val l = PathLayer(list, j.optDouble("bw", 10.0).toFloat(), j.optDouble("bh", 10.0).toFloat())
            l.readBase(j)
            return l
        }
    }
}

/** Hand drawing or vector pen lines, as one movable layer. */
class PathLayer(val strokes: MutableList<Stroke>, var bw: Float, var bh: Float) : Layer() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    companion object {
        /** Makes a layer from strokes drawn in page coordinates. */
        fun fromPageStrokes(list: List<Stroke>): PathLayer? {
            if (list.isEmpty()) return null
            val b = RectF()
            var first = true
            val tmp = Path()
            val r = RectF()
            for (s in list) {
                Stroke.buildPath(s, tmp)
                tmp.computeBounds(r, true)
                val pad = s.width * (if (s.brush == Stroke.BRUSH_NEON) 2.5f else 1f) / 2f + 2f
                r.inset(-pad, -pad)
                if (first) { b.set(r); first = false } else b.union(r)
            }
            val local = list.map { s ->
                val c = s.copy()
                for (i in c.pts.indices step 2) {
                    c.pts[i] -= b.left
                    c.pts[i + 1] -= b.top
                }
                c
            }
            val l = PathLayer(local.toMutableList(), max(b.width(), 4f), max(b.height(), 4f))
            l.x = b.centerX()
            l.y = b.centerY()
            return l
        }
    }

    override fun copy(): PathLayer {
        val c = PathLayer(strokes.map { it.copy() }.toMutableList(), bw, bh)
        c.copyBaseFrom(this)
        return c
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", "path")
        val a = JSONArray()
        for (s in strokes) a.put(s.toJson())
        put("strokes", a); put("bw", bw.toDouble()); put("bh", bh.toDouble())
        putBase(this)
    }

    /** Color of the first line, shown in the color panel. */
    val mainColor: Int get() = strokes.firstOrNull()?.color ?: Color.BLACK

    override fun rescale(k: Float) {
        super.rescale(k)
        bw *= k
        bh *= k
        for (s in strokes) {
            s.width *= k
            for (i in s.pts.indices) s.pts[i] *= k
            s.ctrl?.let { c -> for (i in c.indices) c[i] *= k }
        }
    }

    override fun prepare(fm: FontManager) {
        cW = bw
        cH = bh
    }

    override fun padding(): Float = 4f

    override fun drawLocal(canvas: Canvas, fm: FontManager) {
        prepare(fm)
        for (s in strokes) Stroke.draw(canvas, s, paint)
    }
}

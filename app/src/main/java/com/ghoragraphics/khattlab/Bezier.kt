package com.ghoragraphics.khattlab

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.hypot

/**
 * Hand drawing (vector = false) or the bezier pen (vector = true) in progress.
 * All points are page pixels.
 *
 * Bezier pen, like PixelLab:
 *  - several lines ("paths") in one drawing, [index] is the one being edited
 *  - mode ADD: tap = new point at the end, drag a point = move it
 *  - mode CURVE: drag a point = pull out its two handles, drag a handle = turn / stretch it,
 *    drag the small mark in the middle of a piece = bend that piece
 *  - mode MOVE: drag anywhere = move the whole line
 */
class DrawSession(val vector: Boolean) {
    // ---- hand drawing ----
    val strokes = mutableListOf<Stroke>()
    var current: Stroke? = null

    // ---- settings for the next line (and the line being edited) ----
    var color = Color.BLACK
    var width = 12f
    var brush = Stroke.BRUSH_PEN
    var closed = false
    var fill = Color.TRANSPARENT
    var cap = Stroke.CAP_ROUND
    var grad = 0
    var opacity = 255

    // ---- bezier pen ----
    val paths = mutableListOf<Stroke>()
    var index = 0
    var bezMode = MODE_ADD
    var selPoint = -1
    /** A path layer taken off the page to be edited again; put back if the editing is cancelled. */
    var editing: Layer? = null
    var editingAt = -1

    private val history = ArrayDeque<Pair<List<Stroke>, Int>>()

    companion object {
        const val MODE_ADD = 0
        const val MODE_CURVE = 1
        const val MODE_MOVE = 2

        // what the finger took hold of
        private const val T_NONE = 0
        private const val T_POINT = 1
        private const val T_IN = 2
        private const val T_OUT = 3
        private const val T_PULL = 4
        private const val T_SEG = 5
        private const val T_ALL = 6
    }

    fun newStroke(x: Float, y: Float) =
        Stroke(floatArrayOf(x, y), lineColor(), width, brush, vector, closed, fill).also {
            if (vector) {
                it.ctrl = FloatArray(4)
                it.cap = cap
                it.grad = grad
            }
        }

    fun lineColor(): Int = (color and 0x00FFFFFF) or (opacity.coerceIn(0, 255) shl 24)

    /** The bezier line being edited, or null when a new empty one was started. */
    val path: Stroke? get() = paths.getOrNull(index)
    val pathCount get() = paths.size

    // ---------- undo inside the drawing ----------

    fun remember() {
        history.addLast(Pair(paths.map { it.copy() }, index))
        if (history.size > 60) history.removeFirst()
    }

    fun clearHistory() = history.clear()

    fun canStepBack() = if (vector) history.isNotEmpty() else strokes.isNotEmpty()

    fun stepBack(): Boolean {
        if (!vector) {
            if (strokes.isEmpty()) return false
            strokes.removeAt(strokes.size - 1)
            return true
        }
        val h = history.removeLastOrNull() ?: return false
        paths.clear()
        paths.addAll(h.first)
        index = h.second.coerceIn(0, paths.size)
        selPoint = -1
        return true
    }

    // ---------- settings applied to the line being edited ----------

    fun applyStyle() {
        val p = path ?: return
        p.color = lineColor(); p.width = width; p.cap = cap; p.grad = grad; p.fill = fill; p.closed = closed
    }

    /** Shows the settings of the line being edited in the panel. */
    fun loadStyle() {
        val p = path ?: return
        color = p.color or 0xFF000000.toInt()
        opacity = Color.alpha(p.color)
        width = p.width; cap = p.cap; grad = p.grad; fill = p.fill; closed = p.closed
    }

    // ---------- lines ----------

    fun newPath() {
        if (path == null) return
        index = paths.size
        selPoint = -1
    }

    fun prevPath() {
        if (index > 0) { index--; selPoint = -1; loadStyle() }
    }

    fun nextPath() {
        if (index < paths.size - 1) { index++; selPoint = -1; loadStyle() }
    }

    fun duplicatePath(shift: Float) {
        val p = path ?: return
        remember()
        val c = p.copy()
        for (i in c.pts.indices) c.pts[i] += shift
        paths.add(c)
        index = paths.size - 1
        selPoint = -1
    }

    fun deletePath() {
        if (path == null) return
        remember()
        paths.removeAt(index)
        index = index.coerceAtMost(paths.size - 1).coerceAtLeast(0)
        selPoint = -1
        loadStyle()
    }

    // ---------- points ----------

    fun deletePoint() {
        val p = path ?: return
        val n = p.pts.size / 2
        val i = if (selPoint in 0 until n) selPoint else n - 1
        if (i < 0) return
        remember()
        if (n <= 1) {
            paths.removeAt(index)
            index = index.coerceAtMost(paths.size)
            selPoint = -1
            return
        }
        val pts = FloatArray(p.pts.size - 2)
        val c = p.ctrl ?: FloatArray(n * 4)
        val cc = FloatArray(c.size - 4)
        var k = 0
        for (j in 0 until n) {
            if (j == i) continue
            pts[2 * k] = p.pts[2 * j]; pts[2 * k + 1] = p.pts[2 * j + 1]
            for (q in 0 until 4) cc[4 * k + q] = c[4 * j + q]
            k++
        }
        p.pts = pts
        p.ctrl = cc
        selPoint = if (i >= n - 1) n - 2 else i
    }

    /** Smooth curve through all points (automatic handles). */
    fun smoothAll() {
        val p = path ?: return
        val n = p.pts.size / 2
        if (n < 2) return
        remember()
        val c = FloatArray(n * 4)
        val pt = p.pts
        for (i in 0 until n) {
            val a = if (i == 0) (if (p.closed) n - 1 else 0) else i - 1
            val b = if (i == n - 1) (if (p.closed) 0 else n - 1) else i + 1
            val tx = (pt[2 * b] - pt[2 * a]) / 6f
            val ty = (pt[2 * b + 1] - pt[2 * a + 1]) / 6f
            c[4 * i] = -tx; c[4 * i + 1] = -ty
            c[4 * i + 2] = tx; c[4 * i + 3] = ty
        }
        p.ctrl = c
    }

    /** Straight pieces between the points. */
    fun straightAll() {
        val p = path ?: return
        remember()
        p.ctrl = FloatArray(p.pts.size * 2)
    }

    fun toggleClosed() {
        closed = !closed
        val p = path ?: return
        remember()
        p.closed = closed
    }

    // ---------- touch (page pixels; [r] = how close counts as "on it", in page pixels) ----------

    private var grab = T_NONE
    private var grabIndex = -1
    private var lastX = 0f
    private var lastY = 0f
    private var changed = false

    fun bezDown(x: Float, y: Float, r: Float) {
        grab = T_NONE
        grabIndex = -1
        changed = false
        lastX = x; lastY = y
        val p = path
        if (bezMode == MODE_MOVE) {
            if (p != null) grab = T_ALL
            return
        }
        if (p == null) return
        val pts = p.pts
        val n = pts.size / 2
        val c = p.ctrl ?: FloatArray(n * 4).also { p.ctrl = it }
        if (bezMode == MODE_CURVE) {
            // handles first (they sit on top)
            var best = r
            for (i in 0 until n) {
                for (h in 0..1) {
                    val hx = c[4 * i + 2 * h]
                    val hy = c[4 * i + 2 * h + 1]
                    if (hx == 0f && hy == 0f) continue
                    val d = hypot(pts[2 * i] + hx - x, pts[2 * i + 1] + hy - y)
                    if (d < best) { best = d; grab = if (h == 0) T_IN else T_OUT; grabIndex = i }
                }
            }
            if (grab != T_NONE) return
        }
        var best = r
        for (i in 0 until n) {
            val d = hypot(pts[2 * i] - x, pts[2 * i + 1] - y)
            if (d < best) { best = d; grabIndex = i; grab = if (bezMode == MODE_CURVE) T_PULL else T_POINT }
        }
        if (grab != T_NONE) return
        if (bezMode == MODE_CURVE) {
            val segs = if (p.closed) n else n - 1
            best = r
            val m = FloatArray(2)
            for (i in 0 until segs) {
                segMid(p, i, m)
                val d = hypot(m[0] - x, m[1] - y)
                if (d < best) { best = d; grabIndex = i; grab = T_SEG }
            }
        }
    }

    /** Returns true when something changed (so the page is redrawn). */
    fun bezMove(x: Float, y: Float): Boolean {
        if (grab == T_NONE) return false
        if (!changed) { remember(); changed = true }
        val p = path ?: return false
        val pts = p.pts
        val n = pts.size / 2
        p.fixCtrl()
        val c = p.ctrl ?: return false
        val i = grabIndex
        when (grab) {
            T_ALL -> {
                val dx = x - lastX
                val dy = y - lastY
                for (k in 0 until n) { pts[2 * k] += dx; pts[2 * k + 1] += dy }
            }
            T_POINT -> { pts[2 * i] = x; pts[2 * i + 1] = y; selPoint = i }
            T_PULL -> {
                val hx = x - pts[2 * i]
                val hy = y - pts[2 * i + 1]
                c[4 * i + 2] = hx; c[4 * i + 3] = hy
                c[4 * i] = -hx; c[4 * i + 1] = -hy
                selPoint = i
            }
            T_IN, T_OUT -> {
                val own = if (grab == T_IN) 0 else 2
                val other = 2 - own
                val hx = x - pts[2 * i]
                val hy = y - pts[2 * i + 1]
                c[4 * i + own] = hx; c[4 * i + own + 1] = hy
                // the other handle turns with it (smooth point), keeping its own length
                val len = hypot(c[4 * i + other], c[4 * i + other + 1])
                val l2 = hypot(hx, hy)
                if (len > 0f && l2 > 0f) {
                    c[4 * i + other] = -hx / l2 * len
                    c[4 * i + other + 1] = -hy / l2 * len
                }
                selPoint = i
            }
            T_SEG -> {
                // bend the piece so its middle follows the finger
                val j = (i + 1) % n
                val x0 = pts[2 * i]; val y0 = pts[2 * i + 1]
                val x3 = pts[2 * j]; val y3 = pts[2 * j + 1]
                val qx = 2f * x - (x0 + x3) / 2f
                val qy = 2f * y - (y0 + y3) / 2f
                c[4 * i + 2] = (qx - x0) * 2f / 3f; c[4 * i + 3] = (qy - y0) * 2f / 3f
                c[4 * j] = (qx - x3) * 2f / 3f; c[4 * j + 1] = (qy - y3) * 2f / 3f
            }
        }
        lastX = x; lastY = y
        return true
    }

    /** A tap (no drag). Returns true when something changed. */
    fun bezTap(x: Float, y: Float): Boolean {
        if (grab == T_POINT || grab == T_PULL) {
            selPoint = grabIndex
            return true
        }
        if (bezMode != MODE_ADD) return false
        remember()
        val p = path
        if (p == null) {
            paths.add(newStroke(x, y))
            index = paths.size - 1
            selPoint = 0
            return true
        }
        val n = p.pts.size / 2
        p.fixCtrl()
        p.pts = p.pts.copyOf(p.pts.size + 2).also { it[2 * n] = x; it[2 * n + 1] = y }
        p.ctrl = (p.ctrl ?: FloatArray(n * 4)).copyOf((n + 1) * 4)
        selPoint = n
        return true
    }

    fun bezUp(): Boolean {
        val c = changed
        grab = T_NONE
        changed = false
        return c
    }

    private fun segMid(p: Stroke, i: Int, out: FloatArray) {
        val n = p.pts.size / 2
        val j = (i + 1) % n
        val c = p.ctrl
        val x0 = p.pts[2 * i]; val y0 = p.pts[2 * i + 1]
        val x3 = p.pts[2 * j]; val y3 = p.pts[2 * j + 1]
        val x1 = x0 + (c?.getOrNull(4 * i + 2) ?: 0f); val y1 = y0 + (c?.getOrNull(4 * i + 3) ?: 0f)
        val x2 = x3 + (c?.getOrNull(4 * j) ?: 0f); val y2 = y3 + (c?.getOrNull(4 * j + 1) ?: 0f)
        out[0] = (x0 + 3 * x1 + 3 * x2 + x3) / 8f
        out[1] = (y0 + 3 * y1 + 3 * y2 + y3) / 8f
    }

    // ---------- drawing the points on screen ----------

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val tmpPath = Path()

    /** Draws points, handles and marks of the line being edited. Page point (x, y) is at (ox + x * k, oy + y * k). */
    fun drawOverlay(canvas: Canvas, ox: Float, oy: Float, k: Float, density: Float) {
        val p = path ?: return
        val pts = p.pts
        val n = pts.size / 2
        val c = p.ctrl
        fun vx(x: Float) = ox + x * k
        fun vy(y: Float) = oy + y * k
        val blue = 0xFF1E88E5.toInt()
        val gold = 0xFFE8B547.toInt()

        if (bezMode == MODE_MOVE) {
            Stroke.buildPath(p, tmpPath)
            tmpPath.computeBounds(box, true)
            linePaint.color = gold
            linePaint.strokeWidth = 1.5f * density
            linePaint.pathEffect = DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
            val pad = p.width / 2f
            canvas.drawRect(vx(box.left - pad), vy(box.top - pad), vx(box.right + pad), vy(box.bottom + pad), linePaint)
            linePaint.pathEffect = null
        }

        // handles
        if (c != null && bezMode == MODE_CURVE) {
            linePaint.color = blue
            linePaint.strokeWidth = 1.2f * density
            for (i in 0 until n) {
                for (h in 0..1) {
                    val hx = c.getOrNull(4 * i + 2 * h) ?: 0f
                    val hy = c.getOrNull(4 * i + 2 * h + 1) ?: 0f
                    if (hx == 0f && hy == 0f) continue
                    val ax = vx(pts[2 * i]); val ay = vy(pts[2 * i + 1])
                    val bx = vx(pts[2 * i] + hx); val by = vy(pts[2 * i + 1] + hy)
                    canvas.drawLine(ax, ay, bx, by, linePaint)
                    dotPaint.style = Paint.Style.FILL
                    dotPaint.color = blue
                    canvas.drawCircle(bx, by, 6f * density, dotPaint)
                }
            }
            // middle marks: drag to bend a piece
            val segs = if (p.closed) n else n - 1
            val m = FloatArray(2)
            for (i in 0 until segs) {
                segMid(p, i, m)
                val mx = vx(m[0]); val my = vy(m[1])
                dotPaint.style = Paint.Style.FILL
                dotPaint.color = 0xCCFFFFFF.toInt()
                canvas.save()
                canvas.rotate(45f, mx, my)
                val s = 5f * density
                canvas.drawRect(mx - s, my - s, mx + s, my + s, dotPaint)
                dotPaint.style = Paint.Style.STROKE
                dotPaint.strokeWidth = 1.5f * density
                dotPaint.color = blue
                canvas.drawRect(mx - s, my - s, mx + s, my + s, dotPaint)
                canvas.restore()
            }
        }

        // points
        for (i in 0 until n) {
            val x = vx(pts[2 * i]); val y = vy(pts[2 * i + 1])
            val r = (if (i == selPoint) 10f else 8f) * density
            dotPaint.style = Paint.Style.FILL
            dotPaint.color = if (i == selPoint) gold else Color.WHITE
            canvas.drawCircle(x, y, r, dotPaint)
            dotPaint.style = Paint.Style.STROKE
            dotPaint.strokeWidth = 2f * density
            dotPaint.color = if (i == 0) 0xFF43A047.toInt() else 0xFF263238.toInt()
            canvas.drawCircle(x, y, r, dotPaint)
        }
    }

    /** Every finished bezier line (at least 2 points). */
    fun finishedPaths(): List<Stroke> = paths.filter { it.pts.size >= 4 }
}

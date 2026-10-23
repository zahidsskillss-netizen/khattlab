package com.ghoragraphics.khattlab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The page: a photo or a solid color, plus its size in pixels. */
class DocBackground(val bitmap: Bitmap?, val color: Int, val width: Int, val height: Int)

/** A saved state for undo / redo. */
class Snapshot(val layers: List<Layer>, val bg: DocBackground, val selectedIndex: Int)

class EditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onSelectionChanged(layer: Layer?)
        fun onEditRequest(layer: Layer)
        fun onHistoryChanged()
        fun onDrawChanged() {}
    }

    var drawing: DrawSession? = null
        private set

    /** When on, the finger moves and zooms the picture inside the selected shape, not the shape. */
    var imageAdjust = false
        set(v) { field = v; invalidate() }
    private var startImgScale = 1f

    /** When on (perspective panel open), the selected layer shows 4 corner handles that pull its corners freely. */
    var perspEdit = false
        set(v) { field = v; invalidate() }
    private var warpCorner = -1
    private var grabDX = 0f
    private var grabDY = 0f
    private val warpPts = FloatArray(8)
    private val warpLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xAAFFFFFF.toInt()
    }
    private val warpHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var dragPoint = -1
    private val penPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    lateinit var fonts: FontManager
    var listener: Listener? = null

    var doc = DocBackground(null, Color.WHITE, 1080, 1080)
        private set
    val layers = mutableListOf<Layer>()
    var selected: Layer? = null
        private set

    var showGrid = false
        set(v) { field = v; invalidate() }

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private val maxHistory = 40

    // view <-> page transform
    private var fit = 1f
    private var offX = 0f
    private var offY = 0f

    private val density = resources.displayMetrics.density
    private val gold = 0xFFE8B547.toInt()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33FFFFFF
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0x88FFFFFF.toInt()
    }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = gold
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFF141416.toInt()
    }
    private val handleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF141416.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
        isFakeBoldText = true
    }
    private val handleR = 12f * density
    private val handleGlyphs = arrayOf("✕", "⧉", "⤡", "⤢")
    private val selPath = Path()
    private val corners = FloatArray(8)
    private val docRect = RectF()

    // touch state
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var mode = 0 // 0 none, 1 drag, 2 pinch, 3 handle scale+rotate, 4 handle scale, 5 pull a perspective corner
    private var lastX = 0f
    private var lastY = 0f
    private var downVX = 0f
    private var downVY = 0f
    private var moved = false
    private var committed = false
    private var downOnEmpty = false
    private var startDist = 1f
    private var startAngle = 0f
    private var startScale = 1f
    private var startRot = 0f

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val hit = hitTest(toDocX(e.x), toDocY(e.y))
            if (hit != null) listener?.onEditRequest(hit)
            return true
        }
    })

    init {
        // Blur, emboss and shadows on pictures need software drawing on older Android
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // ---------- history ----------

    private fun snapshot() = Snapshot(layers.map { it.copy() }, doc, layers.indexOf(selected))

    /** Call BEFORE changing something, so the change can be undone. */
    fun commit() {
        undoStack.addLast(snapshot())
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
        listener?.onHistoryChanged()
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(snapshot())
        restore(undoStack.removeLast())
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(snapshot())
        restore(redoStack.removeLast())
    }

    private fun restore(s: Snapshot) {
        layers.clear()
        layers.addAll(s.layers.map { it.copy() })
        doc = s.bg
        selected = layers.getOrNull(s.selectedIndex)?.takeIf { !it.locked && !it.hidden }
        listener?.onSelectionChanged(selected)
        listener?.onHistoryChanged()
        invalidate()
    }

    // ---------- editing operations ----------

    fun select(layer: Layer?) {
        if (selected !== layer) {
            selected = layer
            listener?.onSelectionChanged(layer)
        }
        invalidate()
    }

    fun addText(text: String) {
        commit()
        val l = TextLayer(text, doc.width / 2f, doc.height / 2f)
        l.textSize = min(doc.width, doc.height) * 0.09f
        l.highlightPadding = l.textSize * 0.25f
        l.fontKey = fonts.defaultKey
        if (doc.bitmap != null) {
            l.color = Color.WHITE
            l.shadowRadius = l.textSize * 0.12f
            l.shadowDy = l.textSize * 0.04f
        } else {
            l.color = if (Color.luminance(doc.color) > 0.5f) Color.BLACK else Color.WHITE
        }
        layers.add(l)
        select(l)
    }

    fun addImage(b: Bitmap) {
        commit()
        val l = ImageLayer(b, doc.width / 2f, doc.height / 2f)
        l.scale = min(doc.width * 0.6f / b.width, doc.height * 0.6f / b.height)
        layers.add(l)
        select(l)
    }

    fun update(commitFirst: Boolean = true, block: (Layer) -> Unit) {
        val s = selected ?: return
        if (commitFirst) commit()
        block(s)
        invalidate()
    }

    fun deleteSelected() {
        val s = selected ?: return
        commit()
        layers.remove(s)
        select(null)
    }

    fun duplicateSelected() {
        val s = selected ?: return
        commit()
        val c = s.copy()
        c.x += doc.width * 0.04f
        c.y += doc.height * 0.04f
        layers.add(c)
        select(c)
    }

    fun moveSelected(up: Boolean) {
        val s = selected ?: return
        val i = layers.indexOf(s)
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j < 0 || j >= layers.size) return
        commit()
        layers.removeAt(i)
        layers.add(j, s)
        invalidate()
    }

    fun nudge(dx: Float, dy: Float) {
        update { it.x += dx; it.y += dy }
    }

    /** h: 0 right, 1 center, 2 left (null = keep). v: 0 top, 1 middle, 2 bottom (null = keep). */
    fun alignTo(h: Int?, v: Int?) {
        val s = selected ?: return
        commit()
        val b = s.pageBounds(fonts)
        when (h) {
            0 -> s.x += doc.width - b.right
            1 -> s.x += doc.width / 2f - b.centerX()
            2 -> s.x += -b.left
        }
        when (v) {
            0 -> s.y += -b.top
            1 -> s.y += doc.height / 2f - b.centerY()
            2 -> s.y += doc.height - b.bottom
        }
        invalidate()
    }

    fun setDocColor(c: Int) {
        commit()
        doc = DocBackground(null, c, doc.width, doc.height)
        invalidate()
    }

    fun setDocSize(w: Int, h: Int) {
        commit()
        val old = doc
        doc = DocBackground(null, old.color, w, h)
        relayout(old)
    }

    fun setPhoto(b: Bitmap) {
        commit()
        val old = doc
        // the page keeps the photo's shape but never gets small: a small photo would make
        // every text and shape on it small and blurry when saved
        val long = max(b.width, b.height)
        val k = if (long < MIN_PAGE) MIN_PAGE / long.toFloat() else 1f
        doc = DocBackground(b, old.color, (b.width * k).roundToInt(), (b.height * k).roundToInt())
        relayout(old)
    }

    companion object {
        /** Longest side of a page made from a photo, at least. */
        const val MIN_PAGE = 2048
    }

    fun newDoc() {
        commit()
        layers.clear()
        doc = DocBackground(null, Color.WHITE, 1080, 1080)
        select(null)
        invalidate()
    }

    private fun relayout(old: DocBackground) {
        if (old.width != doc.width || old.height != doc.height) {
            val sx = doc.width / old.width.toFloat()
            val sy = doc.height / old.height.toFloat()
            val k = min(sx, sy)
            for (l in layers) {
                l.x *= sx
                l.y *= sy
                l.rescale(k)
            }
        }
        invalidate()
    }

    // ---------- layers list ----------

    /** Picks a layer from the layers list. A locked or hidden layer can't be picked. */
    fun selectFromList(l: Layer): Boolean {
        if (l.locked || l.hidden || !layers.contains(l)) return false
        select(l)
        return true
    }

    /** Moves a layer to a new place in the stack (0 = bottom). */
    fun moveLayerTo(l: Layer, to: Int) {
        val from = layers.indexOf(l)
        if (from < 0 || layers.size < 2) return
        val t = to.coerceIn(0, layers.size - 1)
        if (from == t) return
        commit()
        layers.removeAt(from)
        layers.add(t.coerceIn(0, layers.size), l)
        listener?.onHistoryChanged()
        invalidate()
    }

    fun setLocked(l: Layer, on: Boolean) {
        if (!layers.contains(l)) return
        commit()
        l.locked = on
        // a locked layer can't stay selected, so nothing can change it until it is unlocked
        if (on && selected === l) select(null)
        listener?.onHistoryChanged()
        invalidate()
    }

    fun setHidden(l: Layer, on: Boolean) {
        if (!layers.contains(l)) return
        commit()
        l.hidden = on
        if (on && selected === l) select(null)
        listener?.onHistoryChanged()
        invalidate()
    }

    fun deleteLayer(l: Layer) {
        if (!layers.contains(l)) return
        commit()
        layers.remove(l)
        listener?.onHistoryChanged()
        if (selected === l) select(null) else invalidate()
    }

    fun addShape(kind: Int) {
        commit()
        val side = min(doc.width, doc.height) * 0.4f
        val l = ShapeLayer(kind, doc.width / 2f, doc.height / 2f)
        l.w = side
        l.h = if (ShapeLayer.isLine(kind)) side * 0.18f else if (kind == ShapeLayer.ARROW) side * 0.55f else if (kind == ShapeLayer.HALF_CIRCLE) side / 2f else side
        if (ShapeLayer.isLine(kind)) l.strokeWidth = side * 0.035f
        l.fillColor = if (Color.luminance(doc.color) > 0.5f || doc.bitmap != null) 0xFFE8B547.toInt() else Color.WHITE
        layers.add(l)
        select(l)
    }

    // ---------- drawing ----------

    fun startDrawing(vector: Boolean): DrawSession {
        select(null)
        val d = DrawSession(vector)
        d.width = max(doc.width, doc.height) * (if (vector) 0.006f else 0.012f)
        d.color = if (Color.luminance(doc.color) > 0.5f && doc.bitmap == null) Color.BLACK else Color.WHITE
        drawing = d
        invalidate()
        return d
    }

    /** Can [l] be opened again in the bezier pen? (lines made with the vector / bezier pen) */
    fun canEditPoints(l: Layer?): Boolean = l is PathLayer && l.strokes.isNotEmpty() && l.strokes.all { it.smooth }

    /** Takes a pen layer off the page and opens its points in the bezier pen. */
    fun editPoints(l: PathLayer): DrawSession? {
        if (!canEditPoints(l) || l.locked) return null
        val at = layers.indexOf(l)
        if (at < 0) return null
        commit()
        layers.removeAt(at)
        select(null)
        val d = DrawSession(true)
        d.editing = l
        d.editingAt = at
        val a = Math.toRadians(l.rotation.toDouble())
        val cos = Math.cos(a).toFloat()
        val sin = Math.sin(a).toFloat()
        val k = l.scale
        val bx = l.bw / 2f
        val by = l.bh / 2f
        for (st in l.strokes) {
            val c = st.copy()
            if (c.ctrl == null) {
                // older smooth line: give it handles that draw the same curve
                d.paths.add(c); d.index = d.paths.size - 1
                c.ctrl = FloatArray(c.pts.size * 2)
                d.smoothAll()
            }
            for (i in 0 until c.pts.size / 2) {
                val lx = (c.pts[2 * i] - bx) * k
                val ly = (c.pts[2 * i + 1] - by) * k
                c.pts[2 * i] = l.x + lx * cos - ly * sin
                c.pts[2 * i + 1] = l.y + lx * sin + ly * cos
            }
            c.ctrl?.let { h ->
                for (i in 0 until h.size / 2) {
                    val hx = h[2 * i] * k
                    val hy = h[2 * i + 1] * k
                    h[2 * i] = hx * cos - hy * sin
                    h[2 * i + 1] = hx * sin + hy * cos
                }
            }
            c.width *= k
            if (!d.paths.contains(c)) d.paths.add(c)
        }
        d.index = 0
        d.clearHistory()
        d.loadStyle()
        d.bezMode = DrawSession.MODE_CURVE
        drawing = d
        invalidate()
        return d
    }

    /** One step back inside the drawing. */
    fun undoDrawStep() {
        val d = drawing ?: return
        if (d.stepBack()) {
            d.loadStyle()
            listener?.onDrawChanged()
            invalidate()
        }
    }

    /** Ends drawing; keep = true turns what was drawn into a layer. */
    fun finishDrawing(keep: Boolean) {
        val d = drawing ?: return
        drawing = null
        val list = if (d.vector) d.finishedPaths().toMutableList() else d.strokes.toMutableList()
        val l = if (keep) PathLayer.fromPageStrokes(list) else null
        val old = d.editing
        if (l != null) {
            if (old != null) {
                l.opacity = old.opacity
                l.reflectSize = old.reflectSize; l.reflectGap = old.reflectGap; l.reflectAlpha = old.reflectAlpha
            }
            if (old == null) commit()
            if (old != null && d.editingAt in 0..layers.size) layers.add(d.editingAt, l) else layers.add(l)
            listener?.onHistoryChanged()
            select(l)
        } else if (old != null && (!keep || list.isEmpty())) {
            // cancelled (or nothing left): the layer comes back as it was
            if (!keep) layers.add(d.editingAt.coerceIn(0, layers.size), old)
            listener?.onHistoryChanged()
        }
        invalidate()
    }

    /** Something in the bezier panel changed the line: redraw. */
    fun drawingChanged() {
        listener?.onDrawChanged()
        invalidate()
    }

    /** True when the page has nothing on it yet. */
    fun isBlank() = layers.isEmpty() && doc.bitmap == null && drawing == null

    /** Puts an opened project on the page. Undo history starts fresh. */
    fun loadDoc(d: DocBackground, list: List<Layer>) {
        undoStack.clear()
        redoStack.clear()
        layers.clear()
        layers.addAll(list)
        doc = d
        selected = null
        listener?.onSelectionChanged(null)
        listener?.onHistoryChanged()
        invalidate()
    }

    /** A small picture of the page (longest side = [side]) for the projects list. */
    fun renderThumb(side: Int): Bitmap {
        val k = side / max(doc.width, doc.height).toFloat()
        val w = (doc.width * k).roundToInt().coerceAtLeast(1)
        val h = (doc.height * k).roundToInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        c.scale(k, k)
        drawDoc(c)
        return b
    }

    /** Draws the finished picture at full size, without the selection box. */
    fun render(): Bitmap = render(max(doc.width, doc.height))

    /**
     * Draws the finished picture with its longest side [longSide] pixels. Texts, shapes and lines are
     * drawn again at that size, so they stay sharp; [opaque] puts white under see-through parts (for JPG).
     */
    fun render(longSide: Int, opaque: Boolean = false): Bitmap {
        val k = longSide / max(doc.width, doc.height).toFloat()
        val w = (doc.width * k).roundToInt().coerceAtLeast(1)
        val h = (doc.height * k).roundToInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (opaque) c.drawColor(Color.WHITE)
        c.scale(w / doc.width.toFloat(), h / doc.height.toFloat())
        drawDoc(c)
        return b
    }

    // ---------- drawing ----------

    private fun updateFit() {
        val m = 20f * density
        val aw = (width - 2 * m).coerceAtLeast(1f)
        val ah = (height - 2 * m).coerceAtLeast(1f)
        fit = min(aw / doc.width, ah / doc.height)
        offX = (width - doc.width * fit) / 2f
        offY = (height - doc.height * fit) / 2f
    }

    private fun drawDoc(canvas: Canvas) {
        val bmp = doc.bitmap
        if (bmp != null && !bmp.isRecycled) {
            docRect.set(0f, 0f, doc.width.toFloat(), doc.height.toFloat())
            canvas.drawBitmap(bmp, null, docRect, bmpPaint)
        } else {
            canvas.drawColor(doc.color)
        }
        for (l in layers) if (!l.hidden) l.draw(canvas, fonts)
    }

    private fun selectionCorners(s: Layer) {
        s.corners(fonts, corners)
        for (i in 0 until 4) {
            corners[2 * i] = offX + corners[2 * i] * fit
            corners[2 * i + 1] = offY + corners[2 * i + 1] * fit
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateFit()

        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(fit, fit)
        canvas.clipRect(0f, 0f, doc.width.toFloat(), doc.height.toFloat())
        drawDoc(canvas)
        drawing?.let { d ->
            if (d.vector) {
                for (st in d.paths) Stroke.draw(canvas, st, penPaint)
            } else {
                for (st in d.strokes) Stroke.draw(canvas, st, penPaint)
                d.current?.let { Stroke.draw(canvas, it, penPaint) }
            }
        }
        canvas.restore()

        val l = offX
        val t = offY
        val r = offX + doc.width * fit
        val b = offY + doc.height * fit
        canvas.drawRect(l, t, r, b, framePaint)
        if (showGrid) {
            for (i in 1..2) {
                val gx = l + (r - l) * i / 3f
                val gy = t + (b - t) * i / 3f
                canvas.drawLine(gx, t, gx, b, gridPaint)
                canvas.drawLine(l, gy, r, gy, gridPaint)
            }
        }

        drawing?.let { d ->
            // points and handles of the bezier pen
            if (d.vector) d.drawOverlay(canvas, offX, offY, fit, density)
            return
        }

        val s = selected ?: return
        if (s.hidden) return
        if (perspEdit && !s.locked) {
            drawWarp(canvas, s)
            return
        }
        selectionCorners(s)
        selPath.reset()
        selPath.moveTo(corners[0], corners[1])
        selPath.lineTo(corners[2], corners[3])
        selPath.lineTo(corners[4], corners[5])
        selPath.lineTo(corners[6], corners[7])
        selPath.close()
        canvas.drawPath(selPath, selPaint)
        if (s.locked) return
        if (imageAdjust && s is ShapeLayer && s.hasImage()) {
            selPaint.color = 0xFF1E88E5.toInt()
            canvas.drawPath(selPath, selPaint)
            selPaint.color = gold
            return
        }
        val ty = (handleText.descent() + handleText.ascent()) / 2f
        for (i in 0 until 4) {
            val hx = corners[2 * i]
            val hy = corners[2 * i + 1]
            canvas.drawCircle(hx, hy, handleR, handlePaint)
            canvas.drawCircle(hx, hy, handleR, handleRing)
            canvas.drawText(handleGlyphs[i], hx, hy - ty, handleText)
        }
    }

    private fun warpCornersView(s: Layer) {
        s.contentCorners(fonts, warpPts)
        for (i in 0 until 4) {
            warpPts[2 * i] = offX + warpPts[2 * i] * fit
            warpPts[2 * i + 1] = offY + warpPts[2 * i + 1] * fit
        }
    }

    /** Point at (u, v) (0..1) inside the quad of [warpPts], following the perspective. */
    private fun quadPoint(u: Float, v: Float, out: FloatArray, at: Int) {
        val m = android.graphics.Matrix()
        m.setPolyToPoly(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f), 0, warpPts, 0, 4)
        val p = floatArrayOf(u, v)
        m.mapPoints(p)
        out[at] = p[0]; out[at + 1] = p[1]
    }

    /** Outline, a 3x3 grid and 4 round corner handles, like PixelLab's free perspective. */
    private fun drawWarp(canvas: Canvas, s: Layer) {
        warpCornersView(s)
        selPath.reset()
        selPath.moveTo(warpPts[0], warpPts[1])
        for (i in 1 until 4) selPath.lineTo(warpPts[2 * i], warpPts[2 * i + 1])
        selPath.close()
        canvas.drawPath(selPath, selPaint)
        warpLine.strokeWidth = 1f * density
        val a = FloatArray(4)
        for (k in 1..2) {
            val t = k / 3f
            quadPoint(t, 0f, a, 0); quadPoint(t, 1f, a, 2)
            canvas.drawLine(a[0], a[1], a[2], a[3], warpLine)
            quadPoint(0f, t, a, 0); quadPoint(1f, t, a, 2)
            canvas.drawLine(a[0], a[1], a[2], a[3], warpLine)
        }
        val r = handleR * 1.05f
        for (i in 0 until 4) {
            val hx = warpPts[2 * i]
            val hy = warpPts[2 * i + 1]
            warpHandle.color = if (i == warpCorner) gold else Color.WHITE
            canvas.drawCircle(hx, hy, r, warpHandle)
            canvas.drawCircle(hx, hy, r, handleRing)
        }
    }

    /** Which free-perspective corner (0..3) is under the finger, or -1. */
    private fun hitWarp(vx: Float, vy: Float): Int {
        val s = selected ?: return -1
        if (!perspEdit || s.locked || s.hidden) return -1
        warpCornersView(s)
        var best = -1
        var bestD = handleR * 2.2f
        for (i in 0 until 4) {
            val d = hypot(vx - warpPts[2 * i], vy - warpPts[2 * i + 1])
            if (d <= bestD) { bestD = d; best = i }
        }
        return best
    }

    // ---------- touch ----------

    private fun toDocX(vx: Float) = (vx - offX) / fit
    private fun toDocY(vy: Float) = (vy - offY) / fit

    private fun hitTest(dx: Float, dy: Float): Layer? {
        val slop = 10f * density / fit
        for (i in layers.indices.reversed()) {
            val l = layers[i]
            if (l.hidden || l.locked) continue
            if (l.contains(dx, dy, fonts, slop)) return l
        }
        return null
    }

    /** Which corner handle (0..3) is under the finger, or -1. */
    private fun hitHandle(vx: Float, vy: Float): Int {
        val s = selected ?: return -1
        if (s.locked || s.hidden || perspEdit) return -1
        selectionCorners(s)
        for (i in 0 until 4) {
            if (hypot(vx - corners[2 * i], vy - corners[2 * i + 1]) <= handleR * 1.7f) return i
        }
        return -1
    }

    private fun spacing(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    private fun angle(e: MotionEvent) =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()

    private fun commitOnce() {
        if (!committed) {
            commit()
            committed = true
        }
    }

    private fun snapAngle(a: Float): Float {
        var r = a % 360f
        if (r > 180f) r -= 360f
        if (r < -180f) r += 360f
        val nearest = (r / 90f).roundToInt() * 90f
        return if (abs(r - nearest) < 4f) nearest else r
    }

    private fun centerVX(s: Layer) = offX + s.x * fit
    private fun centerVY(s: Layer) = offY + s.y * fit

    private fun drawTouch(d: DrawSession, e: MotionEvent): Boolean {
        val x = toDocX(e.x)
        val y = toDocY(e.y)
        if (!d.vector) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> d.current = d.newStroke(x, y)
                MotionEvent.ACTION_MOVE -> {
                    val c = d.current ?: return true
                    val p = c.pts
                    if (hypot(x - p[p.size - 2], y - p[p.size - 1]) * fit >= 2f * density) {
                        c.pts = p.copyOf(p.size + 2).also { it[p.size] = x; it[p.size + 1] = y }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    d.current?.let { d.strokes.add(it) }
                    d.current = null
                    listener?.onDrawChanged()
                }
                MotionEvent.ACTION_CANCEL -> d.current = null
            }
            invalidate()
            return true
        }
        // bezier pen
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downVX = e.x; downVY = e.y; moved = false
                d.bezDown(x, y, 24f * density / fit)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(e.x - downVX, e.y - downVY) < touchSlop) return true
                moved = true
                if (d.bezMove(x, y)) invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val changed = if (!moved) d.bezTap(x, y) else d.bezUp()
                if (!moved) d.bezUp()
                if (changed) listener?.onDrawChanged()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { d.bezUp(); invalidate() }
        }
        return true
    }

    private fun imageTouch(s: ShapeLayer, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                committed = false
                mode = 1
                lastX = e.x
                lastY = e.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount >= 2) {
                mode = 2
                startDist = spacing(e).coerceAtLeast(1f)
                startImgScale = s.imgScale
            }
            MotionEvent.ACTION_MOVE -> {
                commitOnce()
                if (mode == 1) {
                    // finger movement in page pixels, turned to the shape's own direction and size
                    val dx = (e.x - lastX) / fit
                    val dy = (e.y - lastY) / fit
                    lastX = e.x
                    lastY = e.y
                    val a = Math.toRadians(-s.rotation.toDouble())
                    val lx = (dx * Math.cos(a) - dy * Math.sin(a)).toFloat() / s.scale
                    val ly = (dx * Math.sin(a) + dy * Math.cos(a)).toFloat() / s.scale
                    s.imgX += lx / s.w.coerceAtLeast(1f)
                    s.imgY += ly / s.h.coerceAtLeast(1f)
                } else if (mode == 2 && e.pointerCount >= 2) {
                    s.imgScale = (startImgScale * spacing(e) / startDist).coerceIn(0.2f, 8f)
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val stay = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(stay)
                lastY = e.getY(stay)
                mode = 1
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = 0
                if (committed) listener?.onDrawChanged()
            }
        }
        return true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        drawing?.let { return drawTouch(it, e) }
        val adj = selected as? ShapeLayer
        if (imageAdjust && adj != null && adj.hasImage()) return imageTouch(adj, e)
        gestures.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                committed = false
                moved = false
                downOnEmpty = false
                downVX = e.x
                downVY = e.y
                val dx = toDocX(e.x)
                val dy = toDocY(e.y)

                // free perspective: pull one corner
                val wc = hitWarp(e.x, e.y)
                if (wc >= 0) {
                    warpCorner = wc
                    grabDX = warpPts[2 * wc] - e.x
                    grabDY = warpPts[2 * wc + 1] - e.y
                    mode = 5
                    invalidate()
                    return true
                }

                // corner handles of the selected layer
                val h = hitHandle(e.x, e.y)
                val s0 = selected
                if (h >= 0 && s0 != null) {
                    when (h) {
                        0 -> { deleteSelected(); mode = 0 }
                        1 -> { duplicateSelected(); mode = 0 }
                        else -> {
                            mode = if (h == 2) 3 else 4
                            startDist = hypot(e.x - centerVX(s0), e.y - centerVY(s0)).coerceAtLeast(1f)
                            startAngle = Math.toDegrees(atan2((e.y - centerVY(s0)).toDouble(), (e.x - centerVX(s0)).toDouble())).toFloat()
                            startScale = s0.scale
                            startRot = s0.rotation
                        }
                    }
                    return true
                }

                val hit = hitTest(dx, dy)
                if (hit != null && hit !== selected) select(hit)
                if (hit == null && selected?.locked == true) select(null)
                if (selected != null && selected?.locked != true) {
                    // a selected layer moves wherever the finger is put down
                    mode = 1
                    lastX = dx
                    lastY = dy
                    downOnEmpty = hit == null
                } else {
                    mode = 0
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val s = selected
                if (mode != 5 && s != null && !s.locked && e.pointerCount >= 2) {
                    mode = 2
                    startDist = spacing(e).coerceAtLeast(1f)
                    startAngle = angle(e)
                    startScale = s.scale
                    startRot = s.rotation
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val s = selected ?: return true
                when (mode) {
                    1 -> {
                        if (!moved && hypot(e.x - downVX, e.y - downVY) < touchSlop) return true
                        moved = true
                        commitOnce()
                        val dx = toDocX(e.x)
                        val dy = toDocY(e.y)
                        s.x += dx - lastX
                        s.y += dy - lastY
                        lastX = dx
                        lastY = dy
                        invalidate()
                    }
                    2 -> if (e.pointerCount >= 2) {
                        commitOnce()
                        s.scale = (startScale * spacing(e) / startDist).coerceIn(0.02f, 40f)
                        s.rotation = snapAngle(startRot + angle(e) - startAngle)
                        invalidate()
                    }
                    5 -> {
                        if (!moved && hypot(e.x - downVX, e.y - downVY) < touchSlop / 2f) return true
                        moved = true
                        commitOnce()
                        s.moveCorner(fonts, warpCorner, toDocX(e.x + grabDX), toDocY(e.y + grabDY))
                        invalidate()
                    }
                    3, 4 -> {
                        commitOnce()
                        val vx = e.x - centerVX(s)
                        val vy = e.y - centerVY(s)
                        s.scale = (startScale * hypot(vx, vy) / startDist).coerceIn(0.02f, 40f)
                        if (mode == 3) {
                            val a = Math.toDegrees(atan2(vy.toDouble(), vx.toDouble())).toFloat()
                            s.rotation = snapAngle(startRot + a - startAngle)
                        }
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == 2) {
                    // keep dragging with the finger that stays down
                    val stay = if (e.actionIndex == 0) 1 else 0
                    lastX = toDocX(e.getX(stay))
                    lastY = toDocY(e.getY(stay))
                    moved = true
                    mode = 1
                }
            }
            MotionEvent.ACTION_UP -> {
                // a tap (no drag) on an empty spot unselects
                if (mode == 1 && !moved && downOnEmpty) select(null)
                if (mode == 5) {
                    warpCorner = -1
                    invalidate()
                    if (committed) listener?.onDrawChanged()
                }
                mode = 0
            }
            MotionEvent.ACTION_CANCEL -> { mode = 0; warpCorner = -1; invalidate() }
        }
        return true
    }

    @Suppress("unused")
    private fun maxSide() = max(doc.width, doc.height)
}

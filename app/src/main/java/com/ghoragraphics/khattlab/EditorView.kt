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
    }

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
    private var mode = 0 // 0 none, 1 drag, 2 pinch, 3 handle scale+rotate, 4 handle scale
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
        selected = layers.getOrNull(s.selectedIndex)
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
        doc = DocBackground(b, old.color, b.width, b.height)
        relayout(old)
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

    /** Draws the finished picture at full size, without the selection box. */
    fun render(): Bitmap {
        val b = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        drawDoc(Canvas(b))
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
        for (l in layers) l.draw(canvas, fonts)
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

        val s = selected ?: return
        selectionCorners(s)
        selPath.reset()
        selPath.moveTo(corners[0], corners[1])
        selPath.lineTo(corners[2], corners[3])
        selPath.lineTo(corners[4], corners[5])
        selPath.lineTo(corners[6], corners[7])
        selPath.close()
        canvas.drawPath(selPath, selPaint)
        val ty = (handleText.descent() + handleText.ascent()) / 2f
        for (i in 0 until 4) {
            val hx = corners[2 * i]
            val hy = corners[2 * i + 1]
            canvas.drawCircle(hx, hy, handleR, handlePaint)
            canvas.drawCircle(hx, hy, handleR, handleRing)
            canvas.drawText(handleGlyphs[i], hx, hy - ty, handleText)
        }
    }

    // ---------- touch ----------

    private fun toDocX(vx: Float) = (vx - offX) / fit
    private fun toDocY(vy: Float) = (vy - offY) / fit

    private fun hitTest(dx: Float, dy: Float): Layer? {
        val slop = 10f * density / fit
        for (i in layers.indices.reversed()) {
            if (layers[i].contains(dx, dy, fonts, slop)) return layers[i]
        }
        return null
    }

    /** Which corner handle (0..3) is under the finger, or -1. */
    private fun hitHandle(vx: Float, vy: Float): Int {
        val s = selected ?: return -1
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
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
                if (selected != null) {
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
                if (s != null && e.pointerCount >= 2) {
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
                mode = 0
            }
            MotionEvent.ACTION_CANCEL -> mode = 0
        }
        return true
    }

    @Suppress("unused")
    private fun maxSide() = max(doc.width, doc.height)
}

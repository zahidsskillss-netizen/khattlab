package com.ghoragraphics.khattlab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** The page: a photo or a solid color, plus its size in pixels. */
class DocBackground(val bitmap: Bitmap?, val color: Int, val width: Int, val height: Int)

/** A saved state for undo / redo. */
class Snapshot(val layers: List<TextLayer>, val bg: DocBackground, val selectedIndex: Int)

class EditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onSelectionChanged(layer: TextLayer?)
        fun onEditRequest(layer: TextLayer)
        fun onHistoryChanged()
    }

    lateinit var fonts: FontManager
    var listener: Listener? = null

    var doc = DocBackground(null, Color.WHITE, 1080, 1080)
        private set
    val layers = mutableListOf<TextLayer>()
    var selected: TextLayer? = null
        private set

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private val maxHistory = 40

    // view <-> document transform
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
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = gold
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
    private val selPath = Path()
    private val corners = FloatArray(8)
    private val docRect = RectF()

    // touch state
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var mode = 0 // 0 none, 1 drag, 2 pinch
    private var lastX = 0f
    private var lastY = 0f
    private var downVX = 0f
    private var downVY = 0f
    private var moved = false
    private var committed = false
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

    fun select(layer: TextLayer?) {
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

    fun update(commitFirst: Boolean = true, block: (TextLayer) -> Unit) {
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

    fun centerSelected() {
        update { it.x = doc.width / 2f; it.y = doc.height / 2f }
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
        val m = 16f * density
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateFit()

        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(fit, fit)
        canvas.clipRect(0f, 0f, doc.width.toFloat(), doc.height.toFloat())
        drawDoc(canvas)
        canvas.restore()
        canvas.drawRect(offX, offY, offX + doc.width * fit, offY + doc.height * fit, framePaint)

        val s = selected ?: return
        s.corners(fonts, corners)
        for (i in 0 until 4) {
            corners[2 * i] = offX + corners[2 * i] * fit
            corners[2 * i + 1] = offY + corners[2 * i + 1] * fit
        }
        selPath.reset()
        selPath.moveTo(corners[0], corners[1])
        selPath.lineTo(corners[2], corners[3])
        selPath.lineTo(corners[4], corners[5])
        selPath.lineTo(corners[6], corners[7])
        selPath.close()
        canvas.drawPath(selPath, selPaint)
        for (i in 0 until 4) canvas.drawCircle(corners[2 * i], corners[2 * i + 1], 4f * density, handlePaint)
    }

    // ---------- touch ----------

    private fun toDocX(vx: Float) = (vx - offX) / fit
    private fun toDocY(vy: Float) = (vy - offY) / fit

    private fun hitTest(dx: Float, dy: Float): TextLayer? {
        val slop = 14f * density / fit
        for (i in layers.indices.reversed()) {
            if (layers[i].contains(dx, dy, fonts, slop)) return layers[i]
        }
        return null
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gestures.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                committed = false
                moved = false
                downVX = e.x
                downVY = e.y
                val dx = toDocX(e.x)
                val dy = toDocY(e.y)
                val hit = hitTest(dx, dy)
                select(hit)
                if (hit != null) {
                    mode = 1
                    lastX = dx
                    lastY = dy
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
                val s = selected
                if (s != null && mode == 1) {
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
                } else if (s != null && mode == 2 && e.pointerCount >= 2) {
                    commitOnce()
                    s.scale = (startScale * spacing(e) / startDist).coerceIn(0.05f, 20f)
                    s.rotation = snapAngle(startRot + angle(e) - startAngle)
                    invalidate()
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = 0
            }
        }
        return true
    }
}

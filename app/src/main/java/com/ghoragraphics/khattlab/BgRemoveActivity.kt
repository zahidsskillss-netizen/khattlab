package com.ghoragraphics.khattlab

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Hands pictures to and from [BgRemoveActivity] (too big to put in an Intent). */
object BgHolder {
    var input: Bitmap? = null
    var output: Bitmap? = null
}

/**
 * Easy background removal:
 *  - Auto: Google's on-phone AI finds the person / object and removes the rest
 *  - Magic: tap a color, that area (and similar colors touching it) is removed
 *  - Erase / Restore: paint with the finger; two fingers move and zoom
 */
class BgRemoveActivity : Activity() {

    private lateinit var orig: Bitmap
    private lateinit var work: Bitmap
    private lateinit var view: EraseView
    private lateinit var busy: ProgressBar
    private val undo = ArrayDeque<Bitmap>()
    private var tool = TOOL_ERASE
    private var brush = 40
    private var tolerance = 40
    private lateinit var bold: Typeface

    private val bg = 0xFF141416.toInt()
    private val bar = 0xFF22222A.toInt()
    private val gold = 0xFFE8B547.toInt()

    companion object {
        const val TOOL_ERASE = 0
        const val TOOL_RESTORE = 1
        const val TOOL_MAGIC = 2
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inp = BgHolder.input
        BgHolder.input = null
        if (inp == null || inp.isRecycled) { finish(); return }
        orig = inp.copy(Bitmap.Config.ARGB_8888, false)
        work = inp.copy(Bitmap.Config.ARGB_8888, true)
        bold = try { Typeface.createFromAsset(assets, "ui/bahij_bold.ttf") } catch (e: Exception) { Typeface.DEFAULT_BOLD }
        window.statusBarColor = bar
        window.navigationBarColor = bar

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        // ---- top bar: close, title, undo, done ----
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(bar)
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        top.addView(iconBtn(R.drawable.ic_close) { setResult(RESULT_CANCELED); finish() })
        top.addView(TextView(this).apply {
            setText(R.string.t_remove_bg); textSize = 17f; setTextColor(Color.WHITE); typeface = bold
            setPadding(dp(8), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconBtn(R.drawable.ic_undo) { stepBack() })
        top.addView(iconBtn(R.drawable.ic_check, gold) { done() })
        root.addView(top)

        // ---- the picture ----
        val frame = FrameLayout(this)
        view = EraseView(this)
        frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        busy = ProgressBar(this).apply { visibility = View.GONE }
        frame.addView(busy, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---- tools ----
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bar)
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        root.addView(panel)
        setContentView(root)
        buildPanel(panel)
    }

    private fun buildPanel(panel: LinearLayout) {
        panel.removeAllViews()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = listOf(
            getString(R.string.bg_auto), getString(R.string.bg_magic), getString(R.string.bg_erase), getString(R.string.bg_restore)
        )
        val sel = when (tool) { TOOL_MAGIC -> 1; TOOL_ERASE -> 2; else -> 3 }
        names.forEachIndexed { i, n ->
            row.addView(chip(n, i == sel) {
                when (i) {
                    0 -> runAuto()
                    1 -> tool = TOOL_MAGIC
                    2 -> tool = TOOL_ERASE
                    3 -> tool = TOOL_RESTORE
                }
                buildPanel(panel)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
        panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        panel.addView(TextView(this).apply {
            setText(when (tool) {
                TOOL_MAGIC -> R.string.bg_hint_magic
                TOOL_RESTORE -> R.string.bg_hint_restore
                else -> R.string.bg_hint_erase
            })
            textSize = 12.5f; setTextColor(0xB3FFFFFF.toInt()); setPadding(0, dp(8), 0, dp(4))
        })
        if (tool == TOOL_MAGIC) {
            panel.addView(seek(R.string.bg_tolerance, 1, 120, tolerance) { tolerance = it })
        } else {
            panel.addView(seek(R.string.bg_brush, 5, 150, brush) { brush = it })
        }
    }

    private fun iconBtn(icon: Int, tint: Int = Color.WHITE, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tint)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }

    private fun chip(text: String, on: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 14f
        typeface = bold
        setTextColor(if (on) bg else Color.WHITE)
        setPadding(dp(16), dp(8), dp(16), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            if (on) setColor(gold) else { setColor(0x1AFFFFFF); setStroke(dp(1), 0x40FFFFFF) }
        }
        setOnClickListener { onClick() }
    }

    private fun seek(labelRes: Int, minV: Int, maxV: Int, value: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tv = TextView(this).apply {
            text = getString(labelRes) + ": " + value
            setTextColor(Color.WHITE); textSize = 13.5f
        }
        box.addView(tv, LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(SeekBar(this).apply {
            max = maxV - minV
            progress = value - minV
            progressTintList = ColorStateList.valueOf(gold)
            thumbTintList = ColorStateList.valueOf(gold)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    val v = p + minV
                    tv.text = getString(labelRes) + ": " + v
                    onChange(v)
                    view.invalidate()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return box
    }

    // ---------- undo ----------

    private fun remember() {
        undo.addLast(work.copy(Bitmap.Config.ARGB_8888, true))
        // big pictures use a lot of memory: keep fewer steps
        val maxSteps = if (work.width * work.height > 2_500_000) 4 else 10
        while (undo.size > maxSteps) undo.removeFirst().recycle()
    }

    private fun stepBack() {
        val b = undo.removeLastOrNull() ?: return
        work = b
        view.invalidate()
    }

    private fun done() {
        BgHolder.output = work
        setResult(RESULT_OK)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
    }

    // ---------- auto (AI) ----------

    /**
     * Auto removal, in this order:
     *  1. Google's object AI (people, animals, things), when Google Play services has it on the phone
     *  2. otherwise the AI built into this app (people only), which works without internet,
     *     while the object AI is asked to download for next time
     */
    private fun runAuto() {
        if (busy.visibility == View.VISIBLE) return
        busy.visibility = View.VISIBLE
        toast(R.string.bg_working)
        val seg = try {
            com.google.mlkit.vision.segmentation.subject.SubjectSegmentation.getClient(
                com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .build()
            )
        } catch (e: Throwable) { null }
        if (seg == null) { runSelfie(); return }
        val installer = try { com.google.android.gms.common.moduleinstall.ModuleInstall.getClient(this) } catch (e: Throwable) { null }
        if (installer == null) { runSubject(seg, true); return }
        try {
            installer.areModulesAvailable(seg)
                .addOnSuccessListener { r ->
                    if (r.areModulesAvailable()) runSubject(seg, true)
                    else { seg.close(); requestObjectAi(installer); runSelfie() }
                }
                .addOnFailureListener { seg.close(); runSelfie() }
        } catch (e: Throwable) {
            seg.close()
            runSelfie()
        }
    }

    private fun runSubject(seg: com.google.mlkit.vision.segmentation.subject.SubjectSegmenter, fallback: Boolean) {
        try {
            seg.process(com.google.mlkit.vision.common.InputImage.fromBitmap(orig, 0))
                .addOnSuccessListener { r ->
                    seg.close()
                    val fg = r.foregroundBitmap
                    if (fg == null) {
                        if (fallback) runSelfie() else { busy.visibility = View.GONE; toast(R.string.bg_nothing) }
                    } else {
                        busy.visibility = View.GONE
                        remember()
                        work = if (fg.width == orig.width && fg.height == orig.height) fg.copy(Bitmap.Config.ARGB_8888, true)
                        else Bitmap.createScaledBitmap(fg, orig.width, orig.height, true).copy(Bitmap.Config.ARGB_8888, true)
                        view.invalidate()
                        toast(R.string.bg_auto_done)
                    }
                }
                .addOnFailureListener {
                    seg.close()
                    if (fallback) runSelfie() else { busy.visibility = View.GONE; Toast.makeText(this, R.string.bg_auto_fail, Toast.LENGTH_LONG).show() }
                }
        } catch (e: Throwable) {
            seg.close()
            runSelfie()
        }
    }

    /** Asks Google Play services to download the object AI now (a sideloaded APK never gets it at install). */
    private fun requestObjectAi(installer: com.google.android.gms.common.moduleinstall.ModuleInstallClient) {
        try {
            val seg = com.google.mlkit.vision.segmentation.subject.SubjectSegmentation.getClient(
                com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
            )
            var lastShown = -20
            val listener = com.google.android.gms.common.moduleinstall.InstallStatusListener { u ->
                val p = u.progressInfo
                if (p != null && p.totalBytesToDownload > 0) {
                    val pct = (p.bytesDownloaded * 100 / p.totalBytesToDownload).toInt()
                    if (pct - lastShown >= 25 && !isFinishing) {
                        lastShown = pct
                        Toast.makeText(this, getString(R.string.bg_ai_downloading, pct), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val req = com.google.android.gms.common.moduleinstall.ModuleInstallRequest.newBuilder()
                .addApi(seg)
                .setListener(listener)
                .build()
            installer.installModules(req)
                .addOnCompleteListener { seg.close() }
        } catch (e: Throwable) {
            // no Google Play services: the built-in AI is used
        }
    }

    /** The AI built into the app: finds people, works without internet. */
    private fun runSelfie() {
        try {
            // a smaller copy is enough for the mask, and much faster
            val k = min(1f, 1024f / max(orig.width, orig.height))
            val small = if (k < 1f) Bitmap.createScaledBitmap(orig, max(1, (orig.width * k).roundToInt()), max(1, (orig.height * k).roundToInt()), true) else orig
            val seg = com.google.mlkit.vision.segmentation.Segmentation.getClient(
                com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions.Builder()
                    .setDetectorMode(com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build()
            )
            seg.process(com.google.mlkit.vision.common.InputImage.fromBitmap(small, 0))
                .addOnSuccessListener { mask ->
                    seg.close()
                    val mw = mask.width
                    val mh = mask.height
                    val buf = mask.buffer
                    buf.rewind()
                    val alpha = IntArray(mw * mh)
                    var found = 0
                    for (i in 0 until mw * mh) {
                        val c = buf.float
                        // a soft but clean edge around the person
                        val a = ((c - 0.3f) / 0.4f).coerceIn(0f, 1f)
                        if (a > 0.5f) found++
                        alpha[i] = ((a * 255f).roundToInt() shl 24)
                    }
                    if (found < mw * mh / 200) {
                        busy.visibility = View.GONE
                        Toast.makeText(this, R.string.bg_auto_fail, Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    val m = Bitmap.createBitmap(alpha, mw, mh, Bitmap.Config.ARGB_8888)
                    val out = orig.copy(Bitmap.Config.ARGB_8888, true)
                    val cv = Canvas(out)
                    val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    }
                    cv.drawBitmap(m, null, android.graphics.RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), p)
                    m.recycle()
                    busy.visibility = View.GONE
                    remember()
                    work = out
                    view.invalidate()
                    toast(R.string.bg_ai_offline)
                }
                .addOnFailureListener {
                    seg.close()
                    busy.visibility = View.GONE
                    Toast.makeText(this, R.string.bg_auto_fail, Toast.LENGTH_LONG).show()
                }
        } catch (e: Throwable) {
            busy.visibility = View.GONE
            Toast.makeText(this, R.string.bg_auto_fail, Toast.LENGTH_LONG).show()
        }
    }

    private fun toast(r: Int) = Toast.makeText(this, r, Toast.LENGTH_SHORT).show()

    // ---------- magic: remove the touched color area ----------

    private fun magicAt(px: Int, py: Int) {
        if (px !in 0 until work.width || py !in 0 until work.height) return
        if (busy.visibility == View.VISIBLE) return
        busy.visibility = View.VISIBLE
        remember()
        val w = work.width
        val h = work.height
        val tol = tolerance
        val src = work
        Thread {
            val px0 = IntArray(w * h)
            src.getPixels(px0, 0, w, 0, 0, w, h)
            val seed = px0[py * w + px]
            val sr = Color.red(seed); val sg = Color.green(seed); val sb = Color.blue(seed)
            val limit = tol * tol * 3
            val seen = BooleanArray(w * h)
            val stack = IntArray(w * h)
            var top = 0
            stack[top++] = py * w + px
            seen[py * w + px] = true
            var removed = 0
            while (top > 0) {
                val i = stack[--top]
                val c = px0[i]
                if (Color.alpha(c) == 0) continue
                val dr = Color.red(c) - sr; val dg = Color.green(c) - sg; val db = Color.blue(c) - sb
                val d = dr * dr + dg * dg + db * db
                if (d > limit) {
                    // the edge: soften it a little instead of a hard cut
                    if (d < limit * 2) px0[i] = (c and 0x00FFFFFF) or ((Color.alpha(c) / 2) shl 24)
                    continue
                }
                px0[i] = 0
                removed++
                val x = i % w
                val y = i / w
                if (x > 0 && !seen[i - 1]) { seen[i - 1] = true; stack[top++] = i - 1 }
                if (x < w - 1 && !seen[i + 1]) { seen[i + 1] = true; stack[top++] = i + 1 }
                if (y > 0 && !seen[i - w]) { seen[i - w] = true; stack[top++] = i - w }
                if (y < h - 1 && !seen[i + w]) { seen[i + w] = true; stack[top++] = i + w }
            }
            runOnUiThread {
                busy.visibility = View.GONE
                if (removed > 0 && src === work) {
                    work.setPixels(px0, 0, w, 0, 0, w, h)
                    view.invalidate()
                }
            }
        }.start()
    }

    // ---------- the picture view ----------

    @SuppressLint("ViewConstructor")
    inner class EraseView(c: Context) : View(c) {
        private val m = Matrix()
        private val inv = Matrix()
        private var zoom = 1f
        private var panX = 0f
        private var panY = 0f
        private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val restorer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
        private val checker: Paint by lazy {
            val s = (10 * resources.displayMetrics.density).roundToInt()
            val b = Bitmap.createBitmap(s * 2, s * 2, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            cv.drawColor(0xFFEDEDF0.toInt())
            val p = Paint().apply { color = 0xFFC9C9CF.toInt() }
            cv.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
            cv.drawRect(s.toFloat(), s.toFloat(), 2f * s, 2f * s, p)
            Paint().apply { shader = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        }

        // touch
        private var lastX = 0f
        private var lastY = 0f
        private var fingerX = -1f
        private var fingerY = -1f
        private var painting = false
        private var twoFinger = false
        private var startDist = 1f
        private var startZoom = 1f
        private var midX = 0f
        private var midY = 0f
        private var downX = 0f
        private var downY = 0f
        private var movedFar = false

        private fun baseFit(): Float {
            val pad = 16f * resources.displayMetrics.density
            return min((width - 2 * pad) / work.width, (height - 2 * pad) / work.height).coerceAtLeast(0.01f)
        }

        private fun buildMatrix() {
            val f = baseFit() * zoom
            m.reset()
            m.postScale(f, f)
            m.postTranslate((width - work.width * f) / 2f + panX, (height - work.height * f) / 2f + panY)
            m.invert(inv)
        }

        override fun onDraw(canvas: Canvas) {
            buildMatrix()
            val f = baseFit() * zoom
            val l = (width - work.width * f) / 2f + panX
            val t = (height - work.height * f) / 2f + panY
            canvas.drawRect(l, t, l + work.width * f, t + work.height * f, checker)
            canvas.drawBitmap(work, m, bmpPaint)
            if (fingerX >= 0f && tool != TOOL_MAGIC) {
                ring.strokeWidth = 1.5f * resources.displayMetrics.density
                ring.color = if (tool == TOOL_ERASE) 0xFFFF5252.toInt() else 0xFF69F0AE.toInt()
                canvas.drawCircle(fingerX, fingerY, brush * resources.displayMetrics.density / 2f, ring)
            }
        }

        private fun toBmp(x: Float, y: Float): FloatArray {
            val p = floatArrayOf(x, y)
            inv.mapPoints(p)
            return p
        }

        private fun stroke(x0: Float, y0: Float, x1: Float, y1: Float) {
            val a = toBmp(x0, y0)
            val b = toBmp(x1, y1)
            val f = baseFit() * zoom
            val wpx = brush * resources.displayMetrics.density / f
            val cv = Canvas(work)
            if (tool == TOOL_ERASE) {
                eraser.strokeWidth = wpx
                if (a[0] == b[0] && a[1] == b[1]) cv.drawPoint(a[0], a[1], eraser) else cv.drawLine(a[0], a[1], b[0], b[1], eraser)
            } else {
                restorer.strokeWidth = wpx
                restorer.shader = BitmapShader(orig, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                restorer.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
                if (a[0] == b[0] && a[1] == b[1]) cv.drawPoint(a[0], a[1], restorer) else cv.drawLine(a[0], a[1], b[0], b[1], restorer)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    twoFinger = false
                    painting = false
                    movedFar = false
                    lastX = e.x; lastY = e.y; downX = e.x; downY = e.y
                    fingerX = e.x; fingerY = e.y
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                    // second finger: move / zoom, and undo any paint the first finger just started
                    if (painting) stepBack()
                    painting = false
                    twoFinger = true
                    fingerX = -1f
                    startDist = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)).coerceAtLeast(1f)
                    startZoom = zoom
                    midX = (e.getX(0) + e.getX(1)) / 2f
                    midY = (e.getY(0) + e.getY(1)) / 2f
                }
                MotionEvent.ACTION_MOVE -> {
                    if (twoFinger && e.pointerCount >= 2) {
                        val d = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                        zoom = (startZoom * d / startDist).coerceIn(0.5f, 12f)
                        val mx = (e.getX(0) + e.getX(1)) / 2f
                        val my = (e.getY(0) + e.getY(1)) / 2f
                        panX += mx - midX
                        panY += my - midY
                        midX = mx; midY = my
                        invalidate()
                    } else if (!twoFinger && tool != TOOL_MAGIC) {
                        if (!painting) {
                            if (abs(e.x - downX) + abs(e.y - downY) < 6f * resources.displayMetrics.density) return true
                            painting = true
                            remember()
                            stroke(downX, downY, downX, downY)
                            lastX = downX; lastY = downY
                        }
                        stroke(lastX, lastY, e.x, e.y)
                        lastX = e.x; lastY = e.y
                        fingerX = e.x; fingerY = e.y
                        invalidate()
                    } else if (abs(e.x - downX) + abs(e.y - downY) > 12f * resources.displayMetrics.density) {
                        movedFar = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!twoFinger) {
                        if (tool == TOOL_MAGIC) {
                            if (!movedFar) {
                                buildMatrix()
                                val p = toBmp(e.x, e.y)
                                magicAt(p[0].toInt(), p[1].toInt())
                            }
                        } else if (!painting) {
                            // a tap paints one dot
                            remember()
                            stroke(e.x, e.y, e.x, e.y)
                        }
                    }
                    painting = false
                    fingerX = -1f
                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> { painting = false; fingerX = -1f; invalidate() }
            }
            return true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        undo.forEach { if (it !== work) it.recycle() }
        undo.clear()
    }
}

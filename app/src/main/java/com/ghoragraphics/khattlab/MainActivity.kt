package com.ghoragraphics.khattlab

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity(), EditorView.Listener {

    companion object {
        private const val REQ_IMAGE = 11
        private const val REQ_FONT = 12
        private const val REQ_WRITE = 13
        private val SWATCHES = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFE8B547.toInt(), 0xFFFFD54F.toInt(),
            0xFFFF9800.toInt(), 0xFFF44336.toInt(), 0xFFB71C1C.toInt(), 0xFFE91E63.toInt(),
            0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(),
            0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF4CAF50.toInt(),
            0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(), 0xFF795548.toInt(), 0xFF9E9E9E.toInt(),
            0xFF607D8B.toInt(), 0xFF263238.toInt(), 0xFF1B5E20.toInt(), 0xFF0D47A1.toInt()
        )
    }

    private lateinit var editor: EditorView
    private lateinit var fonts: FontManager
    private lateinit var tools: LinearLayout
    private lateinit var panelHost: FrameLayout
    private lateinit var btnUndo: View
    private lateinit var btnRedo: View

    private var activeTool: String? = null
    private val gold = 0xFFE8B547.toInt()
    private val textMain = 0xFFF2F2F5.toInt()
    private val textDim = 0xFFA8A8B3.toInt()

    // ================= life cycle =================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fonts = FontManager(this)
        editor = findViewById(R.id.editor)
        editor.fonts = fonts
        editor.listener = this

        tools = findViewById(R.id.tools)
        panelHost = findViewById(R.id.panel_host)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)

        btnUndo.setOnClickListener { editor.undo() }
        btnRedo.setOnClickListener { editor.redo() }
        findViewById<View>(R.id.btn_save).setOnClickListener { saveImage() }
        findViewById<View>(R.id.btn_share).setOnClickListener { shareImage() }

        refreshTools()
        onHistoryChanged()
        handleIncoming(intent)
    }

    /** A photo shared into KhattLab from the gallery or another app. */
    private fun handleIncoming(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri: Uri? = i.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri != null) loadPhoto(uri)
    }

    override fun onBackPressed() {
        when {
            activeTool != null && editor.selected == null -> closePanel()
            editor.selected != null -> editor.select(null)
            editor.layers.isNotEmpty() || editor.doc.bitmap != null -> {
                AlertDialog.Builder(this)
                    .setMessage(R.string.exit_confirm)
                    .setPositiveButton(R.string.exit) { _, _ -> finish() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    // ================= editor callbacks =================

    private val mainPanels = listOf("bgcolor", "canvas")

    override fun onSelectionChanged(layer: TextLayer?) {
        val key = activeTool
        when {
            key == null -> refreshTools()
            layer == null && key !in mainPanels -> closePanel()
            layer != null && key in mainPanels -> closePanel()
            else -> openPanel(key) // same panel, now for the new selection
        }
    }

    override fun onEditRequest(layer: TextLayer) {
        promptText(layer)
    }

    override fun onHistoryChanged() {
        btnUndo.alpha = if (editor.canUndo()) 1f else 0.35f
        btnRedo.alpha = if (editor.canRedo()) 1f else 0.35f
    }

    // ================= small helpers =================

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun ripple(): Drawable? {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        return if (tv.resourceId != 0) getDrawable(tv.resourceId) else null
    }

    private fun circle(color: Int, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(if (selected) 3 else 1), if (selected) gold else 0x55FFFFFF)
    }

    private fun pill(color: Int, strokeColor: Int) = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(color)
        setStroke(dp(1), strokeColor)
    }

    // ================= bottom toolbar =================

    private fun toolButton(key: String, glyph: String, labelRes: Int, onClick: () -> Unit): View {
        val active = key == activeTool
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(66)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = ripple()
            isClickable = true
            isFocusable = true
        }
        val g = TextView(this).apply {
            text = glyph
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(if (active) gold else textMain)
        }
        val l = TextView(this).apply {
            setText(labelRes)
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(if (active) gold else textDim)
        }
        box.addView(g)
        box.addView(l)
        box.setOnClickListener { onClick() }
        return box
    }

    private fun panelTool(key: String, glyph: String, labelRes: Int) =
        toolButton(key, glyph, labelRes) { togglePanel(key) }

    /** Rebuilds the bottom toolbar: page tools, or text tools when a text is selected. */
    private fun refreshTools() {
        val items = if (editor.selected == null) listOf(
            toolButton("add", "T+", R.string.t_add_text) { promptText(null) },
            toolButton("photo", "▣", R.string.t_photo) { pickImage() },
            panelTool("bgcolor", "◐", R.string.t_bg_color),
            panelTool("canvas", "⬚", R.string.t_canvas),
            toolButton("import", "Aa+", R.string.t_import_font) { importFont() },
            toolButton("new", "✦", R.string.t_new) { confirmNew() },
            toolButton("about", "ⓘ", R.string.t_about) { showAbout() }
        ) else listOf(
            toolButton("edit", "✎", R.string.t_edit) { editor.selected?.let { promptText(it) } },
            panelTool("font", "Aa", R.string.t_font),
            panelTool("color", "●", R.string.t_color),
            panelTool("size", "⤢", R.string.t_size),
            panelTool("stroke", "◯", R.string.t_stroke),
            panelTool("shadow", "◩", R.string.t_shadow),
            panelTool("opacity", "◌", R.string.t_opacity),
            panelTool("align", "≡", R.string.t_align),
            panelTool("spacing", "↔", R.string.t_spacing),
            panelTool("highlight", "▬", R.string.t_highlight),
            panelTool("order", "⇅", R.string.t_order),
            toolButton("copy", "⧉", R.string.t_copy) { editor.duplicateSelected() },
            toolButton("delete", "✕", R.string.t_delete) { editor.deleteSelected() },
            toolButton("done", "✓", R.string.t_done) { editor.select(null) }
        )
        tools.removeAllViews()
        items.forEach { tools.addView(it) }
    }

    // ================= option panels =================

    private fun togglePanel(key: String) {
        if (activeTool == key) closePanel() else openPanel(key)
    }

    private fun closePanel() {
        activeTool = null
        panelHost.removeAllViews()
        panelHost.visibility = View.GONE
        refreshTools()
    }

    private fun openPanel(key: String) {
        activeTool = key
        panelHost.removeAllViews()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val s = editor.selected

        when (key) {
            "bgcolor" -> box.addView(swatchRow(editor.doc.color, false) { editor.setDocColor(it) })

            "canvas" -> {
                val sizes = listOf(
                    "1:1" to Pair(1080, 1080),
                    "4:5" to Pair(1080, 1350),
                    "9:16" to Pair(1080, 1920),
                    "16:9" to Pair(1920, 1080),
                    "3:4" to Pair(1080, 1440),
                    "A4" to Pair(1240, 1754)
                )
                box.addView(chipRow(sizes.map { it.first }) { i ->
                    val p = sizes[i].second
                    editor.setDocSize(p.first, p.second)
                })
            }

            "font" -> box.addView(fontRow())

            "color" -> if (s != null) box.addView(swatchRow(s.color, false) { c -> editor.update { it.color = c } })

            "size" -> if (s != null) {
                box.addView(slider(R.string.p_size, 5, 800, (s.scale * 100).roundToInt(), { "$it%" }) { v ->
                    editor.update(false) { it.scale = v / 100f }
                })
                box.addView(slider(R.string.p_rotation, -180, 180, s.rotation.roundToInt(), { "$it°" }) { v ->
                    editor.update(false) { it.rotation = v.toFloat() }
                })
                box.addView(chipRow(listOf(getString(R.string.p_center), getString(R.string.p_reset_rotation))) { i ->
                    if (i == 0) editor.centerSelected() else editor.update { it.rotation = 0f }
                    openPanel("size")
                })
            }

            "stroke" -> if (s != null) {
                box.addView(slider(R.string.p_width, 0, 60, s.strokeWidth.roundToInt(), { "$it" }) { v ->
                    editor.update(false) { it.strokeWidth = v.toFloat() }
                })
                box.addView(swatchRow(s.strokeColor, false) { c ->
                    editor.update {
                        it.strokeColor = c
                        if (it.strokeWidth <= 0f) it.strokeWidth = (it.textSize * 0.06f).coerceAtLeast(2f)
                    }
                    openPanel("stroke")
                })
            }

            "shadow" -> if (s != null) {
                box.addView(slider(R.string.p_blur, 0, 60, s.shadowRadius.roundToInt(), { "$it" }) { v ->
                    editor.update(false) { it.shadowRadius = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_x, -60, 60, s.shadowDx.roundToInt(), { "$it" }) { v ->
                    editor.update(false) { it.shadowDx = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_y, -60, 60, s.shadowDy.roundToInt(), { "$it" }) { v ->
                    editor.update(false) { it.shadowDy = v.toFloat() }
                })
                box.addView(swatchRow(s.shadowColor, false) { c ->
                    editor.update {
                        it.shadowColor = (c and 0x00FFFFFF) or (0xAA shl 24)
                        if (it.shadowRadius <= 0f && it.shadowDx == 0f && it.shadowDy == 0f) {
                            it.shadowRadius = (it.textSize * 0.1f).coerceAtLeast(4f)
                            it.shadowDy = (it.textSize * 0.04f).coerceAtLeast(2f)
                        }
                    }
                    openPanel("shadow")
                })
            }

            "opacity" -> if (s != null) {
                box.addView(slider(R.string.p_opacity, 0, 100, (s.opacity * 100f / 255f).roundToInt(), { "$it%" }) { v ->
                    editor.update(false) { it.opacity = (v * 255f / 100f).roundToInt() }
                })
            }

            "align" -> if (s != null) {
                val names = listOf(getString(R.string.p_align_start), getString(R.string.p_align_center), getString(R.string.p_align_end))
                box.addView(chipRow(names, s.align) { i ->
                    editor.update { it.align = i }
                    openPanel("align")
                })
            }

            "spacing" -> if (s != null) {
                box.addView(slider(R.string.p_letter_spacing, -20, 100, (s.letterSpacing * 100).roundToInt(), { "$it" }) { v ->
                    editor.update(false) { it.letterSpacing = v / 100f }
                })
                box.addView(slider(R.string.p_line_spacing, 50, 300, (s.lineSpacing * 100).roundToInt(), { "$it%" }) { v ->
                    editor.update(false) { it.lineSpacing = v / 100f }
                })
            }

            "highlight" -> if (s != null) {
                box.addView(swatchRow(s.highlightColor, true) { c ->
                    editor.update { it.highlightColor = c }
                    openPanel("highlight")
                })
                box.addView(slider(R.string.p_padding, 0, 120, s.highlightPadding.roundToInt(), { "$it" }) { v ->
                    editor.update(false) { it.highlightPadding = v.toFloat() }
                })
            }

            "order" -> if (s != null) {
                box.addView(chipRow(listOf(getString(R.string.p_bring_forward), getString(R.string.p_send_backward))) { i ->
                    editor.moveSelected(i == 0)
                })
            }
        }

        panelHost.addView(box)
        panelHost.visibility = View.VISIBLE
        refreshTools()
    }

    // ---------- panel widgets ----------

    private fun slider(labelRes: Int, min: Int, max: Int, value: Int, format: (Int) -> String, onChange: (Int) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val label = TextView(this).apply {
            setText(labelRes)
            setTextColor(textDim)
            textSize = 13f
        }
        val valueText = TextView(this).apply {
            setTextColor(textMain)
            textSize = 13f
            gravity = Gravity.CENTER
            text = format(value.coerceIn(min, max))
            textDirection = View.TEXT_DIRECTION_LTR
        }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = value.coerceIn(min, max) - min
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(s: SeekBar) {
                editor.commit()
            }
            override fun onStopTrackingTouch(s: SeekBar) {}
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                val v = p + min
                valueText.text = format(v)
                if (fromUser) onChange(v)
            }
        })
        row.addView(label, LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(valueText, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun horizontalRow(): Pair<HorizontalScrollView, LinearLayout> {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(4), 0, dp(4))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(row)
        return scroll to row
    }

    private fun swatchRow(current: Int, allowNone: Boolean, onPick: (Int) -> Unit): View {
        val (scroll, row) = horizontalRow()
        val size = dp(36)
        val margin = dp(5)
        val swatchViews = mutableListOf<Pair<View, Int>>()

        fun refreshMarks(chosen: Int) {
            for ((v, c) in swatchViews) v.background = circle(c, c == chosen)
        }

        fun add(v: View) {
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(margin, 0, margin, 0)
            row.addView(v, lp)
        }

        // custom color
        val custom = TextView(this).apply {
            text = "+"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            background = circle(0xFF2A2A30.toInt(), false)
            setOnClickListener {
                ColorPicker.show(this@MainActivity, if (Color.alpha(current) == 0) Color.WHITE else current) { c ->
                    onPick(c)
                    refreshMarks(c)
                }
            }
        }
        add(custom)

        if (allowNone) {
            val none = TextView(this).apply {
                text = "∅"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(textMain)
                background = circle(0xFF2A2A30.toInt(), Color.alpha(current) == 0)
                setOnClickListener { onPick(Color.TRANSPARENT) }
            }
            add(none)
        }

        for (c in SWATCHES) {
            val v = View(this)
            v.background = circle(c, c == current)
            v.setOnClickListener {
                onPick(c)
                refreshMarks(c)
            }
            swatchViews.add(v to c)
            add(v)
        }
        return scroll
    }

    private fun chipRow(names: List<String>, selectedIndex: Int = -1, onPick: (Int) -> Unit): View {
        val (scroll, row) = horizontalRow()
        names.forEachIndexed { i, name ->
            val chip = TextView(this).apply {
                text = name
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(8), dp(16), dp(8))
                val on = i == selectedIndex
                setTextColor(if (on) 0xFF141416.toInt() else textMain)
                background = pill(if (on) gold else 0xFF2A2A30.toInt(), if (on) gold else 0x33FFFFFF)
                setOnClickListener { onPick(i) }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            row.addView(chip, lp)
        }
        return scroll
    }

    private fun fontRow(): View {
        val (scroll, row) = horizontalRow()
        val currentKey = editor.selected?.fontKey

        val add = TextView(this).apply {
            text = getString(R.string.font_add)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextColor(gold)
            background = pill(0xFF2A2A30.toInt(), gold)
            setOnClickListener { importFont() }
        }
        val lpAdd = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpAdd.setMargins(dp(4), 0, dp(4), 0)
        row.addView(add, lpAdd)

        for (f in fonts.fonts) {
            val on = f.key == currentKey
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = pill(if (on) 0xFF3A3220.toInt() else 0xFF2A2A30.toInt(), if (on) gold else 0x33FFFFFF)
                setOnClickListener { editor.update { it.fontKey = f.key }; openPanel("font") }
            }
            val sample = TextView(this).apply {
                text = getString(R.string.font_sample)
                typeface = f.typeface
                textSize = 18f
                setTextColor(textMain)
                gravity = Gravity.CENTER
            }
            val name = TextView(this).apply {
                text = f.label
                textSize = 10f
                setTextColor(if (on) gold else textDim)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            item.addView(sample)
            item.addView(name)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            row.addView(item, lp)
        }
        return scroll
    }

    // ================= dialogs =================

    private fun promptText(layer: TextLayer?) {
        val et = EditText(this).apply {
            setText(layer?.text ?: "")
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            setHint(R.string.text_hint)
        }
        val wrap = FrameLayout(this)
        wrap.setPadding(dp(20), dp(8), dp(20), 0)
        wrap.addView(et)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (layer == null) R.string.t_add_text else R.string.t_edit)
            .setView(wrap)
            .setPositiveButton(R.string.ok) { _, _ ->
                val t = et.text.toString()
                if (t.isNotBlank()) {
                    if (layer == null) editor.addText(t) else editor.update { it.text = t }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        et.requestFocus()
    }

    private fun confirmNew() {
        AlertDialog.Builder(this)
            .setMessage(R.string.new_confirm)
            .setPositiveButton(R.string.ok) { _, _ -> editor.newDoc() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        val tv = TextView(this).apply {
            text = getString(R.string.about_text, version)
            textSize = 15f
            setTextColor(textMain)
            setLineSpacing(0f, 1.3f)
            setPadding(dp(24), dp(16), dp(24), dp(8))
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setView(tv)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    // ================= files =================

    private fun pickImage() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(i, getString(R.string.t_photo)), REQ_IMAGE)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.err_no_app)
        }
    }

    private fun importFont() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(i, REQ_FONT)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.err_no_app)
        }
    }

    private fun loadPhoto(uri: Uri) {
        toast(R.string.loading)
        Thread {
            val bmp = ImageIO.loadBitmap(this, uri, 2560)
            runOnUiThread {
                if (bmp == null) toast(R.string.err_image) else editor.setPhoto(bmp)
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_IMAGE -> loadPhoto(uri)
            REQ_FONT -> {
                val item = fonts.importFont(uri)
                if (item == null) {
                    toast(R.string.err_font)
                } else {
                    toast(R.string.font_added)
                    if (editor.selected != null) {
                        editor.update { it.fontKey = item.key }
                        openPanel("font")
                    }
                }
            }
        }
    }

    private fun needsWritePermission(): Boolean =
        Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    private fun saveImage() {
        if (needsWritePermission()) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_WRITE)
            return
        }
        val bmp = try {
            editor.render()
        } catch (e: OutOfMemoryError) {
            toast(R.string.err_save)
            return
        }
        val name = "KhattLab_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        Thread {
            val ok = ImageIO.saveToGallery(this, bmp, name)
            runOnUiThread { toast(if (ok) R.string.saved else R.string.err_save) }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_WRITE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) saveImage()
            else toast(R.string.err_permission)
        }
    }

    private fun shareImage() {
        val bmp = try {
            editor.render()
        } catch (e: OutOfMemoryError) {
            toast(R.string.err_save)
            return
        }
        Thread {
            val uri = ImageIO.writeShareFile(this, bmp)
            runOnUiThread {
                if (uri == null) {
                    toast(R.string.err_save)
                    return@runOnUiThread
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(Intent.createChooser(send, getString(R.string.share)))
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.err_no_app)
                }
            }
        }.start()
    }
}

package com.ghoragraphics.khattlab

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity(), EditorView.Listener {

    companion object {
        private const val REQ_BG_IMAGE = 11
        private const val REQ_FONT = 12
        private const val REQ_WRITE = 13
        private const val REQ_ADD_IMAGE = 14
        private const val REQ_TEXTURE = 15
        private const val REQ_REPLACE = 16
        private val SWATCHES = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFE8B547.toInt(), 0xFFFFD54F.toInt(),
            0xFFFF9800.toInt(), 0xFFF44336.toInt(), 0xFFB71C1C.toInt(), 0xFFE91E63.toInt(),
            0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(),
            0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF4CAF50.toInt(),
            0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(), 0xFF795548.toInt(), 0xFF9E9E9E.toInt(),
            0xFF607D8B.toInt(), 0xFF263238.toInt(), 0xFF1B5E20.toInt(), 0xFF0D47A1.toInt()
        )

        private val MAIN_PANELS = setOf("bgcolor", "canvas", "projects")
        private val COMMON_PANELS = setOf("position", "relpos", "size", "opacity", "rotate", "persp", "rot3d", "reflect")
        private val TEXT_PANELS = COMMON_PANELS + setOf(
            "styles", "font", "style", "color", "texture", "curve", "background", "align", "spacing",
            "stroke", "shadow", "inner", "emboss", "depth", "floor"
        )
        private val IMAGE_PANELS = COMMON_PANELS + setOf("flip", "round", "border", "imgshadow")
    }

    private lateinit var editor: EditorView
    private lateinit var fonts: FontManager
    private lateinit var tools: LinearLayout
    private lateinit var panelHost: FrameLayout
    private lateinit var btnUndo: View
    private lateinit var btnRedo: View
    private lateinit var btnGrid: TextView

    private var activeTool: String? = null
    private var panelScroll: ScrollView? = null

    // ---- projects ----
    private lateinit var projects: ProjectStore
    private var currentId: String? = null
    private var currentName = ""
    private var dirty = false
    private val gold = 0xFFE8B547.toInt()
    private val textMain = 0xFFF2F2F5.toInt()
    private val textDim = 0xFFA8A8B3.toInt()
    private val chipBg = 0xFF2A2A30.toInt()

    private val st: TextLayer? get() = editor.selected as? TextLayer
    private val si: ImageLayer? get() = editor.selected as? ImageLayer

    // ================= life cycle =================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fonts = FontManager(this)
        projects = ProjectStore(this)
        editor = findViewById(R.id.editor)
        editor.fonts = fonts
        editor.listener = this

        tools = findViewById(R.id.tools)
        panelHost = findViewById(R.id.panel_host)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        btnGrid = findViewById(R.id.btn_grid)

        btnUndo.setOnClickListener { editor.undo() }
        btnRedo.setOnClickListener { editor.redo() }
        btnGrid.setOnClickListener {
            editor.showGrid = !editor.showGrid
            btnGrid.setTextColor(if (editor.showGrid) gold else textMain)
        }
        findViewById<View>(R.id.btn_save).setOnClickListener { saveImage() }
        findViewById<View>(R.id.btn_share).setOnClickListener { shareImage() }

        refreshTools()
        onHistoryChanged()
        handleIncoming(intent)
    }

    /** A photo shared into NawakhtGar from the gallery or another app. */
    private fun handleIncoming(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri: Uri? = i.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri != null) loadBitmapThen(uri, 2560) { editor.setPhoto(it) }
    }

    override fun onPause() {
        super.onPause()
        // never lose work: whatever is on the page is kept as a project
        if (dirty && !editor.isBlank()) saveProject(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            activeTool != null -> closePanel()
            editor.selected != null -> editor.select(null)
            editor.layers.isNotEmpty() || editor.doc.bitmap != null -> {
                // the work is kept in the projects, so leaving is safe
                saveProject(true) { finish() }
            }
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    // ================= editor callbacks =================

    private fun panelFits(key: String, layer: Layer?): Boolean = when (layer) {
        null -> key in MAIN_PANELS
        is TextLayer -> key in TEXT_PANELS
        is ImageLayer -> key in IMAGE_PANELS
        else -> false
    }

    override fun onSelectionChanged(layer: Layer?) {
        val key = activeTool
        when {
            key == null -> refreshTools()
            !panelFits(key, layer) -> closePanel()
            else -> openPanel(key) // same panel, now for the new selection
        }
    }

    override fun onEditRequest(layer: Layer) {
        if (layer is TextLayer) promptText(layer)
    }

    override fun onHistoryChanged() {
        dirty = true
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

    /** Change the selected text (does nothing when a picture is selected). */
    private fun ut(commit: Boolean = true, block: (TextLayer) -> Unit) =
        editor.update(commit) { if (it is TextLayer) block(it) }

    /** Change the selected picture. */
    private fun ui(commit: Boolean = true, block: (ImageLayer) -> Unit) =
        editor.update(commit) { if (it is ImageLayer) block(it) }

    // ================= bottom toolbar =================

    private fun toolButton(key: String, glyph: String, labelRes: Int, onClick: () -> Unit): View {
        val active = key == activeTool
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(64)
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = ripple()
            isClickable = true
            isFocusable = true
        }
        val g = TextView(this).apply {
            text = glyph
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(if (active) gold else textMain)
        }
        val l = TextView(this).apply {
            setText(labelRes)
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(if (active) gold else textDim)
        }
        box.addView(g)
        box.addView(l)
        box.setOnClickListener { onClick() }
        return box
    }

    private fun panelTool(key: String, glyph: String, labelRes: Int) =
        toolButton(key, glyph, labelRes) { togglePanel(key) }

    /** Rebuilds the bottom toolbar for the page, a text, or a picture. */
    private fun refreshTools() {
        val items = when (editor.selected) {
            is TextLayer -> listOf(
                panelTool("styles", "✦", R.string.t_styles),
                toolButton("edit", "✎", R.string.t_edit) { st?.let { promptText(it) } },
                toolButton("delete", "✕", R.string.t_delete) { editor.deleteSelected() },
                toolButton("copy", "⧉", R.string.t_copy) { editor.duplicateSelected() },
                toolButton("front", "⇡", R.string.t_to_front) { editor.moveSelected(true) },
                toolButton("back", "⇣", R.string.t_to_back) { editor.moveSelected(false) },
                panelTool("position", "✥", R.string.t_position),
                panelTool("relpos", "⊞", R.string.t_relpos),
                panelTool("size", "⤢", R.string.t_size),
                panelTool("color", "●", R.string.t_color),
                panelTool("texture", "▨", R.string.t_texture),
                panelTool("opacity", "◌", R.string.t_opacity),
                panelTool("rotate", "↻", R.string.t_rotate),
                panelTool("font", "Aa", R.string.t_font),
                panelTool("style", "B", R.string.t_style),
                panelTool("curve", "⌒", R.string.t_curve),
                panelTool("background", "▬", R.string.t_background),
                panelTool("align", "≡", R.string.t_align),
                panelTool("spacing", "↔", R.string.t_spacing),
                panelTool("stroke", "◯", R.string.t_stroke),
                panelTool("shadow", "◩", R.string.t_shadow),
                panelTool("inner", "◘", R.string.t_inner_shadow),
                panelTool("emboss", "◍", R.string.t_emboss),
                panelTool("persp", "⏢", R.string.t_perspective),
                panelTool("rot3d", "⟲", R.string.t_rotate3d),
                panelTool("depth", "3D", R.string.t_text3d),
                panelTool("floor", "▱", R.string.t_shadow3d),
                panelTool("reflect", "⊥", R.string.t_reflection),
                toolButton("done", "✓", R.string.t_done) { editor.select(null) }
            )
            is ImageLayer -> listOf(
                toolButton("replace", "⇄", R.string.t_replace) { pick(REQ_REPLACE) },
                toolButton("delete", "✕", R.string.t_delete) { editor.deleteSelected() },
                toolButton("copy", "⧉", R.string.t_copy) { editor.duplicateSelected() },
                toolButton("front", "⇡", R.string.t_to_front) { editor.moveSelected(true) },
                toolButton("back", "⇣", R.string.t_to_back) { editor.moveSelected(false) },
                panelTool("position", "✥", R.string.t_position),
                panelTool("relpos", "⊞", R.string.t_relpos),
                panelTool("size", "⤢", R.string.t_size),
                panelTool("opacity", "◌", R.string.t_opacity),
                panelTool("rotate", "↻", R.string.t_rotate),
                panelTool("flip", "⇋", R.string.t_flip),
                panelTool("round", "▢", R.string.t_round),
                panelTool("border", "◯", R.string.t_border),
                panelTool("imgshadow", "◩", R.string.t_shadow),
                panelTool("persp", "⏢", R.string.t_perspective),
                panelTool("rot3d", "⟲", R.string.t_rotate3d),
                panelTool("reflect", "⊥", R.string.t_reflection),
                toolButton("done", "✓", R.string.t_done) { editor.select(null) }
            )
            else -> listOf(
                panelTool("projects", "▦", R.string.t_projects),
                toolButton("add", "T+", R.string.t_add_text) { promptText(null) },
                toolButton("addimg", "▣+", R.string.t_add_image) { pick(REQ_ADD_IMAGE) },
                toolButton("photo", "▣", R.string.t_photo) { pick(REQ_BG_IMAGE) },
                panelTool("bgcolor", "◐", R.string.t_bg_color),
                panelTool("canvas", "⬚", R.string.t_canvas),
                toolButton("import", "Aa+", R.string.t_import_font) { importFont() },
                toolButton("new", "✦", R.string.t_new) { confirmNew() },
                toolButton("about", "ⓘ", R.string.t_about) { showAbout() }
            )
        }
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

    private fun label(res: Int): View = TextView(this).apply {
        setText(res)
        setTextColor(textDim)
        textSize = 12f
        setPadding(0, dp(4), 0, dp(2))
    }

    private fun openPanel(key: String) {
        val keepScroll = if (activeTool == key) panelScroll?.scrollY ?: 0 else 0
        activeTool = key
        panelHost.removeAllViews()
        panelScroll = null
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val s = editor.selected
        val t = st
        val im = si

        when (key) {
            // ---------- page ----------
            "bgcolor" -> box.addView(swatchRow(editor.doc.color, false) { editor.setDocColor(it) })

            "canvas" -> {
                val sizes = listOf(
                    "1:1" to Pair(1080, 1080), "4:5" to Pair(1080, 1350), "9:16" to Pair(1080, 1920),
                    "16:9" to Pair(1920, 1080), "3:4" to Pair(1080, 1440), "A4" to Pair(1240, 1754)
                )
                box.addView(chipRow(sizes.map { it.first }) { i ->
                    val p = sizes[i].second
                    editor.setDocSize(p.first, p.second)
                })
            }

            "projects" -> buildProjectsPanel(box)

            // ---------- any layer ----------
            "position" -> if (s != null) {
                val step = editor.doc.width / 200f
                box.addView(label(R.string.p_step_small))
                box.addView(chipRow(listOf("→", "↑", "↓", "←")) { i ->
                    when (i) {
                        0 -> editor.nudge(step, 0f)
                        1 -> editor.nudge(0f, -step)
                        2 -> editor.nudge(0f, step)
                        3 -> editor.nudge(-step, 0f)
                    }
                })
                box.addView(label(R.string.p_step_big))
                box.addView(chipRow(listOf("⇉", "⇈", "⇊", "⇇")) { i ->
                    val big = step * 10f
                    when (i) {
                        0 -> editor.nudge(big, 0f)
                        1 -> editor.nudge(0f, -big)
                        2 -> editor.nudge(0f, big)
                        3 -> editor.nudge(-big, 0f)
                    }
                })
            }

            "relpos" -> if (s != null) {
                box.addView(label(R.string.p_horizontal))
                box.addView(chipRow(listOf(getString(R.string.p_right), getString(R.string.p_center), getString(R.string.p_left))) { i ->
                    editor.alignTo(i, null)
                })
                box.addView(label(R.string.p_vertical))
                box.addView(chipRow(listOf(getString(R.string.p_top), getString(R.string.p_middle), getString(R.string.p_bottom))) { i ->
                    editor.alignTo(null, i)
                })
            }

            "size" -> if (s != null) {
                box.addView(slider(R.string.p_size, 2, 800, (s.scale * 100).roundToInt(), { "$it%" }) { v ->
                    editor.update(false) { it.scale = v / 100f }
                })
                if (t != null) {
                    box.addView(slider(R.string.p_font_size, 8, 600, t.textSize.roundToInt(), { "$it" }) { v ->
                        ut(false) { it.textSize = v.toFloat() }
                    })
                }
            }

            "opacity" -> if (s != null) {
                box.addView(slider(R.string.p_opacity, 0, 100, (s.opacity * 100f / 255f).roundToInt(), { "$it%" }) { v ->
                    editor.update(false) { it.opacity = (v * 255f / 100f).roundToInt() }
                })
            }

            "rotate" -> if (s != null) {
                box.addView(slider(R.string.p_rotation, -180, 180, s.rotation.roundToInt(), { "$it°" }) { v ->
                    editor.update(false) { it.rotation = v.toFloat() }
                })
                box.addView(chipRow(listOf("0°", "90°", "180°", "-90°")) { i ->
                    editor.update { it.rotation = floatArrayOf(0f, 90f, 180f, -90f)[i] }
                    openPanel("rotate")
                })
            }

            "persp" -> if (s != null) {
                box.addView(slider(R.string.p_persp_h, -80, 80, s.perspH.roundToInt(), { "$it" }) { v ->
                    editor.update(false) { it.perspH = v.toFloat() }
                })
                box.addView(slider(R.string.p_persp_v, -80, 80, s.perspV.roundToInt(), { "$it" }) { v ->
                    editor.update(false) { it.perspV = v.toFloat() }
                })
                box.addView(resetChip { it.perspH = 0f; it.perspV = 0f })
            }

            "rot3d" -> if (s != null) {
                box.addView(slider(R.string.p_rot_x, -80, 80, s.rotX.roundToInt(), { "$it°" }) { v ->
                    editor.update(false) { it.rotX = v.toFloat() }
                })
                box.addView(slider(R.string.p_rot_y, -80, 80, s.rotY.roundToInt(), { "$it°" }) { v ->
                    editor.update(false) { it.rotY = v.toFloat() }
                })
                box.addView(resetChip { it.rotX = 0f; it.rotY = 0f })
            }

            "reflect" -> if (s != null) {
                box.addView(slider(R.string.p_reflect_size, 0, 100, s.reflectSize.roundToInt(), { "$it%" }) { v ->
                    editor.update(false) { it.reflectSize = v.toFloat() }
                })
                box.addView(slider(R.string.p_gap, 0, 200, s.reflectGap.roundToInt(), { "$it" }) { v ->
                    editor.update(false) { it.reflectGap = v.toFloat() }
                })
                box.addView(slider(R.string.p_opacity, 0, 100, (s.reflectAlpha * 100f / 255f).roundToInt(), { "$it%" }) { v ->
                    editor.update(false) { it.reflectAlpha = (v * 255f / 100f).roundToInt() }
                })
            }

            // ---------- text ----------
            "styles" -> if (t != null) box.addView(stylesRow())

            "font" -> box.addView(fontRow())

            "style" -> if (t != null) {
                val names = listOf(getString(R.string.p_bold), getString(R.string.p_italic), getString(R.string.p_underline), getString(R.string.p_strike))
                val states = booleanArrayOf(t.bold, t.italic, t.underline, t.strike)
                box.addView(toggleRow(names, states) { i ->
                    ut {
                        when (i) {
                            0 -> it.bold = !it.bold
                            1 -> it.italic = !it.italic
                            2 -> it.underline = !it.underline
                            3 -> it.strike = !it.strike
                        }
                    }
                    openPanel("style")
                })
            }

            "color" -> if (t != null) {
                box.addView(chipRow(listOf(getString(R.string.p_solid), getString(R.string.p_gradient)), if (t.fillMode == 1) 1 else 0) { i ->
                    ut { it.fillMode = i }
                    openPanel("color")
                })
                if (t.fillMode == 1) {
                    box.addView(label(R.string.p_color1))
                    box.addView(swatchRow(t.gradColor1, false) { c -> ut { it.gradColor1 = c } })
                    box.addView(label(R.string.p_color2))
                    box.addView(swatchRow(t.gradColor2, false) { c -> ut { it.gradColor2 = c } })
                    box.addView(slider(R.string.p_angle, 0, 359, t.gradAngle.roundToInt(), { "$it°" }) { v ->
                        ut(false) { it.gradAngle = v.toFloat() }
                    })
                } else {
                    box.addView(swatchRow(t.color, false) { c -> ut { it.color = c; it.fillMode = 0 } })
                }
            }

            "texture" -> if (t != null) {
                box.addView(chipRow(listOf(getString(R.string.p_pick_image), getString(R.string.p_remove)), if (t.fillMode == 2) 0 else -1) { i ->
                    if (i == 0) pick(REQ_TEXTURE) else { ut { it.fillMode = 0; it.texture = null }; openPanel("texture") }
                })
            }

            "curve" -> if (t != null) {
                box.addView(slider(R.string.p_curve, -100, 100, t.curve.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.curve = v.toFloat() }
                })
            }

            "background" -> if (t != null) {
                box.addView(swatchRow(t.highlightColor, true) { c -> ut { it.highlightColor = c }; openPanel("background") })
                box.addView(slider(R.string.p_padding, 0, 200, t.highlightPadding.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.highlightPadding = v.toFloat() }
                })
                box.addView(slider(R.string.p_round, 0, 100, t.highlightRound.roundToInt(), { "$it%" }) { v ->
                    ut(false) { it.highlightRound = v.toFloat() }
                })
            }

            "align" -> if (t != null) {
                val names = listOf(getString(R.string.p_align_start), getString(R.string.p_align_center), getString(R.string.p_align_end))
                box.addView(chipRow(names, t.align) { i ->
                    ut { it.align = i }
                    openPanel("align")
                })
            }

            "spacing" -> if (t != null) {
                box.addView(slider(R.string.p_letter_spacing, -20, 100, (t.letterSpacing * 100).roundToInt(), { "$it" }) { v ->
                    ut(false) { it.letterSpacing = v / 100f }
                })
                box.addView(slider(R.string.p_line_spacing, 50, 300, (t.lineSpacing * 100).roundToInt(), { "$it%" }) { v ->
                    ut(false) { it.lineSpacing = v / 100f }
                })
            }

            "stroke" -> if (t != null) {
                box.addView(slider(R.string.p_width, 0, 80, t.strokeWidth.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.strokeWidth = v.toFloat() }
                })
                box.addView(swatchRow(t.strokeColor, false) { c ->
                    ut {
                        it.strokeColor = c
                        if (it.strokeWidth <= 0f) it.strokeWidth = (it.textSize * 0.06f).coerceAtLeast(2f)
                    }
                    openPanel("stroke")
                })
            }

            "shadow" -> if (t != null) {
                box.addView(slider(R.string.p_blur, 0, 80, t.shadowRadius.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.shadowRadius = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_x, -80, 80, t.shadowDx.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.shadowDx = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_y, -80, 80, t.shadowDy.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.shadowDy = v.toFloat() }
                })
                box.addView(swatchRow(t.shadowColor, false) { c ->
                    ut {
                        it.shadowColor = (c and 0x00FFFFFF) or (0xAA shl 24)
                        if (it.shadowRadius <= 0f && it.shadowDx == 0f && it.shadowDy == 0f) {
                            it.shadowRadius = (it.textSize * 0.1f).coerceAtLeast(4f)
                            it.shadowDy = (it.textSize * 0.04f).coerceAtLeast(2f)
                        }
                    }
                    openPanel("shadow")
                })
            }

            "inner" -> if (t != null) {
                box.addView(slider(R.string.p_blur, 0, 60, t.innerRadius.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.innerRadius = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_x, -40, 40, t.innerDx.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.innerDx = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_y, -40, 40, t.innerDy.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.innerDy = v.toFloat() }
                })
                box.addView(swatchRow(t.innerColor, false) { c ->
                    ut {
                        it.innerColor = (c and 0x00FFFFFF) or (0xAA shl 24)
                        if (it.innerRadius <= 0f && it.innerDx == 0f && it.innerDy == 0f) {
                            it.innerRadius = (it.textSize * 0.06f).coerceAtLeast(3f)
                            it.innerDy = (it.textSize * 0.04f).coerceAtLeast(2f)
                        }
                    }
                    openPanel("inner")
                })
            }

            "emboss" -> if (t != null) {
                box.addView(slider(R.string.p_strength, 0, 100, t.emboss.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.emboss = v.toFloat() }
                })
            }

            "depth" -> if (t != null) {
                box.addView(label(R.string.p_enabled))
                box.addView(onOff(t.d3On) { on ->
                    ut {
                        it.d3On = on
                        if (on && it.depth <= 0f) it.depth = (it.textSize * 0.15f).coerceAtLeast(4f)
                    }
                    openPanel("depth")
                })
                box.addView(label(R.string.p_view_type))
                box.addView(chipRow(listOf(getString(R.string.p_perspective), getString(R.string.p_oblique)), if (t.d3Persp) 0 else 1) { i ->
                    ut {
                        it.d3Persp = i == 0
                        if (it.depth <= 0f) it.depth = (it.textSize * 0.15f).coerceAtLeast(4f)
                    }
                    openPanel("depth")
                })

                box.addView(header(R.string.p_depth))
                box.addView(slider(R.string.p_depth, 0, 200, t.depth.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.depth = v.toFloat(); if (v > 0) it.d3On = true }
                })
                box.addView(slider(R.string.p_direction, 0, 359, t.depthAngle.roundToInt(), { "$it°" }) { v ->
                    ut(false) { it.depthAngle = v.toFloat() }
                })
                box.addView(label(R.string.p_depth_color))
                box.addView(chipRow(listOf(getString(R.string.p_color_one), getString(R.string.p_auto), getString(R.string.p_gradient)), t.depthMode) { i ->
                    ut {
                        it.depthMode = i
                        if (it.depth <= 0f) it.depth = (it.textSize * 0.15f).coerceAtLeast(4f)
                    }
                    openPanel("depth")
                })
                when (t.depthMode) {
                    0 -> box.addView(swatchRow(t.depthColor, false) { c -> ut { it.depthColor = c } })
                    2 -> {
                        box.addView(label(R.string.p_near_color))
                        box.addView(swatchRow(t.depthColor, false) { c -> ut { it.depthColor = c } })
                        box.addView(label(R.string.p_far_color))
                        box.addView(swatchRow(t.depthColor2, false) { c -> ut { it.depthColor2 = c } })
                    }
                }
                box.addView(slider(R.string.p_darken, 0, 100, t.depthDarken.roundToInt(), { "$it%" }) { v ->
                    ut(false) { it.depthDarken = v.toFloat() }
                })

                box.addView(header(R.string.t_rotate3d))
                box.addView(slider(R.string.p_rot_x, -80, 80, t.rotX.roundToInt(), { "$it°" }) { v ->
                    ut(false) { it.rotX = v.toFloat() }
                })
                box.addView(slider(R.string.p_rot_y, -80, 80, t.rotY.roundToInt(), { "$it°" }) { v ->
                    ut(false) { it.rotY = v.toFloat() }
                })
                box.addView(slider(R.string.p_rot_z, -180, 180, t.rotation.roundToInt(), { "$it°" }) { v ->
                    ut(false) { it.rotation = v.toFloat() }
                })

                box.addView(header(R.string.p_light))
                box.addView(onOff(t.lightOn) { on -> ut { it.lightOn = on; if (on) it.d3On = true }; openPanel("depth") })
                box.addView(slider(R.string.p_light_angle, 0, 359, t.lightAngle.roundToInt(), { "$it°" }) { v ->
                    ut(false) { it.lightAngle = v.toFloat() }
                })
                box.addView(slider(R.string.p_strength, 0, 100, t.lightStrength.roundToInt(), { "$it%" }) { v ->
                    ut(false) { it.lightStrength = v.toFloat() }
                })
                box.addView(resetChip { l ->
                    if (l is TextLayer) {
                        l.depth = 0f; l.depthAngle = 45f; l.d3Persp = false; l.depthMode = 1; l.depthDarken = 30f
                        l.lightOn = false; l.lightAngle = 72f; l.lightStrength = 50f
                        l.rotX = 0f; l.rotY = 0f
                    }
                })
            }

            "floor" -> if (t != null) {
                box.addView(slider(R.string.p_length, 0, 100, t.floorLength.roundToInt(), { "$it%" }) { v ->
                    ut(false) { it.floorLength = v.toFloat() }
                })
                box.addView(slider(R.string.p_skew, -100, 100, t.floorSkew.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.floorSkew = v.toFloat() }
                })
                box.addView(slider(R.string.p_blur, 0, 40, t.floorBlur.roundToInt(), { "$it" }) { v ->
                    ut(false) { it.floorBlur = v.toFloat() }
                })
                box.addView(swatchRow(t.floorColor, false) { c ->
                    ut {
                        it.floorColor = (c and 0x00FFFFFF) or (0x88 shl 24)
                        if (it.floorLength <= 0f) it.floorLength = 50f
                    }
                    openPanel("floor")
                })
            }

            // ---------- picture ----------
            "flip" -> if (im != null) {
                box.addView(chipRow(listOf(getString(R.string.p_flip_h), getString(R.string.p_flip_v))) { i ->
                    ui { if (i == 0) it.flipH = !it.flipH else it.flipV = !it.flipV }
                })
            }

            "round" -> if (im != null) {
                box.addView(slider(R.string.p_round, 0, 100, im.round.roundToInt(), { "$it%" }) { v ->
                    ui(false) { it.round = v.toFloat() }
                })
            }

            "border" -> if (im != null) {
                box.addView(slider(R.string.p_width, 0, 60, im.borderWidth.roundToInt(), { "$it" }) { v ->
                    ui(false) { it.borderWidth = v.toFloat() }
                })
                box.addView(swatchRow(im.borderColor, false) { c ->
                    ui {
                        it.borderColor = c
                        if (it.borderWidth <= 0f) it.borderWidth = 8f
                    }
                    openPanel("border")
                })
            }

            "imgshadow" -> if (im != null) {
                box.addView(slider(R.string.p_blur, 0, 80, im.shadowRadius.roundToInt(), { "$it" }) { v ->
                    ui(false) { it.shadowRadius = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_x, -80, 80, im.shadowDx.roundToInt(), { "$it" }) { v ->
                    ui(false) { it.shadowDx = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_y, -80, 80, im.shadowDy.roundToInt(), { "$it" }) { v ->
                    ui(false) { it.shadowDy = v.toFloat() }
                })
                box.addView(swatchRow(im.shadowColor, false) { c ->
                    ui {
                        it.shadowColor = (c and 0x00FFFFFF) or (0xAA shl 24)
                        if (it.shadowRadius <= 0f && it.shadowDx == 0f && it.shadowDy == 0f) {
                            it.shadowRadius = 20f
                            it.shadowDy = 8f
                        }
                    }
                    openPanel("imgshadow")
                })
            }
        }

        if (box.childCount > 6) {
            // long panels scroll, so the page stays visible
            val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = true }
            sv.addView(box)
            panelHost.addView(sv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(290)))
            panelScroll = sv
            if (keepScroll > 0) sv.post { sv.scrollTo(0, keepScroll) }
        } else {
            panelHost.addView(box)
        }
        panelHost.visibility = View.VISIBLE
        refreshTools()
    }

    private fun header(res: Int): View = TextView(this).apply {
        setText(res)
        setTextColor(gold)
        textSize = 14f
        setPadding(0, dp(10), 0, dp(2))
    }

    private fun onOff(on: Boolean, onPick: (Boolean) -> Unit): View =
        chipRow(listOf(getString(R.string.p_on), getString(R.string.p_off)), if (on) 0 else 1) { i -> onPick(i == 0) }

    private fun resetChip(block: (Layer) -> Unit): View =
        chipRow(listOf(getString(R.string.p_reset))) { _ ->
            editor.update { block(it) }
            openPanel(activeTool ?: return@chipRow)
        }

    // ---------- styles ----------

    private val prefs get() = getSharedPreferences("khattlab", Context.MODE_PRIVATE)

    private fun savedStyles(): JSONArray = try {
        JSONArray(prefs.getString("styles", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    private fun storeStyles(a: JSONArray) {
        prefs.edit().putString("styles", a.toString()).apply()
    }

    /** Ready-made looks. Sizes are relative to the text size so they fit any text. */
    private val presets: List<(TextLayer) -> Unit> = listOf(
        { it.color = Color.WHITE },
        { it.color = 0xFFE8B547.toInt(); it.strokeWidth = it.textSize * 0.07f; it.strokeColor = Color.BLACK },
        { it.color = Color.WHITE; it.shadowColor = 0xFF00E5FF.toInt(); it.shadowRadius = it.textSize * 0.35f },
        { it.color = 0xFFFFD54F.toInt(); it.depth = it.textSize * 0.18f; it.depthMode = 0; it.depthColor = 0xFFB26A00.toInt(); it.depthAngle = 50f },
        { it.color = Color.WHITE; it.depth = it.textSize * 0.3f; it.d3Persp = true; it.depthMode = 2; it.depthColor = 0xFF0D47A1.toInt(); it.depthColor2 = 0xFF000000.toInt(); it.depthAngle = 90f; it.lightOn = true; it.lightAngle = 250f },
        { it.color = Color.TRANSPARENT; it.strokeWidth = it.textSize * 0.05f; it.strokeColor = Color.WHITE },
        { it.fillMode = 1; it.gradColor1 = 0xFFFFEB3B.toInt(); it.gradColor2 = 0xFFF44336.toInt(); it.gradAngle = 90f },
        { it.color = Color.WHITE; it.highlightColor = 0xFFC62828.toInt(); it.highlightPadding = it.textSize * 0.3f },
        { it.color = Color.WHITE; it.reflectSize = 60f; it.reflectGap = it.textSize * 0.03f },
        { it.color = 0xFFE8B547.toInt(); it.emboss = 70f },
        { it.color = Color.WHITE; it.floorLength = 55f; it.floorSkew = 45f; it.floorBlur = it.textSize * 0.05f },
        { it.color = Color.WHITE; it.innerRadius = it.textSize * 0.06f; it.innerDy = it.textSize * 0.05f; it.strokeWidth = it.textSize * 0.04f; it.strokeColor = 0xFF1B5E20.toInt() },
        { it.fillMode = 1; it.gradColor1 = 0xFF00E5FF.toInt(); it.gradColor2 = 0xFF673AB7.toInt(); it.gradAngle = 0f; it.strokeWidth = it.textSize * 0.05f; it.strokeColor = Color.WHITE }
    )

    private fun stylePreview(apply: (TextLayer) -> Unit): Bitmap {
        val w = dp(84)
        val h = dp(56)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFF3A3A44.toInt())
        val l = TextLayer(getString(R.string.style_sample), w / 2f, h / 2f)
        l.fontKey = st?.fontKey ?: fonts.defaultKey
        l.textSize = h * 0.42f
        l.highlightPadding = l.textSize * 0.25f
        apply(l)
        l.draw(c, fonts)
        return b
    }

    private fun previewTile(bmp: Bitmap, onClick: () -> Unit, onLongClick: (() -> Unit)? = null): View {
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            background = pill(chipBg, 0x33FFFFFF)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
        return iv
    }

    private fun stylesRow(): View {
        val (scroll, row) = horizontalRow()
        fun add(v: View) {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            row.addView(v, lp)
        }

        add(TextView(this).apply {
            text = getString(R.string.p_save_style)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))
            setTextColor(gold)
            background = pill(chipBg, gold)
            setOnClickListener {
                val t = st ?: return@setOnClickListener
                val a = savedStyles()
                a.put(t.styleToJson())
                storeStyles(a)
                toast(R.string.style_saved)
                openPanel("styles")
            }
        })

        val saved = savedStyles()
        for (i in saved.length() - 1 downTo 0) {
            val j = saved.optJSONObject(i) ?: continue
            add(previewTile(stylePreview { it.applyStyleJson(j) }, {
                ut { it.applyStyleJson(j) }
            }, {
                AlertDialog.Builder(this)
                    .setMessage(R.string.style_delete)
                    .setPositiveButton(R.string.t_delete) { _, _ ->
                        val a = savedStyles()
                        val n = JSONArray()
                        for (k in 0 until a.length()) if (k != i) n.put(a.get(k))
                        storeStyles(n)
                        openPanel("styles")
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }))
        }

        for (p in presets) {
            add(previewTile(stylePreview { l -> l.resetEffects(); p(l) }, {
                ut { l ->
                    l.resetEffects()
                    p(l)
                }
            }))
        }
        return scroll
    }

    // ---------- panel widgets ----------

    private fun slider(labelRes: Int, min: Int, max: Int, value: Int, format: (Int) -> String, onChange: (Int) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(1), 0, dp(1))
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
        row.addView(label, LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT))
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
        val size = dp(34)
        val margin = dp(5)
        val swatchViews = mutableListOf<Pair<View, Int>>()
        val currentOpaque = current or (0xFF shl 24)

        fun refreshMarks(chosen: Int) {
            for ((v, c) in swatchViews) v.background = circle(c, c == chosen)
        }

        fun add(v: View) {
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(margin, 0, margin, 0)
            row.addView(v, lp)
        }

        add(TextView(this).apply {
            text = "+"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            background = circle(chipBg, false)
            setOnClickListener {
                ColorPicker.show(this@MainActivity, if (Color.alpha(current) == 0) Color.WHITE else currentOpaque) { c ->
                    onPick(c)
                    refreshMarks(c)
                }
            }
        })

        if (allowNone) {
            add(TextView(this).apply {
                text = "∅"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(textMain)
                background = circle(chipBg, Color.alpha(current) == 0)
                setOnClickListener { onPick(Color.TRANSPARENT) }
            })
        }

        for (c in SWATCHES) {
            val v = View(this)
            v.background = circle(c, Color.alpha(current) != 0 && c == currentOpaque)
            v.setOnClickListener {
                onPick(c)
                refreshMarks(c)
            }
            swatchViews.add(v to c)
            add(v)
        }
        return scroll
    }

    private fun chip(name: String, on: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        text = name
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setTextColor(if (on) 0xFF141416.toInt() else textMain)
        background = pill(if (on) gold else chipBg, if (on) gold else 0x33FFFFFF)
        setOnClickListener { onClick() }
    }

    private fun chipRow(names: List<String>, selectedIndex: Int = -1, onPick: (Int) -> Unit): View {
        val (scroll, row) = horizontalRow()
        names.forEachIndexed { i, name ->
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            row.addView(chip(name, i == selectedIndex) { onPick(i) }, lp)
        }
        return scroll
    }

    private fun toggleRow(names: List<String>, states: BooleanArray, onToggle: (Int) -> Unit): View {
        val (scroll, row) = horizontalRow()
        names.forEachIndexed { i, name ->
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            row.addView(chip(name, states[i]) { onToggle(i) }, lp)
        }
        return scroll
    }

    private fun fontRow(): View {
        val (scroll, row) = horizontalRow()
        val currentKey = st?.fontKey

        val add = TextView(this).apply {
            text = getString(R.string.font_add)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextColor(gold)
            background = pill(chipBg, gold)
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
                background = pill(if (on) 0xFF3A3220.toInt() else chipBg, if (on) gold else 0x33FFFFFF)
                setOnClickListener { ut { it.fontKey = f.key }; openPanel("font") }
            }
            item.addView(TextView(this).apply {
                text = getString(R.string.font_sample)
                typeface = f.typeface
                textSize = 18f
                setTextColor(textMain)
                gravity = Gravity.CENTER
            })
            item.addView(TextView(this).apply {
                text = f.label
                textSize = 10f
                setTextColor(if (on) gold else textDim)
                gravity = Gravity.CENTER
                maxLines = 1
            })
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
                    if (layer == null) editor.addText(t) else ut { it.text = t }
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
            .setPositiveButton(R.string.ok) { _, _ -> startNewDoc() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAbout() {
        val version = try {
            @Suppress("DEPRECATION")
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

    // ================= projects =================

    private fun defaultName(): String =
        getString(R.string.project) + " " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    /** Saves the page as the current project (a new one if needed), then runs [then]. */
    private fun saveProject(silent: Boolean, then: (() -> Unit)? = null) {
        if (editor.isBlank()) {
            then?.invoke()
            return
        }
        val id = currentId ?: projects.newId().also { currentId = it }
        if (currentName.isEmpty()) currentName = defaultName()
        val thumb = try { editor.renderThumb(dp(140)) } catch (e: OutOfMemoryError) { null }
        val snap = projects.snapshot(id, currentName, editor.doc, editor.layers.toList(), thumb)
        dirty = false
        Thread {
            val ok = projects.write(id, snap)
            runOnUiThread {
                if (!silent) toast(if (ok) R.string.project_saved else R.string.err_project)
                if (!ok) dirty = true
                if (activeTool == "projects") openPanel("projects")
                then?.invoke()
            }
        }.start()
    }

    private fun startNewDoc() {
        saveProject(true) {
            editor.newDoc()
            currentId = null
            currentName = ""
            dirty = false
        }
    }

    private fun openProject(info: ProjectStore.Info) {
        if (info.id == currentId) {
            closePanel()
            return
        }
        val go = {
            toast(R.string.project_opening)
            Thread {
                val p = projects.load(info.id)
                runOnUiThread {
                    if (p == null) {
                        toast(R.string.err_project)
                    } else {
                        editor.loadDoc(p.doc, p.layers)
                        currentId = info.id
                        currentName = p.name.ifEmpty { defaultName() }
                        dirty = false
                        closePanel()
                    }
                }
            }.start()
        }
        if (dirty && !editor.isBlank()) saveProject(true) { go() } else go()
    }

    private fun buildProjectsPanel(box: LinearLayout) {
        box.addView(chipRow(listOf(getString(R.string.project_save_now), getString(R.string.t_new))) { i ->
            if (i == 0) {
                if (editor.isBlank()) toast(R.string.project_empty) else saveProject(false)
            } else {
                confirmNew()
            }
        })
        val list = projects.list()
        if (list.isEmpty()) {
            box.addView(label(R.string.project_none))
            return
        }
        val (scroll, row) = horizontalRow()
        for (info in list) {
            val on = info.id == currentId
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
                background = pill(if (on) 0xFF3A3220.toInt() else chipBg, if (on) gold else 0x33FFFFFF)
                setOnClickListener { openProject(info) }
                setOnLongClickListener { projectMenu(info); true }
            }
            item.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                projects.thumbnail(info)?.let { setImageBitmap(it) }
            }, LinearLayout.LayoutParams(dp(84), dp(84)))
            item.addView(TextView(this).apply {
                text = info.name
                textSize = 10f
                maxLines = 1
                gravity = Gravity.CENTER
                setTextColor(if (on) gold else textDim)
            }, LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            row.addView(item, lp)
        }
        box.addView(scroll)
        box.addView(label(R.string.project_hint))
    }

    private fun projectMenu(info: ProjectStore.Info) {
        val items = arrayOf(getString(R.string.project_open), getString(R.string.project_rename), getString(R.string.t_copy), getString(R.string.t_delete))
        AlertDialog.Builder(this)
            .setTitle(info.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openProject(info)
                    1 -> renameProject(info)
                    2 -> {
                        if (info.id == currentId && dirty) saveProject(true) { projects.duplicate(info.id); openPanel("projects") }
                        else { projects.duplicate(info.id); openPanel("projects") }
                    }
                    3 -> AlertDialog.Builder(this)
                        .setMessage(R.string.project_delete)
                        .setPositiveButton(R.string.t_delete) { _, _ ->
                            projects.delete(info.id)
                            if (info.id == currentId) {
                                currentId = null
                                currentName = ""
                                dirty = !editor.isBlank()
                            }
                            openPanel("projects")
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    private fun renameProject(info: ProjectStore.Info) {
        val et = EditText(this).apply {
            setText(info.name)
            setSelection(text.length)
            isSingleLine = true
        }
        val wrap = FrameLayout(this)
        wrap.setPadding(dp(20), dp(8), dp(20), 0)
        wrap.addView(et)
        AlertDialog.Builder(this)
            .setTitle(R.string.project_rename)
            .setView(wrap)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) {
                    projects.rename(info.id, n)
                    if (info.id == currentId) currentName = n
                    openPanel("projects")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ================= files =================

    private fun pick(req: Int) {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(i, getString(R.string.t_photo)), req)
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

    private fun loadBitmapThen(uri: Uri, maxSide: Int, then: (Bitmap) -> Unit) {
        toast(R.string.loading)
        Thread {
            val bmp = ImageIO.loadBitmap(this, uri, maxSide)
            runOnUiThread {
                if (bmp == null) toast(R.string.err_image) else then(bmp)
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_BG_IMAGE -> loadBitmapThen(uri, 2560) { editor.setPhoto(it) }
            REQ_ADD_IMAGE -> loadBitmapThen(uri, 1600) { editor.addImage(it) }
            REQ_REPLACE -> loadBitmapThen(uri, 1600) { b ->
                ui {
                    val oldW = it.bitmap.width * it.scale
                    it.bitmap = b
                    it.scale = oldW / b.width
                }
            }
            REQ_TEXTURE -> loadBitmapThen(uri, 1024) { b ->
                ut { it.texture = b; it.fillMode = 2 }
                if (activeTool == "texture") openPanel("texture")
            }
            REQ_FONT -> {
                val item = fonts.importFont(uri)
                if (item == null) {
                    toast(R.string.err_font)
                } else {
                    toast(R.string.font_added)
                    if (st != null) {
                        ut { it.fontKey = item.key }
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
        saveProject(true)
        val name = "NawakhtGar_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
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

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
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.content.res.ColorStateList
import android.view.MotionEvent
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity(), EditorView.Listener {

    companion object {
        private const val REQ_BG_IMAGE = 11
        private const val REQ_FONT = 12
        private const val REQ_WRITE = 13
        private const val REQ_ADD_IMAGE = 14
        private const val REQ_TEXTURE = 15
        private const val REQ_REPLACE = 16
        private const val REQ_SHAPE_IMG = 17
        private const val REQ_BG_REMOVE = 18
        private const val REQ_PLP_SAVE = 19
        private const val REQ_PLP_OPEN = 20
        private const val REQ_FONT_TREE = 21
        private val SWATCHES = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFE8B547.toInt(), 0xFFFFD54F.toInt(),
            0xFFFF9800.toInt(), 0xFFF44336.toInt(), 0xFFB71C1C.toInt(), 0xFFE91E63.toInt(),
            0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(),
            0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF4CAF50.toInt(),
            0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(), 0xFF795548.toInt(), 0xFF9E9E9E.toInt(),
            0xFF607D8B.toInt(), 0xFF263238.toInt(), 0xFF1B5E20.toInt(), 0xFF0D47A1.toInt()
        )

        private val MAIN_PANELS = setOf("bgcolor", "canvas", "projects", "shapes", "stickers", "draw", "vector")
        private val COMMON_PANELS = setOf("position", "relpos", "size", "opacity", "rotate", "persp", "rot3d", "reflect")
        private val TEXT_PANELS = COMMON_PANELS + setOf(
            "styles", "font", "style", "color", "texture", "curve", "background", "align", "spacing",
            "stroke", "shadow", "inner", "emboss", "depth", "floor"
        )
        private val IMAGE_PANELS = COMMON_PANELS + setOf("flip", "round", "border", "outline", "imgshadow", "paper")
        private val SHAPE_PANELS = COMMON_PANELS + setOf("sfill", "sstroke", "outline", "sform", "sshadow", "simage")
        private val PATH_PANELS = COMMON_PANELS + setOf("pcolor", "pwidth")

        private val STICKERS = (
            "😀 😂 🤣 😍 🥰 😎 🤩 😇 🤔 😢 😭 😡 👍 👎 👏 🙏 💪 🤝 ✌️ 👌 " +
            "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 💯 ✨ ⭐ 🌟 🔥 ⚡ 🌙 ☀️ 🌈 ☁️ ❄️ " +
            "🌹 🌸 🌺 🌻 🌷 🍀 🌴 🌍 🕌 🕋 📿 🤲 🎉 🎁 🎂 🎈 🏆 🥇 🎵 📷 " +
            "📌 📍 ✅ ❌ ⚠️ ➡️ ⬅️ ⬆️ ⬇️ 🔴 🟢 🔵 🟡 ⚪ ⚫ 🇦🇫 📢 💡 📞 ✉️"
        ).split(" ").filter { it.isNotBlank() }

    }

    private lateinit var editor: EditorView
    private lateinit var fonts: FontManager
    private lateinit var tools: LinearLayout
    private lateinit var panelHost: FrameLayout
    private lateinit var btnUndo: View
    private lateinit var btnRedo: View
    private lateinit var btnGrid: ImageView

    private var activeTool: String? = null
    private var panelScroll: ScrollView? = null

    // ---- projects ----
    private lateinit var projects: ProjectStore
    private var currentId: String? = null
    private var currentName = ""
    private var dirty = false
    private val gold = 0xFFE8B547.toInt()
    private val accent = 0xFFC8901A.toInt()      // active tool on the light bars
    private val textMain = 0xFF202124.toInt()
    private val textDim = 0xFF5F6368.toInt()
    private val chipBg = 0xFFE6E6EB.toInt()
    private val line = 0x22000000
    private lateinit var uiFont: Typeface
    private lateinit var layersBox: LinearLayout

    private val st: TextLayer? get() = editor.selected as? TextLayer
    private val si: ImageLayer? get() = editor.selected as? ImageLayer
    private val ss: ShapeLayer? get() = editor.selected as? ShapeLayer
    private val sp: PathLayer? get() = editor.selected as? PathLayer

    // ================= life cycle =================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Trial.expired(this)) {
            showExpired()
            return
        }
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
        layersBox = findViewById(R.id.layers_overlay)
        uiFont = try { Typeface.createFromAsset(assets, "ui/bahij_bold.ttf") } catch (e: Exception) { Typeface.DEFAULT_BOLD }
        applyUiFont(findViewById(R.id.top_bar))
        layersBox.layoutParams = layersBox.layoutParams.apply {
            width = (resources.displayMetrics.widthPixels * 0.66f).roundToInt()
        }

        btnUndo.setOnClickListener { if (editor.drawing != null) editor.undoDrawStep() else editor.undo() }
        btnRedo.setOnClickListener { editor.redo() }
        btnGrid.setOnClickListener {
            editor.showGrid = !editor.showGrid
            btnGrid.imageTintList = ColorStateList.valueOf(if (editor.showGrid) gold else 0xFFF2F2F5.toInt())
        }
        findViewById<View>(R.id.btn_layers).setOnClickListener { toggleLayers() }
        findViewById<View>(R.id.btn_save).setOnClickListener { saveImage() }
        findViewById<View>(R.id.btn_share).setOnClickListener { shareImage() }

        refreshTools()
        onHistoryChanged()
        handleIncoming(intent)
    }

    /** A photo shared into NawakhtGar from the gallery or another app. */
    private fun handleIncoming(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_VIEW) {
            // a .plp project opened from the file manager or a chat
            i.data?.let { readPlp(it) }
            return
        }
        if (i.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri: Uri? = i.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null) return
        if (i.type?.startsWith("image/") == true) loadBitmapThen(uri, 2560) { editor.setPhoto(it) }
        else readPlp(uri)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    private var trialOver = false

    private fun showExpired() {
        trialOver = true
        startActivity(Intent(this, ExpiredActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        // the app may stay open across the last day
        if (!trialOver && Trial.expired(this)) {
            if (dirty && !editor.isBlank()) saveProject(true)
            showExpired()
        }
    }

    override fun onPause() {
        super.onPause()
        if (trialOver) return
        // never lose work: whatever is on the page is kept as a project
        if (dirty && !editor.isBlank()) saveProject(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            editor.drawing != null -> { editor.finishDrawing(true); closePanel() }
            layersBox.visibility == View.VISIBLE -> hideLayers()
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
        is ShapeLayer -> key in SHAPE_PANELS
        is PathLayer -> key in PATH_PANELS
        else -> false
    }

    override fun onSelectionChanged(layer: Layer?) {
        if (layersBox.visibility == View.VISIBLE) rebuildLayers()
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
        if (layersBox.visibility == View.VISIBLE) rebuildLayers()
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
        setStroke(dp(if (selected) 3 else 1), if (selected) accent else 0x33000000)
    }

    private fun pill(color: Int, strokeColor: Int) = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(color)
        setStroke(dp(1), strokeColor)
    }

    /** Change the selected text (does nothing when a picture is selected). */
    private fun ut(commit: Boolean = true, block: (TextLayer) -> Unit) =
        editor.update(commit) { if (it is TextLayer) block(it) }

    private fun us(commit: Boolean = true, block: (ShapeLayer) -> Unit) =
        editor.update(commit) { if (it is ShapeLayer) block(it) }

    private fun up(commit: Boolean = true, block: (PathLayer) -> Unit) =
        editor.update(commit) { if (it is PathLayer) block(it) }

    /** Change the selected picture. */
    private fun ui(commit: Boolean = true, block: (ImageLayer) -> Unit) =
        editor.update(commit) { if (it is ImageLayer) block(it) }

    // ================= bottom toolbar =================

    private fun toolButton(key: String, icon: Int, labelRes: Int, onClick: () -> Unit): View {
        val active = key == activeTool
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(78)
            setPadding(dp(6), dp(10), dp(6), dp(8))
            background = ripple()
            isClickable = true
            isFocusable = true
        }
        val g = ImageView(this).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(if (active) accent else 0xFF5F6368.toInt())
        }
        val l = TextView(this).apply {
            setText(labelRes)
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(if (active) accent else textDim)
        }
        box.addView(g, LinearLayout.LayoutParams(dp(30), dp(30)))
        box.addView(l)
        box.setOnClickListener { onClick() }
        return box
    }

    private fun panelTool(key: String, icon: Int, labelRes: Int) =
        toolButton(key, icon, labelRes) { togglePanel(key) }

    /** Sets the app's own font (Bahij TheSansArabic Bold) on every label under [v]. */
    private fun applyUiFont(v: View) {
        if (v.tag == "own_font") return
        if (v is TextView) v.typeface = uiFont
        if (v is ViewGroup) for (i in 0 until v.childCount) applyUiFont(v.getChildAt(i))
    }

    /** Rebuilds the bottom toolbar for the page, a text, or a picture. */
    private fun refreshTools() {
        val common = listOf(
            panelTool("position", R.drawable.ic_open_with, R.string.t_position),
            panelTool("relpos", R.drawable.ic_align_horizontal_center, R.string.t_relpos),
            panelTool("size", R.drawable.ic_open_in_full, R.string.t_size),
            panelTool("opacity", R.drawable.ic_opacity, R.string.t_opacity),
            panelTool("rotate", R.drawable.ic_rotate_right, R.string.t_rotate)
        )
        val arrange = listOf(
            toolButton("delete", R.drawable.ic_delete, R.string.t_delete) { editor.deleteSelected() },
            toolButton("copy", R.drawable.ic_content_copy, R.string.t_copy) { editor.duplicateSelected() },
            toolButton("front", R.drawable.ic_flip_to_front, R.string.t_to_front) { editor.moveSelected(true) },
            toolButton("back", R.drawable.ic_flip_to_back, R.string.t_to_back) { editor.moveSelected(false) }
        )
        val tail = listOf(
            panelTool("persp", R.drawable.ic_crop_rotate, R.string.t_perspective),
            panelTool("rot3d", R.drawable.ic_three_d_rotation, R.string.t_rotate3d),
            panelTool("reflect", R.drawable.ic_flip, R.string.t_reflection),
            toolButton("done", R.drawable.ic_check, R.string.t_done) { editor.select(null) }
        )
        val items = when {
            editor.drawing != null -> listOf(
                toolButton("dok", R.drawable.ic_check, R.string.t_done) { editor.finishDrawing(true); closePanel() },
                toolButton("dundo", R.drawable.ic_undo, R.string.undo) { editor.undoDrawStep() },
                toolButton("dcancel", R.drawable.ic_close, R.string.cancel) { editor.finishDrawing(false); closePanel() }
            )
            editor.selected is TextLayer -> listOf(
                panelTool("styles", R.drawable.ic_auto_awesome, R.string.t_styles),
                toolButton("edit", R.drawable.ic_edit, R.string.t_edit) { st?.let { promptText(it) } },
                panelTool("font", R.drawable.ic_font_download, R.string.t_font),
                panelTool("color", R.drawable.ic_palette, R.string.t_color),
                panelTool("depth", R.drawable.ic_view_in_ar, R.string.t_text3d)
            ) + arrange + common + listOf(
                panelTool("texture", R.drawable.ic_texture, R.string.t_texture),
                panelTool("style", R.drawable.ic_format_bold, R.string.t_style),
                panelTool("curve", R.drawable.ic_looks, R.string.t_curve),
                panelTool("background", R.drawable.ic_highlight, R.string.t_background),
                panelTool("align", R.drawable.ic_format_align_center, R.string.t_align),
                panelTool("spacing", R.drawable.ic_format_line_spacing, R.string.t_spacing),
                panelTool("stroke", R.drawable.ic_radio_button_unchecked, R.string.t_stroke),
                panelTool("shadow", R.drawable.ic_filter_none, R.string.t_shadow),
                panelTool("inner", R.drawable.ic_blur_on, R.string.t_inner_shadow),
                panelTool("emboss", R.drawable.ic_terrain, R.string.t_emboss),
                panelTool("floor", R.drawable.ic_dehaze, R.string.t_shadow3d)
            ) + tail
            editor.selected is ImageLayer -> listOf(
                toolButton("replace", R.drawable.ic_swap_horiz, R.string.t_replace) { pick(REQ_REPLACE) },
                toolButton("bgremove", R.drawable.ic_auto_fix, R.string.t_remove_bg) { openBgRemover() },
                panelTool("paper", R.drawable.ic_page_curl, R.string.t_paper)
            ) + arrange + common + listOf(
                panelTool("flip", R.drawable.ic_flip, R.string.t_flip),
                panelTool("round", R.drawable.ic_rounded_corner, R.string.t_round),
                panelTool("border", R.drawable.ic_border_outer, R.string.t_border),
                panelTool("outline", R.drawable.ic_polyline, R.string.t_outline),
                panelTool("imgshadow", R.drawable.ic_filter_none, R.string.t_shadow)
            ) + tail
            editor.selected is ShapeLayer -> listOf(
                toolButton("simage", R.drawable.ic_add_photo_alternate, R.string.t_shape_image) {
                    if (ss?.hasImage() == true) togglePanel("simage") else pick(REQ_SHAPE_IMG)
                },
                panelTool("sfill", R.drawable.ic_format_color_fill, R.string.t_fill),
                panelTool("sstroke", R.drawable.ic_border_color, R.string.t_stroke),
                panelTool("outline", R.drawable.ic_border_outer, R.string.t_outline),
                panelTool("sform", R.drawable.ic_tune, R.string.t_shape_form),
                panelTool("sshadow", R.drawable.ic_filter_none, R.string.t_shadow)
            ) + arrange + common + tail
            editor.selected is PathLayer -> (if (editor.canEditPoints(editor.selected)) listOf(
                toolButton("pedit", R.drawable.ic_polyline, R.string.bz_edit_points) { editPathPoints() }
            ) else emptyList()) + listOf(
                panelTool("pcolor", R.drawable.ic_palette, R.string.t_color),
                panelTool("pwidth", R.drawable.ic_line_weight, R.string.t_thickness)
            ) + arrange + common + tail
            else -> listOf(
                panelTool("projects", R.drawable.ic_folder, R.string.t_projects),
                toolButton("add", R.drawable.ic_text_fields, R.string.t_add_text) { promptText(null) },
                toolButton("addimg", R.drawable.ic_add_photo_alternate, R.string.t_add_image) { pick(REQ_ADD_IMAGE) },
                panelTool("shapes", R.drawable.ic_category, R.string.t_shapes),
                toolButton("draw", R.drawable.ic_brush, R.string.t_draw) { startDraw(false) },
                toolButton("vector", R.drawable.ic_bezier, R.string.t_vector) { startDraw(true) },
                panelTool("stickers", R.drawable.ic_emoji_emotions, R.string.t_stickers),
                toolButton("photo", R.drawable.ic_wallpaper, R.string.t_photo) { pick(REQ_BG_IMAGE) },
                panelTool("bgcolor", R.drawable.ic_format_color_fill, R.string.t_bg_color),
                panelTool("canvas", R.drawable.ic_aspect_ratio, R.string.t_canvas),
                toolButton("import", R.drawable.ic_font_download, R.string.t_import_font) { importFont() },
                toolButton("new", R.drawable.ic_note_add, R.string.t_new) { confirmNew() },
                toolButton("about", R.drawable.ic_info, R.string.t_about) { showAbout() }
            )
        }
        tools.removeAllViews()
        items.forEach { tools.addView(it) }
        applyUiFont(tools)
    }

    // ================= option panels =================

    private fun togglePanel(key: String) {
        if (activeTool == key) closePanel() else openPanel(key)
    }

    private fun closePanel() {
        editor.imageAdjust = false
        editor.perspEdit = false
        activeTool = null
        panelHost.removeAllViews()
        panelHost.visibility = View.GONE
        refreshTools()
    }

    private fun label(res: Int): View = TextView(this).apply {
        setText(res)
        setTextColor(textDim)
        textSize = 14f
        setPadding(0, dp(6), 0, dp(2))
    }

    private fun openPanel(key: String) {
        if (key != "simage") editor.imageAdjust = false
        editor.perspEdit = key == "persp"
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

            "shapes" -> buildShapesPanel(box)

            "stickers" -> buildStickersPanel(box)

            "draw", "vector" -> buildDrawPanel(box, key == "vector")

            // ---------- shape ----------
            "sfill" -> ss?.let { sh ->
                box.addView(chipRow(listOf(getString(R.string.p_solid), getString(R.string.p_gradient), getString(R.string.p_none)), sh.fillMode) { i ->
                    us { it.fillMode = i }
                    openPanel("sfill")
                })
                when (sh.fillMode) {
                    0 -> box.addView(swatchRow(sh.fillColor, false) { c -> us { it.fillColor = c } })
                    1 -> {
                        box.addView(label(R.string.p_color1))
                        box.addView(swatchRow(sh.gradColor1, false) { c -> us { it.gradColor1 = c } })
                        box.addView(label(R.string.p_color2))
                        box.addView(swatchRow(sh.gradColor2, false) { c -> us { it.gradColor2 = c } })
                        box.addView(slider(R.string.p_angle, 0, 359, sh.gradAngle.roundToInt(), { "$it°" }) { v ->
                            us(false) { it.gradAngle = v.toFloat() }
                        })
                    }
                }
            }

            "simage" -> ss?.let { sh ->
                if (!sh.hasImage()) {
                    box.addView(chipRow(listOf(getString(R.string.p_pick_image))) { pick(REQ_SHAPE_IMG) })
                    return@let
                }
                editor.imageAdjust = true
                box.addView(label(R.string.shape_img_hint))
                box.addView(slider(R.string.p_zoom, 20, 800, (sh.imgScale * 100).roundToInt(), { "$it%" }) { v ->
                    us(false) { it.imgScale = v / 100f }
                })
                box.addView(slider(R.string.p_offset_x, -100, 100, (sh.imgX * 100).roundToInt(), { "$it" }) { v ->
                    us(false) { it.imgX = v / 100f }
                })
                box.addView(slider(R.string.p_offset_y, -100, 100, (sh.imgY * 100).roundToInt(), { "$it" }) { v ->
                    us(false) { it.imgY = v / 100f }
                })
                box.addView(chipRow(listOf("✓ " + getString(R.string.t_done), getString(R.string.p_reset), getString(R.string.t_replace), getString(R.string.p_remove))) { i ->
                    when (i) {
                        0 -> closePanel()
                        1 -> { us { it.imgScale = 1f; it.imgX = 0f; it.imgY = 0f }; openPanel("simage") }
                        2 -> pick(REQ_SHAPE_IMG)
                        3 -> { us { it.image = null }; closePanel() }
                    }
                })
            }

            "sstroke" -> ss?.let { sh ->
                box.addView(slider(R.string.p_width, 0, 120, sh.strokeWidth.roundToInt(), { "$it" }) { v ->
                    us(false) { it.strokeWidth = v.toFloat() }
                })
                box.addView(swatchRow(sh.strokeColor, false) { c ->
                    us {
                        it.strokeColor = c
                        if (it.strokeWidth <= 0f) it.strokeWidth = 10f
                    }
                    openPanel("sstroke")
                })
                box.addView(chipRow(listOf(getString(R.string.p_line_solid), getString(R.string.p_line_dashed)), if (sh.dashed) 1 else 0) { i ->
                    us { it.dashed = i == 1 }
                    openPanel("sstroke")
                })
            }

            "sform" -> ss?.let { sh ->
                val mx = (max(editor.doc.width, editor.doc.height) * 1.5f).roundToInt()
                box.addView(slider(R.string.p_width_px, 4, mx, sh.w.roundToInt(), { "$it" }) { v ->
                    us(false) { it.w = v.toFloat() }
                })
                box.addView(slider(R.string.p_height_px, 4, mx, sh.h.roundToInt(), { "$it" }) { v ->
                    us(false) { it.h = v.toFloat() }
                })
                if (sh.kind == ShapeLayer.ROUND_RECT || sh.kind == ShapeLayer.BUBBLE) {
                    addCornerSliders(box, sh.corner, sh.radii) { i, v ->
                        us(false) { if (i < 0) it.setAllCorners(v) else it.radii[i] = v }
                    }
                }
                if (sh.kind == ShapeLayer.STAR || sh.kind == ShapeLayer.POLYGON || sh.kind == ShapeLayer.BURST) {
                    box.addView(slider(R.string.p_points, 3, 16, sh.points, { "$it" }) { v ->
                        us(false) { it.points = v }
                    })
                }
                if (sh.kind == ShapeLayer.STAR || sh.kind == ShapeLayer.RING) {
                    box.addView(slider(R.string.p_inner, 10, 95, sh.inner.roundToInt(), { "$it%" }) { v ->
                        us(false) { it.inner = v.toFloat() }
                    })
                }
                box.addView(label(R.string.p_change_shape))
                box.addView(shapeGrid(sh.kind) { k -> us { it.kind = k }; openPanel("sform") })
            }

            "sshadow" -> ss?.let { sh ->
                box.addView(slider(R.string.p_blur, 0, 80, sh.shadowRadius.roundToInt(), { "$it" }) { v ->
                    us(false) { it.shadowRadius = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_x, -80, 80, sh.shadowDx.roundToInt(), { "$it" }) { v ->
                    us(false) { it.shadowDx = v.toFloat() }
                })
                box.addView(slider(R.string.p_offset_y, -80, 80, sh.shadowDy.roundToInt(), { "$it" }) { v ->
                    us(false) { it.shadowDy = v.toFloat() }
                })
                box.addView(swatchRow(sh.shadowColor, false) { c ->
                    us {
                        it.shadowColor = (c and 0x00FFFFFF) or (0xAA shl 24)
                        if (it.shadowRadius <= 0f && it.shadowDx == 0f && it.shadowDy == 0f) {
                            it.shadowRadius = 20f
                            it.shadowDy = 8f
                        }
                    }
                    openPanel("sshadow")
                })
            }

            // ---------- drawing layer ----------
            "pcolor" -> sp?.let { p ->
                box.addView(swatchRow(p.mainColor, false) { c -> up { l -> l.strokes.forEach { it.color = c } } })
                if (p.strokes.any { it.smooth }) {
                    box.addView(label(R.string.p_fill))
                    box.addView(swatchRow(p.strokes.firstOrNull { it.smooth }?.fill ?: 0, true) { c ->
                        up { l -> l.strokes.forEach { if (it.smooth) it.fill = c } }
                        openPanel("pcolor")
                    })
                }
            }

            "pwidth" -> sp?.let { p ->
                val cur = p.strokes.maxOfOrNull { it.width } ?: 10f
                val base = p.strokes.map { it.width }
                val mx = (max(editor.doc.width, editor.doc.height) * 0.06f).roundToInt().coerceAtLeast(20)
                box.addView(slider(R.string.p_width, 1, mx, cur.roundToInt(), { "$it" }) { v ->
                    up(false) { l ->
                        val k = v / cur.coerceAtLeast(0.1f)
                        l.strokes.forEachIndexed { i, st -> st.width = ((base.getOrNull(i) ?: st.width) * k).coerceAtLeast(0.5f) }
                    }
                })
            }

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
                val sh = ss
                if (sh != null) {
                    val mx = (max(editor.doc.width, editor.doc.height) * 1.5f).roundToInt()
                    box.addView(slider(R.string.p_width_px, 4, mx, sh.w.roundToInt(), { "$it" }) { v ->
                        editor.update(false) { (it as? ShapeLayer)?.w = v.toFloat() }
                    })
                    box.addView(slider(R.string.p_height_px, 4, mx, sh.h.roundToInt(), { "$it" }) { v ->
                        editor.update(false) { (it as? ShapeLayer)?.h = v.toFloat() }
                    })
                }
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
                box.addView(label(R.string.persp_drag_hint))
                box.addView(resetChip { it.perspH = 0f; it.perspV = 0f; it.warp.fill(0f) })
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
                addCornerSliders(box, im.round, im.radii) { i, v ->
                    ui(false) { if (i < 0) it.setAllCorners(v) else it.radii[i] = v }
                }
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

            "outline" -> {
                val sh = ss
                val ol: Layer? = sh ?: im
                if (ol != null) {
                    fun get(): FloatArray = if (sh != null) floatArrayOf(sh.outlineWidth, sh.outlineGap) else floatArrayOf(im!!.outlineWidth, im.outlineGap)
                    val color = sh?.outlineColor ?: im!!.outlineColor
                    val dashed = sh?.outlineDashed ?: im!!.outlineDashed
                    fun set(commit: Boolean, w: Float? = null, g: Float? = null, c: Int? = null, d: Boolean? = null) =
                        editor.update(commit) { l ->
                            when (l) {
                                is ShapeLayer -> { w?.let { l.outlineWidth = it }; g?.let { l.outlineGap = it }; c?.let { l.outlineColor = it }; d?.let { l.outlineDashed = it }
                                    if (c != null && l.outlineWidth <= 0f) l.outlineWidth = 6f }
                                is ImageLayer -> { w?.let { l.outlineWidth = it }; g?.let { l.outlineGap = it }; c?.let { l.outlineColor = it }; d?.let { l.outlineDashed = it }
                                    if (c != null && l.outlineWidth <= 0f) l.outlineWidth = 6f }
                                else -> {}
                            }
                        }
                    val v = get()
                    box.addView(slider(R.string.p_width, 0, 60, v[0].roundToInt(), { "$it" }) { x -> set(false, w = x.toFloat()) })
                    box.addView(slider(R.string.p_gap, -150, 150, v[1].roundToInt(), { "$it" }) { x -> set(false, g = x.toFloat()) })
                    box.addView(label(R.string.p_outline_hint))
                    box.addView(swatchRow(color, false) { c -> set(true, c = c); openPanel("outline") })
                    box.addView(chipRow(listOf(getString(R.string.p_line_solid), getString(R.string.p_line_dashed)), if (dashed) 1 else 0) { i ->
                        set(true, d = i == 1); openPanel("outline")
                    })
                }
            }

            "paper" -> if (im != null) {
                box.addView(chipRow(listOf(getString(R.string.pp_none), getString(R.string.pp_torn), getString(R.string.pp_curl)), im.edge) { i ->
                    ui { it.edge = i }
                    openPanel("paper")
                })
                when (im.edge) {
                    ImageLayer.EDGE_TORN -> {
                        box.addView(label(R.string.pp_sides))
                        val bits = intArrayOf(ImageLayer.SIDE_TOP, ImageLayer.SIDE_RIGHT, ImageLayer.SIDE_BOTTOM, ImageLayer.SIDE_LEFT)
                        val names = listOf(R.string.p_top, R.string.p_right, R.string.p_bottom, R.string.p_left).mapIndexed { i, r ->
                            (if (im.tornSides and bits[i] != 0) "✓ " else "") + getString(r)
                        }
                        box.addView(chipRow(names) { i ->
                            ui { l ->
                                val n = l.tornSides xor bits[i]
                                if (n != 0) l.tornSides = n
                            }
                            openPanel("paper")
                        })
                        box.addView(slider(R.string.pp_depth, 0, 100, im.tornDepth.roundToInt(), { "$it" }) { v ->
                            ui(false) { it.tornDepth = v.toFloat() }
                        })
                        box.addView(slider(R.string.pp_rim, 0, 60, im.tornRim.roundToInt(), { "$it" }) { v ->
                            ui(false) { it.tornRim = v.toFloat() }
                        })
                        box.addView(chipRow(listOf(getString(R.string.pp_new_tear))) { ui { it.tornSeed = (1..99999).random() } })
                    }
                    ImageLayer.EDGE_CURL -> {
                        box.addView(label(R.string.pp_corner))
                        box.addView(chipRow(listOf("↖", "↗", "↘", "↙"), im.curlCorner) { i ->
                            ui { it.curlCorner = i }
                            openPanel("paper")
                        })
                        box.addView(slider(R.string.pp_curl_size, 5, 70, im.curlSize.roundToInt(), { "$it%" }) { v ->
                            ui(false) { it.curlSize = v.toFloat() }
                        })
                    }
                }
                if (im.edge != ImageLayer.EDGE_NONE) {
                    box.addView(label(R.string.pp_paper_color))
                    box.addView(swatchRow(im.paperColor, false) { c -> ui { it.paperColor = c } })
                }
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
            panelHost.addView(sv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
            panelScroll = sv
            if (keepScroll > 0) sv.post { sv.scrollTo(0, keepScroll) }
        } else {
            panelHost.addView(box)
        }
        applyUiFont(panelHost)
        panelHost.visibility = View.VISIBLE
        refreshTools()
    }

    private fun header(res: Int): View = TextView(this).apply {
        setText(res)
        setTextColor(accent)
        textSize = 16f
        setPadding(0, dp(12), 0, dp(2))
    }

    private fun onOff(on: Boolean, onPick: (Boolean) -> Unit): View =
        chipRow(listOf(getString(R.string.p_on), getString(R.string.p_off)), if (on) 0 else 1) { i -> onPick(i == 0) }

    /**
     * Roundness: one slider for all corners, then one slider per corner.
     * [onChange] gets the corner (-1 = all, 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left) and the value.
     */
    private fun addCornerSliders(box: LinearLayout, all: Float, radii: FloatArray, onChange: (Int, Float) -> Unit) {
        val bars = ArrayList<SeekBar>()
        box.addView(slider(R.string.p_round_all, 0, 100, all.roundToInt(), { "$it%" }) { v ->
            onChange(-1, v.toFloat())
            bars.forEach { it.progress = v } // the per-corner sliders follow
        })
        box.addView(label(R.string.p_round_each))
        val names = intArrayOf(R.string.p_corner_tl, R.string.p_corner_tr, R.string.p_corner_br, R.string.p_corner_bl)
        for (i in 0 until 4) {
            val row = slider(names[i], 0, 100, radii[i].roundToInt(), { "$it%" }) { v -> onChange(i, v.toFloat()) }
            ((row as ViewGroup).getChildAt(1) as? SeekBar)?.let { bars.add(it) }
            box.addView(row)
        }
    }

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
            background = pill(chipBg, line)
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
            textSize = 14f
        }
        val valueText = TextView(this).apply {
            setTextColor(textMain)
            textSize = 14f
            gravity = Gravity.CENTER
            text = format(value.coerceIn(min, max))
            textDirection = View.TEXT_DIRECTION_LTR
        }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = value.coerceIn(min, max) - min
            progressTintList = ColorStateList.valueOf(gold)
            thumbTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(0xFFBDBDC4.toInt())
            minimumHeight = dp(40)
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
        row.addView(label, LinearLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.WRAP_CONTENT))
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
        val size = dp(38)
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
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(10), dp(18), dp(10))
        setTextColor(if (on) 0xFF141416.toInt() else textMain)
        background = pill(if (on) gold else chipBg, if (on) gold else line)
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
                background = pill(if (on) 0xFFFFF1D0.toInt() else chipBg, if (on) gold else line)
                setOnClickListener { ut { it.fontKey = f.key }; openPanel("font") }
            }
            item.addView(TextView(this).apply {
                tag = "own_font"
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
        startActivity(Intent(this, AboutActivity::class.java))
    }

    // ================= shapes, stickers, drawing =================

    private fun shapePreview(kind: Int, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val l = ShapeLayer(kind, size / 2f, size / 2f)
        l.w = size * 0.8f
        l.h = if (ShapeLayer.isLine(kind)) size * 0.3f else if (kind == ShapeLayer.ARROW) size * 0.5f else if (kind == ShapeLayer.HALF_CIRCLE) size * 0.4f else size * 0.8f
        l.fillColor = 0xFF5F6368.toInt()
        if (ShapeLayer.isLine(kind)) l.strokeWidth = size * 0.06f
        l.draw(c, fonts)
        return b
    }

    /** All shapes in rows; [current] is marked. */
    private fun shapeGrid(current: Int, onPick: (Int) -> Unit): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val perRow = (resources.displayMetrics.widthPixels - dp(28)) / dp(62)
        var row: LinearLayout? = null
        for (k in 0 until ShapeLayer.COUNT) {
            if (k % perRow == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(row)
            }
            val iv = ImageView(this).apply {
                setImageBitmap(shapePreview(k, dp(48)))
                background = pill(if (k == current) 0xFFFFF1D0.toInt() else 0xFFFFFFFF.toInt(), if (k == current) accent else line)
                setPadding(dp(5), dp(5), dp(5), dp(5))
                setOnClickListener { onPick(k) }
            }
            val lp = LinearLayout.LayoutParams(dp(56), dp(56))
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            row?.addView(iv, lp)
        }
        return grid
    }

    private fun buildShapesPanel(box: LinearLayout) {
        box.addView(label(R.string.p_pick_shape))
        box.addView(shapeGrid(-1) { k -> editor.addShape(k) })
    }

    private fun buildStickersPanel(box: LinearLayout) {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val perRow = (resources.displayMetrics.widthPixels - dp(28)) / dp(52)
        var row: LinearLayout? = null
        STICKERS.forEachIndexed { i, e ->
            if (i % perRow == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(row)
            }
            val tv = TextView(this).apply {
                tag = "own_font"
                text = e
                textSize = 28f
                gravity = Gravity.CENTER
                background = ripple()
                setOnClickListener { addSticker(e) }
            }
            row?.addView(tv, LinearLayout.LayoutParams(dp(52), dp(52)))
        }
        box.addView(grid)
    }

    private fun addSticker(e: String) {
        editor.addText(e)
        ut(false) {
            it.fontKey = FontManager.DEFAULT_KEY
            it.textSize = min(editor.doc.width, editor.doc.height) * 0.22f
            it.color = Color.BLACK
            it.shadowRadius = 0f
            it.shadowDy = 0f
        }
    }

    private fun startDraw(vector: Boolean) {
        hideLayers()
        editor.startDrawing(vector)
        openPanel(if (vector) "vector" else "draw")
    }

    override fun onDrawChanged() {
        btnUndo.alpha = 1f
        // the picture in a shape was moved by finger: show the new values
        if (activeTool == "simage") openPanel("simage")
    }

    private fun buildDrawPanel(box: LinearLayout, vector: Boolean) {
        val d = editor.drawing ?: run {
            box.addView(label(R.string.draw_start_hint))
            return
        }
        if (vector) {
            buildBezierPanel(box, d)
            return
        }
        box.addView(label(R.string.p_brush))
        val names = listOf(R.string.b_pen, R.string.b_marker, R.string.b_neon, R.string.b_dash, R.string.b_spray, R.string.b_calligraphy).map { getString(it) }
        box.addView(chipRow(names, d.brush) { i ->
            d.brush = i
            openPanel("draw")
        })
        val mx = (max(editor.doc.width, editor.doc.height) * 0.06f).roundToInt().coerceAtLeast(20)
        box.addView(slider(R.string.p_width, 1, mx, d.width.roundToInt(), { "$it" }) { v -> d.width = v.toFloat() })
        box.addView(label(R.string.t_color))
        box.addView(swatchRow(d.color, false) { c -> d.color = c })
        box.addView(chipRow(listOf(getString(R.string.t_done), getString(R.string.undo), getString(R.string.cancel))) { i ->
            when (i) {
                0 -> { editor.finishDrawing(true); closePanel() }
                1 -> editor.undoDrawStep()
                2 -> { editor.finishDrawing(false); closePanel() }
            }
        })
    }

    /** The bezier pen panel, like PixelLab: lines, mode, point actions, then the look of the line. */
    private fun buildBezierPanel(box: LinearLayout, d: DrawSession) {
        fun refresh() { editor.drawingChanged(); openPanel("vector") }
        fun style() { d.applyStyle(); editor.drawingChanged() }

        // ---- lines: new, previous, number, copy, next, delete ----
        val num = if (d.path == null) "${d.pathCount + 1}" else "${d.index + 1}/${d.pathCount}"
        box.addView(chipRow(listOf("＋", "‹", num, "⧉", "›", "－")) { i ->
            when (i) {
                0 -> d.newPath()
                1 -> d.prevPath()
                3 -> d.duplicatePath(max(editor.doc.width, editor.doc.height) * 0.03f)
                4 -> d.nextPath()
                5 -> d.deletePath()
            }
            refresh()
        })

        box.addView(label(R.string.bz_mode))
        box.addView(chipRow(listOf(getString(R.string.bz_add), getString(R.string.bz_curve), getString(R.string.bz_move)), d.bezMode) { i ->
            d.bezMode = i
            refresh()
        })
        box.addView(TextView(this).apply {
            setText(when (d.bezMode) {
                DrawSession.MODE_ADD -> R.string.bz_hint_add
                DrawSession.MODE_CURVE -> R.string.bz_hint_curve
                else -> R.string.bz_hint_move
            })
            textSize = 12.5f
            setTextColor(textDim)
        })

        box.addView(label(R.string.bz_actions))
        box.addView(chipRow(listOf(
            getString(R.string.bz_del_point), getString(R.string.bz_smooth), getString(R.string.bz_straight),
            getString(if (d.closed) R.string.p_open_line else R.string.p_closed_shape)
        )) { i ->
            when (i) {
                0 -> d.deletePoint()
                1 -> d.smoothAll()
                2 -> d.straightAll()
                3 -> d.toggleClosed()
            }
            refresh()
        })

        val mx = (max(editor.doc.width, editor.doc.height) * 0.06f).roundToInt().coerceAtLeast(20)
        box.addView(slider(R.string.p_width, 0, mx, d.width.roundToInt(), { "$it" }) { v ->
            d.width = v.toFloat(); style()
        })
        box.addView(chipRow(listOf(getString(R.string.bz_cap_round), getString(R.string.bz_cap_flat), getString(R.string.bz_cap_square)), d.cap) { i ->
            d.cap = i; style(); openPanel("vector")
        })
        box.addView(slider(R.string.t_opacity, 0, 100, (d.opacity * 100f / 255f).roundToInt(), { "$it%" }) { v ->
            d.opacity = (v * 255f / 100f).roundToInt(); style()
        })

        box.addView(label(R.string.bz_stroke_color))
        box.addView(chipRow(listOf(getString(R.string.p_solid), getString(R.string.p_gradient)), if (d.grad != 0) 1 else 0) { i ->
            d.grad = if (i == 1) (if (d.grad != 0) d.grad else 0xFF1E88E5.toInt()) else 0
            style(); openPanel("vector")
        })
        if (d.grad != 0) box.addView(label(R.string.p_color1))
        box.addView(swatchRow(d.color, false) { c -> d.color = c; style() })
        if (d.grad != 0) {
            box.addView(label(R.string.p_color2))
            box.addView(swatchRow(d.grad, false) { c -> d.grad = c or 0xFF000000.toInt(); style() })
        }
        box.addView(label(R.string.p_fill))
        box.addView(swatchRow(d.fill, true) { c -> d.fill = c; style() })

        box.addView(chipRow(listOf(getString(R.string.t_done), getString(R.string.undo), getString(R.string.cancel))) { i ->
            when (i) {
                0 -> { editor.finishDrawing(true); closePanel() }
                1 -> { editor.undoDrawStep(); openPanel("vector") }
                2 -> { editor.finishDrawing(false); closePanel() }
            }
        })
    }

    /** Opens the background remover for the selected picture. */
    private fun openBgRemover() {
        val b = si?.bitmap ?: return
        if (b.isRecycled) return
        BgHolder.input = b
        BgHolder.output = null
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, BgRemoveActivity::class.java), REQ_BG_REMOVE)
    }

    private fun editPathPoints() {
        val l = sp ?: return
        if (editor.editPoints(l) == null) {
            toast(R.string.bz_cant_edit)
            return
        }
        hideLayers()
        openPanel("vector")
    }

    // ================= layers list =================

    private fun toggleLayers() {
        if (layersBox.visibility == View.VISIBLE) hideLayers() else showLayers()
    }

    private fun showLayers() {
        if (editor.drawing != null) return
        layersBox.visibility = View.VISIBLE
        rebuildLayers()
    }

    private fun hideLayers() {
        layerDragActive = false
        layersBox.visibility = View.GONE
    }

    private val checker: BitmapDrawable by lazy {
        val c = dp(8)
        val b = Bitmap.createBitmap(c * 2, c * 2, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawColor(Color.WHITE)
        val p = Paint().apply { color = 0xFFD6D6DB.toInt() }
        cv.drawRect(0f, 0f, c.toFloat(), c.toFloat(), p)
        cv.drawRect(c.toFloat(), c.toFloat(), 2f * c, 2f * c, p)
        BitmapDrawable(resources, b).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    }

    /** A small picture of one layer alone. */
    private fun layerPreview(l: Layer, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val b = l.pageBounds(fonts)
        if (b.width() < 1f || b.height() < 1f) return out
        val k = min(w / b.width(), h / b.height()) * 0.9f
        c.translate(w / 2f, h / 2f)
        c.scale(k, k)
        c.translate(-b.centerX(), -b.centerY())
        try { l.draw(c, fonts) } catch (e: Exception) { }
        return out
    }

    private fun smallIcon(icon: Int, tint: Int, onClick: () -> Unit): View = ImageView(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tint)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = GradientDrawable().apply { setColor(0xFFF7F7F9.toInt()); setStroke(1, 0x22000000) }
        setOnClickListener { onClick() }
    }

    private var layersRebuildPending = false
    private var layerDragActive = false

    /**
     * Rebuilds the layers list on the next frame, never inside a touch or click on one of its rows.
     * Rebuilding right away removed the row that was still handling the finger, which crashed the app,
     * and it ran before the change was made, so the list showed the old order / old lock state.
     */
    private fun rebuildLayers() {
        if (layersRebuildPending) return
        layersRebuildPending = true
        layersBox.post {
            layersRebuildPending = false
            if (layerDragActive) {
                layersRebuildPending = true // rebuilt when the drag ends
            } else if (layersBox.visibility == View.VISIBLE || layersBox.childCount == 0) {
                buildLayersNow()
            }
        }
    }

    private fun buildLayersNow() {
        layersBox.removeAllViews()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(6), dp(6))
        }
        head.addView(TextView(this).apply {
            setText(R.string.t_layers)
            textSize = 17f
            setTextColor(textMain)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(textDim)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = ripple()
            setOnClickListener { hideLayers() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        layersBox.addView(head)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(12))
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(list)
        layersBox.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        if (editor.layers.isEmpty()) {
            list.addView(TextView(this).apply {
                setText(R.string.layers_empty)
                textSize = 14f
                setTextColor(textDim)
                setPadding(dp(8), dp(16), dp(8), dp(16))
            })
        }

        val itemH = dp(96)
        val margin = dp(6)
        val step = (itemH + margin).toFloat()
        val all = editor.layers.toList()
        val rows = ArrayList<View>()
        // top of the list = front of the page
        all.asReversed().forEachIndexed { pos, l ->
            val sel = l === editor.selected
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply {
                    setColor(if (l.locked) 0xFFF4F4F6.toInt() else Color.WHITE)
                    setStroke(dp(if (sel) 3 else 1), if (sel) accent else if (l.locked) 0x55E53935 else 0x22000000)
                    cornerRadius = dp(6).toFloat()
                }
                setOnClickListener {
                    if (layerDragActive) return@setOnClickListener
                    when {
                        l.locked -> toast(R.string.layer_locked)
                        !l.hidden -> editor.selectFromList(l)
                    }
                }
            }
            if (l.hidden) item.alpha = 0.55f

            val handle = ImageView(this).apply {
                setImageResource(R.drawable.ic_drag_indicator)
                imageTintList = ColorStateList.valueOf(0xFF9AA0A6.toInt())
                setPadding(dp(6), dp(30), dp(2), dp(30))
            }
            item.addView(handle, LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT))

            val prevW = (layersBox.layoutParams.width - dp(16) - dp(30) - dp(88) - dp(10)).coerceAtLeast(dp(60))
            item.addView(ImageView(this).apply {
                background = checker
                scaleType = ImageView.ScaleType.FIT_CENTER
                val bmp = try { layerPreview(l, prevW, itemH - dp(12)) } catch (e: Throwable) { null }
                if (bmp != null) setImageBitmap(bmp)
            }, LinearLayout.LayoutParams(0, itemH - dp(12), 1f).apply { setMargins(dp(2), dp(6), dp(6), dp(6)) })

            val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val bs = itemH / 2
            r1.addView(smallIcon(if (l.locked) R.drawable.ic_lock else R.drawable.ic_lock_open, if (l.locked) 0xFFE53935.toInt() else 0xFF5F6368.toInt()) {
                if (!layerDragActive) editor.setLocked(l, !l.locked)
            }, LinearLayout.LayoutParams(dp(44), bs))
            r1.addView(smallIcon(R.drawable.ic_edit, 0xFF5F6368.toInt()) {
                if (layerDragActive) return@smallIcon
                if (l.locked) {
                    // stays locked until the user unlocks it
                    toast(R.string.layer_locked)
                    return@smallIcon
                }
                if (l.hidden) editor.setHidden(l, false)
                editor.selectFromList(l)
                hideLayers()
                if (l is TextLayer) promptText(l)
            }, LinearLayout.LayoutParams(dp(44), bs))
            r2.addView(smallIcon(if (l.hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, if (l.hidden) 0xFFE53935.toInt() else 0xFF5F6368.toInt()) {
                if (!layerDragActive) editor.setHidden(l, !l.hidden)
            }, LinearLayout.LayoutParams(dp(44), bs))
            r2.addView(smallIcon(R.drawable.ic_delete, 0xFF5F6368.toInt()) {
                if (layerDragActive) return@smallIcon
                if (l.locked) toast(R.string.layer_locked) else editor.deleteLayer(l)
            }, LinearLayout.LayoutParams(dp(44), bs))
            buttons.addView(r1)
            buttons.addView(r2)
            item.addView(buttons, LinearLayout.LayoutParams(dp(88), itemH))

            // drag the dots to move the layer up or down
            var startY = 0f
            var startScroll = 0
            var target = pos
            var mine = false
            handle.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (layerDragActive || all.size < 2) return@setOnTouchListener false
                        mine = true
                        layerDragActive = true
                        v.parent.requestDisallowInterceptTouchEvent(true)
                        startY = e.rawY
                        startScroll = scroll.scrollY
                        target = pos
                        item.elevation = dp(10).toFloat()
                        item.alpha = 0.92f
                    }
                    MotionEvent.ACTION_MOVE -> if (mine) {
                        // scroll the list when the row is pulled to its top or bottom edge
                        val loc = IntArray(2)
                        scroll.getLocationOnScreen(loc)
                        val edge = dp(48)
                        if (e.rawY < loc[1] + edge) scroll.scrollBy(0, -dp(12))
                        else if (e.rawY > loc[1] + scroll.height - edge) scroll.scrollBy(0, dp(12))
                        // the row never leaves the list, so it can't disappear
                        val raw = (e.rawY - startY) + (scroll.scrollY - startScroll)
                        val dy = raw.coerceIn(-pos * step, (all.size - 1 - pos) * step)
                        item.translationY = dy
                        val t = (pos + (dy / step).roundToInt()).coerceIn(0, all.size - 1)
                        if (t != target) {
                            target = t
                            // make room: the rows in between slide one place
                            for (i in rows.indices) {
                                if (i == pos) continue
                                val shift = when {
                                    t > pos && i in (pos + 1)..t -> -step
                                    t < pos && i in t until pos -> step
                                    else -> 0f
                                }
                                rows[i].animate().translationY(shift).setDuration(120).start()
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (mine) {
                        mine = false
                        val newPos = target
                        // apply after this touch has finished, never while the row is handling it
                        item.post {
                            layerDragActive = false
                            layersRebuildPending = false
                            if (newPos != pos && editor.layers.contains(l)) {
                                editor.moveLayerTo(l, all.size - 1 - newPos)
                            }
                            rebuildLayers()
                        }
                    }
                }
                true
            }

            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemH)
            lp.setMargins(0, margin, 0, 0)
            list.addView(item, lp)
            rows.add(item)
        }
        applyUiFont(layersBox)
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
        // .plp files: keep a project in the file manager, send it, or bring one back
        box.addView(chipRow(listOf("⬇ " + getString(R.string.plp_save), "⬆ " + getString(R.string.plp_open))) { i ->
            if (i == 0) {
                if (editor.isBlank()) toast(R.string.project_empty)
                else saveProject(true) { currentId?.let { exportPlp(it) } }
            } else {
                pickPlp()
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
                background = pill(if (on) 0xFFFFF1D0.toInt() else chipBg, if (on) gold else line)
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
        val items = arrayOf(getString(R.string.project_open), getString(R.string.project_rename), getString(R.string.t_copy), getString(R.string.t_delete), getString(R.string.plp_save))
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
                    4 -> if (info.id == currentId && dirty) saveProject(true) { exportPlp(info.id) } else exportPlp(info.id)
                }
            }
            .show()
    }

    // ---------- .plp project files ----------

    private var plpExportId: String? = null

    /** Asks where to save project [id] as a .plp file (file manager, Drive...). */
    private fun exportPlp(id: String) {
        plpExportId = id
        val base = projects.nameOf(id).ifEmpty { "NawakhtGar" }.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim()
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "$base.plp")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_PLP_SAVE)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.err_no_app)
        }
    }

    private fun pickPlp() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_PLP_OPEN)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.err_no_app)
        }
    }

    private fun writePlp(uri: Uri) {
        val id = plpExportId ?: return
        plpExportId = null
        toast(R.string.plp_saving)
        Thread {
            val ok = try {
                contentResolver.openOutputStream(uri, "wt")?.use { projects.exportPlp(id, it) } ?: false
            } catch (e: Exception) { false }
            runOnUiThread { toast(if (ok) R.string.plp_saved else R.string.plp_err_save) }
        }.start()
    }

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) ?: "" else ""
        } ?: ""
    } catch (e: Exception) { "" }

    /** Opens a .plp file: a NawakhtGar project, or a PixelLab project. */
    private fun readPlp(uri: Uri) {
        toast(R.string.project_opening)
        val name = displayName(uri).substringBeforeLast('.').ifBlank { defaultName() }
        Thread {
            // a copy first: a PixelLab project is read as a zip file
            val tmp = java.io.File(cacheDir, "import.plp")
            val copied = try {
                contentResolver.openInputStream(uri)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                true
            } catch (e: Exception) { false }
            if (copied && PixelLabImport.isPixelLab(tmp)) {
                val keep = java.io.File(cacheDir, "pixellab_last.plp")
                tmp.renameTo(keep)
                runOnUiThread { importPixelLab(keep, name, false) }
                return@Thread
            }
            PixelLabImport.allowPixelLabZips()
            val id = if (!copied) null else try { tmp.inputStream().use { projects.importPlp(it) } } catch (e: Exception) { null }
            tmp.delete()
            runOnUiThread {
                val info = id?.let { n -> projects.list().firstOrNull { it.id == n } }
                if (info == null) {
                    toast(R.string.plp_err_open)
                } else {
                    toast(R.string.plp_opened)
                    openProject(info)
                    if (activeTool == "projects") openPanel("projects")
                }
            }
        }.start()
    }

    // ---------- PixelLab projects ----------

    private val appPrefs by lazy { getSharedPreferences("app", Context.MODE_PRIVATE) }
    private var lastPixelLabName = ""

    /**
     * Turns a PixelLab .plp into a NawakhtGar project. [again] = the same file once more (after fonts
     * were found), replacing the project it made the first time.
     */
    private fun importPixelLab(file: java.io.File, name: String, again: Boolean) {
        lastPixelLabName = name
        Thread {
            var why = ""
            val r = try { PixelLabImport.read(file, java.io.File(cacheDir, "pixellab_work"), name) }
                catch (e: Exception) { why = e.toString(); null }
                catch (e: OutOfMemoryError) { why = "out of memory"; null }
            if (r == null) {
                runOnUiThread {
                    // show the reason, so a screenshot tells what went wrong
                    Toast.makeText(this, getString(R.string.plp_err_open) + if (why.isNotEmpty()) "\n$why" else "", Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            // fonts PixelLab took from its fonts folder: fetch them from that folder, if the user picked it once
            val tree = appPrefs.getString("pixellab_fonts", null)?.let { Uri.parse(it) }
            val needed = PixelLabImport.neededFonts(r)
            val found = if (tree != null && needed.isNotEmpty()) findFontsInTree(tree, needed) else emptyMap()
            runOnUiThread {
                for ((n, f) in found) {
                    if (fonts.findByFileName(n) == null) fonts.installFont(f, n)
                }
                val res = try { PixelLabImport.build(r, fonts) } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.plp_err_open) + "\n" + e, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                if (activeTool != null) closePanel()
                val keepId = if (again) currentId else null
                editor.loadDoc(res.doc, res.layers)
                currentId = keepId
                currentName = res.name
                dirty = true
                saveProject(true)
                toast(R.string.plab_opened)
                if (res.missingFonts.isNotEmpty()) askPixelLabFonts(res.missingFonts, tree != null)
            }
        }.start()
    }

    private fun askPixelLabFonts(missing: List<String>, triedFolder: Boolean) {
        val list = missing.take(12).joinToString("\n") { "• " + it.substringBeforeLast('.') } + if (missing.size > 12) "\n…" else ""
        AlertDialog.Builder(this)
            .setTitle(R.string.plab_fonts_title)
            .setMessage(getString(if (triedFolder) R.string.plab_fonts_text_again else R.string.plab_fonts_text) + "\n\n" + list)
            .setPositiveButton(R.string.plab_pick_folder) { _, _ ->
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(i, REQ_FONT_TREE)
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.err_no_app)
                }
            }
            .setNegativeButton(R.string.plab_later, null)
            .show()
    }

    /** Looks through a folder the user picked (and the folders in it) for font files with these names. */
    private fun findFontsInTree(tree: Uri, wanted: Set<String>): Map<String, java.io.File> {
        val out = HashMap<String, java.io.File>()
        val want = wanted.associateBy { it.lowercase() }
        val dir = java.io.File(cacheDir, "pixellab_fonts").apply { mkdirs() }
        try {
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.addLast(Pair(android.provider.DocumentsContract.getTreeDocumentId(tree), 0))
            var folders = 0
            while (queue.isNotEmpty() && folders < 600 && out.size < want.size) {
                val (docId, depth) = queue.removeFirst()
                folders++
                val kids = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
                contentResolver.query(kids, arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                ), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val n = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < 5) queue.addLast(Pair(id, depth + 1))
                        } else {
                            val orig = want[n.lowercase()] ?: continue
                            if (out.containsKey(orig)) continue
                            val f = java.io.File(dir, "f" + out.size + ".ttf")
                            try {
                                contentResolver.openInputStream(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { inp ->
                                    f.outputStream().use { inp.copyTo(it) }
                                }
                                out[orig] = f
                            } catch (e: Exception) { f.delete() }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // folder no longer allowed: the user can pick it again
        }
        return out
    }

    private fun onFontTree(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { }
        appPrefs.edit().putString("pixellab_fonts", uri.toString()).apply()
        val last = java.io.File(cacheDir, "pixellab_last.plp")
        if (last.exists()) importPixelLab(last, lastPixelLabName.ifBlank { currentName }, true)
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
        if (requestCode == REQ_BG_REMOVE) {
            val out = BgHolder.output
            BgHolder.output = null
            if (resultCode == RESULT_OK && out != null) {
                if (si != null) ui { it.bitmap = out } else editor.addImage(out)
            }
            return
        }
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == REQ_PLP_SAVE) { writePlp(uri); return }
        if (requestCode == REQ_PLP_OPEN) { readPlp(uri); return }
        if (requestCode == REQ_FONT_TREE) { onFontTree(uri); return }
        when (requestCode) {
            REQ_BG_IMAGE -> loadBitmapThen(uri, 2560) { editor.setPhoto(it) }
            REQ_ADD_IMAGE -> loadBitmapThen(uri, 1600) { editor.addImage(it) }
            REQ_SHAPE_IMG -> loadBitmapThen(uri, 2048) { b ->
                us { it.image = b; it.imgScale = 1f; it.imgX = 0f; it.imgY = 0f }
                if (ss != null) openPanel("simage")
            }
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

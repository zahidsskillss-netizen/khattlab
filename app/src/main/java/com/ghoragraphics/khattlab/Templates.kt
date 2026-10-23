package com.ghoragraphics.khattlab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ready-made designs shipped as JSON files in assets/templates (same format as a saved project).
 * Every text, shape and color in them stays editable after opening.
 */
class Templates(private val ctx: Context, private val fonts: FontManager) {

    class Item(val file: String, val name: String, val category: String, val width: Int, val height: Int)

    companion object {
        /** Category keys used in the JSON files, in the order shown. */
        val CATEGORIES = listOf("all", "islamic", "business", "events", "social")
    }

    val items: List<Item> by lazy {
        val names = try { ctx.assets.list("templates") ?: emptyArray() } catch (e: Exception) { emptyArray() }
        names.filter { it.endsWith(".json") }.sorted().mapNotNull { f ->
            try {
                val j = json(f)
                Item(f, j.optString("name", f), j.optString("category", ""), j.optInt("width", 1080), j.optInt("height", 1080))
            } catch (e: Exception) {
                null
            }
        }
    }

    private val thumbs = HashMap<String, Bitmap>()

    private fun json(file: String): JSONObject =
        JSONObject(ctx.assets.open("templates/$file").bufferedReader().use { it.readText() })

    /** The page and its layers, ready to put in the editor. */
    fun load(item: Item): ProjectStore.Loaded? = try {
        ProjectStore.parse(json(item.file)) { null }
    } catch (e: Exception) {
        null
    }

    fun cachedThumb(item: Item): Bitmap? = synchronized(thumbs) { thumbs[item.file] }

    /** Draws any design JSON small (longest side = [side]). Slow: call it off the main thread. */
    fun render(j: JSONObject, side: Int): Bitmap? = try {
        val p = ProjectStore.parse(j) { null }
        val k = side / max(p.doc.width, p.doc.height).toFloat()
        val b = Bitmap.createBitmap(
            (p.doc.width * k).roundToInt().coerceAtLeast(1),
            (p.doc.height * k).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val c = Canvas(b)
        c.drawColor(p.doc.color)
        c.scale(k, k)
        for (l in p.layers) if (!l.hidden) l.draw(c, fonts)
        b
    } catch (e: Throwable) {
        null
    }

    /** Draws a small preview (longest side = [side]). Slow: call it off the main thread. */
    fun thumb(item: Item, side: Int): Bitmap? {
        cachedThumb(item)?.let { return it }
        val p = load(item) ?: return null
        return try {
            val k = side / max(p.doc.width, p.doc.height).toFloat()
            val b = Bitmap.createBitmap(
                (p.doc.width * k).roundToInt().coerceAtLeast(1),
                (p.doc.height * k).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val c = Canvas(b)
            c.drawColor(p.doc.color)
            c.scale(k, k)
            for (l in p.layers) if (!l.hidden) l.draw(c, fonts)
            synchronized(thumbs) { thumbs[item.file] = b }
            b
        } catch (e: Throwable) {
            null
        }
    }
}

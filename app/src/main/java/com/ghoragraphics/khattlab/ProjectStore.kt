package com.ghoragraphics.khattlab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.IdentityHashMap

/**
 * Saves designs as projects, so they can be opened and edited again later.
 * Each project is a folder: project.json (all layers and settings), the pictures it uses, and a small preview.
 */
class ProjectStore(ctx: Context) {

    class Info(val id: String, val name: String, val time: Long, val dir: File)

    /** A project read back from disk. */
    class Loaded(val doc: DocBackground, val layers: List<Layer>, val name: String)

    /** Everything needed to write a project, taken on the main thread. */
    class Snapshot(
        val json: JSONObject,
        val bitmaps: Map<String, Bitmap>,
        val thumb: Bitmap?
    )

    private val root = File(ctx.filesDir, "projects").apply { mkdirs() }

    /** Pictures already written for a project, so a big photo is not written again on every save. */
    private val written = HashMap<String, IdentityHashMap<Bitmap, String>>()

    fun newId(): String = "p" + System.currentTimeMillis()

    fun exists(id: String) = File(File(root, id), "project.json").exists()

    fun list(): List<Info> {
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        val out = mutableListOf<Info>()
        for (d in dirs) {
            val f = File(d, "project.json")
            if (!f.exists()) continue
            try {
                val j = JSONObject(f.readText())
                out.add(Info(d.name, j.optString("name", ""), j.optLong("time", f.lastModified()), d))
            } catch (e: Exception) {
                // damaged project: skip it
            }
        }
        return out.sortedByDescending { it.time }
    }

    fun thumbnail(info: Info): Bitmap? {
        val f = File(info.dir, "thumb.jpg")
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    /** Collects the editor's content. Must run on the main thread; [write] can then run in the background. */
    fun snapshot(id: String, name: String, doc: DocBackground, layers: List<Layer>, preview: Bitmap?): Snapshot {
        val names = IdentityHashMap<Bitmap, String>()
        val bitmaps = LinkedHashMap<String, Bitmap>()
        val known = synchronized(this) { written[id]?.let { IdentityHashMap(it) } }
        var n = 0
        fun fileFor(b: Bitmap, jpg: Boolean): String {
            names[b]?.let { return it }
            val prev = known?.get(b)
            val fname = prev ?: ("pic_" + System.nanoTime() + "_" + (n++) + if (jpg) ".jpg" else ".png")
            names[b] = fname
            bitmaps[fname] = b
            return fname
        }

        val j = JSONObject()
        j.put("version", 1)
        j.put("name", name)
        j.put("time", System.currentTimeMillis())
        j.put("width", doc.width)
        j.put("height", doc.height)
        j.put("color", doc.color)
        val bg = doc.bitmap
        if (bg != null && !bg.isRecycled) j.put("photo", fileFor(bg, !bg.hasAlpha()))

        val arr = JSONArray()
        for (l in layers) {
            when (l) {
                is TextLayer -> {
                    val o = l.toJson()
                    val tex = l.texture
                    if (l.fillMode == 2 && tex != null && !tex.isRecycled) o.put("textureFile", fileFor(tex, false))
                    arr.put(o)
                }
                is ImageLayer -> if (!l.bitmap.isRecycled) arr.put(l.toJson(fileFor(l.bitmap, false)))
            }
        }
        j.put("layers", arr)
        return Snapshot(j, bitmaps, preview)
    }

    /** Writes the project to disk. Safe to call from a background thread. */
    @Synchronized
    fun write(id: String, s: Snapshot): Boolean {
        val dir = File(root, id)
        dir.mkdirs()
        return try {
            val map = written.getOrPut(id) { IdentityHashMap() }
            for ((fname, b) in s.bitmaps) {
                val f = File(dir, fname)
                if (map[b] == fname && f.exists()) continue
                val tmp = File(dir, "$fname.tmp")
                tmp.outputStream().use { out ->
                    if (fname.endsWith(".jpg")) b.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    else b.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                tmp.renameTo(f)
                map[b] = fname
            }
            s.thumb?.let { t ->
                File(dir, "thumb.jpg").outputStream().use { t.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }
            val tmp = File(dir, "project.json.tmp")
            tmp.writeText(s.json.toString())
            tmp.renameTo(File(dir, "project.json"))

            // remove pictures this project no longer uses
            val keep = s.bitmaps.keys + setOf("project.json", "thumb.jpg")
            dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
            map.entries.removeAll { it.value !in s.bitmaps.keys }
            true
        } catch (e: Exception) {
            false
        } catch (e: OutOfMemoryError) {
            false
        }
    }

    /** Reads a project. Run in the background: pictures are decoded here. */
    fun load(id: String): Loaded? {
        val dir = File(root, id)
        return try {
            val j = JSONObject(File(dir, "project.json").readText())
            val cache = HashMap<String, Bitmap?>()
            val map = IdentityHashMap<Bitmap, String>()
            fun pic(name: String?): Bitmap? {
                if (name.isNullOrEmpty()) return null
                return cache.getOrPut(name) {
                    val opts = BitmapFactory.Options().apply { inMutable = false }
                    BitmapFactory.decodeFile(File(dir, name).absolutePath, opts)?.also { map[it] = name }
                }
            }

            val w = j.optInt("width", 1080)
            val h = j.optInt("height", 1080)
            val doc = DocBackground(pic(j.optString("photo", "")), j.optInt("color", Color.WHITE), w, h)
            val layers = mutableListOf<Layer>()
            val arr = j.optJSONArray("layers") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                when (o.optString("type")) {
                    "text" -> layers.add(TextLayer.fromJson(o, pic(o.optString("textureFile", ""))))
                    "image" -> pic(o.optString("file"))?.let { layers.add(ImageLayer.fromJson(o, it)) }
                }
            }
            synchronized(this) { written[id] = map }
            Loaded(doc, layers, j.optString("name", ""))
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    fun delete(id: String) {
        synchronized(this) { written.remove(id) }
        File(root, id).deleteRecursively()
    }

    fun rename(id: String, name: String) {
        val f = File(File(root, id), "project.json")
        try {
            val j = JSONObject(f.readText())
            j.put("name", name)
            f.writeText(j.toString())
        } catch (e: Exception) {
            // ignore
        }
    }

    fun duplicate(id: String): Boolean {
        val src = File(root, id)
        val dst = File(root, newId())
        return try {
            src.copyRecursively(dst)
            val f = File(dst, "project.json")
            val j = JSONObject(f.readText())
            j.put("time", System.currentTimeMillis())
            f.writeText(j.toString())
            true
        } catch (e: Exception) {
            dst.deleteRecursively()
            false
        }
    }
}

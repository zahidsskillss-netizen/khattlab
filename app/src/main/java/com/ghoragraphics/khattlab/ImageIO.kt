package com.ghoragraphics.khattlab

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageIO {

    /** Loads a photo, shrinks very large ones and turns it the right way up. */
    fun loadBitmap(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            val longest = max(bmp.width, bmp.height)
            if (longest > maxSide) {
                val k = maxSide.toFloat() / longest
                bmp = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * k).roundToInt().coerceAtLeast(1),
                    (bmp.height * k).roundToInt().coerceAtLeast(1),
                    true
                )
            }

            val orientation = try {
                cr.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val m = Matrix()
                m.postRotate(degrees)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            return bmp
        } catch (e: Throwable) {
            return null
        }
    }

    /** Saves to Pictures/NawakhtGar so it shows in the phone's gallery. */
    fun saveToGallery(ctx: Context, bmp: Bitmap, name: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NawakhtGar")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val cr = ctx.contentResolver
                val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                val ok = cr.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                cr.update(uri, values, null, null)
                ok
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "NawakhtGar")
                dir.mkdirs()
                val f = File(dir, name)
                val ok = FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("image/png"), null)
                ok
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Writes a temporary copy for sharing and returns its content:// link. */
    fun writeShareFile(ctx: Context, bmp: Bitmap): Uri? {
        return try {
            val dir = File(ctx.cacheDir, "share")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val name = "NawakhtGar_${System.currentTimeMillis()}.png"
            val f = File(dir, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Uri.parse("content://${ShareProvider.AUTHORITY}/$name")
        } catch (e: Exception) {
            null
        }
    }
}

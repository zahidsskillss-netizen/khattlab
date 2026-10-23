package com.ghoragraphics.khattlab

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Lets other apps (WhatsApp, Telegram, Facebook...) read the picture we share. */
class ShareProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.ghoragraphics.nawakhtgar.share"
    }

    private fun fileFor(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (name.contains("/") || name.contains("..")) throw FileNotFoundException()
        val f = File(File(ctx.cacheDir, "share"), name)
        if (!f.exists()) throw FileNotFoundException()
        return f
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = if (uri.toString().endsWith(".jpg")) "image/jpeg" else "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val f = fileFor(uri)
        val cols = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c = MatrixCursor(cols)
        c.addRow(arrayOf<Any>(f.name, f.length()))
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

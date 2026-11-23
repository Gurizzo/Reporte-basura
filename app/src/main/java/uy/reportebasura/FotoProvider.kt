package uy.reportebasura

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Proveedor mínimo de archivos (equivalente a androidx FileProvider, sin la dependencia).
 * content://uy.reportebasura.fotos/<id>.jpg  →  files/fotos/<id>.jpg
 * No es exportado: solo accede quien recibe permiso temporal por URI (la cámara para escribir,
 * WhatsApp para leer).
 */
class FotoProvider : ContentProvider() {
    companion object {
        private val NOMBRE = Regex("[A-Za-z0-9_-]{1,64}\\.jpg")

        fun uri(ctx: Context, id: String): Uri = Uri.parse("content://${ctx.packageName}.fotos/$id.jpg")
    }

    override fun onCreate() = true

    private fun archivo(uri: Uri): File {
        val seg = uri.pathSegments
        if (seg.size != 1 || !NOMBRE.matches(seg[0])) throw FileNotFoundException(uri.toString())
        return File(File(context!!.filesDir, "fotos"), seg[0])
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = archivo(uri)
        if (mode.contains('w')) f.parentFile?.mkdirs()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val f = archivo(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c = MatrixCursor(cols, 1)
        c.addRow(cols.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> "basura_${f.name}"
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        })
        return c
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

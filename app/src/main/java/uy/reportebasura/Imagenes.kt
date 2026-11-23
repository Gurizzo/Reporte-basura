package uy.reportebasura

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

/** Compresión y lectura de fotos sin librerías externas. */
object Imagenes {
    /** Lado mayor de la foto guardada/enviada: alcanza para ver la basura y pesa ~200–400 KB. */
    const val LADO_FOTO = 1600
    const val LADO_MINI = 360

    class Resultado(val ok: Boolean, val exifLat: Double?, val exifLon: Double?)

    /**
     * Toma la foto que dejó la app de cámara (a veces de 5–12 MB), la endereza según EXIF,
     * la achica a [LADO_FOTO] y la recomprime a JPEG 80. También genera la miniatura.
     * Si la cámara grabó GPS en el EXIF, lo devuelve como respaldo de ubicación.
     */
    fun procesar(foto: File, mini: File): Resultado {
        var lat: Double? = null
        var lon: Double? = null
        var orientacion = ExifInterface.ORIENTATION_NORMAL
        try {
            val exif = ExifInterface(foto.path)
            orientacion = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val ll = FloatArray(2)
            if (exif.getLatLong(ll) && !(ll[0] == 0f && ll[1] == 0f)) {
                lat = ll[0].toDouble(); lon = ll[1].toDouble()
            }
        } catch (e: Exception) {
        }

        val original = decodificar(foto, LADO_FOTO) ?: return Resultado(false, lat, lon)
        val derecha = rotar(original, orientacion)
        val grande = escalar(derecha, LADO_FOTO)
        if (!guardarJpeg(grande, foto, 80)) return Resultado(false, lat, lon)
        guardarJpeg(escalar(grande, LADO_MINI), mini, 75)
        return Resultado(true, lat, lon)
    }

    /** Decodifica con inSampleSize para no cargar la foto entera en memoria. */
    fun decodificar(f: File, ladoMax: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var muestra = 1
        while (maxOf(o.outWidth, o.outHeight) / (muestra * 2) >= ladoMax) muestra *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = muestra })
    }

    private fun rotar(b: Bitmap, orientacion: Int): Bitmap {
        val m = Matrix()
        when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    private fun escalar(b: Bitmap, ladoMax: Int): Bitmap {
        val lado = maxOf(b.width, b.height)
        if (lado <= ladoMax) return b
        val f = ladoMax.toFloat() / lado
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    private fun guardarJpeg(b: Bitmap, destino: File, calidad: Int): Boolean {
        val tmp = File(destino.path + ".tmp")
        return try {
            FileOutputStream(tmp).use { b.compress(Bitmap.CompressFormat.JPEG, calidad, it) }
            destino.delete()
            tmp.renameTo(destino)
        } catch (e: Exception) {
            tmp.delete(); false
        }
    }
}

/** Carga de miniaturas en segundo plano con una caché chica en memoria. */
object Miniaturas {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun mostrar(iv: ImageView, id: String) {
        iv.tag = id
        val enCache = cache.get(id)
        if (enCache != null) {
            iv.setImageBitmap(enCache); return
        }
        iv.setImageDrawable(null)
        Hilos.fondo.execute {
            val mini = Repo.mini(id)
            val b = (if (mini.exists()) BitmapFactory.decodeFile(mini.path) else null)
                ?: Imagenes.decodificar(Repo.foto(id), Imagenes.LADO_MINI)
            if (b != null) cache.put(id, b)
            Hilos.principal.post { if (iv.tag == id) iv.setImageBitmap(b) }
        }
    }

    fun olvidar(id: String) {
        cache.remove(id)
    }
}

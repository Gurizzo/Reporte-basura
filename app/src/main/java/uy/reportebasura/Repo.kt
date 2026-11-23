package uy.reportebasura

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Un reporte = una foto + dónde se sacó + si ya se mandó. */
class Reporte(
    val id: String,
    val creado: Long,
    var lat: Double? = null,
    var lon: Double? = null,
    var precision: Float? = null,
    var direccion: String? = null,
    var enviado: Boolean = false,
    var enviadoEn: Long? = null,
) {
    /** Solo en memoria (no se guarda): mientras se espera el GPS después de sacar la foto. */
    var buscandoUbicacion = false

    fun tieneCoordenadas() = lat != null && lon != null

    fun coordenadas(): String? =
        if (tieneCoordenadas()) String.format(java.util.Locale.US, "%.6f, %.6f", lat, lon) else null

    /** Texto para mostrar/enviar como dirección. */
    fun lugar(): String = when {
        !direccion.isNullOrBlank() -> direccion!!
        tieneCoordenadas() -> coordenadas()!!
        else -> "ubicación desconocida"
    }
}

/** Hilos de trabajo: uno chico para disco/imágenes y el hilo principal. */
object Hilos {
    val fondo = Executors.newFixedThreadPool(2)
    val principal = Handler(Looper.getMainLooper())
}

/**
 * Almacenamiento local: un JSON (reportes.json) + las fotos en files/fotos y
 * miniaturas en files/miniaturas. Todo se modifica desde el hilo principal.
 */
object Repo {
    private var lista: MutableList<Reporte>? = null
    private lateinit var archivo: AtomicFile
    lateinit var dirFotos: File
        private set
    lateinit var dirMinis: File
        private set

    fun init(ctx: Context) {
        if (lista != null) return
        val base = ctx.applicationContext.filesDir
        dirFotos = File(base, "fotos").apply { mkdirs() }
        dirMinis = File(base, "miniaturas").apply { mkdirs() }
        archivo = AtomicFile(File(base, "reportes.json"))
        lista = cargar()
    }

    fun foto(id: String) = File(dirFotos, "$id.jpg")
    fun mini(id: String) = File(dirMinis, "$id.jpg")

    /** Más nuevas primero. */
    fun todos(): List<Reporte> = lista!!.sortedByDescending { it.creado }

    fun get(id: String): Reporte? = lista!!.find { it.id == id }

    fun agregar(r: Reporte) {
        lista!!.add(r)
        guardar()
    }

    fun actualizar(id: String, cambio: (Reporte) -> Unit) {
        val r = get(id) ?: return
        cambio(r)
        guardar()
    }

    fun actualizarVarios(ids: Collection<String>, cambio: (Reporte) -> Unit) {
        lista!!.filter { it.id in ids }.forEach(cambio)
        guardar()
    }

    fun borrar(ids: Collection<String>) {
        lista!!.removeAll { it.id in ids }
        guardar()
        ids.forEach {
            foto(it).delete()
            mini(it).delete()
            Miniaturas.olvidar(it)
        }
    }

    private fun cargar(): MutableList<Reporte> {
        val salida = mutableListOf<Reporte>()
        try {
            if (!archivo.baseFile.exists()) return salida
            val arr = JSONArray(String(archivo.readFully(), Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                salida += Reporte(
                    id = o.getString("id"),
                    creado = o.getLong("creado"),
                    lat = if (o.has("lat")) o.getDouble("lat") else null,
                    lon = if (o.has("lon")) o.getDouble("lon") else null,
                    precision = if (o.has("precision")) o.getDouble("precision").toFloat() else null,
                    direccion = if (o.has("direccion")) o.getString("direccion") else null,
                    enviado = o.optBoolean("enviado"),
                    enviadoEn = if (o.has("enviadoEn")) o.getLong("enviadoEn") else null,
                )
            }
        } catch (e: Exception) {
            // Archivo dañado: se arranca vacío pero no se pisa hasta el próximo guardado.
        }
        // Descarta registros cuya foto ya no existe.
        return salida.filterTo(mutableListOf()) { foto(it.id).exists() }
    }

    private fun guardar() {
        val arr = JSONArray()
        for (r in lista!!) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("creado", r.creado)
            r.lat?.let { o.put("lat", it) }
            r.lon?.let { o.put("lon", it) }
            r.precision?.let { o.put("precision", it.toDouble()) }
            r.direccion?.let { o.put("direccion", it) }
            o.put("enviado", r.enviado)
            r.enviadoEn?.let { o.put("enviadoEn", it) }
            arr.put(o)
        }
        val out = archivo.startWrite()
        try {
            out.write(arr.toString().toByteArray(Charsets.UTF_8))
            archivo.finishWrite(out)
        } catch (e: Exception) {
            archivo.failWrite(out)
        }
    }
}

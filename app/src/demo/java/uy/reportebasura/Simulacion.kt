package uy.reportebasura

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Variante demo: cámara, ubicación y WhatsApp simulados, para mostrar la app sin sacar fotos
 * reales, sin revelar dónde está el teléfono (no pide permiso de ubicación) y sin mandar nada.
 */
object Simulacion {
    const val ACTIVA = true

    /** Número inventado (+598 99 000 000): la demo no apunta a ningún destino real. */
    const val NUMERO_DEFECTO = "59899000000"

    private val DIRECCIONES = listOf(
        "Calle Ejemplo 1234, Barrio Demo, Montevideo",
        "Av. Siempre Viva 742, Barrio Demo, Montevideo",
        "Pasaje Ficticio 55, Barrio Demo, Montevideo",
        "Calle de Prueba 900, Barrio Demo, Montevideo",
    )
    private const val LAT0 = -34.9000
    private const val LON0 = -56.1900
    private const val PASO = 0.001
    private var siguiente = 0

    fun camara(ctx: Context): Intent = Intent(ctx, CamaraDemoActivity::class.java)

    fun whatsapp(ctx: Context, foto: Uri, texto: String, numero: String): Intent =
        Intent(ctx, WhatsAppDemoActivity::class.java)
            .setData(foto)
            .putExtra("texto", texto)
            .putExtra("numero", numero)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Coordenadas inventadas (una por dirección de ejemplo); nunca se lee el GPS. */
    fun ubicacion(): Pair<Double, Double> {
        val i = siguiente++ % DIRECCIONES.size
        return (LAT0 - i * PASO) to (LON0 + i * PASO)
    }

    fun direccion(lat: Double, lon: Double): String {
        val i = Math.round((LAT0 - lat) / PASO).toInt()
        return DIRECCIONES[Math.floorMod(i, DIRECCIONES.size)]
    }
}

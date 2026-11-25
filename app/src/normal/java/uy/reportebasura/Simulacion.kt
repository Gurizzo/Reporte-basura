package uy.reportebasura

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Versión normal: no simula nada (R8 elimina estas llamadas). La variante `demo`
 * (src/demo) reemplaza este objeto por uno con cámara, ubicación y WhatsApp de mentira.
 */
object Simulacion {
    const val ACTIVA = false
    const val NUMERO_DEFECTO = "59892250260"

    fun camara(ctx: Context): Intent? = null
    fun whatsapp(ctx: Context, foto: Uri, texto: String, numero: String): Intent? = null
    fun ubicacion(): Pair<Double, Double>? = null
    fun direccion(lat: Double, lon: Double): String? = null
}

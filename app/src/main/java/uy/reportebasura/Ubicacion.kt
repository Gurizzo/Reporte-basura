package uy.reportebasura

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import java.util.Locale

/**
 * Ubicación con el LocationManager del sistema (GPS + red), sin Google Play Services.
 * Se prende al tocar "Sacar foto" para que el GPS ya tenga posición cuando vuelve la cámara.
 */
class Ubicador(ctx: Context) {
    private val app = ctx.applicationContext
    private val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var activo = false

    var mejor: Location? = null
        private set

    /** Se llama (en el hilo principal) cada vez que llega una posición nueva. */
    var alCambiar: (() -> Unit)? = null

    private val oyente = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            if (esMejor(l, mejor)) mejor = l
            alCambiar?.invoke()
        }

        // Antes de Android 11 estos métodos son abstractos: hay que implementarlos.
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Requerido antes de API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun tienePermiso(): Boolean =
        app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun servicioPrendido(): Boolean = try {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (e: Exception) {
        false
    }

    fun iniciar() {
        if (activo || !tienePermiso()) return
        try {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (!lm.isProviderEnabled(p)) continue
                lm.requestLocationUpdates(p, 1000L, 0f, oyente, Looper.getMainLooper())
                activo = true
            }
            ultimaConocida(2 * 60_000L)?.let { if (esMejor(it, mejor)) mejor = it }
        } catch (e: SecurityException) {
        } catch (e: IllegalArgumentException) {
        }
    }

    fun detener() {
        alCambiar = null
        if (!activo) return
        try {
            lm.removeUpdates(oyente)
        } catch (e: Exception) {
        }
        activo = false
        mejor = null
    }

    /** Posición reciente y precisa: se puede usar sin esperar más. */
    fun esBuena(l: Location?): Boolean =
        l != null && edadMs(l) < 2 * 60_000L && (!l.hasAccuracy() || l.accuracy <= 50f)

    /** La mejor última posición conocida del sistema que no sea más vieja que [maxEdadMs]. */
    fun ultimaConocida(maxEdadMs: Long): Location? {
        if (!tienePermiso()) return null
        var res: Location? = null
        try {
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (edadMs(l) <= maxEdadMs && esMejor(l, res)) res = l
            }
        } catch (e: SecurityException) {
        }
        return res
    }

    private fun edadMs(l: Location) = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000L

    private fun esMejor(nueva: Location, actual: Location?): Boolean {
        if (actual == null) return true
        val diferencia = (nueva.elapsedRealtimeNanos - actual.elapsedRealtimeNanos) / 1_000_000L
        if (diferencia > 60_000L) return true
        if (diferencia < -60_000L) return false
        val pn = if (nueva.hasAccuracy()) nueva.accuracy else Float.MAX_VALUE
        val pa = if (actual.hasAccuracy()) actual.accuracy else Float.MAX_VALUE
        return pn <= pa
    }
}

/** Geocodificación inversa (coordenadas → "Calle 1234, Barrio") con android.location.Geocoder. */
object Direcciones {
    /** [cb] se llama en el hilo principal con la dirección, o null si no hay servicio o red. */
    fun buscar(ctx: Context, lat: Double, lon: Double, cb: (String?) -> Unit) {
        if (!Geocoder.isPresent()) {
            Hilos.principal.post { cb(null) }; return
        }
        val g = Geocoder(ctx.applicationContext, Locale.forLanguageTag("es-UY"))
        if (Build.VERSION.SDK_INT >= 33) {
            g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(lista: MutableList<Address>) {
                    val d = formatear(lista.firstOrNull())
                    Hilos.principal.post { cb(d) }
                }

                override fun onError(mensaje: String?) {
                    Hilos.principal.post { cb(null) }
                }
            })
        } else {
            Hilos.fondo.execute {
                val d = try {
                    @Suppress("DEPRECATION")
                    formatear(g.getFromLocation(lat, lon, 1)?.firstOrNull())
                } catch (e: Exception) {
                    null
                }
                Hilos.principal.post { cb(d) }
            }
        }
    }

    /** Arma una dirección corta y legible: "Cam. Servando Gómez 2280, Carrasco, Montevideo". */
    fun formatear(a: Address?): String? {
        a ?: return null
        val partes = mutableListOf<String>()
        val calle = a.thoroughfare
        if (!calle.isNullOrBlank()) {
            partes += listOfNotNull(calle, a.subThoroughfare?.takeIf { it.isNotBlank() }).joinToString(" ")
            a.subLocality?.takeIf { it.isNotBlank() }?.let { partes += it }
            a.locality?.takeIf { it.isNotBlank() && it !in partes }?.let { partes += it }
            return partes.joinToString(", ")
        }
        return a.getAddressLine(0)?.takeIf { it.isNotBlank() }
    }
}

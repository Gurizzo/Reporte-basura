package uy.reportebasura

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

/** Preferencias guardadas en SharedPreferences. */
object Ajustes {
    const val PLANTILLA_DEFECTO = "Basura fuera del contenedor en {direccion}"
    const val APP_AUTO = "auto"
    const val APP_NORMAL = "normal"
    const val APP_BUSINESS = "business"

    private fun p(ctx: Context) = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** Destino por defecto (+598 92 250 260), guardado ya normalizado. Se cambia en Ajustes. */
    const val NUMERO_DEFECTO = "59892250260"

    /** Si no hay un número guardado (o quedó vacío) se usa [NUMERO_DEFECTO]. */
    fun numero(ctx: Context): String =
        p(ctx).getString("numero", null)?.takeIf { it.isNotBlank() } ?: NUMERO_DEFECTO
    fun plantilla(ctx: Context): String = p(ctx).getString("plantilla", null)?.takeIf { it.isNotBlank() } ?: PLANTILLA_DEFECTO
    fun enlace(ctx: Context): Boolean = p(ctx).getBoolean("enlace", true)
    fun app(ctx: Context): String = p(ctx).getString("app", APP_AUTO) ?: APP_AUTO

    fun guardar(ctx: Context, numero: String, plantilla: String, enlace: Boolean, app: String) {
        p(ctx).edit()
            .putString("numero", internacional(numero) ?: "")
            .putString("plantilla", plantilla.trim())
            .putBoolean("enlace", enlace)
            .putString("app", app)
            .apply()
    }

    /**
     * Número en formato internacional solo con dígitos (lo que espera WhatsApp), pensado para Uruguay:
     * "098 828 017" → "59898828017"; "+54 9 11 ..." o "0054..." se respetan. Null si no parece válido.
     */
    fun internacional(numero: String): String? {
        val t = numero.trim()
        var d = t.filter { it.isDigit() }
        when {
            t.startsWith("+") -> {}
            d.startsWith("00") -> d = d.drop(2)
            d.startsWith("0") -> d = "598" + d.drop(1)
            d.length == 8 && d.startsWith("9") -> d = "598$d" // celular sin el 0
            d.length == 8 && (d.startsWith("2") || d.startsWith("4")) -> d = "598$d" // fijo uruguayo
        }
        return if (d.length in 8..15) d else null
    }

    /** "59892250260" → "+598 92 250 260" (solo para mostrar). */
    fun legible(n: String): String {
        if (n.startsWith("598") && n.length == 11) return "+598 ${n.substring(3, 5)} ${n.substring(5, 8)} ${n.substring(8)}"
        return "+$n"
    }

    fun mensaje(ctx: Context, r: Reporte): String {
        val sb = StringBuilder(plantilla(ctx).replace("{direccion}", r.lugar()))
        if (!plantilla(ctx).contains("{direccion}")) sb.append(" ").append(r.lugar())
        if (enlace(ctx) && r.tieneCoordenadas()) {
            sb.append("\nhttps://maps.google.com/?q=")
                .append(String.format(Locale.US, "%.6f,%.6f", r.lat, r.lon))
        }
        return sb.toString()
    }
}

/**
 * Envío por WhatsApp.
 *
 * wa.me / api.whatsapp.com no admiten adjuntos, así que se usa ACTION_SEND dirigido al paquete de
 * WhatsApp con la foto (EXTRA_STREAM, por content:// con permiso temporal), el texto como pie de
 * foto (EXTRA_TEXT) y el extra no oficial "jid" = número@s.whatsapp.net para abrir ese chat.
 * Si WhatsApp ignora el "jid" (pasa en algunas versiones o si el número no usa WhatsApp), muestra
 * la lista de chats y el usuario elige a mano; la foto y el texto ya van cargados.
 */
object WhatsApp {
    const val NORMAL = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"

    fun instalada(ctx: Context, paquete: String): Boolean = try {
        ctx.packageManager.getPackageInfo(paquete, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Paquete a usar según el ajuste y lo que haya instalado; null si no hay WhatsApp. */
    fun paquete(ctx: Context): String? {
        val wa = instalada(ctx, NORMAL)
        val wb = instalada(ctx, BUSINESS)
        return when (Ajustes.app(ctx)) {
            Ajustes.APP_BUSINESS -> if (wb) BUSINESS else if (wa) NORMAL else null
            else -> if (wa) NORMAL else if (wb) BUSINESS else null
        }
    }

    /** Intent para mandar una foto con su mensaje. Sin WhatsApp instalado, abre el selector genérico. */
    fun intent(ctx: Context, r: Reporte): Intent {
        val uri = FotoProvider.uri(ctx, r.id)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, Ajustes.mensaje(ctx, r))
            clipData = ClipData.newRawUri("foto", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pkg = paquete(ctx) ?: return Intent.createChooser(envio, "Enviar foto con…")
        envio.setPackage(pkg)
        Ajustes.internacional(Ajustes.numero(ctx))?.let { envio.putExtra("jid", "$it@s.whatsapp.net") }
        return envio
    }
}

package uy.reportebasura

import android.app.Activity
import android.widget.Toolbar

/** Barra superior (Toolbar del sistema) con flecha para volver. */
object Barra {
    fun conVolver(a: Activity, titulo: String) {
        val t = a.findViewById<Toolbar>(R.id.barra)
        a.setActionBar(t)
        a.title = titulo
        t.setNavigationIcon(R.drawable.ic_atras)
        t.navigationContentDescription = "Volver"
        t.setNavigationOnClickListener { a.finish() }
    }
}

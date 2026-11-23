package uy.reportebasura

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Contenedor que recorta su contenido con las esquinas redondeadas de su fondo. */
open class Redondeado(ctx: Context, attrs: AttributeSet?) : FrameLayout(ctx, attrs) {
    init {
        clipToOutline = true
    }
}

/** Celda cuadrada y redondeada para la galería (alto = ancho de la columna). */
class Cuadrado(ctx: Context, attrs: AttributeSet?) : Redondeado(ctx, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}

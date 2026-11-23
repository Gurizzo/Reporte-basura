package uy.reportebasura

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Celda cuadrada para la galería (alto = ancho de la columna). */
class Cuadrado(ctx: Context, attrs: AttributeSet?) : FrameLayout(ctx, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}

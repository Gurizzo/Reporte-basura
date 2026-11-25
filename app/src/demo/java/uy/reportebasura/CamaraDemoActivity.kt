package uy.reportebasura

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.random.Random

/** Cámara de mentira: muestra una escena dibujada y al disparar la guarda como JPEG. */
class CamaraDemoActivity : Activity() {
    private val semilla = System.nanoTime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val raiz = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        raiz.addView(Visor(this, semilla), FrameLayout.LayoutParams(-1, -1))

        raiz.addView(TextView(this).apply {
            text = "Cámara simulada · demo"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 16 * d; setColor(Color.argb(150, 0, 0, 0)) }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (56 * d).toInt() })

        raiz.addView(View(this).apply {
            contentDescription = "Sacar foto"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke((6 * d).toInt(), Color.argb(140, 255, 255, 255))
            }
            setOnClickListener { disparar() }
        }, FrameLayout.LayoutParams((78 * d).toInt(), (78 * d).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = (72 * d).toInt() })
        setContentView(raiz)
    }

    private fun disparar() {
        val uri = intent.clipData?.getItemAt(0)?.uri
        if (uri == null) {
            setResult(RESULT_CANCELED); finish(); return
        }
        val bmp = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        Escena.dibujar(this, Canvas(bmp), 1200f, 1600f, semilla)
        val ok = try {
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) } == true
        } catch (e: Exception) {
            false
        }
        bmp.recycle()
        setResult(if (ok) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    private class Visor(ctx: Context, private val semilla: Long) : View(ctx) {
        override fun onDraw(canvas: Canvas) = Escena.dibujar(context, canvas, width.toFloat(), height.toFloat(), semilla)
    }
}

/** Vereda con un contenedor desbordado (el mismo dibujo del ícono) y bolsas al azar. */
object Escena {
    private val PAREDES = intArrayOf(0xFFD9E2E9.toInt(), 0xFFE8DCCB.toInt(), 0xFFD6E3D2.toInt(), 0xFFE5D6D6.toInt())
    private val BOLSAS = intArrayOf(0xFF24292D.toInt(), 0xFF3E8E41.toInt(), 0xFFF2F4F5.toInt(), 0xFF2B3A4A.toInt(), 0xFFD7372B.toInt())

    fun dibujar(ctx: Context, c: Canvas, w: Float, h: Float, semilla: Long) {
        val r = Random(semilla)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = PAREDES[r.nextInt(PAREDES.size)]; c.drawRect(0f, 0f, w, h * 0.58f, p)
        p.color = 0x22000000; val vx = w * (0.1f + r.nextFloat() * 0.6f); c.drawRect(vx, h * 0.16f, vx + w * 0.22f, h * 0.36f, p)
        p.color = 0xFFC8D3DC.toInt(); c.drawRect(0f, h * 0.58f, w, h * 0.70f, p)
        p.color = 0xFF9AA4AD.toInt(); c.drawRect(0f, h * 0.70f, w, h * 0.715f, p)
        p.color = 0xFF66707A.toInt(); c.drawRect(0f, h * 0.715f, w, h, p)
        p.color = 0xFF7A858F.toInt(); c.drawRect(0f, h * 0.86f, w, h * 0.87f, p); c.drawRect(0f, h * 0.94f, w, h * 0.948f, p)

        // El dibujo del ícono ocupa x 26..82 e y 24..83 de su lienzo de 108.
        val arte = min(w * 0.85f, h * 0.48f)
        val s = arte * 108f / 56f
        val cx = w / 2 + (r.nextFloat() - 0.5f) * w * 0.12f
        val izq = cx - s * 54f / 108f
        val arriba = h * 0.80f - s * 83f / 108f
        ctx.getDrawable(R.drawable.ic_basura)?.apply {
            setBounds(izq.toInt(), arriba.toInt(), (izq + s).toInt(), (arriba + s).toInt())
            draw(c)
        }
        repeat(2 + r.nextInt(4)) {
            p.color = BOLSAS[r.nextInt(BOLSAS.size)]
            val bx = w * (0.08f + r.nextFloat() * 0.84f)
            val by = h * (0.79f + r.nextFloat() * 0.1f)
            val rx = arte * (0.06f + r.nextFloat() * 0.05f)
            c.drawOval(bx - rx, by - rx * 0.7f, bx + rx, by + rx * 0.7f, p)
        }
    }
}

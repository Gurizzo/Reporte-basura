package uy.reportebasura

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla parecida a un chat de WhatsApp para la demo. No es WhatsApp y no manda nada:
 * "enviar" solo agrega la burbuja al chat. Al volver, la app sigue igual que con el WhatsApp real.
 */
class WhatsAppDemoActivity : Activity() {
    private val verde = 0xFF008069.toInt()
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(x: Int) = (x * d).toInt()

    private lateinit var chat: LinearLayout
    private lateinit var redactar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = verde

        val texto = intent.getStringExtra("texto").orEmpty()
        val numero = intent.getStringExtra("numero").orEmpty()
        val foto = intent.data?.let { u ->
            try {
                contentResolver.openInputStream(u)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                }
            } catch (e: Exception) {
                null
            }
        }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEFE7DD.toInt())
        }

        // Barra superior
        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(verde)
            setPadding(dp(4), dp(8), dp(12), dp(8))
        }
        barra.addView(TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            contentDescription = "Volver"
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { finish() }
        })
        barra.addView(TextView(this).apply {
            text = "D"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF54656F.toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFDFE5E7.toInt()) }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        barra.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(context).apply {
                text = "Destino de prueba"
                textSize = 17f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
            })
            addView(TextView(context).apply {
                text = "${Ajustes.legible(numero)} · simulado"
                textSize = 13f
                setTextColor(0xCCFFFFFF.toInt())
            })
        })
        raiz.addView(barra, LinearLayout.LayoutParams(-1, -2))

        raiz.addView(TextView(this).apply {
            text = "Simulación de la demo: esto no es WhatsApp y no se envía nada."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF54656F.toInt())
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFFFFF3C4.toInt()) }
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(24), dp(12), dp(24), dp(4)) })

        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        raiz.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(chat, FrameLayout.LayoutParams(-1, -1))
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        // Vista previa con pie de foto y botón de enviar, como al compartir una foto.
        redactar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1F2C34.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(ImageView(context).apply {
                setImageBitmap(foto)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, LinearLayout.LayoutParams(-1, dp(300)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
                addView(TextView(context).apply {
                    text = texto
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(0xFF2A3942.toInt()) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(context).apply {
                    text = "➤"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    contentDescription = "Enviar"
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF00A884.toInt()) }
                    setOnClickListener { enviar(foto, texto) }
                }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { leftMargin = dp(8) })
            })
        }
        raiz.addView(redactar, LinearLayout.LayoutParams(-1, -2))
        setContentView(raiz)
    }

    private fun enviar(foto: Bitmap?, texto: String) {
        redactar.visibility = View.GONE
        val burbuja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFFD9FDD3.toInt()) }
        }
        burbuja.addView(ImageView(this).apply {
            setImageBitmap(foto)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, LinearLayout.LayoutParams(dp(240), dp(240)))
        burbuja.addView(TextView(this).apply {
            text = texto
            textSize = 15f
            setTextColor(0xFF111B21.toInt())
            setPadding(dp(6), dp(6), dp(6), 0)
            maxWidth = dp(240)
        })
        val hora = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val tildes = TextView(this).apply {
            text = "$hora  ✓✓"
            textSize = 11f
            setTextColor(0xFF667781.toInt())
            gravity = Gravity.END
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        burbuja.addView(tildes, LinearLayout.LayoutParams(dp(240), -2))
        chat.addView(burbuja, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END })
        Toast.makeText(this, "Enviado (simulado): no salió ningún mensaje", Toast.LENGTH_SHORT).show()
        chat.postDelayed({ tildes.setTextColor(0xFF53BDEB.toInt()) }, 900)
    }
}

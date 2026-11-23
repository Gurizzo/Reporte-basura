package uy.reportebasura

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

/** Número destino, texto del mensaje y qué WhatsApp usar. */
class AjustesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ajustes)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val numero = findViewById<EditText>(R.id.numero)
        val final = findViewById<TextView>(R.id.numero_final)
        val plantilla = findViewById<EditText>(R.id.plantilla)
        val enlace = findViewById<CheckBox>(R.id.enlace)
        val app = findViewById<RadioGroup>(R.id.app)

        numero.setText(Ajustes.numero(this))
        plantilla.setText(Ajustes.plantilla(this))
        enlace.isChecked = Ajustes.enlace(this)
        app.check(
            when (Ajustes.app(this)) {
                Ajustes.APP_NORMAL -> R.id.app_normal
                Ajustes.APP_BUSINESS -> R.id.app_business
                else -> R.id.app_auto
            }
        )
        marcarInstalada(R.id.app_normal, WhatsApp.NORMAL)
        marcarInstalada(R.id.app_business, WhatsApp.BUSINESS)

        fun actualizarFinal() {
            val n = Ajustes.internacional(numero.text.toString())
            final.text = when {
                numero.text.isBlank() -> "Sin número configurado."
                n == null -> "Número incompleto."
                else -> "Se abrirá el chat con +$n"
            }
        }
        actualizarFinal()
        numero.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = actualizarFinal()
        })

        findViewById<android.view.View>(R.id.guardar).setOnClickListener {
            if (numero.text.isNotBlank() && Ajustes.internacional(numero.text.toString()) == null) {
                numero.error = "Revisá el número"
                return@setOnClickListener
            }
            val elegida = when (app.checkedRadioButtonId) {
                R.id.app_normal -> Ajustes.APP_NORMAL
                R.id.app_business -> Ajustes.APP_BUSINESS
                else -> Ajustes.APP_AUTO
            }
            Ajustes.guardar(this, numero.text.toString(), plantilla.text.toString(), enlace.isChecked, elegida)
            Toast.makeText(this, "Ajustes guardados", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun marcarInstalada(idBoton: Int, paquete: String) {
        val b = findViewById<RadioButton>(idBoton)
        if (!WhatsApp.instalada(this, paquete)) b.text = "${b.text} (no instalada)"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish(); return true
        }
        return super.onOptionsItemSelected(item)
    }
}

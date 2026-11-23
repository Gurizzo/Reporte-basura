package uy.reportebasura

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Detalle de una foto: dirección, estado y acciones. */
class FotoActivity : Activity() {
    companion object {
        private const val REQ_ENVIO = 10
    }

    private lateinit var id: String
    private lateinit var dir: TextView
    private lateinit var datos: TextView
    private lateinit var estado: TextView
    private lateinit var btnEstado: Button
    private lateinit var btnRegeo: Button
    private lateinit var btnMapa: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        id = intent.getStringExtra("id") ?: return finish()
        if (Repo.get(id) == null) return finish()
        setContentView(R.layout.activity_foto)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Foto"

        dir = findViewById(R.id.dir)
        datos = findViewById(R.id.datos)
        estado = findViewById(R.id.estado)
        btnEstado = findViewById(R.id.btn_estado)
        btnRegeo = findViewById(R.id.btn_regeo)
        btnMapa = findViewById(R.id.btn_mapa)

        val img = findViewById<ImageView>(R.id.img)
        val ancho = resources.displayMetrics.widthPixels
        Hilos.fondo.execute {
            val b = Imagenes.decodificar(Repo.foto(id), ancho.coerceAtMost(Imagenes.LADO_FOTO))
            Hilos.principal.post { if (!isDestroyed) img.setImageBitmap(b) }
        }

        findViewById<View>(R.id.btn_enviar).setOnClickListener { enviar() }
        btnEstado.setOnClickListener {
            Repo.actualizar(id) {
                it.enviado = !it.enviado
                it.enviadoEn = if (it.enviado) System.currentTimeMillis() else null
            }
            mostrar()
        }
        findViewById<View>(R.id.btn_editar).setOnClickListener { editarDireccion() }
        btnRegeo.setOnClickListener { buscarDireccion() }
        btnMapa.setOnClickListener { abrirMapa() }
        findViewById<View>(R.id.btn_borrar).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("¿Borrar esta foto? No se puede deshacer.")
                .setPositiveButton("Borrar") { _, _ -> Repo.borrar(listOf(id)); finish() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        mostrar()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish(); return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun mostrar() {
        val r = Repo.get(id) ?: return finish()
        Estado.pintar(estado, r)
        dir.text = if (r.direccion.isNullOrBlank()) "(sin dirección)" else r.direccion
        val f = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-UY"))
        val lineas = mutableListOf("Sacada el ${f.format(Date(r.creado))}")
        r.coordenadas()?.let { c ->
            lineas += "Coordenadas: $c" + (r.precision?.let { " (±${it.toInt()} m)" } ?: "")
        } ?: run { lineas += "Sin coordenadas GPS" }
        r.enviadoEn?.let { lineas += "Enviada el ${f.format(Date(it))}" }
        datos.text = lineas.joinToString("\n")
        btnEstado.text = if (r.enviado) "Marcar como pendiente" else "Marcar como enviada"
        btnRegeo.visibility = if (r.tieneCoordenadas()) View.VISIBLE else View.GONE
        btnMapa.visibility = if (r.tieneCoordenadas()) View.VISIBLE else View.GONE
    }

    private fun enviar() {
        val r = Repo.get(id) ?: return
        if (Ajustes.internacional(Ajustes.numero(this)) == null) {
            Toast.makeText(this, "Configurá primero el número en Ajustes", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, AjustesActivity::class.java))
            return
        }
        try {
            startActivityForResult(WhatsApp.intent(this, r), REQ_ENVIO)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay ninguna app para compartir la foto", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_ENVIO) return super.onActivityResult(requestCode, resultCode, data)
        // WhatsApp no informa si se envió: se marca y se puede volver a "pendiente" con el botón.
        Repo.actualizar(id) { it.enviado = true; it.enviadoEn = System.currentTimeMillis() }
        mostrar()
        Toast.makeText(this, "Marcada como enviada. Si no se mandó, tocá «Marcar como pendiente».", Toast.LENGTH_LONG).show()
    }

    private fun editarDireccion() {
        val r = Repo.get(id) ?: return
        val campo = EditText(this).apply {
            setText(r.direccion ?: "")
            setSelection(text.length)
            hint = "Calle y número, esquina…"
        }
        val margen = (20 * resources.displayMetrics.density).toInt()
        val marco = android.widget.FrameLayout(this).apply {
            setPadding(margen, margen / 2, margen, 0)
            addView(campo)
        }
        AlertDialog.Builder(this)
            .setTitle("Dirección")
            .setView(marco)
            .setPositiveButton("Guardar") { _, _ ->
                Repo.actualizar(id) { it.direccion = campo.text.toString().trim().ifEmpty { null } }
                mostrar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun buscarDireccion() {
        val r = Repo.get(id) ?: return
        val lat = r.lat ?: return
        val lon = r.lon ?: return
        btnRegeo.isEnabled = false
        Direcciones.buscar(this, lat, lon) { d ->
            if (isDestroyed) return@buscar
            btnRegeo.isEnabled = true
            if (d == null) {
                Toast.makeText(this, "No se pudo obtener la dirección (¿sin internet?)", Toast.LENGTH_LONG).show()
            } else {
                Repo.actualizar(id) { it.direccion = d }
                mostrar()
            }
        }
    }

    private fun abrirMapa() {
        val r = Repo.get(id) ?: return
        val c = String.format(Locale.US, "%.6f,%.6f", r.lat, r.lon)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$c?q=$c(Basura)")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$c")))
        }
    }
}

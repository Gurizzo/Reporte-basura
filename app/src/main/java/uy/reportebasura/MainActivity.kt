package uy.reportebasura

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val REQ_FOTO = 1
        private const val REQ_ENVIO = 2
        private const val REQ_PERMISO = 3
        private const val ESPERA_GPS_MS = 25_000L
    }

    private lateinit var grilla: GridView
    private lateinit var vacio: TextView
    private lateinit var filtro: RadioGroup
    private val adaptador = Adaptador()
    private lateinit var ubicador: Ubicador

    /** Id de la foto que está sacando la cámara (sobrevive si Android mata la app). */
    private var capturaPendiente: String? = null

    /** Cola de envío: se manda una foto por vez y se sigue al volver de WhatsApp. */
    private var cola = ArrayList<String>()
    private var colaTotal = 0

    /** Foto recién sacada que espera una posición GPS buena. */
    private var esperandoGps: String? = null
    private var exifRespaldo: Pair<Double, Double>? = null
    private val finEspera = Runnable { terminarEsperaGps() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContentView(R.layout.activity_main)
        ubicador = Ubicador(this)

        savedInstanceState?.let {
            capturaPendiente = it.getString("captura")
            cola = it.getStringArrayList("cola") ?: ArrayList()
            colaTotal = it.getInt("colaTotal")
        }

        grilla = findViewById(R.id.grilla)
        vacio = findViewById(R.id.vacio)
        setActionBar(findViewById(R.id.barra))
        filtro = findViewById(R.id.filtro)

        grilla.adapter = adaptador
        grilla.setOnItemClickListener { _, _, pos, _ ->
            startActivity(Intent(this, FotoActivity::class.java).putExtra("id", adaptador.items[pos].id))
        }
        grilla.setMultiChoiceModeListener(Seleccion())
        filtro.setOnCheckedChangeListener { _, _ -> refrescar() }
        findViewById<View>(R.id.btn_foto).setOnClickListener { sacarFoto() }
    }

    /** Limpieza liviana: al volver a la app y cada hora mientras está abierta (sin WorkManager). */
    private val limpieza = object : Runnable {
        override fun run() {
            val enUso = cola + listOfNotNull(capturaPendiente, esperandoGps)
            val borradas = Repo.limpiarViejas(enUso)
            if (borradas > 0) {
                toast("Se borraron $borradas foto(s) de hace más de 2 días")
                refrescar()
            }
            Hilos.principal.postDelayed(this, 60 * 60_000L)
        }
    }

    override fun onPause() {
        Hilos.principal.removeCallbacks(limpieza)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Hilos.principal.removeCallbacks(limpieza)
        limpieza.run()
        refrescar()
        reintentarDirecciones()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("captura", capturaPendiente)
        outState.putStringArrayList("cola", cola)
        outState.putInt("colaTotal", colaTotal)
    }

    override fun onDestroy() {
        Hilos.principal.removeCallbacks(finEspera)
        ubicador.detener()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.principal, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ajustes -> startActivity(Intent(this, AjustesActivity::class.java))
            R.id.enviar_pendientes -> {
                val pendientes = Repo.todos().filter { !it.enviado }.map { it.id }
                if (pendientes.isEmpty()) toast("No hay fotos pendientes de reportar")
                else AlertDialog.Builder(this)
                    .setMessage("Se van a enviar ${pendientes.size} foto(s) por WhatsApp, una por una, cada una con su dirección.")
                    .setPositiveButton("Enviar") { _, _ -> enviar(pendientes) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------- Galería ----------

    private fun refrescar() {
        val todos = Repo.todos()
        adaptador.items = when (filtro.checkedRadioButtonId) {
            R.id.f_pendientes -> todos.filter { !it.enviado }
            R.id.f_enviadas -> todos.filter { it.enviado }
            else -> todos
        }
        adaptador.notifyDataSetChanged()
        vacio.visibility = if (adaptador.items.isEmpty()) View.VISIBLE else View.GONE
        if (adaptador.items.isEmpty() && todos.isNotEmpty()) vacio.text = "No hay fotos con este filtro."
        else vacio.setText(R.string.vacio)
        val pendientes = todos.count { !it.enviado }
        actionBar?.subtitle = when {
            todos.isEmpty() -> null
            pendientes > 0 -> "$pendientes pendiente(s) de reportar"
            else -> "Todo reportado"
        }
    }

    private inner class Adaptador : BaseAdapter() {
        var items: List<Reporte> = emptyList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_foto, parent, false)
            val r = items[position]
            Miniaturas.mostrar(v.findViewById<ImageView>(R.id.img), r.id)
            Estado.pintar(v.findViewById(R.id.estado), r)
            val lugar = if (r.buscandoUbicacion) "Buscando ubicación…" else r.lugar()
            val horas = ((r.venceEn() - System.currentTimeMillis()) / 3_600_000L).coerceAtLeast(0)
            v.findViewById<TextView>(R.id.dir).text =
                if (horas < 12) "$lugar\nSe borra en ${if (horas < 1) "menos de 1 h" else "$horas h"}" else lugar
            return v
        }
    }

    private inner class Seleccion : AbsListView.MultiChoiceModeListener {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.seleccion, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onItemCheckedStateChanged(mode: ActionMode, position: Int, id: Long, checked: Boolean) {
            mode.title = "${grilla.checkedItemCount} seleccionada(s)"
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val elegidos = seleccionados()
            when (item.itemId) {
                R.id.todas -> for (i in 0 until adaptador.count) grilla.setItemChecked(i, true)
                R.id.enviar -> { mode.finish(); enviar(elegidos.map { it.id }) }
                R.id.marcar_enviadas -> {
                    Repo.actualizarVarios(elegidos.map { it.id }) { it.enviado = true; it.enviadoEn = System.currentTimeMillis() }
                    mode.finish(); refrescar()
                }
                R.id.marcar_pendientes -> {
                    Repo.actualizarVarios(elegidos.map { it.id }) { it.enviado = false; it.enviadoEn = null }
                    mode.finish(); refrescar()
                }
                R.id.borrar -> AlertDialog.Builder(this@MainActivity)
                    .setMessage("¿Borrar ${elegidos.size} foto(s)? No se puede deshacer.")
                    .setPositiveButton("Borrar") { _, _ ->
                        Repo.borrar(elegidos.map { it.id }); mode.finish(); refrescar()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                else -> return false
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    /** Seleccionadas, de la más vieja a la más nueva (orden en que se reportan). */
    private fun seleccionados(): List<Reporte> {
        val marcadas = grilla.checkedItemPositions ?: return emptyList()
        val res = mutableListOf<Reporte>()
        for (i in 0 until marcadas.size()) {
            if (marcadas.valueAt(i)) adaptador.items.getOrNull(marcadas.keyAt(i))?.let { res += it }
        }
        return res.sortedBy { it.creado }
    }

    // ---------- Cámara + ubicación ----------

    private fun sacarFoto() {
        val prefs = getSharedPreferences("estado", MODE_PRIVATE)
        val yaPedido = prefs.getBoolean("pidioUbicacion", false)
        if (!ubicador.tienePermiso() &&
            (!yaPedido || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION))
        ) {
            prefs.edit().putBoolean("pidioUbicacion", true).apply()
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_PERMISO)
            return
        }
        if (ubicador.tienePermiso() && !ubicador.servicioPrendido()) {
            AlertDialog.Builder(this)
                .setTitle("Ubicación apagada")
                .setMessage("Prendé la ubicación del teléfono para guardar la dirección de la foto.")
                .setPositiveButton("Prender") { _, _ ->
                    try { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (e: ActivityNotFoundException) {}
                }
                .setNegativeButton("Seguir sin ubicación") { _, _ -> abrirCamara() }
                .show()
            return
        }
        abrirCamara()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISO) return
        if (!ubicador.tienePermiso()) toast("Sin permiso de ubicación: podés escribir la dirección a mano después")
        if (ubicador.tienePermiso() && !ubicador.servicioPrendido()) sacarFoto() else abrirCamara()
    }

    private fun abrirCamara() {
        esperandoGps?.let { terminarEsperaGps() } // si quedaba una foto esperando GPS, se cierra con lo que haya
        ubicador.iniciar() // el GPS va calentando mientras se saca la foto

        val id = "R" + System.currentTimeMillis()
        val uri = FotoProvider.uri(this, id)
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("foto", uri)
        try {
            capturaPendiente = id
            startActivityForResult(i, REQ_FOTO)
        } catch (e: ActivityNotFoundException) {
            capturaPendiente = null
            ubicador.detener()
            toast("No encontré una app de cámara")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQ_FOTO -> fotoTomada(resultCode == RESULT_OK)
            REQ_ENVIO -> volvioDeWhatsApp()
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun fotoTomada(ok: Boolean) {
        val id = capturaPendiente ?: return
        capturaPendiente = null
        val foto = Repo.foto(id)
        if (!ok || !foto.exists() || foto.length() == 0L) {
            foto.delete()
            ubicador.detener()
            return
        }
        // Si Android cerró la app mientras estaba la cámara, el GPS se apagó: se vuelve a prender.
        ubicador.iniciar()
        Hilos.fondo.execute {
            val res = Imagenes.procesar(foto, Repo.mini(id))
            Hilos.principal.post {
                if (isDestroyed) return@post
                if (!res.ok) {
                    foto.delete(); ubicador.detener()
                    toast("No se pudo guardar la foto"); return@post
                }
                Repo.agregar(Reporte(id, System.currentTimeMillis()))
                ubicar(id, if (res.exifLat != null && res.exifLon != null) res.exifLat to res.exifLon else null)
                refrescar()
            }
        }
    }

    /** Asigna la ubicación a la foto: GPS si ya hay una buena; si no, espera hasta 25 s. */
    private fun ubicar(id: String, exif: Pair<Double, Double>?) {
        val actual = ubicador.mejor
        if (ubicador.esBuena(actual)) {
            fijar(id, actual!!.latitude, actual.longitude, if (actual.hasAccuracy()) actual.accuracy else null)
            ubicador.detener()
            return
        }
        if (!ubicador.tienePermiso()) {
            if (exif != null) fijar(id, exif.first, exif.second, null)
            return
        }
        esperandoGps = id
        exifRespaldo = exif
        Repo.get(id)?.buscandoUbicacion = true
        toast("Buscando ubicación GPS…")
        ubicador.alCambiar = { if (ubicador.esBuena(ubicador.mejor)) terminarEsperaGps() }
        Hilos.principal.postDelayed(finEspera, ESPERA_GPS_MS)
    }

    private fun terminarEsperaGps() {
        val id = esperandoGps ?: return
        esperandoGps = null
        Hilos.principal.removeCallbacks(finEspera)
        val l = ubicador.mejor ?: ubicador.ultimaConocida(10 * 60_000L)
        val exif = exifRespaldo
        exifRespaldo = null
        ubicador.detener()
        Repo.get(id)?.buscandoUbicacion = false
        when {
            l != null -> fijar(id, l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else null)
            exif != null -> fijar(id, exif.first, exif.second, null)
            else -> toast("No se pudo obtener la ubicación. Podés escribir la dirección a mano.")
        }
        refrescar()
    }

    private fun fijar(id: String, lat: Double, lon: Double, precision: Float?) {
        Repo.actualizar(id) { it.lat = lat; it.lon = lon; it.precision = precision }
        geocodificar(id)
    }

    private fun geocodificar(id: String) {
        val r = Repo.get(id) ?: return
        val lat = r.lat ?: return
        val lon = r.lon ?: return
        Direcciones.buscar(this, lat, lon) { dir ->
            if (dir != null && Repo.get(id)?.direccion.isNullOrBlank()) {
                Repo.actualizar(id) { it.direccion = dir }
                if (!isDestroyed) refrescar()
            }
        }
    }

    /** Fotos con coordenadas pero sin dirección (se sacaron sin red): se reintenta al volver a la app. */
    private fun reintentarDirecciones() {
        Repo.todos().filter { it.tieneCoordenadas() && it.direccion.isNullOrBlank() && !it.buscandoUbicacion }
            .take(10)
            .forEach { geocodificar(it.id) }
    }

    // ---------- Envío por WhatsApp ----------

    private fun enviar(ids: List<String>) {
        if (ids.isEmpty()) return
        if (Ajustes.internacional(Ajustes.numero(this)) == null) {
            AlertDialog.Builder(this)
                .setMessage("Primero configurá el número de WhatsApp al que se mandan los reportes.")
                .setPositiveButton("Ir a Ajustes") { _, _ -> startActivity(Intent(this, AjustesActivity::class.java)) }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        cola = ArrayList(ids)
        colaTotal = ids.size
        enviarSiguiente()
    }

    private fun enviarSiguiente() {
        while (cola.isNotEmpty() && Repo.get(cola[0]) == null) cola.removeAt(0)
        if (cola.isEmpty()) {
            colaTotal = 0; refrescar(); return
        }
        val r = Repo.get(cola[0])!!
        try {
            startActivityForResult(WhatsApp.intent(this, r), REQ_ENVIO)
        } catch (e: ActivityNotFoundException) {
            cola.clear(); colaTotal = 0
            toast("No hay ninguna app para compartir la foto")
        }
    }

    /**
     * WhatsApp no avisa si el mensaje se mandó (siempre devuelve "cancelado"), así que al volver se
     * marca como enviada y se pregunta, con opción de deshacer, antes de seguir con la próxima.
     */
    private fun volvioDeWhatsApp() {
        if (cola.isEmpty()) return
        val id = cola.removeAt(0)
        val n = colaTotal - cola.size
        Repo.actualizar(id) { it.enviado = true; it.enviadoEn = System.currentTimeMillis() }
        refrescar()
        val quedan = cola.isNotEmpty()
        val b = AlertDialog.Builder(this)
            .setTitle(if (colaTotal > 1) "Foto $n de $colaTotal" else "Foto enviada")
            .setMessage(
                "Se marcó como «Enviado»." +
                    if (quedan) "\n¿Seguimos con la siguiente?" else ""
            )
            .setCancelable(false)
            .setPositiveButton(if (quedan) "Siguiente" else "Listo") { _, _ -> enviarSiguiente() }
            .setNegativeButton("No se envió") { _, _ ->
                Repo.actualizar(id) { it.enviado = false; it.enviadoEn = null }
                refrescar()
                if (quedan) enviarSiguiente() else colaTotal = 0
            }
        if (quedan) b.setNeutralButton("Parar") { _, _ -> cola.clear(); colaTotal = 0 }
        b.show()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}

/** Etiqueta de estado tipo "chip": "Pendiente de reportar" (naranja) o "Enviado" (verde). */
object Estado {
    fun pintar(tv: TextView, r: Reporte) {
        val c = tv.context
        tv.text = if (r.enviado) "✓ Enviado" else "Pendiente de reportar"
        tv.setTextColor(c.getColor(if (r.enviado) R.color.sobre_enviado_cont else R.color.sobre_pendiente_cont))
        tv.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 8 * c.resources.displayMetrics.density
            setColor(c.getColor(if (r.enviado) R.color.enviado_cont else R.color.pendiente_cont))
        }
    }
}

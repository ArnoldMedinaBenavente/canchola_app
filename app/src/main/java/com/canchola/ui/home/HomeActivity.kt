package com.canchola.ui.home

import QuotesAdapter
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.canchola.MainActivity
import com.canchola.R
import com.canchola.data.local.AppDatabase
import com.canchola.data.local.SessionManager
import com.canchola.data.remote.RetrofitClient
import com.canchola.data.repository.QuoteConceptRepository
import com.canchola.databinding.ActivityHomeBinding
import com.canchola.models.LogEntry
import com.canchola.models.Quote
import com.canchola.models.QuoteConcepts
import com.canchola.ui.AddLogSheet
import com.canchola.ui.photo.PhotoAdapter
import com.canchola.ui.photo.Photos
import com.canchola.ui.quotes.QuoteDetailActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var adapter: QuotesAdapter
    private val apiService by lazy { RetrofitClient.getInstance(this) }
    private lateinit var db: AppDatabase

    private lateinit var sessionManager: SessionManager
    var currentUserId: Int? =null

    // Variables para manejar qué lista y adaptador actualizar al tomar una foto
    private var currentEditingUriList: MutableList<Uri>? = null
    private var currentEditingAdapter: PhotoAdapter? = null

    // Variable temporal para la foto que se está tomando en este momento
    private var currentPhotoUri: Uri? = null
    private var currentPhotoAbsolutePath: String? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) {
            currentPhotoAbsolutePath?.let { path ->
                val newUri = Uri.fromFile(java.io.File(path))
                
                // Si tenemos un "contexto" de edición (un RecyclerView específico), lo actualizamos
                currentEditingUriList?.let { list ->
                    list.add(newUri)
                    currentEditingAdapter?.notifyItemInserted(list.size - 1)
                }
            }
        }
    }
    private var activeEditText: EditText? = null
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let {
                val currentText = activeEditText?.text.toString()
                if (currentText.isEmpty()) {
                    activeEditText?.setText(it)
                } else {
                    activeEditText?.setText("$currentText $it")
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            getLastLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermissions()
        // Inicializas la base de datos
        db = AppDatabase.getDatabase(this)
        setupRecyclerView()
        setupListeners()
        loadQuotes()
        updateSyncBadge()
        sessionManager = SessionManager(this)
         currentUserId = sessionManager.getUserId()
    }

    private fun checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            getLastLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lastLat = location.latitude
                lastLon = location.longitude
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvQuotes.layoutManager = LinearLayoutManager(this)
    }

    private fun loadQuotes() {
        lifecycleScope.launch {
            try {
                // Paso 1: Intentar traer de Laravel (Online)
                val response = apiService.getQuotes()

                if (response.isSuccessful && response.body() != null) {
                    val remoteQuotes = response.body()!!
                    try {
                        db.quoteDao().insertQuotes(remoteQuotes)
                        Log.d("ROOM_DB", "¡ÉXITO! Se guardó en la memoria.")
                    } catch (dbError: Exception) {
                        Log.e("ROOM_DB", "ERROR AL INSERTAR: ${dbError.message}")
                    }
                    displayQuotes(remoteQuotes)
                } else {
                    // Si el servidor falla (ej. error 500), intentar cargar local
                    loadFromLocalDatabase("Error del servidor, mostrando datos locales")
                }
            } catch (e: Exception) {
                // Paso 4: Si no hay internet (Network Exception), cargar local
                loadFromLocalDatabase(getString(R.string.loading))
            }
        }
    }

    // Función extra para manejar el fallo de red
    private suspend fun loadFromLocalDatabase(message: String) {
        val localQuotes = db.quoteDao().getAllQuotes()
        if (localQuotes.isNotEmpty()) {
            displayQuotes(localQuotes)
            Toast.makeText(this@HomeActivity, message, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this@HomeActivity, "No hay datos guardados", Toast.LENGTH_SHORT).show()
        }
    }


    private fun displayQuotes(list: List<Quote>) {
        // 1. Creamos el adaptador pasando la lista y las dos acciones (Descarga y Opciones)
        adapter = QuotesAdapter(
            list,
            { quote ->
                // Acción cuando tocan el botón azul de descarga
                downloadQuote(quote)
            },
            { quote ->
                // Acción cuando tocan la tarjeta (abre el menú de iOS)
                showQuoteOptions(quote)
            }
        )

        // 2. Asignamos el adaptador al RecyclerView
        binding.rvQuotes.adapter = adapter
    }


    private fun downloadQuote(quote: Quote) {

    }

    private fun setupListeners() {
        // Botón Actualizar
        binding.btnRefresh.setOnClickListener {
            loadQuotes() // Llama a la función que trae los datos de Laravel
            updateSyncBadge()
            Toast.makeText(this, "Actualizando...", Toast.LENGTH_SHORT).show()
        }

        // Botón Cerrar Sesión
        binding.btnLogout.setOnClickListener {
            val sessionManager = SessionManager(this)
            sessionManager.clearSession() // Debes tener este método en tu SessionManager

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // Botón Sincronizar Todo (Campanita)
        binding.btnSyncAll.setOnClickListener {
            checkAndSyncAll()
        }
    }

    private fun checkAndSyncAll() {
        lifecycleScope.launch {
            val unsyncedLogs = db.logEntryDao().getUnsyncedLogs()
            val unsyncedPhotos = db.photoDao().getUnsyncedPhotos()
            val unsyncedConcepts = db.quoteConceptDao().getAllUnsyncedConcepts()
            
            val totalComments = unsyncedLogs.size + unsyncedConcepts.size
            val totalPhotos = unsyncedPhotos.size
            
            if (totalComments == 0 && totalPhotos == 0) {
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("Al día")
                    .setMessage("Todo está sincronizado. No hay notas ni fotos pendientes de enviar.")
                    .setPositiveButton("Cerrar", null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("Pendientes de enviar")
                    .setMessage("Notas por enviar: $totalComments\nFotos por enviar: $totalPhotos")
                    .setPositiveButton("Enviar Todo") { _, _ ->
                        performSyncAll()
                    }
                    .setNegativeButton("Más tarde", null)
                    .show()
            }
        }
    }

    private fun performSyncAll() {
        val loading = MaterialAlertDialogBuilder(this)
            .setTitle("Sincronizando")
            .setMessage("Enviando todos los datos pendientes...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val repository = QuoteConceptRepository(
                    null, currentUserId ?: 0, db.quoteConceptDao(), db.logEntryDao(), db.photoDao(), apiService, this@HomeActivity
                )
                val success = repository.syncAllUnsyncedData()
                if (success) {
                    Toast.makeText(this@HomeActivity, "✅ Todo sincronizado correctamente", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@HomeActivity, "⚠️ Algunos datos no se pudieron enviar", Toast.LENGTH_LONG).show()
                }
                updateSyncBadge()
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "❌ Error al sincronizar", Toast.LENGTH_SHORT).show()
            } finally {
                loading.dismiss()
            }
        }
    }

    private fun updateSyncBadge() {
        lifecycleScope.launch {
            val unsyncedLogsCount = db.logEntryDao().getUnsyncedLogs().size
            val unsyncedConceptsCount = db.quoteConceptDao().getAllUnsyncedConcepts().size
            val unsyncedPhotosCount = db.photoDao().getUnsyncedPhotos().size
            val total = unsyncedLogsCount + unsyncedConceptsCount + unsyncedPhotosCount
            
            if (total > 0) {
                binding.tvSyncBadge.text = total.toString()
                binding.tvSyncBadge.visibility = View.VISIBLE
               // binding.ivBell.imageTintList = ColorStateList.valueOf(Color.parseColor("#007AFF")) // Azul
            } else {
                binding.tvSyncBadge.visibility = View.GONE
               // binding.ivBell.imageTintList = ColorStateList.valueOf(Color.GRAY) // Gris
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSyncBadge()
    }

    private fun showQuoteOptions(quote: Quote) {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.layout_quote_options, null)

        // Opción 1: VER MÁS (Navegación)
        view.findViewById<TextView>(R.id.btnViewMore).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, QuoteDetailActivity::class.java)
            intent.putExtra("QUOTE_DATA", quote) // Pasamos toda la info de la cotización
            startActivity(intent)
        }

        // Opción 2: REGISTRAR AVANCES
        view.findViewById<TextView>(R.id.btnProgress).setOnClickListener {
            dialog.dismiss()
            mostrarFormularioAvance(quote)
        }
        // Opción 3: Commentario
        view.findViewById<TextView>(R.id.btnAddComment).setOnClickListener {
            dialog.dismiss()
            val sheet = AddLogSheet(quoteId = quote.idQuote,"comment") // Pasas el ID
            sheet.show(supportFragmentManager, "AddLog")
        }
        // Opción 4: TOMAR FOTO
        view.findViewById<TextView>(R.id.btnAddPhoto).setOnClickListener {
            dialog.dismiss()
            // Aquí podrías manejar fotos generales de la cotización si fuera necesario
            val sheet = AddLogSheet(quoteId = quote.idQuote,"photo") // Pasas el ID
            sheet.show(supportFragmentManager, "AddLog")
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun mostrarFormularioAvance(quote: Quote) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_registrar_avance, null)
        dialog.setContentView(view)

        val container = view.findViewById<LinearLayout>(R.id.containerAlcancesDialog)
        val btnGuardar = view.findViewById<Button>(R.id.btnGuardarTodoDialog)

        val cadenaAlcances = quote?.conceptos ?: ""

        // Mapas para guardar las referencias de datos por cada vista inflada
        val dataMap = mutableMapOf<View, MutableList<Uri>>()

        if (cadenaAlcances.isNotEmpty()) {
            val lista = cadenaAlcances.split("|")

            lista.forEach { concepto ->
                val conceptoLimpio = concepto.trim()
                if (conceptoLimpio.isNotEmpty()) {
                    val dataConcepto = conceptoLimpio.split(";")
                    val idConcepto = dataConcepto.get(0)
                    val nombreConcepto = dataConcepto.get(1)
                    val arrayNombreConcepto = nombreConcepto.split(" ")

                    val cantidadTotal = arrayNombreConcepto.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                    val unitConcepto = arrayNombreConcepto.get(1)


                    val itemView = layoutInflater.inflate(R.layout.item_alcance, null)
                    val tvIdConcept = itemView.findViewById<TextView>(R.id.tvIdConcept)
                    val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreAlcance)
                    val etCantidad = itemView.findViewById<EditText>(R.id.etCantidadAvance)
                    val btnAgregarFoto = itemView.findViewById<Button>(R.id.btnAgregarFoto)
                    val btnAgregarComentario = itemView.findViewById<Button>(R.id.btnAgregarComentario)
                    val rvPhotos = itemView.findViewById<RecyclerView>(R.id.rvPhotosHome)
                    val etComentario = itemView.findViewById<EditText>(R.id.etComentario)
                    val layoutComentarioVoz = itemView.findViewById<View>(R.id.layoutComentarioVoz)
                    val btnVoice = itemView.findViewById<ImageButton>(R.id.btnVoiceAlcance)
                    val tvAcumulado = itemView.findViewById<TextView>(R.id.tvCantidadAcumulada)

                    tvIdConcept.text = idConcepto
                    tvNombre.text = nombreConcepto
                    etCantidad.setHint("0.0")

                    lifecycleScope.launch {
                        try {
                            val logs = db.logEntryDao().getLogsByConcept(idConcepto)
                            val suma = logs.sumOf { it.cantidad?.toDoubleOrNull() ?: 0.0 }

                            tvAcumulado.text = " ${String.format("%.2f", suma)} / $cantidadTotal ${unitConcepto}"

                            when {
                                suma > cantidadTotal && cantidadTotal > 0 -> {
                                    tvAcumulado.setTextColor(Color.parseColor("#FF3B30")) // Rojo (Mayor)
                                }
                                suma == cantidadTotal && cantidadTotal > 0 -> {
                                    tvAcumulado.setTextColor(Color.parseColor("#34C759")) // Verde (Igual)
                                }
                                suma > 0 && suma < cantidadTotal -> {
                                    tvAcumulado.setTextColor(Color.parseColor("#FFCC00")) // Amarillo (Menor)
                                }
                                else -> {
                                    tvAcumulado.setTextColor(Color.GRAY)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ROOM_ERROR", "Error al obtener logs: ${e.message}")
                        }
                    }

                    val itemUriList = mutableListOf<Uri>()
                    dataMap[itemView] = itemUriList
                    
                    val itemPhotoAdapter = PhotoAdapter(itemUriList) { position ->
                        itemUriList.removeAt(position)
                        (rvPhotos.adapter as? PhotoAdapter)?.notifyItemRemoved(position)
                    }

                    rvPhotos.visibility = View.VISIBLE
                    rvPhotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                    rvPhotos.adapter = itemPhotoAdapter

                    container.addView(itemView)
                    btnAgregarComentario.setOnClickListener {
                        layoutComentarioVoz.visibility = if (layoutComentarioVoz.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    btnAgregarFoto.setOnClickListener {
                        currentEditingUriList = itemUriList
                        currentEditingAdapter = itemPhotoAdapter
                        openCamera()
                    }
                    btnVoice.setOnClickListener {
                        activeEditText = etComentario
                        startVoiceRecognition()
                    }
                }
            }
        }

        btnGuardar.setOnClickListener {
            // ... (Lógica de guardado existente) ...
            guardarTodo(container, dataMap, quote, dialog)
        }
        dialog.show()
    }

    private fun guardarTodo(container: LinearLayout, dataMap: Map<View, List<Uri>>, quote: Quote, dialog: BottomSheetDialog) {
        val loadingDialog = MaterialAlertDialogBuilder(this@HomeActivity)
            .setTitle("Procesando")
            .setMessage("Enviando información, por favor espere...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                for (i in 0 until container.childCount) {
                    val item = container.getChildAt(i)
                    val idConcept = item.findViewById<TextView>(R.id.tvIdConcept).text.toString()
                    val nombre = item.findViewById<TextView>(R.id.tvNombreAlcance).text.toString()
                    val cant = item.findViewById<EditText>(R.id.etCantidadAvance).text.toString()
                    val comentario = item.findViewById<EditText>(R.id.etComentario).text.toString()
                    val uris = dataMap[item] ?: emptyList<Uri>()

                    var logIdGenerated: Int? = null

                    if (comentario.isNotEmpty() || uris.isNotEmpty() || cant.isNotEmpty()) {
                        val insertedId = db.logEntryDao().insert(LogEntry(
                            quoteId = quote.idQuote,
                            idConcept = idConcept,
                            comment = if(comentario.isEmpty()) "Avance de $nombre" else comentario,
                            cantidad = cant,
                            isSynced = false,
                            latitude = lastLat,
                            longitude = lastLon
                        ))
                        logIdGenerated = insertedId.toInt()

                        if (uris.isNotEmpty()) {
                            val photosToInsert = uris.map { uri ->
                                Photos(
                                    logEntryId = logIdGenerated,
                                    uri = uri.toString(),
                                    isUploaded = false
                                )
                            }
                            db.photoDao().insertPhotos(photosToInsert)
                        }
                    }

                    if (cant.isNotEmpty()) {
                        val newConcept = QuoteConcepts(
                            idConcept = idConcept,
                            quoteId = quote?.idQuote,
                            nameConcept = nombre,
                            cantConcept = cant,
                            comment = if(comentario.isNotEmpty()) comentario else null,
                            idLog = logIdGenerated,
                            isSynced = false,
                            idUser = currentUserId,
                            logIdGenerated = logIdGenerated,
                            latitude = lastLat,
                            longitude = lastLon
                        )
                        db.quoteConceptDao().insert(newConcept)
                    }
                }
                
                val repository = QuoteConceptRepository(
                    quote, sessionManager.getUserId(), db.quoteConceptDao(), db.logEntryDao(), db.photoDao(), apiService, this@HomeActivity
                )
                val quoteConceptsList = db.quoteConceptDao().getUnsyncedConcepts(quote.idQuote)
                val isSynced = repository.SyncConcepts(quoteConceptsList)

                if (isSynced) {
                    Toast.makeText(this@HomeActivity, "✅ Enviado exitosamente", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@HomeActivity, "💾 Guardado localmente ", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("SAVE_ERROR", "Error: ${e.message}")
                Toast.makeText(this@HomeActivity, "❌ Error al procesar datos", Toast.LENGTH_SHORT).show()
            } finally {
                loadingDialog.dismiss()
                updateSyncBadge()
                dialog.dismiss()
            }
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta tu comentario...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tu dispositivo no soporta dictado por voz", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        val photoFile = createImageFile()
        currentPhotoAbsolutePath = photoFile.absolutePath
        currentPhotoUri = FileProvider.getUriForFile(
            this, "${this.packageName}.provider", photoFile
        )

        currentPhotoUri?.let { uri ->
            takePhotoLauncher.launch(uri)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("CANCHOLA_${timeStamp}_", ".jpg", storageDir)
    }
}
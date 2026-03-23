package com.canchola.ui.home

import QuotesAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializas la base de datos
        db = AppDatabase.getDatabase(this)
        setupRecyclerView()
        setupListeners()
        loadQuotes()
        sessionManager = SessionManager(this)
         currentUserId = sessionManager.getUserId()
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
                    
                    val itemView = layoutInflater.inflate(R.layout.item_alcance, null)
                    val tvIdConcept = itemView.findViewById<TextView>(R.id.tvIdConcept)
                    val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreAlcance)
                    val etCantidad = itemView.findViewById<EditText>(R.id.etCantidadAvance)
                    val btnAgregarFoto = itemView.findViewById<Button>(R.id.btnAgregarFoto)
                    val btnAgregarComentario = itemView.findViewById<Button>(R.id.btnAgregarComentario)
                    val rvPhotos = itemView.findViewById<RecyclerView>(R.id.rvPhotosHome)
                    val etComentario = itemView.findViewById<EditText>(R.id.etComentario)
                    
                    // --- NUEVO: Cada concepto tiene su propia lista de  y su propio adaptador ---
                    val itemUriList = mutableListOf<Uri>()
                    dataMap[itemView] = itemUriList
                    
                    val itemPhotoAdapter = PhotoAdapter(itemUriList) { position ->
                        itemUriList.removeAt(position)
                        (rvPhotos.adapter as? PhotoAdapter)?.notifyItemRemoved(position)
                    }

                    rvPhotos.visibility = View.VISIBLE
                    rvPhotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                    rvPhotos.adapter = itemPhotoAdapter

                    tvIdConcept.text = idConcepto
                    tvNombre.text = nombreConcepto
                    etCantidad.setHint("0.0")

                    container.addView(itemView)
                    btnAgregarComentario.setOnClickListener {
                        etComentario.visibility = if(etComentario.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    btnAgregarFoto.setOnClickListener {
                        currentEditingUriList = itemUriList
                        currentEditingAdapter = itemPhotoAdapter
                        openCamera()
                    }
                }
            }
        }

        btnGuardar.setOnClickListener {
            // 1. Mostrar diálogo de carga
            val loadingDialog = MaterialAlertDialogBuilder(this@HomeActivity)
                .setTitle("Procesando")
                .setMessage("Enviando información, por favor espere...")
                .setCancelable(false)
                .show()
            
            btnGuardar.isEnabled = false // Desactivar el botón

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

                        // 1. Primero guardamos el LogEntry si hay evidencia (comentario o fotos)
                        if(cant.isNotEmpty() ) {
                            val insertedId = db.logEntryDao().insert(
                                LogEntry(
                                    quoteId = quote.idQuote,
                                    idConcept = idConcept,
                                    comment = if (comentario.isEmpty()) "Registro de avance" else comentario,
                                    cantidad = cant,
                                    isSynced = false
                                )
                            )
                            logIdGenerated = insertedId.toInt()


                            // Guardar las Fotos vinculadas al LogEntry
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

//                        // 2. Guardar el Avance (QuoteConcepts) vinculando el idLog
//                        if (cant.isNotEmpty()) {
//                            val newConcept = QuoteConcepts(
//                                idConcept = idConcept,
//                                quoteId = quote?.idQuote,
//                                nameConcept = nombre,
//                                cantConcept = cant,
//                                comment = if(comentario.isNotEmpty()) comentario else null,
//                                idLog = logIdGenerated, // VÍNCULO DIRECTO
//                                isSynced = false,
//                                idUser = currentUserId,
//                                logIdGenerated = logIdGenerated
//                            )
//                            db.quoteConceptDao().insert(newConcept)
//                        }
                    }
                    
                    val repoitoryQuoteConcepts = QuoteConceptRepository(
                        quote, sessionManager.getUserId(), db.quoteConceptDao(),db.logEntryDao(),db.photoDao(), apiService, this@HomeActivity
                    )
                    val quoteConceptsList = db.quoteConceptDao().getUnsyncedConcepts(quote.idQuote)
                    val isSynced = repoitoryQuoteConcepts.SyncConcepts(quoteConceptsList)

                    if (isSynced) {
                        Toast.makeText(this@HomeActivity, "✅ Enviado exitosamente", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@HomeActivity, "💾 Guardado localmente ", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("SAVE_ERROR", "Error: ${e.message}")
                    Toast.makeText(this@HomeActivity, "❌ Error al procesar datos", Toast.LENGTH_SHORT).show()
                } finally {
                    // 2. Ocultar diálogo y cerrar el formulario al terminar
                    loadingDialog.dismiss()
                    currentEditingUriList = null
                    currentEditingAdapter = null
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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
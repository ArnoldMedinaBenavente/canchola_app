package com.canchola.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.canchola.MainActivity
import com.canchola.R
import com.canchola.data.local.AppDatabase
import com.canchola.data.local.SessionManager
import com.canchola.data.remote.RetrofitClient
import com.canchola.data.repository.DataSyncRepository
import com.canchola.data.repository.QuoteConceptRepository
import com.canchola.databinding.ActivityHomeBinding
import com.canchola.models.LogEntry
import com.canchola.models.Quote
import com.canchola.models.QuoteConcepts
import com.canchola.ui.AddLogSheet
import com.canchola.ui.photo.PhotoAdapter
import com.canchola.ui.photo.Photos
import com.canchola.ui.quotes.CreateQuoteActivity
import com.canchola.ui.quotes.QuoteDetailActivity
import com.canchola.ui.quotes.QuotesListFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val apiService by lazy { RetrofitClient.getInstance(this) }
    private lateinit var db: AppDatabase
    private lateinit var sessionManager: SessionManager
    private var currentUserId: Int? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    private var currentEditingUriList: MutableList<Uri>? = null
    private var currentEditingAdapter: PhotoAdapter? = null
    private var currentPhotoUri: Uri? = null
    private var currentPhotoAbsolutePath: String? = null

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) {
            currentPhotoAbsolutePath?.let { path ->
                val newUri = Uri.fromFile(File(path))
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
                activeEditText?.setText(if (currentText.isEmpty()) it else "$currentText $it")
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

        db = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        currentUserId = sessionManager.getUserId()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        checkLocationPermissions()
        setupBottomNavigation()
        setupListeners()
        updateSyncBadge()
        
        loadServerData()
        
        if (savedInstanceState == null) {
            binding.tvTitle.text = "Operaciones"
            binding.fabAddQuote.visibility = View.GONE
            replaceFragment(QuotesListFragment.newInstance(QuotesListFragment.TYPE_OPERATIONS))
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_operations -> {
                    binding.tvTitle.text = "Operaciones"
                    binding.fabAddQuote.visibility = View.GONE
                    replaceFragment(QuotesListFragment.newInstance(QuotesListFragment.TYPE_OPERATIONS))
                    true
                }
                R.id.nav_new_quotes -> {
                    binding.tvTitle.text = "Solicitudes"
                    binding.fabAddQuote.visibility = View.VISIBLE
                    replaceFragment(QuotesListFragment.newInstance(QuotesListFragment.TYPE_NEW_QUOTES))
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun loadServerData() {
        lifecycleScope.launch {
            try {
                val response = apiService.getActives()
                if (response.isSuccessful && response.body() != null) {
                    val remoteQuotes = response.body()!!
                    val processedData = remoteQuotes.map { item ->
                        item.apply { unique_id = if (idQuote != null) "Q$idQuote" else "P$idProject" }
                    }
                    db.quoteDao().insertQuotes(processedData)
                }
            } catch (e: Exception) {
                Log.e("HOME", "Error al cargar cotizaciones: ${e.message}")
            }

            try {
                val syncRepo = DataSyncRepository(apiService, db.customerDao(), db.conceptDao(), db.unitDao())
                syncRepo.syncCatalogData()
            } catch (e: Exception) {
                Log.e("HOME", "Error al sincronizar catálogos: ${e.message}")
            }
            
            refreshCurrentFragment()
        }
    }

    private fun refreshCurrentFragment() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is QuotesListFragment) {
            currentFragment.loadQuotes()
        }
    }

    private fun setupListeners() {
        binding.fabAddQuote.setOnClickListener {
            val intent = Intent(this, CreateQuoteActivity::class.java)
            startActivity(intent)
        }

        binding.btnRefresh.setOnClickListener {
            Toast.makeText(this, "Actualizando datos...", Toast.LENGTH_SHORT).show()
            loadServerData()
            updateSyncBadge()
        }

        binding.btnLogout.setOnClickListener {
            sessionManager.clearSession()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.btnSyncAll.setOnClickListener {
            checkAndSyncAll()
        }
    }

    fun showQuoteOptions(quote: Quote) {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.layout_quote_options, null)

        val btnEdit = view.findViewById<TextView>(R.id.btnEditQuote)
        val btnDelete = view.findViewById<TextView>(R.id.btnDeleteQuote)
        val btnSend = view.findViewById<TextView>(R.id.btnSendToServer)
        
        val btnViewMore = view.findViewById<TextView>(R.id.btnViewMore)
        val btnProgress = view.findViewById<TextView>(R.id.btnProgress)
        val btnAddComment = view.findViewById<TextView>(R.id.btnAddComment)
        val btnAddPhoto = view.findViewById<TextView>(R.id.btnAddPhoto)

        if (quote.isLocal()) {
            // MOSTRAR solo opciones de Solicitud (Gestión)
            btnEdit.visibility = View.VISIBLE
            btnDelete.visibility = View.VISIBLE
            // Mostrar enviar a cotizar solo si no se ha sincronizado
            btnSend.visibility =  View.VISIBLE
            
            // OCULTAR todas las opciones de seguimiento operativo (Pedido por el usuario)
            btnViewMore.visibility = View.GONE
            btnProgress.visibility = View.GONE
            btnAddComment.visibility = View.GONE
            btnAddPhoto.visibility = View.GONE
            
            btnEdit.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, CreateQuoteActivity::class.java)
                intent.putExtra("QUOTE_DATA", quote)
                startActivity(intent)
            }

            btnDelete.setOnClickListener {
                dialog.dismiss()
                confirmDeleteQuote(quote)
            }
            
            btnSend.setOnClickListener {
                dialog.dismiss()
                performSyncAll()
            }
        } else {
            // OPERACIÓN OFICIAL: Ocultar gestión local y mostrar operativo
            btnEdit.visibility = View.GONE
            btnDelete.visibility = View.GONE
            btnSend.visibility = View.GONE
            
            btnViewMore.visibility = View.VISIBLE
            btnProgress.visibility = View.VISIBLE
            btnAddComment.visibility = View.VISIBLE
            btnAddPhoto.visibility = View.VISIBLE

            btnViewMore.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, QuoteDetailActivity::class.java)
                intent.putExtra("QUOTE_DATA", quote)
                startActivity(intent)
            }

            btnProgress.setOnClickListener {
                dialog.dismiss()
                mostrarFormularioAvance(quote)
            }
            
            btnAddComment.setOnClickListener {
                dialog.dismiss()
                val sheet = AddLogSheet(quoteId = quote.idQuote, idProject = quote.idProject, type = "comment")
                sheet.show(supportFragmentManager, "AddLog")
            }
            
            btnAddPhoto.setOnClickListener {
                dialog.dismiss()
                val sheet = AddLogSheet(quoteId = quote.idQuote, idProject = quote.idProject, type = "photo")
                sheet.show(supportFragmentManager, "AddLog")
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    fun confirmDeleteQuote(quote: Quote) {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Eliminar cotización?")
            .setMessage("Esta acción eliminará la cotización local y todos sus conceptos asociados. Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                deleteLocalQuote(quote)
            }
            .show()
    }

    private fun deleteLocalQuote(quote: Quote) {
        lifecycleScope.launch {
            try {
                val logId = quote.unique_id.removePrefix("LOCAL_").toIntOrNull()
                if (logId != null) {
                    db.quoteConceptDao().deleteByLog(logId)
                    db.photoDao().deletePhotosByLog(logId)
                    db.logEntryDao().deleteById(logId)
                }
                
                db.quoteDao().deleteQuoteByUniqueId(quote.unique_id)
                
                Toast.makeText(this@HomeActivity, "Cotización eliminada", Toast.LENGTH_SHORT).show()
                refreshCurrentFragment()
                updateSyncBadge()
            } catch (e: Exception) {
                Log.e("DELETE_ERROR", "Error al eliminar: ${e.message}")
                Toast.makeText(this@HomeActivity, "Error al eliminar cotización", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarFormularioAvance(quote: Quote) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_registrar_avance, null)
        dialog.setContentView(view)

        val container = view.findViewById<LinearLayout>(R.id.containerAlcancesDialog)
        val btnGuardar = view.findViewById<Button>(R.id.btnGuardarTodoDialog)

        val cadenaAlcances = quote.conceptos_detalle ?: ""
        val dataMap = mutableMapOf<View, MutableList<Uri>>()

        if (cadenaAlcances.isNotEmpty()) {
            val lista = cadenaAlcances.split("|")
            lista.forEach { concepto ->
                val dataConcepto = concepto.trim().split(";")
                if (dataConcepto.size >= 2) {
                    val idConcepto = dataConcepto[0]
                    val nombreConcepto = dataConcepto[1]
                    val itemDetails = dataConcepto.getOrNull(2) ?: ""

                    val itemView = layoutInflater.inflate(R.layout.item_alcance, null)
                    val tvIdConcept = itemView.findViewById<TextView>(R.id.tvIdConcept)
                    val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreAlcance)
                    val etCantidad = itemView.findViewById<EditText>(R.id.etCantidadAvance)
                    val btnAgregarFoto = itemView.findViewById<Button>(R.id.btnAgregarFoto)
                    val rvPhotos = itemView.findViewById<RecyclerView>(R.id.rvPhotosHome)
                    val etComentario = itemView.findViewById<EditText>(R.id.etComentario)
                    val tvAcumulado = itemView.findViewById<TextView>(R.id.tvCantidadAcumulada)

                    tvIdConcept.text = idConcepto
                    tvNombre.text = nombreConcepto
                    etCantidad.setHint("0.0")
                    tvAcumulado.text = "Meta: $itemDetails"

                    val itemUriList = mutableListOf<Uri>()
                    dataMap[itemView] = itemUriList

                    val itemPhotoAdapter = PhotoAdapter(itemUriList, onDeleteClick = { position ->
                        itemUriList.removeAt(position)
                        (rvPhotos.adapter as? PhotoAdapter)?.notifyItemRemoved(position)
                    })

                    rvPhotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                    rvPhotos.adapter = itemPhotoAdapter
                    rvPhotos.visibility = View.VISIBLE

                    btnAgregarFoto.setOnClickListener {
                        currentEditingUriList = itemUriList
                        currentEditingAdapter = itemPhotoAdapter
                        openCamera()
                    }

                    container.addView(itemView)
                }
            }
        }

        btnGuardar.setOnClickListener {
            guardarTodo(container, dataMap, quote, dialog)
        }
        dialog.show()
    }

    private fun guardarTodo(container: LinearLayout, dataMap: Map<View, List<Uri>>, quote: Quote, dialog: BottomSheetDialog) {
        val loading = MaterialAlertDialogBuilder(this).setTitle("Procesando").setMessage("Guardando avances...").setCancelable(false).show()

        lifecycleScope.launch {
            try {
                for (i in 0 until container.childCount) {
                    val item = container.getChildAt(i)
                    val idConcept = item.findViewById<TextView>(R.id.tvIdConcept).text.toString()
                    val nombre = item.findViewById<TextView>(R.id.tvNombreAlcance).text.toString()
                    val cant = item.findViewById<EditText>(R.id.etCantidadAvance).text.toString()
                    val comentario = item.findViewById<EditText>(R.id.etComentario).text.toString()
                    val uris = dataMap[item] ?: emptyList()

                    if (cant.isNotEmpty() || uris.isNotEmpty() || comentario.isNotEmpty()) {
                        val logId = db.logEntryDao().insert(LogEntry(
                            quoteId = quote.idQuote,
                            idProject = quote.idProject,
                            idConcept = idConcept,
                            comment = comentario.ifEmpty { "Avance de $nombre" },
                            cantidad = cant,
                            latitude = lastLat,
                            longitude = lastLon
                        )).toInt()

                        val photosToInsert = uris.map { Photos(logEntryId = logId, uri = it.toString(), isUploaded = false) }
                        db.photoDao().insertPhotos(photosToInsert)
                        
                        if (cant.isNotEmpty()) {
                            db.quoteConceptDao().insert(QuoteConcepts(
                                idConcept = idConcept,
                                quoteId = quote.idQuote,
                                idProject = quote.idProject,
                                nameConcept = nombre,
                                cantConcept = cant,
                                idUser = currentUserId,
                                logIdGenerated = logId
                            ))
                        }
                    }
                }

                val repository = QuoteConceptRepository(quote, currentUserId ?: 0, db.quoteDao(), db.quoteConceptDao(), db.logEntryDao(), db.photoDao(), apiService, this@HomeActivity)
                repository.SyncConcepts(db.quoteConceptDao().getUnsyncedConcepts(quote.idQuote, quote.idProject))
                
                Toast.makeText(this@HomeActivity, "✅ Registros guardados", Toast.LENGTH_SHORT).show()
                refreshCurrentFragment()
            } catch (e: Exception) {
                Log.e("SAVE_ERROR", "Error: ${e.message}")
            } finally {
                loading.dismiss()
                updateSyncBadge()
                dialog.dismiss()
            }
        }
    }

    private fun checkAndSyncAll() {
        lifecycleScope.launch {
            val unsyncedLogs = db.logEntryDao().getUnsyncedLogs()
            val total = unsyncedLogs.size + db.quoteConceptDao().getAllUnsyncedConcepts().size
            if (total == 0) {
                Toast.makeText(this@HomeActivity, "Todo sincronizado", Toast.LENGTH_SHORT).show()
            } else {
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("Pendientes")
                    .setMessage("Tienes $total registros por enviar.")
                    .setPositiveButton("Sincronizar") { _, _ -> performSyncAll() }
                    .show()
            }
        }
    }

    private fun performSyncAll() {
        val loading = MaterialAlertDialogBuilder(this).setTitle("Sincronizando").setMessage("Por favor espere...").setCancelable(false).show()
        lifecycleScope.launch {
            try {
                val repository = QuoteConceptRepository(null, currentUserId ?: 0, db.quoteDao(), db.quoteConceptDao(), db.logEntryDao(), db.photoDao(), apiService, this@HomeActivity)
                if (repository.syncAllUnsyncedData()) Toast.makeText(this@HomeActivity, "✅ Sincronización exitosa", Toast.LENGTH_SHORT).show()
                updateSyncBadge()
            } catch (e: Exception) {
                Log.e("SYNC", "Error: ${e.message}")
            } finally {
                loading.dismiss()
            }
        }
    }

    fun updateSyncBadge() {
        lifecycleScope.launch {
            val count = db.logEntryDao().getUnsyncedLogs().size + db.quoteConceptDao().getAllUnsyncedConcepts().size
            binding.tvSyncBadge.apply {
                text = count.toString()
                visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openCamera() {
        val photoFile = File.createTempFile("AVANCE_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        currentPhotoAbsolutePath = photoFile.absolutePath
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile)
        currentPhotoUri = uri
        takePhotoLauncher.launch(uri)
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
}

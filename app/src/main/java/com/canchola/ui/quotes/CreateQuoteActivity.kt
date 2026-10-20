package com.canchola.ui.quotes

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.canchola.R
import com.canchola.data.local.AppDatabase
import com.canchola.data.local.SessionManager
import com.canchola.data.remote.RetrofitClient
import com.canchola.data.repository.QuoteConceptRepository
import com.canchola.databinding.ActivityCreateQuoteBinding
import com.canchola.models.Concept
import com.canchola.models.Customer
import com.canchola.models.Quote
import com.canchola.models.MeasurementUnit
import com.canchola.models.LogEntry
import com.canchola.models.QuoteConcepts
import com.canchola.ui.photo.PhotoAdapter
import com.canchola.ui.photo.Photos
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CreateQuoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateQuoteBinding
    private lateinit var db: AppDatabase
    private lateinit var sessionManager: SessionManager
    private val apiService by lazy { RetrofitClient.getInstance(this) }
    
    private var customersList = listOf<Customer>()
    private var conceptsList = listOf<Concept>()
    private var unitsList = listOf<MeasurementUnit>()
    
    private val addedConcepts = mutableListOf<QuoteConcepts>()
    private val photoUris = mutableListOf<Uri>()
    private lateinit var photoAdapter: PhotoAdapter
    private var currentPhotoUri: Uri? = null
    private var currentPhotoAbsolutePath: String? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    private var editingQuote: Quote? = null
    private var editingLogId: Int? = null

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) {
            currentPhotoAbsolutePath?.let { path ->
                val newUri = Uri.fromFile(File(path))
                photoUris.add(newUri)
                photoAdapter.notifyItemInserted(photoUris.size - 1)
                binding.rvPhotosQuote.visibility = View.VISIBLE
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
        binding = ActivityCreateQuoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        editingQuote = intent.getSerializableExtra("QUOTE_DATA") as? Quote
        if (editingQuote != null && editingQuote!!.unique_id.startsWith("LOCAL_")) {
            editingLogId = editingQuote!!.unique_id.removePrefix("LOCAL_").toIntOrNull()
        }

        setupToolbar()
        setupPhotos()
        loadCatalogs()
        setupListeners()
        checkLocationPermissions()

        if (editingQuote != null) {
            populateFields(editingQuote!!)
        }
    }

    private fun populateFields(quote: Quote) {
        binding.acCustomer.setText(quote.nameCustomer)
        binding.etProjectName.setText(quote.projectName)
        binding.etGeneralComment.setText(quote.atte)
        
        editingLogId?.let { logId ->
            lifecycleScope.launch {
                val concepts = db.quoteConceptDao().getAllLConceptsDirect()
                    .filter { it.logIdGenerated == logId }
                
                addedConcepts.clear()
                binding.containerAddedConcepts.removeAllViews()
                concepts.forEach { 
                    addedConcepts.add(it)
                    addConceptView(it)
                }

                val photos = db.photoDao().getPhotosByLog(logId)
                photoUris.clear()
                photos.forEach { photoUris.add(Uri.parse(it.uri)) }
                photoAdapter.notifyDataSetChanged()
                if (photoUris.isNotEmpty()) binding.rvPhotosQuote.visibility = View.VISIBLE
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarCreateQuote)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarCreateQuote.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        if (editingQuote != null) {
            binding.toolbarCreateQuote.title = "Editar Solicitud"
            binding.btnSaveFullQuote.text = "ACTUALIZAR SOLICITUD"
        }
    }

    private fun setupPhotos() {
        photoAdapter = PhotoAdapter(
            photoList = photoUris,
            onDeleteClick = { position ->
                photoUris.removeAt(position)
                photoAdapter.notifyItemRemoved(position)
                if (photoUris.isEmpty()) binding.rvPhotosQuote.visibility = View.GONE
            }
        )
        binding.rvPhotosQuote.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvPhotosQuote.adapter = photoAdapter
    }

    private fun loadCatalogs() {
        lifecycleScope.launch {
            customersList = db.customerDao().getAllCustomers()
            conceptsList = db.conceptDao().getAllConcepts()
            unitsList = db.unitDao().getAllUnits()

            val customerNames = customersList.map { it.name }
            val conceptNames = conceptsList.map { it.concepto ?: "" }
            val unitNames = unitsList.map { it.unidad }

            binding.acCustomer.setAdapter(ArrayAdapter(this@CreateQuoteActivity, R.layout.item_dropdown, customerNames))
            binding.acConcept.setAdapter(ArrayAdapter(this@CreateQuoteActivity, R.layout.item_dropdown, conceptNames))
            binding.acConceptUnit.setAdapter(ArrayAdapter(this@CreateQuoteActivity, R.layout.item_dropdown, unitNames))
        }
    }

    private fun setupListeners() {
        binding.btnAddConceptToList.setOnClickListener {
            val conceptName = binding.acConcept.text.toString()
            val quantity = binding.etConceptQuantity.text.toString()
            val unit = binding.acConceptUnit.text.toString()

            if (conceptName.isBlank() || quantity.isBlank()) {
                Toast.makeText(this, "Ingresa concepto y cantidad", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fullQuantity = "$quantity $unit".trim()
            val newConcept = QuoteConcepts(
                idConcept = UUID.randomUUID().toString(),
                quoteId = null,
                idProject = null,
                nameConcept = conceptName,
                cantConcept = fullQuantity,
                idUser = sessionManager.getUserId(),
                isSynced = false
            )

            addedConcepts.add(newConcept)
            addConceptView(newConcept)
            
            binding.acConcept.setText("")
            binding.etConceptQuantity.setText("")
            binding.acConceptUnit.setText("")
        }

        binding.btnTakePhotoQuote.setOnClickListener { openCamera() }

        binding.btnSaveFullQuote.setOnClickListener {
            saveQuote()
        }
    }

    private fun addConceptView(concept: QuoteConcepts) {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_added_concept, binding.containerAddedConcepts, false)
        itemView.findViewById<TextView>(R.id.tvConceptName).text = concept.nameConcept
        itemView.findViewById<TextView>(R.id.tvConceptDetails).text = "Cant: ${concept.cantConcept}"
        
        itemView.findViewById<View>(R.id.btnRemoveConcept).setOnClickListener {
            addedConcepts.remove(concept)
            binding.containerAddedConcepts.removeView(itemView)
        }

        binding.containerAddedConcepts.addView(itemView)
    }

    private fun saveQuote() {
        val customerName = binding.acCustomer.text.toString()
        val projectName = binding.etProjectName.text.toString()
        val comment = binding.etGeneralComment.text.toString()

        if (customerName.isBlank()) {
            Toast.makeText(this, "El nombre del cliente es obligatorio", Toast.LENGTH_SHORT).show()
            return
        }

        if (addedConcepts.isEmpty()) {
            Toast.makeText(this, "Agrega al menos un concepto", Toast.LENGTH_SHORT).show()
            return
        }

        val loading = MaterialAlertDialogBuilder(this).setTitle("Guardando").setMessage("Procesando información...").setCancelable(false).show()

        lifecycleScope.launch {
            try {
                // 1. Guardar localmente
                val logEntry = LogEntry(
                    id = editingLogId ?: 0,
                    comment = "Solicitud: $customerName - $projectName\n$comment",
                    cantidad = "0",
                    isSynced = false,
                    latitude = lastLat,
                    longitude = lastLon
                )
                val logId = db.logEntryDao().insert(logEntry).toInt()

                val quoteRecord = Quote(
                    unique_id = "LOCAL_$logId",
                    idQuote = null,
                    idProject = null,
                    string_Fecha = editingQuote?.string_Fecha ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    nameCustomer = customerName,
                    customerName = customerName,
                    projectName = projectName,
                    keywords = "Local • Nueva",
                    is_active_app = 1,
                    atte = comment,
                    conceptos_detalle = addedConcepts.joinToString("|") { "${it.idConcept};${it.nameConcept};${it.cantConcept}" },
                    meta_total = 0.0,
                    avance_total = 0.0,
                    porcentaje_completado = 0,
                    isSynced = false
                )
                db.quoteDao().insertQuotes(listOf(quoteRecord))

                db.photoDao().deletePhotosByLog(logId)
                val photosToInsert = photoUris.map { Photos(logEntryId = logId, uri = it.toString(), isUploaded = false) }
                db.photoDao().insertPhotos(photosToInsert)

                db.quoteConceptDao().deleteByLog(logId)
                val finalConcepts = addedConcepts.map { it.copy(id = 0, idLog = logId, logIdGenerated = logId) }
                finalConcepts.forEach { db.quoteConceptDao().insert(it) }

                // 2. Sincronización Automática
                loading.setMessage("Enviando al servidor...")
                val repository = QuoteConceptRepository(
                    quoteRecord, 
                    sessionManager.getUserId() ?: 0, 
                    db.quoteDao(), // NUEVO PARÁMETRO
                    db.quoteConceptDao(), 
                    db.logEntryDao(), 
                    db.photoDao(), 
                    apiService, 
                    this@CreateQuoteActivity
                )
                
                // Enviamos esta solicitud específica
                val synced = repository.syncAppRequests()
                
                if (synced) {
                    Toast.makeText(this@CreateQuoteActivity, "✅ Solicitud guardada y enviada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@CreateQuoteActivity, "💾 Guardado local (pendiente envío)", Toast.LENGTH_SHORT).show()
                }
                
                finish()
            } catch (e: Exception) {
                Log.e("SAVE_ERROR", "Error: ${e.message}")
                Toast.makeText(this@CreateQuoteActivity, "Error al guardar", Toast.LENGTH_SHORT).show()
            } finally {
                loading.dismiss()
            }
        }
    }

    private fun openCamera() {
        val photoFile = File.createTempFile("QUOTE_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
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

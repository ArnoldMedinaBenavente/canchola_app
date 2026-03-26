package com.canchola.ui.quotes

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
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.canchola.R
import com.canchola.data.local.AppDatabase
import com.canchola.data.local.SessionManager
import com.canchola.data.remote.RetrofitClient
import com.canchola.data.repository.QuoteConceptRepository
import com.canchola.databinding.ActivityQuoteDetailBinding
import com.canchola.models.LogEntry
import com.canchola.models.Quote
import com.canchola.models.QuoteConcepts
import com.canchola.ui.AddLogSheet
import com.canchola.ui.photo.PhotoAdapter
import com.canchola.ui.photo.Photos
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

class QuoteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuoteDetailBinding
    private lateinit var db: AppDatabase
    private lateinit var sessionManager: SessionManager
    private val apiService by lazy { RetrofitClient.getInstance(this) }
    private var quote: Quote? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    private val dataMap = mutableMapOf<View, MutableList<Uri>>()
    
    private var currentEditingUriList: MutableList<Uri>? = null
    private var currentEditingAdapter: PhotoAdapter? = null
    private var currentPhotoUri: Uri? = null
    private var currentPhotoAbsolutePath: String? = null

    // Para dictado por voz en alcances
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

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) {
            currentPhotoAbsolutePath?.let { path ->
                val newUri = Uri.fromFile(java.io.File(path))
                currentEditingUriList?.let { list ->
                    list.add(newUri)
                    currentEditingAdapter?.notifyItemInserted(list.size - 1)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        quote = intent.getSerializableExtra("QUOTE_DATA") as? Quote

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermissions()

        quote?.let {
            mostrarDatos(it)
            setupAlcances(it)
            setupButtons(it)
        }
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

    private fun mostrarDatos(quote: Quote) {
        binding.detailsTvFolio.text = "Folio: #${quote.idQuote}"
        binding.tvClienteDetalle.text = quote.nameCustomer
        binding.tvFechaDetalle.text = quote.atte

        if (!quote.keywords.isNullOrEmpty()) {
            binding.detailsTvKeywords.visibility = View.VISIBLE
            binding.detailsTvKeywords.text = quote.keywords.replace(",", " • ")
        } else {
            binding.detailsTvKeywords.visibility = View.GONE
        }
    }

    private fun setupAlcances(quote: Quote) {
        binding.containerAlcances.removeAllViews()
        val cadenaConceptos = quote.conceptos ?: ""

        if (cadenaConceptos.isNotEmpty()) {
            val lista = cadenaConceptos.split("|")
            lista.forEach { concepto ->
                val conceptoLimpio = concepto.trim()
                if (conceptoLimpio.isNotEmpty()) {
                    val dataConcepto = conceptoLimpio.split(";")
                    val idConcepto = dataConcepto.getOrNull(0) ?: ""
                    val nombreConcepto = dataConcepto.getOrNull(1) ?: ""

                    val itemView = layoutInflater.inflate(R.layout.item_alcance, null)
                    val tvIdConcept = itemView.findViewById<TextView>(R.id.tvIdConcept)
                    val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreAlcance)
                    val etCantidad = itemView.findViewById<EditText>(R.id.etCantidadAvance)
                    val btnAgregarFoto = itemView.findViewById<Button>(R.id.btnAgregarFoto)
                    val btnAgregarComentario = itemView.findViewById<Button>(R.id.btnAgregarComentario)
                    val rvPhotos = itemView.findViewById<RecyclerView>(R.id.rvPhotosHome)
                    val etComentario = itemView.findViewById<EditText>(R.id.etComentario)
                    val btnVoice = itemView.findViewById<ImageButton>(R.id.btnVoiceAlcance)
                    val layoutComentarioVoz = itemView.findViewById<View>(R.id.layoutComentarioVoz)

                    tvIdConcept.text = idConcepto
                    tvNombre.text = nombreConcepto
                    etCantidad.setHint("0.0")

                    val itemUriList = mutableListOf<Uri>()
                    dataMap[itemView] = itemUriList
                    
                    val itemPhotoAdapter = PhotoAdapter(
                        photoList = itemUriList,
                        onPhotoClick = { uri -> mostrarFotoGrande(uri) },
                        onDeleteClick = { position ->
                            itemUriList.removeAt(position)
                            (rvPhotos.adapter as? PhotoAdapter)?.notifyItemRemoved(position)
                        }
                    )

                    rvPhotos.visibility = View.VISIBLE
                    rvPhotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                    rvPhotos.adapter = itemPhotoAdapter

                    btnAgregarComentario.setOnClickListener {
                        layoutComentarioVoz.visibility = if(layoutComentarioVoz.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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

                    binding.containerAlcances.addView(itemView)
                }
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

    private fun setupButtons(quote: Quote) {
        binding.btnTomarFoto.setOnClickListener {
            val sheet = AddLogSheet(quoteId = quote.idQuote, "photo")
            sheet.show(supportFragmentManager, "AddLog")
        }

        binding.btnRegistrarComentario.setOnClickListener {
            val sheet = AddLogSheet(quoteId = quote.idQuote, "comment")
            sheet.show(supportFragmentManager, "AddLog")
        }

        binding.btnGuardarAlcances.setOnClickListener {
            guardarAvances(quote)
        }
        
        binding.btnVerFotos.setOnClickListener {
            verFotos(quote)
        }
        
        binding.btnVerComentarios.setOnClickListener {
            verNotas(quote)
        }
    }

    private fun verFotos(quote: Quote) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_ver_fotos, null)
        val rv = view.findViewById<RecyclerView>(R.id.rvVerFotos)
        
        lifecycleScope.launch {
            val photos = db.photoDao().getPhotosByQuote(quote.idQuote)
            val uris = photos.map { Uri.parse(it.uri) }.toMutableList()
            
            if (uris.isEmpty()) {
                Toast.makeText(this@QuoteDetailActivity, "No hay fotos registradas", Toast.LENGTH_SHORT).show()
                return@launch
            }

            rv.layoutManager = LinearLayoutManager(this@QuoteDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            rv.adapter = PhotoAdapter(
                photoList = uris,
                isReadOnly = true,
                onPhotoClick = { uri -> mostrarFotoGrande(uri) }
            )
            dialog.setContentView(view)
            dialog.show()
        }
    }

    private fun mostrarFotoGrande(uri: Uri) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(this)
        imageView.layoutParams = ViewGroup.LayoutParams(-1, -1)
        com.bumptech.glide.Glide.with(this).load(uri).into(imageView)
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }

    private fun verNotas(quote: Quote) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_ver_notas, null)
        val rv = view.findViewById<RecyclerView>(R.id.rvVerNotas)
        
        lifecycleScope.launch {
            val logs = db.logEntryDao().getLogsByQuote(quote.idQuote).first()
            if (logs.isEmpty()) {
                Toast.makeText(this@QuoteDetailActivity, "No hay notas registradas", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            rv.layoutManager = LinearLayoutManager(this@QuoteDetailActivity)
            rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val tv = TextView(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(48, 24, 48, 24)
                        textSize = 16f
                        setTextColor(android.graphics.Color.BLACK)
                        setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                    }
                    return object : RecyclerView.ViewHolder(tv) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val log = logs[position]
                    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(log.createdAt))
                    (holder.itemView as TextView).text = "${dateStr}\n${log.comment}"
                }
                override fun getItemCount() = logs.size
            }
            dialog.setContentView(view)
            dialog.show()
        }
    }

    private fun guardarAvances(quote: Quote) {
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Procesando")
            .setMessage("Enviando información, por favor espere...")
            .setCancelable(false)
            .show()
        
        binding.btnGuardarAlcances.isEnabled = false

        lifecycleScope.launch {
            try {
                val container = binding.containerAlcances
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
                            quoteId = quote.idQuote,
                            nameConcept = nombre,
                            cantConcept = cant,
                            comment = if(comentario.isNotEmpty()) comentario else null,
                            idLog = logIdGenerated,
                            isSynced = false,
                            idUser = sessionManager.getUserId(),
                            logIdGenerated = logIdGenerated,
                            latitude = lastLat,
                            longitude = lastLon
                        )
                        db.quoteConceptDao().insert(newConcept)
                    }
                }
                
                val repository = QuoteConceptRepository(
                    quote, sessionManager.getUserId(), db.quoteConceptDao(), db.logEntryDao(), db.photoDao(), apiService, this@QuoteDetailActivity
                )
                val quoteConceptsList = db.quoteConceptDao().getUnsyncedConcepts(quote.idQuote)
                val isSynced = repository.SyncConcepts(quoteConceptsList)

                if (isSynced) {
                    Toast.makeText(this@QuoteDetailActivity, "✅ Enviado exitosamente", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@QuoteDetailActivity, "💾 Guardado localmente", Toast.LENGTH_LONG).show()
                }
                finish()
            } catch (e: Exception) {
                Log.e("SAVE_ERROR", "Error: ${e.message}")
                Toast.makeText(this@QuoteDetailActivity, "❌ Error al procesar datos", Toast.LENGTH_SHORT).show()
            } finally {
                loadingDialog.dismiss()
                binding.btnGuardarAlcances.isEnabled = true
            }
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
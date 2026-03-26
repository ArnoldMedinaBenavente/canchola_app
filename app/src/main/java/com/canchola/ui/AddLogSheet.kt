package com.canchola.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.canchola.R
import com.canchola.data.local.AppDatabase
import com.canchola.data.remote.RetrofitClient
import com.canchola.data.repository.LogRepository
import com.canchola.models.LogEntry
import com.canchola.ui.photo.PhotoAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddLogSheet(val quoteId: Int? = null, val type: String?) : BottomSheetDialogFragment() {

    private val viewModel: LogViewModel by activityViewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val apiService = RetrofitClient.getInstance(requireContext())
        val repository = LogRepository(database.logEntryDao(), database.photoDao(), apiService, requireContext())
        LogViewModel.Factory(requireActivity().application, repository)
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    private val photoPaths = mutableListOf<String>()
    private lateinit var photoAdapter: PhotoAdapter
    private var currentPhotoUri: Uri? = null
    private var currentPhotoAbsolutePath: String? = null
    private val uriList = mutableListOf<Uri>()

    // Launcher para dictado por voz
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let {
                val etComment = view?.findViewById<EditText>(R.id.etComment)
                val currentText = etComment?.text.toString()
                if (currentText.isEmpty()) {
                    etComment?.setText(it)
                } else {
                    etComment?.setText("$currentText $it")
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
                photoPaths.add(path)
                val newUri = Uri.fromFile(java.io.File(path))
                uriList.add(newUri)
                photoAdapter.notifyItemInserted(uriList.size - 1)
                view?.findViewById<RecyclerView>(R.id.rvPhotos)?.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        checkLocationPermissions()
    }

    private fun checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.layout_add_log_entry, container, false)
        val etComment = view.findViewById<EditText>(R.id.etComment)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val btnPhoto = view.findViewById<Button>(R.id.btnTakePhoto)
        val rvPhotos = view.findViewById<RecyclerView>(R.id.rvPhotos)
        val btnVoice = view.findViewById<ImageButton>(R.id.btnVoiceComment)

        photoAdapter = PhotoAdapter(
            photoList = uriList,
            onPhotoClick = { uri -> mostrarFotoGrande(uri) },
            onDeleteClick = { position ->
                photoPaths.removeAt(position)
                uriList.removeAt(position)
                photoAdapter.notifyItemRemoved(position)
            }
        )

        rvPhotos.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvPhotos.adapter = photoAdapter

        btnPhoto.setOnClickListener { openCamera() }
        btnVoice.setOnClickListener { startVoiceRecognition() }

        if (type == "photo") openCamera()

        btnSave.setOnClickListener {
            val comment = etComment.text.toString()
            if (comment.isNotBlank() || photoPaths.isNotEmpty()) {
                val newLog = LogEntry(
                    quoteId = quoteId,
                    comment = comment,
                    cantidad = "Solo comentario",
                    isSynced = false,
                    latitude = lastLat,
                    longitude = lastLon
                )
                viewModel.saveLog(newLog, photoPaths)
                dismiss()
            } else {
                Toast.makeText(context, "Escribe un comentario o toma una foto", Toast.LENGTH_SHORT).show()
            }
        }
        return view
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
            Toast.makeText(requireContext(), "Tu dispositivo no soporta dictado por voz", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarFotoGrande(uri: Uri) {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(requireContext())
        imageView.layoutParams = ViewGroup.LayoutParams(-1, -1)
        com.bumptech.glide.Glide.with(this).load(uri).into(imageView)
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }

    private fun openCamera() {
        val photoFile = createImageFile()
        currentPhotoAbsolutePath = photoFile.absolutePath
        currentPhotoUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", photoFile)
        takePhotoLauncher.launch(currentPhotoUri)
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("CANCHOLA_${timeStamp}_", ".jpg", storageDir)
    }
}
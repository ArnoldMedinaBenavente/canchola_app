package com.canchola.ui

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.canchola.R
import com.canchola.data.local.AppDatabase
import com.canchola.data.remote.RetrofitClient
import com.canchola.data.repository.LogRepository
import com.canchola.models.LogEntry
import com.canchola.data.local.db.LogEntryDao
import com.canchola.ui.photo.PhotoAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddLogSheet(val quoteId: Int? = null) : BottomSheetDialogFragment() {

    // 1. DECLARACIÓN CORRECTA (A nivel de clase y con Factory)
    private val viewModel: LogViewModel by activityViewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val apiService = RetrofitClient.getInstance(requireContext())
        val repository = LogRepository(database.logEntryDao(), database.photoDao(),apiService, requireContext())

        LogViewModel.Factory(requireActivity().application ,repository) // Asegúrate de tener la clase Factory en tu LogViewModel
    }
    // 1. NUEVO: Lista para guardar múltiples rutas
    private val photoPaths = mutableListOf<String>()
    private lateinit var photoAdapter: PhotoAdapter

    // Variable temporal para la foto que se está tomando en este momento
    private var currentPhotoUri: Uri? = null
    private var currentPhotoAbsolutePath: String? = null
    private val uriList = mutableListOf<Uri>()

    // 2. NUEVO: Contrato para abrir la cámara y recibir el resultado
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) {

            currentPhotoAbsolutePath?.let { path ->
                photoPaths.add(path)
                val newUri = Uri.fromFile(java.io.File(path))
                uriList.add(newUri) // Agregamos a la lista que YA tiene el adapter
                photoAdapter.notifyItemInserted(uriList.size - 1)
                // Hacemos visible el RecyclerView si es la primera foto
                view?.findViewById<RecyclerView>(R.id.rvPhotos)?.visibility = View.VISIBLE
            }
        }
    }




    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.layout_add_log_entry, container, false)
        val etComment = view.findViewById<EditText>(R.id.etComment)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val btnPhoto = view.findViewById<Button>(R.id.btnTakePhoto)
        val rvPhotos = view.findViewById<RecyclerView>(R.id.rvPhotos)

        // 3. CONFIGURACIÓN DEL RECYCLERVIEW
        photoAdapter = PhotoAdapter(uriList) { position ->
            photoPaths.removeAt(position)
            uriList.removeAt(position) // Quitamos de la lista de Uris
            photoAdapter.notifyItemRemoved(position)
        }

        rvPhotos.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvPhotos.adapter = photoAdapter

        // Acción de Tomar Foto
        btnPhoto.setOnClickListener {
            openCamera()
        }

        // Acción de Guardar
        btnSave.setOnClickListener {
            val comment = etComment.text.toString()
            if (comment.isNotBlank() || photoPaths.isNotEmpty()) {
                val newLog = LogEntry(
                    quoteId = quoteId,
                    comment = comment,
                //    photoUri = if (photoPaths.isNotEmpty()) photoPaths[0] else null, // Temporal: solo guarda la primera
                    isSynced = false
                )

                // Aquí llamamos al Repository que creamos antes
                // (Usando el ViewModel de la actividad que lo llama)
                viewModel.saveLog(newLog,photoPaths)
                dismiss()
            }else {
                Toast.makeText(context, "Escribe un comentario o toma una foto", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }

    private fun openCamera() {
        val photoFile = createImageFile()

        // Guardamos la ruta absoluta para agregarla a la lista después
        currentPhotoAbsolutePath = photoFile.absolutePath

        // Creamos la URI usando FileProvider
        currentPhotoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider", // Asegúrate que esto coincida con tu AndroidManifest
            photoFile
        )

        takePhotoLauncher.launch(currentPhotoUri)
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("CANCHOLA_${timeStamp}_", ".jpg", storageDir)
    }
}
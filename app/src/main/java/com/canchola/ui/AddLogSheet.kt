package com.canchola.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.activityViewModels
import com.canchola.R
import com.canchola.data.local.AppDatabase
import com.canchola.data.remote.RetrofitClient
import com.canchola.data.repository.LogRepository
import com.canchola.models.LogEntry
import com.canchola.data.local.db.LogEntryDao
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddLogSheet(val quoteId: Int? = null) : BottomSheetDialogFragment() {

    // 1. DECLARACIÓN CORRECTA (A nivel de clase y con Factory)
    private val viewModel: LogViewModel by activityViewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val apiService = RetrofitClient.getInstance(requireContext())
        val repository = LogRepository(database.logEntryDao(), apiService, requireContext())

        LogViewModel.Factory(repository) // Asegúrate de tener la clase Factory en tu LogViewModel
    }
    private var currentPhotoPath: String? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.layout_add_log_entry, container, false)
        val etComment = view.findViewById<EditText>(R.id.etComment)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val btnPhoto = view.findViewById<Button>(R.id.btnTakePhoto)

        // Acción de Tomar Foto
        btnPhoto.setOnClickListener {
            openCamera()
        }

        // Acción de Guardar
        btnSave.setOnClickListener {
            val comment = etComment.text.toString()
            if (comment.isNotBlank()) {
                val newLog = LogEntry(
                    quoteId = quoteId,
                    comment = comment,
                    photoUri = currentPhotoPath,
                    isSynced = false
                )

                // Aquí llamamos al Repository que creamos antes
                // (Usando el ViewModel de la actividad que lo llama)
                viewModel.saveLog(newLog)
                dismiss()
            }
        }
        return view
    }

    private fun openCamera() {
        // Aquí usas el Intent de la cámara que ya conoces
        // Y guardas la ruta en currentPhotoPath
    }
}
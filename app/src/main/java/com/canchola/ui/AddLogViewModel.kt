package com.canchola.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.canchola.data.repository.LogRepository
import com.canchola.models.LogEntry
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogViewModel(application: Application,
                   private val repository: LogRepository)
                    : AndroidViewModel(application) {

    fun saveLog(logEntry: LogEntry,photoPaths: List<String>) {
        // Usamos viewModelScope para que la tarea viva aunque se cierre el BottomSheet
        viewModelScope.launch(Dispatchers.IO) {
           val isSynced= repository.saveAndSyncLog(logEntry, photoPaths = photoPaths)
            withContext(Dispatchers.Main) {
                if (isSynced) {
                    Toast.makeText(getApplication(), "✅ Registrado con exito", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(getApplication(), "💾 Registrado con exito", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    class Factory(
        private val application: Application, // 1. Recibe application
        private val repository: LogRepository  // 2. Recibe repository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                // 3. Pasa ambos al constructor
                return LogViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
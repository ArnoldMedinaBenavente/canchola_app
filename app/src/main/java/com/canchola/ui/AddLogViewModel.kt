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

    fun saveLog(logEntry: LogEntry, photoPaths: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
           val isSynced = repository.saveAndSyncLog(logEntry, photoPaths = photoPaths)
            withContext(Dispatchers.Main) {
                if (isSynced) {
                    Toast.makeText(getApplication(), "✅ Registrado con éxito", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(getApplication(), "💾 Registrado localmente (Sin internet)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun syncPendingLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.syncAllUnsyncedLogs()
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(getApplication(), "✅ Sincronización completa", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(getApplication(), "❌ Error o sin conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    class Factory(
        private val application: Application,
        private val repository: LogRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LogViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
package com.canchola.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.canchola.data.repository.LogRepository
import com.canchola.models.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LogViewModel(private val repository: LogRepository) : ViewModel() {

    fun saveLog(logEntry: LogEntry) {
        // Usamos viewModelScope para que la tarea viva aunque se cierre el BottomSheet
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveAndSyncLog(logEntry)
        }
    }
    // AGREGA ESTE BLOQUE AQUÍ:
    class Factory(private val repository: LogRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LogViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
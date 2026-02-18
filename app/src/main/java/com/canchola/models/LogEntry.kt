package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val quoteId: Int? = null,       // El ID de la cotización (Laravel)
    val comment: String,
   // val photoUri: String?,      // Ruta de la foto en el móvil
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false, // false = pendiente de enviar a Laravel
    val needsNetworkWarning: Boolean = true
)
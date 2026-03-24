package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val quoteId: Int? = null,
    val idConcept: String? = null,
    val comment: String? = "Sin comentarios",
    val cantidad: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val needsNetworkWarning: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null
)
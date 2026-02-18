package com.canchola.ui.photo

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.canchola.models.LogEntry

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = LogEntry::class,
            parentColumns = ["id"],
            childColumns = ["logEntryId"],
            onDelete = ForeignKey.CASCADE // Si borras el reporte, se borran sus fotos
        )
    ],
    indices = [Index(value = ["logEntryId"])] // Para que las busquedas sean rápidas
)
data class Photos(
    @PrimaryKey(autoGenerate = true) val photoId: Int = 0,
    val logEntryId: Int, // ID del padre (LogEntry)
    val uri: String,      // La ruta de la foto
    val isUploaded: Boolean = false // Opcional: Para controlar subida por foto individual
)
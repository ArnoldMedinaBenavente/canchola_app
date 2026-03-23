package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "quoteConcepts")
data class QuoteConcepts (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val idConcept: String,
    val quoteId: Int?,
    val nameConcept: String?,
    val cantConcept: String?,
    val comment: String? = null,    // Agregamos el comentario directamente aquí
    val idLog: Int? = null,         // Y opcionalmente el ID de la bitácora vinculada
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val idUser: Int?,
    val logIdGenerated: Int? = null
): Serializable

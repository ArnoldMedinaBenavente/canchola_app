package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
@Entity(tableName = "quoteConcepts")
data class QuoteConcepts (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val idConcept: String,
    val quoteId: Int?,
    val nameConcept: String?, // <-- Agrégale el ? porque viene null
    val cantConcept: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val idUser: Int?,
):Serializable




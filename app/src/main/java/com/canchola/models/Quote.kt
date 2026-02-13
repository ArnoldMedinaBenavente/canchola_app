package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey
    val idQuote: Int,
    val idProject: Int?, // <-- Agrégale el ? porque viene null
    val string_Fecha: String?,
    val nameCustomer: String?,
    val keywords: String?,
    val input_total: String?, // Viene como texto en el JSON
    val status: String?,
    val is_active_app: Int?,
    val conceptos: String?,
    val atte: String?
): Serializable



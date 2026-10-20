package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "quotes_concept")
data class Concept(
    @PrimaryKey val id: Int,
    val concepto: String?,
    val procedimiento: String?
) : Serializable

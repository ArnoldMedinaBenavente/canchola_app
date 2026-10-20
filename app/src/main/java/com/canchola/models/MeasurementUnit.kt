package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "unidades")
data class MeasurementUnit(
    @PrimaryKey val id: Int,
    val unidad: String
) : Serializable

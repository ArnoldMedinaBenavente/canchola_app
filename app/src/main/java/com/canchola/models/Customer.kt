package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val idCustomer: Int,
    val name: String,
    val created_at: String?,
    val razonSocial: String?,
    val ubicacion: String?,
    val contacto: String?
) : Serializable

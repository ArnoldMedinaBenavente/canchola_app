package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey
    var unique_id: String = "", // Llave primaria generada (ej. "Q123" o "P45")
    val idQuote: Int?,
    val idProject: Int?,
    val string_Fecha: String?,
    val nameCustomer: String?,    // Cliente de la cotización
    val customerName: String?,    // Cliente del proyecto
    val projectName: String?,     // Nombre del proyecto
    val keywords: String?,
    val is_active_app: Int?,
    val atte: String?,

    // Nuevos campos del dashboard
    val conceptos_detalle: String?,
    val meta_total: Double?,
    val avance_total: Double?,
    val porcentaje_completado: Int?
): Serializable {

    // Función auxiliar para obtener el cliente sin importar de dónde venga
    fun getFinalCustomer(): String {
        return nameCustomer ?: customerName ?: "Sin cliente"
    }

    fun isQuote(): Boolean = idQuote != null

    fun getDisplayTitle(): String {
        return if (isQuote()) "Cotización #$idQuote" else "Proyecto: ${projectName ?: "S/N"}"
    }
}

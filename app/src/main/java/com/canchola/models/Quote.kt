package com.canchola.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey
    var unique_id: String = "", // Llave primaria generada (ej. "Q123" o "P45" o "LOCAL_1")
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
    val porcentaje_completado: Int?,
    
    val isSynced: Boolean = false // Para controlar envíos duplicados de solicitudes
): Serializable {

    fun getFinalCustomer(): String {
        return nameCustomer ?: customerName ?: "Sin cliente"
    }

    fun isQuote(): Boolean = idQuote != null
    fun isProject(): Boolean = idProject != null
    fun isLocal(): Boolean = idQuote == null && idProject == null

    fun getDisplayTitle(): String {
        return when {
            isQuote() -> "Cotización #$idQuote"
            isProject() -> "Proyecto: ${projectName ?: "S/N"}"
            else -> "Solicitud: ${projectName ?: "S/N"}"
        }
    }
}

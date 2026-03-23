package com.canchola.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateHelper {
    // Formato estándar de Laravel: YYYY-MM-DD HH:MM:SS
    private const val DATABASE_FORMAT = "yyyy-MM-dd HH:mm:ss"

    fun getCurrentDateForServer(): String {
        val sdf = SimpleDateFormat(DATABASE_FORMAT, Locale.getDefault())
        return sdf.format(Date())
    }

    fun formatLongToDate(time: Long): String {
        val sdf = SimpleDateFormat(DATABASE_FORMAT, Locale.getDefault())
        return sdf.format(Date(time))
    }

    // Opcional: Para mostrar en la UI de forma más amigable
    fun formatToFriendlyDate(date: Date): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(date)
    }
}
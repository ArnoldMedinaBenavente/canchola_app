package com.canchola.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.canchola.data.local.db.LogEntryDao
import com.canchola.data.remote.ApiService
import com.canchola.models.LogEntry
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

class LogRepository(
    private val logEntryDao: LogEntryDao,
    private val apiService: ApiService,
    private val context: Context
) {
    // Flow para observar todos los logs desde la UI
    val allLogs: Flow<List<LogEntry>> = logEntryDao.getAllLogs()

    suspend fun saveAndSyncLog(log: LogEntry) {
        // 1. SIEMPRE guardar en Room primero
        val localId = logEntryDao.insert(log).toInt()

        // 2. Verificar si hay internet
        if (isNetworkAvailable(context)) {
            try {
                // 3. Intentar enviar a Laravel
                val response = uploadToLaravel(log)

                if (response.isSuccessful) {
                    // 4. Si Laravel responde OK, marcamos como sincronizado
                    logEntryDao.updateSyncStatus(localId)
                } else {
                    // Si el servidor falla (ej. error 500), disparar notificación
                    showOfflineNotification()
                }
            } catch (e: Exception) {
                // Si falla la conexión durante la subida
                showOfflineNotification()
            }
        } else {
            // 5. Si no hay internet desde el inicio
            showOfflineNotification()
        }
    }

    private suspend fun uploadToLaravel(log: LogEntry): Response<ApiService.GenericResponse> {
        // Aquí conviertes el LogEntry a los parámetros de MultipartBody
        // (Preparamos el comentario, el ID de cotización y la imagen)
        val commentPart = log.comment.toRequestBody("text/plain".toMediaTypeOrNull())
        val quoteIdPart = log.quoteId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

        // La foto se convierte de la ruta local a un archivo para Retrofit
        val photoPart = log.photoUri?.let { uri ->
            val file = File(uri)
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("photo", file.name, requestFile)
        }

        return apiService.uploadLogEntry(commentPart, quoteIdPart, photoPart)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showOfflineNotification() {
        // Aquí va tu lógica para disparar la notificación de "Información pendiente de enviar"
        // Puedes usar un NotificationManager básico
    }
}
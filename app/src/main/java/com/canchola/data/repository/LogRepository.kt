package com.canchola.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.canchola.data.local.db.LogEntryDao
import com.canchola.data.local.db.PhotoDao
import com.canchola.data.remote.ApiService
import com.canchola.models.LogEntry
import com.canchola.ui.photo.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class LogRepository(
    private val logEntryDao: LogEntryDao,
    private val photoDao: PhotoDao,
    private val apiService: ApiService,
    private val context: Context
) {
    // Flow para observar todos los logs desde la UI
    val allLogs: Flow<List<LogEntry>> = logEntryDao.getAllLogs()

    suspend fun saveAndSyncLog(log: LogEntry, photoPaths: List<String>): Boolean {
        return withContext(Dispatchers.IO) {
            val localId = logEntryDao.insert(log).toInt()
            // ... (resto de tu lógica de guardado de fotos)

            if (isNetworkAvailable(context)) {
                try {
                    val response = uploadToLaravel(log, photoPaths)
                    if (response.isSuccessful) {
                        logEntryDao.updateSyncStatus(localId)
                        return@withContext true // Éxito total
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            showOfflineNotification()
            return@withContext false // Solo se guardó localmente
        }
    }

    private suspend fun uploadToLaravel(log: LogEntry, paths: List<String>): Response<ApiService.GenericResponse> {

        val photoParts = paths.map { path ->
            val file = File(path)

            // OPCIONAL: Comprimir antes de enviar
            // val fileToUpload = compressImage(file)
            val fileToUpload = compressImage(file)

            // CORRECCIÓN: Usar .asRequestBody() que es la forma moderna en Kotlin
            val requestFile = fileToUpload.asRequestBody("image/jpeg".toMediaTypeOrNull())

            // El nombre "photos[]" es clave para que Laravel lo reciba como array
            MultipartBody.Part.createFormData("photos[]", fileToUpload.name, requestFile)
        }

        val commentPart = (log.comment ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
        val quoteIdPart = log.quoteId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val createdAtPart = log.createdAt.toString().toRequestBody("text/plain".toMediaTypeOrNull())

        // ERROR CORREGIDO: Te faltaba pasar el parámetro 'createdAtPart' en la llamada
        return apiService.uploadLogEntry(commentPart, quoteIdPart, photoParts, createdAtPart)
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

    // Función rápida para comprimir antes de crear el RequestBody
    private fun compressImage(file: File): File {
        val bitmap = decodeFile(file.path)
        val compressedFile = File(context.cacheDir, "comp_" + file.name)
        val out = FileOutputStream(compressedFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out) // 70% de calidad es suficiente
        out.flush()
        out.close()
        return compressedFile
    }
}
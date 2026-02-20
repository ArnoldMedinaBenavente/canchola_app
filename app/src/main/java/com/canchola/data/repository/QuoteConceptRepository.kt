package com.canchola.data.repository



import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.canchola.data.local.db.QuoteConceptDao
import com.canchola.data.remote.ApiService
import com.canchola.models.LogEntry
import com.canchola.models.QuoteConcepts
import com.canchola.ui.photo.Photos
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class QuoteConceptRepository(
  private val quoteConceptDao: QuoteConceptDao,
    private val apiService: ApiService,
    private val context: Context
) {
    // Flow para observar todos los logs desde la UI
    val allLogs: Flow<List<QuoteConcepts>> = quoteConceptDao.getAllLConcepts()

    suspend fun SyncConcepts( photoPaths: List<QuoteConcepts>): Boolean {
        return withContext(Dispatchers.IO) {


            if (isNetworkAvailable(context)) {
                try {
                    val response = uploadToLaravel(log, photoPaths)
                    if (response.isSuccessful) {
                        logEntryDao.updateSyncStatus(localId)
                        photoDao.markPhotosAsUploaded(localId)

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

    private suspend fun uploadToLaravel(quoteId: Int?, // Suponiendo que son Int, ajústalos a tus tipos reales
                                        userId: Int?,
                                        quoteConcepts: List<QuoteConcepts>,
                                        createdAt: String?
    ): Response<ApiService.GenericResponse> {

        // 1. Convertimos la lista completa a un String en formato JSON
        val gson = Gson()
        val conceptsJsonString = gson.toJson(quoteConcepts)

        // 2. Empaquetamos el JSON indicando el tipo correcto (application/json)
        val conceptsPart = conceptsJsonString.toRequestBody("application/json".toMediaTypeOrNull())

        // 3. Empaquetamos los demás parámetros (manejando los posibles nulos de forma segura)
        val quoteIdPart = quoteId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val userIdPart = userId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val createdAtPart = createdAt?.toRequestBody("text/plain".toMediaTypeOrNull())

        // 4. Llamamos a Retrofit
        return apiService.uploadQuoteConcept(
            quoteId = quoteIdPart,
            conecepts = conceptsPart,
            userId = userIdPart,
            created_at_app = createdAtPart
        )

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
package com.canchola.data.repository



import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.canchola.data.local.db.QuoteConceptDao
import com.canchola.data.remote.ApiService
import com.canchola.models.DateHelper
import com.canchola.models.Quote
import com.canchola.models.QuoteConcepts
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class QuoteConceptRepository(
    private val quote: Quote?,
    private val userId:Int,
    private val quoteConceptDao: QuoteConceptDao,
    private val apiService: ApiService,
    private val context: Context
) {
    // Flow para observar todos los logs desde la UI
    val allLogs: Flow<List<QuoteConcepts>> = quoteConceptDao.getAllLConcepts()

    suspend fun SyncConcepts( quoteConcepts: List<QuoteConcepts>): Boolean {
        return withContext(Dispatchers.IO) {
            val currentQuoteId = quote?.idQuote ?: 0

            if (isNetworkAvailable(context)) {
                try {
                    val response = uploadToLaravel(currentQuoteId, userId,quoteConcepts, DateHelper.getCurrentDateForServer() )
                    if (response.isSuccessful) {

                        quoteConceptDao.updateSyncStatus(currentQuoteId)

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
            concepts = conceptsPart,
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

}
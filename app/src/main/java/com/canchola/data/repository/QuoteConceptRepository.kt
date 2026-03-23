package com.canchola.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.canchola.data.local.db.LogEntryDao
import com.canchola.data.local.db.PhotoDao
import com.canchola.data.local.db.QuoteConceptDao
import com.canchola.data.remote.ApiService
import com.canchola.models.DateHelper
import com.canchola.models.LogEntry
import com.canchola.models.Quote
import com.canchola.models.QuoteConcepts
import com.canchola.ui.photo.Photos
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

class QuoteConceptRepository(
    private val quote: Quote?,
    private val userId: Int,
    private val quoteConceptDao: QuoteConceptDao,
    private val logEntryDao: LogEntryDao,
    private val photoDao: PhotoDao,
    private val apiService: ApiService,
    private val context: Context
) {
    // Flow para observar todos los logs desde la UI
    val allLogs: Flow<List<LogEntry>> = logEntryDao.getAllLogs()

    suspend fun SyncConcepts(quoteConcepts: List<QuoteConcepts>): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isNetworkAvailable(context)) {
                return@withContext false
            }

            try {
                var allSuccess = true
                val currentQuoteId = quote?.idQuote ?: 0

                // 1. Sincronizar Conceptos de esta cotización
                if (quoteConcepts.isNotEmpty()) {
                    val response = uploadToLaravel(currentQuoteId, userId, quoteConcepts, DateHelper.getCurrentDateForServer())
                    if (response.isSuccessful && response.body()?.status == "success") {
                        quoteConceptDao.updateSyncStatus(currentQuoteId)
                    } else {
                        allSuccess = false
                    }
                }

                // 2. Sincronizar Logs pendientes (Bitácoras) de esta cotización
                val unsyncedLogs = logEntryDao.getUnsyncedLogs().filter { it.quoteId == currentQuoteId }
                for (log in unsyncedLogs) {
                    val photos = photoDao.getUnsyncedPhotosByLog(log.id)
                    val logResponse = uploadLogWithPhotos(log, photos)

                    if (logResponse.isSuccessful && logResponse.body()?.status == "success") {
                        logEntryDao.updateSyncStatus(log.id)
                        photoDao.markPhotosAsUploaded(log.id)
                        // También actualizar el concepto asociado si existe
                        quoteConceptDao.updateSyncStatusByLog(log.id)
                    } else {
                        allSuccess = false
                    }
                }

                return@withContext allSuccess
            } catch (e: Exception) {
                Log.e("SYNC_ERROR", "Error en SyncConcepts: ${e.message}")
                return@withContext false
            }
        }
    }

    suspend fun syncAllUnsyncedData(): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isNetworkAvailable(context)) return@withContext false

            try {
                var allSuccess = true

                // 1. Sincronizar TODOS los Conceptos pendientes
                val allUnsyncedConcepts = quoteConceptDao.getAllUnsyncedConcepts()
                if (allUnsyncedConcepts.isNotEmpty()) {
                    val groupedByQuote = allUnsyncedConcepts.groupBy { it.quoteId }
                    for ((qId, concepts) in groupedByQuote) {
                        if (qId == null) continue
                        val response = uploadToLaravel(qId, userId, concepts, DateHelper.getCurrentDateForServer())
                        if (response.isSuccessful && response.body()?.status == "success") {
                            quoteConceptDao.updateSyncStatus(qId)
                        } else {
                            allSuccess = false
                        }
                    }
                }

                // 2. Sincronizar TODOS los Logs pendientes
                val allUnsyncedLogs = logEntryDao.getUnsyncedLogs()
                for (log in allUnsyncedLogs) {
                    val photos = photoDao.getUnsyncedPhotosByLog(log.id)
                    val logResponse = uploadLogWithPhotos(log, photos)

                    if (logResponse.isSuccessful && logResponse.body()?.status == "success") {
                        logEntryDao.updateSyncStatus(log.id)
                        photoDao.markPhotosAsUploaded(log.id)
                        quoteConceptDao.updateSyncStatusByLog(log.id)
                    } else {
                        allSuccess = false
                    }
                }

                return@withContext allSuccess
            } catch (e: Exception) {
                Log.e("SYNC_ALL_ERROR", "Error en syncAllUnsyncedData: ${e.message}")
                return@withContext false
            }
        }
    }

    private suspend fun uploadToLaravel(
        quoteId: Int?,
        userId: Int?,
        quoteConcepts: List<QuoteConcepts>,
        createdAt: String?
    ): Response<ApiService.GenericResponse> {
        val gson = Gson()
        val conceptsJsonString = gson.toJson(quoteConcepts)
        val conceptsPart = conceptsJsonString.toRequestBody("application/json".toMediaTypeOrNull())
        val quoteIdPart = quoteId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val userIdPart = userId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val createdAtPart = createdAt?.toRequestBody("text/plain".toMediaTypeOrNull())

        return apiService.uploadQuoteConcept(
            quoteId = quoteIdPart,
            concepts = conceptsPart,
            userId = userIdPart,
            created_at_app = createdAtPart
        )
    }

    private suspend fun uploadLogWithPhotos(
        log: LogEntry,
        photos: List<Photos>
    ): Response<ApiService.GenericResponse> {
        val commentPart = (log.comment ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
        val quoteIdPart = log.quoteId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val idConceptPart = log.idConcept?.toRequestBody("text/plain".toMediaTypeOrNull())
        val createdAtPart = DateHelper.formatLongToDate(log.createdAt).toRequestBody("text/plain".toMediaTypeOrNull())
        val amountPart = log.cantidad?.toRequestBody("text/plain".toMediaTypeOrNull())

        val photoParts = photos.mapNotNull { photo ->
            try {
                val uri = Uri.parse(photo.uri)
                val path = uri.path ?: return@mapNotNull null
                val file = File(path)
                if (file.exists()) {
                    val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("photos[]", file.name, requestFile)
                } else null
            } catch (e: Exception) {
                null
            }
        }

        return apiService.uploadLogEntry(
            comment = commentPart,
            quoteId = quoteIdPart,
            idConcept = idConceptPart,
            amount = amountPart,
            photos = photoParts,
            created_at_app = createdAtPart
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
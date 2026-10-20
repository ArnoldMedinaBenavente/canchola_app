package com.canchola.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.canchola.data.local.db.LogEntryDao
import com.canchola.data.local.db.PhotoDao
import com.canchola.data.local.db.QuoteConceptDao
import com.canchola.data.local.quotes.QuoteDao
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
    private val quoteDao: QuoteDao,
    private val quoteConceptDao: QuoteConceptDao,
    private val logEntryDao: LogEntryDao,
    private val photoDao: PhotoDao,
    private val apiService: ApiService,
    private val context: Context
) {
    val allLogs: Flow<List<LogEntry>> = logEntryDao.getAllLogs()

    /**
     * Sincroniza SOLICITUDES de cotización (registros creados en la App)
     */
    suspend fun syncAppRequests(): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isNetworkAvailable(context)) return@withContext false
            
            try {
                var allSuccess = true
                val unsyncedRequests = quoteDao.getUnsyncedAppRequests()

                for (request in unsyncedRequests) {
                    val logId = request.unique_id.removePrefix("LOCAL_").toIntOrNull() ?: continue
                    val concepts = quoteConceptDao.getConceptsByLog(logId)
                    val photos = photoDao.getPhotosByLog(logId)
                    
                    val response = uploadRequestToLaravel(request, concepts, photos)
                    
                    if (response.isSuccessful && response.body()?.status == "success") {
                        quoteDao.markAsSynced(request.unique_id)
                        photoDao.markPhotosAsUploaded(logId)
                    } else {
                        allSuccess = false
                    }
                }
                return@withContext allSuccess
            } catch (e: Exception) {
                Log.e("SYNC_REQUESTS", "Error: ${e.message}")
                return@withContext false
            }
        }
    }

    /**
     * Sincroniza AVANCES en cotizaciones oficiales o proyectos
     */
    suspend fun SyncConcepts(quoteConcepts: List<QuoteConcepts>): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isNetworkAvailable(context)) return@withContext false

            try {
                var allSuccess = true
                val currentQuoteId = quote?.idQuote
                val currentProjectId = quote?.idProject

                if (quoteConcepts.isNotEmpty()) {
                    val firstConcept = quoteConcepts.firstOrNull()
                    val response = uploadToLaravel(
                        currentQuoteId,
                        currentProjectId,
                        userId, 
                        quoteConcepts, 
                        DateHelper.getCurrentDateForServer(),
                        firstConcept?.latitude,
                        firstConcept?.longitude
                    )
                    if (response.isSuccessful && response.body()?.status == "success") {
                        if (currentQuoteId != null) quoteConceptDao.updateSyncStatus(currentQuoteId)
                        if (currentProjectId != null) quoteConceptDao.updateSyncStatus(currentProjectId)
                    } else {
                        allSuccess = false
                    }
                }

                val unsyncedLogs = logEntryDao.getUnsyncedLogs().filter { 
                    (currentQuoteId != null && it.quoteId == currentQuoteId) || 
                    (currentProjectId != null && it.idProject == currentProjectId) 
                }
                
                for (log in unsyncedLogs) {
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
                Log.e("SYNC_ERROR", "Error en SyncConcepts: ${e.message}")
                return@withContext false
            }
        }
    }

    suspend fun syncAllUnsyncedData(): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isNetworkAvailable(context)) return@withContext false

            try {
                syncAppRequests()

                val allUnsyncedConcepts = quoteConceptDao.getAllUnsyncedConcepts()
                if (allUnsyncedConcepts.isNotEmpty()) {
                    val groupedByQuote = allUnsyncedConcepts.groupBy { it.quoteId ?: it.idProject }
                    
                    for ((groupId, concepts) in groupedByQuote) {
                        if (groupId == null) continue
                        val firstConcept = concepts.firstOrNull()
                        val response = uploadToLaravel(
                            firstConcept?.quoteId,
                            firstConcept?.idProject,
                            userId, 
                            concepts, 
                            DateHelper.getCurrentDateForServer(),
                            firstConcept?.latitude,
                            firstConcept?.longitude
                        )
                        if (response.isSuccessful && response.body()?.status == "success") {
                            quoteConceptDao.updateSyncStatus(groupId)
                        }
                    }
                }

                val allUnsyncedLogs = logEntryDao.getUnsyncedLogs()
                for (log in allUnsyncedLogs) {
                    val photos = photoDao.getUnsyncedPhotosByLog(log.id)
                    val logResponse = uploadLogWithPhotos(log, photos)

                    if (logResponse.isSuccessful && logResponse.body()?.status == "success") {
                        logEntryDao.updateSyncStatus(log.id)
                        photoDao.markPhotosAsUploaded(log.id)
                        quoteConceptDao.updateSyncStatusByLog(log.id)
                    }
                }

                return@withContext true
            } catch (e: Exception) {
                Log.e("SYNC_ALL_ERROR", "Error: ${e.message}")
                return@withContext false
            }
        }
    }

    private suspend fun uploadRequestToLaravel(
        quote: Quote,
        quoteConcepts: List<QuoteConcepts>,
        photos: List<Photos>
    ): Response<ApiService.GenericResponse> {
        val gson = Gson()
        val conceptsJson = gson.toJson(quoteConcepts).toRequestBody("application/json".toMediaTypeOrNull())
        
        val customerName = quote.nameCustomer?.toRequestBody("text/plain".toMediaTypeOrNull())
        val projectName = quote.projectName?.toRequestBody("text/plain".toMediaTypeOrNull())
        val comment = quote.atte?.toRequestBody("text/plain".toMediaTypeOrNull())
        val userIdPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val createdAtPart = DateHelper.getCurrentDateForServer().toRequestBody("text/plain".toMediaTypeOrNull())
        
        val first = quoteConcepts.firstOrNull()
        val latPart = first?.latitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val lonPart = first?.longitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

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

        return apiService.uploadQuoteRequest(
            customer = customerName,
            project = projectName,
            comment = comment,
            concepts = conceptsJson,
            photos = photoParts,
            userId = userIdPart,
            created_at_app = createdAtPart,
            latitude = latPart,
            longitude = lonPart
        )
    }

    private suspend fun uploadToLaravel(
        quoteId: Int?,
        projectId: Int?,
        userId: Int?,
        quoteConcepts: List<QuoteConcepts>,
        createdAt: String?,
        latitude: Double?,
        longitude: Double?
    ): Response<ApiService.GenericResponse> {
        val gson = Gson()
        val conceptsJsonString = gson.toJson(quoteConcepts)
        val conceptsPart = conceptsJsonString.toRequestBody("application/json".toMediaTypeOrNull())
        val quoteIdPart = quoteId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val projectIdPart = projectId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val userIdPart = userId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val createdAtPart = createdAt?.toRequestBody("text/plain".toMediaTypeOrNull())
        
        val latPart = latitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val lonPart = longitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

        return apiService.uploadQuoteConcept(
            quoteId = quoteIdPart,
            idProject = projectIdPart,
            concepts = conceptsPart,
            userId = userIdPart,
            created_at_app = createdAtPart,
            latitude = latPart,
            longitude = lonPart
        )
    }

    private suspend fun uploadLogWithPhotos(
        log: LogEntry,
        photos: List<Photos>
    ): Response<ApiService.GenericResponse> {
        val commentPart = (log.comment ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
        val quoteIdPart = log.quoteId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val projectIdPart = log.idProject?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val idConceptPart = log.idConcept?.toRequestBody("text/plain".toMediaTypeOrNull())
        val createdAtPart = log.createdAt.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val amountPart = log.cantidad?.toRequestBody("text/plain".toMediaTypeOrNull())
        
        val latitudePart = log.latitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val longitudePart = log.longitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

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
            idProject = projectIdPart,
            idConcept = idConceptPart,
            amount = amountPart,
            photos = photoParts,
            created_at_app = createdAtPart,
            latitude = latitudePart,
            longitude = longitudePart
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
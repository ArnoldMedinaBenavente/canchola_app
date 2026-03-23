package com.canchola.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.canchola.data.local.db.LogEntryDao
import com.canchola.data.local.db.PhotoDao
import com.canchola.data.remote.ApiService
import com.canchola.models.DateHelper
import com.canchola.models.LogEntry
import com.canchola.ui.photo.Photos
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

class LogRepository(
    private val logEntryDao: LogEntryDao,
    private val photoDao: PhotoDao,
    private val apiService: ApiService,
    private val context: Context
) {
    val allLogs: Flow<List<LogEntry>> = logEntryDao.getAllLogs()

    suspend fun saveAndSyncLog(log: LogEntry, photoPaths: List<String>): Boolean {
        return withContext(Dispatchers.IO) {
            val localId = logEntryDao.insert(log).toInt()
            
            val photoEntities = photoPaths.map { path ->
                Photos(
                    logEntryId = localId,
                    uri = path,
                    isUploaded = false
                )
            }

            if (photoEntities.isNotEmpty()) {
                photoDao.insertPhotos(photoEntities)
            }

            if (isNetworkAvailable(context)) {
                try {
                    val response = uploadToLaravel(log.copy(id = localId), photoPaths)
                    if (response.isSuccessful && response.body()?.status == "success") {
                        logEntryDao.updateSyncStatus(localId)
                        photoDao.markPhotosAsUploaded(localId)
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.e("SYNC_ERROR", "Error syncing log: ${e.message}")
                }
            }
            return@withContext false
        }
    }

    suspend fun syncAllUnsyncedLogs(): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isNetworkAvailable(context)) return@withContext false
            
            try {
                val unsyncedLogs = logEntryDao.getUnsyncedLogs()
                var allSuccess = true
                
                for (log in unsyncedLogs) {
                    val photos = photoDao.getUnsyncedPhotosByLog(log.id)
                    val photoPaths = photos.map { it.uri }
                    
                    val response = uploadToLaravel(log, photoPaths)
                    if (response.isSuccessful && response.body()?.status == "success") {
                        logEntryDao.updateSyncStatus(log.id)
                        photoDao.markPhotosAsUploaded(log.id)
                    } else {
                        allSuccess = false
                    }
                }
                return@withContext allSuccess
            } catch (e: Exception) {
                Log.e("SYNC_ALL_ERROR", "Error syncing all: ${e.message}")
                return@withContext false
            }
        }
    }

    private suspend fun uploadToLaravel(log: LogEntry, paths: List<String>): Response<ApiService.GenericResponse> {
        val photoParts = paths.mapNotNull { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val compressedFile = compressImage(file)
                    val requestFile = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("photos[]", compressedFile.name, requestFile)
                } else null
            } catch (e: Exception) {
                null
            }
        }

        val commentPart = (log.comment ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
        val quoteIdPart = log.quoteId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val idConceptPart = log.idConcept?.toRequestBody("text/plain".toMediaTypeOrNull())
        val createdAtPart = DateHelper.formatLongToDate(log.createdAt).toRequestBody("text/plain".toMediaTypeOrNull())
        val amountPart = log.cantidad?.toRequestBody("text/plain".toMediaTypeOrNull())

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

    private fun compressImage(file: File): File {
        val bitmap = decodeFile(file.path)
        val compressedFile = File(context.cacheDir, "comp_" + file.name)
        val out = FileOutputStream(compressedFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        out.flush()
        out.close()
        return compressedFile
    }
}
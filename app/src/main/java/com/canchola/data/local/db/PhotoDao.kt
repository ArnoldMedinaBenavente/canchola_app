package com.canchola.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.canchola.ui.photo.Photos

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertPhotos(photos: List<Photos>)

    @Query("DELETE FROM photos WHERE photoId = :photoId")
    suspend fun deletePhoto(photoId: Int)

    // Útil para tu Sync con Laravel
    @Query("SELECT * FROM photos WHERE isUploaded = 0")
    suspend fun getUnsyncedPhotos(): List<Photos>

    @Query("UPDATE photos SET isUploaded = 1 WHERE logEntryId = :idLog")
    suspend fun markPhotosAsUploaded(idLog:Int)
}
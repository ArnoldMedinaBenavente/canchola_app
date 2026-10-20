package com.canchola.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.canchola.models.QuoteConcepts
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteConceptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: QuoteConcepts): Long

    @Query("SELECT * FROM quoteConcepts ORDER BY createdAt DESC")
    fun getAllLConcepts(): Flow<List<QuoteConcepts>>

    @Query("SELECT * FROM quoteConcepts ORDER BY createdAt DESC")
    suspend fun getAllLConceptsDirect(): List<QuoteConcepts>

    @Query("SELECT * FROM quoteConcepts WHERE quoteId = :qId OR idProject = :qId ORDER BY createdAt DESC")
    fun getConceptsByQuote(qId: Int): Flow<List<QuoteConcepts>>

    @Query("SELECT * FROM quoteConcepts WHERE isSynced = 0 AND (quoteId = :quoteId OR idProject = :projectId)")
    suspend fun getUnsyncedConcepts(quoteId: Int?, projectId: Int?): List<QuoteConcepts>

    @Query("SELECT * FROM quoteConcepts WHERE logIdGenerated = :logId")
    suspend fun getConceptsByLog(logId: Int): List<QuoteConcepts>

    @Query("SELECT * FROM quoteConcepts WHERE isSynced = 0")
    suspend fun getAllUnsyncedConcepts(): List<QuoteConcepts>

    @Query("UPDATE quoteConcepts SET isSynced = 1 WHERE idLog = :idLog")
    suspend fun updateSyncStatusByLog(idLog: Int)

    @Query("UPDATE quoteConcepts SET isSynced = 1 WHERE quoteId = :id OR idProject = :id")
    suspend fun updateSyncStatus(id: Int)

    @Query("DELETE FROM quoteConcepts WHERE logIdGenerated = :logId")
    suspend fun deleteByLog(logId: Int)

    @Delete
    suspend fun delete(entry: QuoteConcepts)
}

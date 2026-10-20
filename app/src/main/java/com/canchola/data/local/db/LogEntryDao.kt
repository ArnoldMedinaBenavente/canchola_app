package com.canchola.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.canchola.models.LogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntry): Long

    @Query("SELECT * FROM log_entries ORDER BY createdAt DESC")
    fun getAllLogs(): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE quoteId = :qId OR idProject = :pId ORDER BY createdAt DESC")
    fun getLogsByQuoteOrProject(qId: Int?, pId: Int?): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<LogEntry>

    @Query("UPDATE log_entries SET isSynced = 1 WHERE id = :id")
    suspend fun updateSyncStatus(id: Int)

    @Delete
    suspend fun delete(entry: LogEntry)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Int)
    
    @Query("SELECT * FROM log_entries WHERE idConcept = :idConcept AND (quoteId = :qId OR idProject = :pId) ORDER BY createdAt DESC")
    suspend fun getLogsByConcept(idConcept: String, qId: Int?, pId: Int?): List<LogEntry>

}
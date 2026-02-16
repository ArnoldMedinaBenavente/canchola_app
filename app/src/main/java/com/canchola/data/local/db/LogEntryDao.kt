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

    // Guarda la bitácora localmente (Estado inicial: isSynced = false)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntry): Long

    // Obtener todas las bitácoras para mostrar en una lista
    @Query("SELECT * FROM log_entries ORDER BY createdAt DESC")
    fun getAllLogs(): Flow<List<LogEntry>>

    // Filtrar bitácoras de una cotización específica (si quoteId no es null)
    @Query("SELECT * FROM log_entries WHERE quoteId = :qId ORDER BY createdAt DESC")
    fun getLogsByQuote(qId: Int): Flow<List<LogEntry>>

    // --- FUNCIONES PARA EL FLUJO DE INTERNET ---

    // Obtener los registros que aún no se han subido a la nube de Laravel
    @Query("SELECT * FROM log_entries WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<LogEntry>

    // Actualizar el estado a 'Sincronizado' cuando Laravel confirme la recepción
    @Query("UPDATE log_entries SET isSynced = 1 WHERE id = :id")
    suspend fun updateSyncStatus(id: Int)

    // Borrar un registro (por si el usuario desea eliminar una evidencia)
    @Delete
    suspend fun delete(entry: LogEntry)
}
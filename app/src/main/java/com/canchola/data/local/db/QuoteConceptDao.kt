package com.canchola.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.canchola.models.LogEntry
import com.canchola.models.QuoteConcepts
import kotlinx.coroutines.flow.Flow

@Dao
interface  QuoteConceptDao{


        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(entry: QuoteConcepts): Long

        // Obtener todas las bitácoras para mostrar en una lista
        @Query("SELECT * FROM quoteConcepts ORDER BY createdAt DESC")
        fun getAllLConcepts(): Flow<List<QuoteConcepts>>

        // Filtrar bitácoras de una cotización específica (si quoteId no es null)
        @Query("SELECT * FROM quoteConcepts WHERE quoteId = :qId ORDER BY createdAt DESC")
        fun getConceptsByQuote(qId: Int): Flow<List<QuoteConcepts>>

        // --- FUNCIONES PARA EL FLUJO DE INTERNET ---

        // Obtener los registros que aún no se han subido a la nube de Laravel
        @Query("SELECT * FROM quoteConcepts WHERE isSynced = 0")
        suspend fun getUnsyncedConcepts(): List<QuoteConcepts>

        // Actualizar el estado a 'Sincronizado' cuando Laravel confirme la recepción
        @Query("UPDATE quoteConcepts SET isSynced = 1 WHERE id = :id")
        suspend fun updateSyncStatus(id: Int)

        // Borrar un registro (por si el usuario desea eliminar una evidencia)
        @Delete
        suspend fun delete(entry: QuoteConcepts)
}
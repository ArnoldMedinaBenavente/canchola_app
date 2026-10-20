package com.canchola.data.local.quotes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.canchola.models.Quote

@Dao
interface QuoteDao {

    // Operaciones: Cotizaciones oficiales o Proyectos (Vienen del servidor)
    @Query("SELECT * FROM quotes WHERE idQuote IS NOT NULL OR idProject IS NOT NULL ORDER BY unique_id DESC")
    suspend fun getOperationQuotes(): List<Quote>

    // Solicitudes: Creadas desde la App (Aún no son oficiales)
    @Query("SELECT * FROM quotes WHERE idQuote IS NULL AND idProject IS NULL ORDER BY unique_id DESC")
    suspend fun getAppRequests(): List<Quote>

    // Obtener solicitudes que aún no se han enviado al servidor
    @Query("SELECT * FROM quotes WHERE idQuote IS NULL AND idProject IS NULL AND isSynced = 0")
    suspend fun getUnsyncedAppRequests(): List<Quote>

    @Query("SELECT * FROM quotes ORDER BY unique_id DESC")
    suspend fun getAllQuotes(): List<Quote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<Quote>)

    @Query("UPDATE quotes SET isSynced = 1 WHERE unique_id = :uniqueId")
    suspend fun markAsSynced(uniqueId: String)

    @Query("DELETE FROM quotes")
    suspend fun clearAll()

    @Query("SELECT * FROM quotes WHERE unique_id = :id LIMIT 1")
    suspend fun getQuoteById(id: String): Quote?

    @Query("DELETE FROM quotes WHERE unique_id = :uniqueId")
    suspend fun deleteQuoteByUniqueId(uniqueId: String)
}

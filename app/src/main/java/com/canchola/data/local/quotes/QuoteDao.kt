package com.canchola.data.local.quotes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.canchola.models.Quote

@Dao
interface QuoteDao {

    // 1. Obtener todas las cotizaciones guardadas para el modo Offline
    @Query("SELECT * FROM quotes ORDER BY idQuote DESC")
    suspend fun getAllQuotes(): List<Quote>

    // 2. Guardar las cotizaciones que vienen de Laravel
    // OnConflictStrategy.REPLACE es vital: si la cotización ya existe, la actualiza
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<Quote>)

    // 3. Borrar todo (útil para cuando el usuario cierra sesión)
    @Query("DELETE FROM quotes")
    suspend fun clearAll()

    // 4. Buscar una cotización específica por folio o ID
    @Query("SELECT * FROM quotes WHERE idQuote = :id LIMIT 1")
    suspend fun getQuoteById(id: Int): Quote?
}
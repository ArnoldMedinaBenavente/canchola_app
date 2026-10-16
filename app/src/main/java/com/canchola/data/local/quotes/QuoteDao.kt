package com.canchola.data.local.quotes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.canchola.models.Quote

@Dao
interface QuoteDao {

    @Query("SELECT * FROM quotes ORDER BY unique_id DESC")
    suspend fun getAllQuotes(): List<Quote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<Quote>)

    @Query("DELETE FROM quotes")
    suspend fun clearAll()

    @Query("SELECT * FROM quotes WHERE unique_id = :id LIMIT 1")
    suspend fun getQuoteById(id: String): Quote?
}
package com.canchola.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.canchola.models.Concept

@Dao
interface ConceptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConcepts(concepts: List<Concept>)

    @Query("SELECT * FROM quotes_concept ORDER BY id ASC")
    suspend fun getAllConcepts(): List<Concept>

    @Query("DELETE FROM quotes_concept")
    suspend fun deleteAll()
}

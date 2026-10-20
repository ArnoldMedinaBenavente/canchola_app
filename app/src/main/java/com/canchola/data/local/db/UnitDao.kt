package com.canchola.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.canchola.models.MeasurementUnit

@Dao
interface UnitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<MeasurementUnit>)

    @Query("SELECT * FROM unidades ORDER BY unidad ASC")
    suspend fun getAllUnits(): List<MeasurementUnit>

    @Query("DELETE FROM unidades")
    suspend fun deleteAll()
}

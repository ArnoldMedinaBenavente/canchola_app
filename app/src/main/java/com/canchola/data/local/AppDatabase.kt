package com.canchola.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.canchola.data.local.quotes.QuoteDao
import com.canchola.models.Quote

// 1. Aquí registras el modelo (Entity) y la versión
@Database(entities = [Quote::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    // 2. AQUÍ SE REGISTRA EL DAO
    abstract fun quoteDao(): QuoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "canchola_db" // Nombre del archivo en el celular
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
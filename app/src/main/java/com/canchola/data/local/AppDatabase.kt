package com.canchola.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.canchola.data.local.db.LogEntryDao
import com.canchola.data.local.db.PhotoDao
import com.canchola.data.local.db.QuoteConceptDao
import com.canchola.data.local.quotes.QuoteDao
import com.canchola.models.LogEntry
import com.canchola.models.Quote
import com.canchola.models.QuoteConcepts
import com.canchola.ui.photo.Photos

// 1. Aquí registras el modelo (Entity) y la versión
@Database(entities = [Quote::class,LogEntry::class,
                      Photos::class,QuoteConcepts::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // 2. AQUÍ SE REGISTRA EL DAO
    abstract fun quoteDao(): QuoteDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun photoDao(): PhotoDao
    abstract fun quoteConceptDao(): QuoteConceptDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "canchola_db" // Nombre del archivo en el celular
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
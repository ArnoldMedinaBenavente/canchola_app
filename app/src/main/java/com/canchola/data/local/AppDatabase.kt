package com.canchola.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.canchola.data.local.db.ConceptDao
import com.canchola.data.local.db.CustomerDao
import com.canchola.data.local.db.UnitDao
import com.canchola.data.local.db.LogEntryDao
import com.canchola.data.local.db.PhotoDao
import com.canchola.data.local.db.QuoteConceptDao
import com.canchola.data.local.quotes.QuoteDao
import com.canchola.models.Concept
import com.canchola.models.Customer
import com.canchola.models.MeasurementUnit
import com.canchola.models.LogEntry
import com.canchola.models.Quote
import com.canchola.models.QuoteConcepts
import com.canchola.ui.photo.Photos

@Database(entities = [Quote::class, LogEntry::class, Photos::class,
    QuoteConcepts::class, Customer::class, Concept::class, MeasurementUnit::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun quoteDao(): QuoteDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun photoDao(): PhotoDao
    abstract fun quoteConceptDao(): QuoteConceptDao
    abstract fun customerDao(): CustomerDao
    abstract fun conceptDao(): ConceptDao
    abstract fun unitDao(): UnitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "canchola_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
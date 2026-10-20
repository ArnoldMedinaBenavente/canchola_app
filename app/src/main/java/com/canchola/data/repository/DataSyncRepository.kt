package com.canchola.data.repository

import android.util.Log
import com.canchola.data.local.db.ConceptDao
import com.canchola.data.local.db.CustomerDao
import com.canchola.data.local.db.UnitDao
import com.canchola.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataSyncRepository(
    private val apiService: ApiService,
    private val customerDao: CustomerDao,
    private val conceptDao: ConceptDao,
    private val unitDao: UnitDao
) {
    suspend fun syncCatalogData(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.loadDataForSync()
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    
                    // Guardar Clientes
                    customerDao.deleteAll()
                    customerDao.insertCustomers(data.customers)
                    
                    // Guardar Conceptos
                    conceptDao.deleteAll()
                    conceptDao.insertConcepts(data.concepts)

                    // Guardar Unidades
                    unitDao.deleteAll()
                    unitDao.insertUnits(data.units)
                    
                    Log.d("SYNC_CATALOG", "Catálogos sincronizados: ${data.customers.size} clientes, ${data.concepts.size} conceptos, ${data.units.size} unidades")
                    true
                } else {
                    Log.e("SYNC_CATALOG", "Error en respuesta: ${response.code()}")
                    false
                }
            } catch (e: Exception) {
                Log.e("SYNC_CATALOG", "Excepción al sincronizar: ${e.message}")
                false
            }
        }
    }
}

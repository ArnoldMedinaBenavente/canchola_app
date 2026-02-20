package com.canchola.ui.home

import QuotesAdapter
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.canchola.MainActivity
import com.canchola.R
import com.canchola.data.local.AppDatabase
import com.canchola.data.local.SessionManager
import com.canchola.data.remote.RetrofitClient
import com.canchola.databinding.ActivityHomeBinding
import com.canchola.models.Quote
import com.canchola.models.QuoteConcepts
import com.canchola.ui.AddLogSheet
import com.canchola.ui.quotes.QuoteDetailActivity

import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var adapter: QuotesAdapter
    private val apiService by lazy { RetrofitClient.getInstance(this) }
    private lateinit var db: AppDatabase

    private lateinit var sessionManager: SessionManager
    var currentUserId: Int? =null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Inicializas la base de datos
        db = AppDatabase.getDatabase(this)
        setupRecyclerView()
        setupListeners()
        loadQuotes()
        sessionManager = SessionManager(this)
         currentUserId = sessionManager.getUserId()
    }

    private fun setupRecyclerView() {
        binding.rvQuotes.layoutManager = LinearLayoutManager(this)
    }

    private fun loadQuotes() {
        lifecycleScope.launch {
            try {
                // Paso 1: Intentar traer de Laravel (Online)
                val response = apiService.getQuotes()

                if (response.isSuccessful && response.body() != null) {
                    val remoteQuotes = response.body()!!
                    try {
                        db.quoteDao().insertQuotes(remoteQuotes)
                        Log.d("ROOM_DB", "¡ÉXITO! Se guardó en la memoria.")
                    } catch (dbError: Exception) {
                        Log.e("ROOM_DB", "ERROR AL INSERTAR: ${dbError.message}")
                    }
                    displayQuotes(remoteQuotes)
                } else {
                    // Si el servidor falla (ej. error 500), intentar cargar local
                    loadFromLocalDatabase("Error del servidor, mostrando datos locales")
                }
            } catch (e: Exception) {
                // Paso 4: Si no hay internet (Network Exception), cargar local
                loadFromLocalDatabase(getString(R.string.loading))
            }
        }
    }

    // Función extra para manejar el fallo de red
    private suspend fun loadFromLocalDatabase(message: String) {
        val localQuotes = db.quoteDao().getAllQuotes()
        if (localQuotes.isNotEmpty()) {
            displayQuotes(localQuotes)
            Toast.makeText(this@HomeActivity, message, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this@HomeActivity, "No hay datos guardados", Toast.LENGTH_SHORT).show()
        }
    }


    private fun displayQuotes(list: List<Quote>) {
        // 1. Creamos el adaptador pasando la lista y las dos acciones (Descarga y Opciones)
        adapter = QuotesAdapter(
            list,
            { quote ->
                // Acción cuando tocan el botón azul de descarga
                downloadQuote(quote)
            },
            { quote ->
                // Acción cuando tocan la tarjeta (abre el menú de iOS)
                showQuoteOptions(quote)
            }
        )

        // 2. Asignamos el adaptador al RecyclerView
        binding.rvQuotes.adapter = adapter

        // 3. (Opcional) Si la lista está vacía, podrías mostrar un mensaje de "No hay cotizaciones"

    }


    private fun downloadQuote(quote: Quote) {

    }

    private fun setupListeners() {
        // Botón Actualizar
        binding.btnRefresh.setOnClickListener {
            loadQuotes() // Llama a la función que trae los datos de Laravel
            Toast.makeText(this, "Actualizando...", Toast.LENGTH_SHORT).show()
        }

        // Botón Cerrar Sesión
        binding.btnLogout.setOnClickListener {
            val sessionManager = SessionManager(this)
            sessionManager.clearSession() // Debes tener este método en tu SessionManager

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showQuoteOptions(quote: Quote) {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.layout_quote_options, null)

        // Opción 1: VER MÁS (Navegación)
        view.findViewById<TextView>(R.id.btnViewMore).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, QuoteDetailActivity::class.java)
            intent.putExtra("QUOTE_DATA", quote) // Pasamos toda la info de la cotización
            startActivity(intent)
        }

        // Opción 2: REGISTRAR AVANCES
        view.findViewById<TextView>(R.id.btnProgress).setOnClickListener {
            dialog.dismiss()
            mostrarFormularioAvance(quote)
        }
        // Opción 3: Commentario
        view.findViewById<TextView>(R.id.btnAddComment).setOnClickListener {
            dialog.dismiss()
            val sheet = AddLogSheet(quoteId = quote.idQuote,"comment") // Pasas el ID
            sheet.show(supportFragmentManager, "AddLog")
        }
        // Opción 4: TOMAR FOTO
        view.findViewById<TextView>(R.id.btnAddPhoto).setOnClickListener {

            dialog.dismiss()
            val sheet = AddLogSheet(quoteId = quote.idQuote,"photo") // Pasas el ID
            sheet.show(supportFragmentManager, "AddLog")
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun mostrarFormularioAvance(quote: Quote?) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_registrar_avance, null)
        dialog.setContentView(view)

        val container = view.findViewById<LinearLayout>(R.id.containerAlcancesDialog)
        val btnGuardar = view.findViewById<Button>(R.id.btnGuardarTodoDialog)

        // 1. Obtenemos la cadena de conceptos (ej: "Pintura | Tablaroca | Piso")
        val cadenaAlcances = quote?.conceptos ?: ""

        if (cadenaAlcances.isNotEmpty()) {
            val lista = cadenaAlcances.split("|")

            lista.forEach { concepto ->
                val conceptoLimpio = concepto.trim()
                if (conceptoLimpio.isNotEmpty()) {
                    val dataConcepto = conceptoLimpio.split(";")
                    val idConcepto = dataConcepto.get(0)
                    val nombreConcepto = dataConcepto.get(1)
                    // 2. Inflamos el diseño que ya tenías (item_alcance)
                    val itemView = layoutInflater.inflate(R.layout.item_alcance, null)
                    val tvIdConcept=itemView.findViewById<TextView>(R.id.tvIdConcept)
                    val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreAlcance)
                    val etCantidad = itemView.findViewById<EditText>(R.id.etCantidadAvance)
                    tvIdConcept.text= idConcepto
                    tvNombre.text = nombreConcepto
                    etCantidad.setHint("0.0")

                    // Agregamos el item al contenedor del DIÁLOGO
                    container.addView(itemView)
                }
            }
        }

        // 3. Lógica para guardar todo lo que se escribió en el diálogo
        btnGuardar.setOnClickListener {
            val datosAEnviar = mutableListOf<String>()

            for (i in 0 until container.childCount) {
                val item = container.getChildAt(i)
                val idConcept = item.findViewById<TextView>(R.id.tvIdConcept).text.toString()
                val nombre = item.findViewById<TextView>(R.id.tvNombreAlcance).text.toString()
                val cant = item.findViewById<EditText>(R.id.etCantidadAvance).text.toString()

                if (cant.isNotEmpty()) {
                    val newConcept= QuoteConcepts(
                        idConcept = idConcept,
                        quoteId = quote?.idQuote,
                        nameConcept = nombre,
                        cantConcept = cant,
                        isSynced = false,
                        idUser = currentUserId
                    )
                    datosAEnviar.add("$nombre:$cant")
                    lifecycleScope.launch {
                        db.quoteConceptDao().insert(newConcept)

                    }
                }
            }
            dialog.dismiss()
           Toast.makeText(this,"")
        }

        dialog.show()
    }


}
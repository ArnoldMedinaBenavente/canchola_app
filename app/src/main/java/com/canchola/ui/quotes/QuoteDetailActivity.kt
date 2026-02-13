package com.canchola.ui.quotes

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.canchola.R
import com.canchola.databinding.ActivityQuoteDetailBinding
import com.canchola.models.Quote

class QuoteDetailActivity : AppCompatActivity() {

    // 1. Declarar la variable del binding
    private lateinit var binding: ActivityQuoteDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Inflar el layout (AQUÍ ES DONDE SE RESUELVE EL ERROR)
        binding = ActivityQuoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3. Recuperar la información que enviaste desde el Home
        val quote = intent.getSerializableExtra("QUOTE_DATA") as? Quote

        if (quote != null) {
            mostrarDatos(quote)
        }

        // 3. Cambiar el título si lo deseas (opcional)
        if (quote != null) {

            binding.detailsTvFolio.text="Folio:${quote.idQuote}"
        }



        val cadenaKeywords = quote?.keywords ?: "" // El campo que viene de tu Laravel


        if (!cadenaKeywords.isNullOrEmpty()) {
            binding.detailsTvKeywords.visibility = View.VISIBLE
            binding.detailsTvKeywords.text = cadenaKeywords.replace(",", " • ")
        } else {
            binding.detailsTvKeywords.visibility = View.GONE
        }
        // 1. Limpiar el contenedor antes de agregar
        binding.containerAlcances.removeAllViews()
        // 2. Obtener la cadena y separarla
        // Asumiendo que el campo se llama 'conceptosTexto' en tu modelo
        val cadenaConceptos = quote?.conceptos ?: ""

        if (cadenaConceptos.isNotEmpty()) {
            // Separamos por el pipe |
            val listaConceptos = cadenaConceptos.split("|")

            listaConceptos.forEach { nombreConcepto ->
                // Quitamos espacios en blanco extra que puedan venir
                val nombreLimpio = nombreConcepto.trim()

                if (nombreLimpio.isNotEmpty()) {
                    // Dentro de tu forEach donde inflas los alcances
                    val itemView = layoutInflater.inflate(R.layout.item_alcance, null)
                    val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreAlcance)
                    val layoutRegistro = itemView.findViewById<LinearLayout>(R.id.layoutRegistro)

                    tvNombre.text = nombreLimpio
                    // Dentro de tu mostrarDatos (el bucle de los alcances)


// Opcional: Si quieres que el usuario sepa que puede editar,
// puedes dejar un clic para que el teclado se abra directo en el EditText
                    itemView.setOnClickListener {
                        layoutRegistro.requestFocus()
                        // Aquí podrías mostrar el teclado automáticamente
                    }



                    binding.containerAlcances.addView(itemView)
                }
            }
        }
    }

    private fun mostrarDatos(quote: Quote) {
        // Ahora ya puedes usar binding sin errores
       // binding.tvFolioDetalle.text = "Folio: #${quote.idQuote}"
        binding.tvClienteDetalle.text = quote.nameCustomer
        binding.tvFechaDetalle.text=quote.atte
        // Para el HTML de los términos que manda tu Laravel

    }
}
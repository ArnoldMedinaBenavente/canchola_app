package com.canchola.ui.quotes

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.canchola.databinding.ActivityCreateQuoteBinding

class CreateQuoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateQuoteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateQuoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarCreateQuote)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarCreateQuote.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupListeners() {
        binding.btnSaveNewQuote.setOnClickListener {
            val customer = binding.etCustomerName.text.toString()
            val project = binding.etProjectName.text.toString()
            val keywords = binding.etKeywords.text.toString()

            if (customer.isBlank()) {
                Toast.makeText(this, "El nombre del cliente es obligatorio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Aquí iría la lógica para guardar en Room y sincronizar con Laravel
            Toast.makeText(this, "Cotización guardada (Funcionalidad en desarrollo)", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
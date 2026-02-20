package com.canchola

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.canchola.data.local.SessionManager
import com.canchola.data.remote.RetrofitClient
import com.canchola.data.repository.AuthRepository
import com.canchola.databinding.ActivityMainBinding // Se genera automáticamente
import com.canchola.ui.home.HomeActivity
import com.canchola.ui.login.LoginViewModel
import com.canchola.ui.login.LoginViewModelFactory

class MainActivity : AppCompatActivity() {

    // 1. Declaramos el binding
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: LoginViewModel



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Inicializamos el binding
        // 1. Inicializamos el binding y la vista
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Inicializamos el SessionManager
        val sessionManager = SessionManager(this)

        // --- AQUÍ EL TRUCO PARA LA SESIÓN ---
        if (!sessionManager.fetchAuthToken().isNullOrEmpty()) {
            // Si ya hay un token, vamos directo al Home
            goToHome()
            return // Importante: salimos del onCreate para no inicializar lo demás
        }
        // Inicialización de la arquitectura (Igual que antes)
        val apiService = RetrofitClient.getInstance(this)

        val repository = AuthRepository(apiService, sessionManager)
        val factory = LoginViewModelFactory(repository)

        viewModel = ViewModelProvider(this, factory).get(LoginViewModel::class.java)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        // Observar resultado del Login
        viewModel.loginResult.observe(this) { response ->
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()?.user

                Toast.makeText(this, "Bienvenido, ${user?.name}", Toast.LENGTH_LONG).show()
                // Ir al Home y cerrar el Login
                val intent = Intent(this, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish() // Finaliza esta actividad
            } else {
                Toast.makeText(this, "Error: Verifica tus credenciales", Toast.LENGTH_SHORT).show()
            }
        }

        // Observar estado de carga (opcional pero recomendado)
        viewModel.isLoading.observe(this) { isLoading ->
            binding.btnLogin.isEnabled = !isLoading
            // binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            // 3. Accedemos a los views directamente por su ID del XML
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor llena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isNetworkAvailable()) {
                viewModel.login(email, password)
            } else {
                Toast.makeText(this, "No tienes conexión a internet. Revisa tu red.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                )
    }
}
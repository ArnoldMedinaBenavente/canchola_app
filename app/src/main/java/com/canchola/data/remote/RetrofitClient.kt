package com.canchola.data.remote

import android.content.Context
import com.canchola.data.local.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor // Importa esto
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.canchola.BuildConfig
object RetrofitClient {
    private const val BASE_URL = BuildConfig.BASE_URL
    private var retrofit: Retrofit? = null

    fun getInstance(context: Context): ApiService {
        if (retrofit == null) {
            // 1. Creamos el interceptor de logs
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val sessionManager = SessionManager(context)

            // 2. Agregamos AMBOS interceptores al cliente
            val client = OkHttpClient.Builder()
                .addInterceptor(logging) // Interceptor de Logcat
                .addInterceptor(AuthInterceptor(sessionManager)) // Tu interceptor de Token
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }
}
package com.canchola.data.remote

import com.canchola.models.LoginResponse
import com.canchola.models.Quote
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @POST("login")
    suspend fun login(@Body credentials: Map<String, String>): Response<LoginResponse>

    @GET("quotes")
    suspend fun getQuotes(): Response<List<Quote>>

    //LOGeNTRI COMENTARIOS Y FOTOS
        @Multipart
        @POST("create/logEntries") // Ajusta esta ruta según tus rutas en Laravel
        suspend fun uploadLogEntry(
        @Part("comment") comment: RequestBody,
        @Part("quote_id") quoteId: RequestBody?, // Puede ser null si es general
        @Part photos: List<MultipartBody.Part>,        // Puede ser null si no hay foto
        @Part ("created_at") created_at_app: RequestBody?        // Puede ser null si no hay foto
        ): Response<GenericResponse>


    // Clase para recibir mensajes de éxito de tu ERP (ej: {"message": "Guardado"})
    data class GenericResponse(val message: String, val status: Int)
}
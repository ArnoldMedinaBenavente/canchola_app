package com.canchola.data.remote

import com.canchola.models.LoginResponse
import com.canchola.models.Quote
import com.canchola.models.QuoteConcepts
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

    @Multipart
    @POST("create/logEntries")
    suspend fun uploadLogEntry(
        @Part("comment") comment: RequestBody,
        @Part("quote_id") quoteId: RequestBody?,
        @Part("id_concept") idConcept: RequestBody?,
        @Part("amount_app") amount: RequestBody?,
        @Part photos: List<MultipartBody.Part>,
        @Part("created_at") created_at_app: RequestBody?,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?
    ): Response<GenericResponse>

    @Multipart
    @POST("create/quoteConcept")
    suspend fun uploadQuoteConcept(
        @Part("quote_id") quoteId: RequestBody?,
        @Part("concepts") concepts: RequestBody,
        @Part("idUser") userId: RequestBody?,
        @Part("created_at") created_at_app: RequestBody?,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?
    ): Response<GenericResponse>

    data class GenericResponse(val message: String, val status: String)
}
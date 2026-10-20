package com.canchola.data.remote

import com.canchola.models.LoginResponse
import com.canchola.models.Quote
import com.canchola.models.SyncDataResponse
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

    @GET("getActives")
    suspend fun getActives(): Response<List<Quote>>

    @GET("getAllDataForApp")
    suspend fun loadDataForSync(): Response<SyncDataResponse>

    @Multipart
    @POST("create/logEntries/v1")
    suspend fun uploadLogEntry(
        @Part("comment") comment: RequestBody,
        @Part("quote_id") quoteId: RequestBody?,
        @Part("project_id") idProject: RequestBody?,
        @Part("id_concept") idConcept: RequestBody?,
        @Part("amount_app") amount: RequestBody?,
        @Part photos: List<MultipartBody.Part>,
        @Part("created_at_app") created_at_app: RequestBody?,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?
    ): Response<GenericResponse>

    @Multipart
    @POST("create/quoteRequest/v1")
    suspend fun uploadQuoteRequest(
        @Part("customer_name") customer: RequestBody?,
        @Part("project_name") project: RequestBody?,
        @Part("comment") comment: RequestBody?,
        @Part("concepts") concepts: RequestBody,
        @Part photos: List<MultipartBody.Part>,
        @Part("idUser") userId: RequestBody?,
        @Part("created_at_app") created_at_app: RequestBody?,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?
    ): Response<GenericResponse>

    @Multipart
    @POST("create/quoteConcept/v1")
    suspend fun uploadQuoteConcept(
        @Part("quote_id") quoteId: RequestBody?,
        @Part("project_id") idProject: RequestBody?,
        @Part("concepts") concepts: RequestBody,
        @Part("idUser") userId: RequestBody?,
        @Part("created_at_app") created_at_app: RequestBody?,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?
    ): Response<GenericResponse>

    data class GenericResponse(val message: String, val status: String)
}

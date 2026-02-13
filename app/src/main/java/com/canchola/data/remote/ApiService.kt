package com.canchola.data.remote

import com.canchola.models.LoginResponse
import com.canchola.models.Quote
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("login")
    suspend fun login(@Body credentials: Map<String, String>): Response<LoginResponse>

    @GET("quotes")
    suspend fun getQuotes(): Response<List<Quote>>
}
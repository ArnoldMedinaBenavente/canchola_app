package com.canchola.data.repository

import com.canchola.data.local.SessionManager
import com.canchola.data.remote.ApiService
import com.canchola.models.LoginResponse
import retrofit2.Response

class AuthRepository(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun login(credentials: Map<String, String>): Response<LoginResponse> {
        val response = apiService.login(credentials)

        // Si el login en Laravel fue exitoso, guardamos el token
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.access_token?.let { token ->
                sessionManager.saveAuthToken(token)
            }
            val user = response.body()?.user
            user?.id?.let{
                sessionManager.saveUserId(it)
            }

        }
        return response
    }
}
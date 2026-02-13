package com.canchola.models

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val access_token: String?,
    val user: User?
)
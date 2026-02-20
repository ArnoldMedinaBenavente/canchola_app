package com.canchola.data.local

import android.content.Context

// Archivo: data/local/SessionManager.kt
class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
    companion object {
        const val USER_ID = "user_id"
    }

    fun saveUserId(id: Int) {
        val editor = prefs.edit()
        editor.putInt(USER_ID, id)
        editor.apply()
    }

    fun getUserId(): Int {
        // Retorna -1 si no encuentra el ID
        return prefs.getInt(USER_ID, -1)
    }
    fun saveAuthToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun clearSession() {
        val editor = prefs.edit()
        editor.clear() // Borra el token y todos los datos guardados
        editor.apply()
    }
}
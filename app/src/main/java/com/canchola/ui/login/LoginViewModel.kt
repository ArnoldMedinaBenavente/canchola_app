package com.canchola.ui.login

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canchola.data.repository.AuthRepository
import com.canchola.models.LoginResponse
import kotlinx.coroutines.launch
import retrofit2.Response

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    val loginResult = MutableLiveData<Response<LoginResponse>>()
    val isLoading = MutableLiveData<Boolean>()

    fun login(email: String, pass: String) {
        isLoading.value = true
        val credentials = mapOf("email" to email, "password" to pass)

        viewModelScope.launch {
            val response = repository.login(credentials)
            loginResult.postValue(response)
            isLoading.value = false
        }
    }
}
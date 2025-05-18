package com.example.deliverybox.data.repositories

import com.example.deliverybox.core.UserData
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<UserData>
    suspend fun signup(email: String): Result<String> // 인증 토큰 반환
    suspend fun verifyEmail(token: String, email: String): Result<Boolean>
    suspend fun setPassword(password: String): Result<Boolean>
    suspend fun logout(): Result<Boolean>
    suspend fun sendPasswordResetEmail(email: String): Result<Boolean>
    fun getCurrentUser(): UserData?
    fun observeAuthState(): Flow<AuthState>
}

sealed class AuthState {
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object EmailVerificationNeeded : AuthState()
    object PasswordSetupNeeded : AuthState()
    data class Error(val message: String) : AuthState()
}
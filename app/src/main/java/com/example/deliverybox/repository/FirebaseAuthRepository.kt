package com.example.deliverybox.repository

import com.example.deliverybox.core.UserData
import com.example.deliverybox.utils.FirebaseAuthExceptionMapper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<UserData> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("인증 실패")

            if (!user.isEmailVerified) {
                return Result.failure(Exception("이메일 인증이 필요합니다"))
            }

            val userData = getUserDataFromFirestore(user.uid)
            Result.success(userData)
        } catch (e: Exception) {
            Result.failure(FirebaseAuthExceptionMapper.mapException(e))
        }
    }

    override suspend fun signup(email: String): Result<String> {
        return try {
            val token = generateSecureToken()
            val temporaryPassword = generateSecureToken() // 임시 비밀번호

            val authResult = auth.createUserWithEmailAndPassword(email, temporaryPassword).await()
            val user = authResult.user ?: throw Exception("계정 생성 실패")

            // Firestore에 사용자 정보 저장
            val userData = hashMapOf(
                "email" to email,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "emailVerified" to false,
                "passwordSet" to false,
                "verificationToken" to token,  // 인증 토큰 저장
                "tokenCreatedAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("users").document(user.uid).set(userData).await()

            // 이메일 인증 전송
            user.sendEmailVerification().await()

            Result.success(token)
        } catch (e: Exception) {
            Result.failure(FirebaseAuthExceptionMapper.mapException(e))
        }
    }

    override suspend fun verifyEmail(token: String, email: String): Result<Boolean> {
        return try {
            val user = auth.currentUser ?: throw Exception("로그인이 필요합니다")

            user.reload().await()

            if (!user.isEmailVerified) {
                return Result.failure(Exception("이메일 인증이 완료되지 않았습니다"))
            }

            // Firestore 업데이트
            db.collection("users").document(user.uid)
                .update("emailVerified", true)
                .await()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(FirebaseAuthExceptionMapper.mapException(e))
        }
    }

    override suspend fun setPassword(password: String): Result<Boolean> {
        return try {
            val user = auth.currentUser ?: throw Exception("로그인이 필요합니다")

            user.updatePassword(password).await()

            // Firestore 업데이트
            db.collection("users").document(user.uid)
                .update("passwordSet", true)
                .await()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(FirebaseAuthExceptionMapper.mapException(e))
        }
    }

    override suspend fun logout(): Result<Boolean> {
        return try {
            auth.signOut()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Boolean> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(FirebaseAuthExceptionMapper.mapException(e))
        }
    }

    override fun getCurrentUser(): UserData? {
        val firebaseUser = auth.currentUser ?: return null
        return UserData(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = firebaseUser.displayName ?: "",
            photoUrl = firebaseUser.photoUrl?.toString() ?: "",
            boxIds = emptyList(), // Firestore에서 가져오는 것이 필요
            isPasswordSet = true // 기본값, 실제로는 Firestore에서 확인 필요
        )
    }

    override fun observeAuthState(): Flow<AuthState> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(AuthState.Unauthenticated)
                return@AuthStateListener
            }

            if (!user.isEmailVerified) {
                trySend(AuthState.EmailVerificationNeeded)
                return@AuthStateListener
            }

            // 비밀번호 설정 여부 확인
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    val passwordSet = document.getBoolean("passwordSet") ?: false
                    if (!passwordSet) {
                        trySend(AuthState.PasswordSetupNeeded)
                    } else {
                        trySend(AuthState.Authenticated)
                    }
                }
                .addOnFailureListener {
                    trySend(AuthState.Error("사용자 정보 로드 실패"))
                }
        }

        auth.addAuthStateListener(authStateListener)

        awaitClose {
            auth.removeAuthStateListener(authStateListener)
        }
    }

    private suspend fun getUserDataFromFirestore(uid: String): UserData {
        return suspendCoroutine { continuation ->
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val email = document.getString("email") ?: ""
                        val displayName = document.getString("nickname") ?: ""
                        val photoUrl = document.getString("photoUrl") ?: ""
                        val boxIds = document.get("boxIds") as? List<String> ?: emptyList()
                        val isPasswordSet = document.getBoolean("passwordSet") ?: false

                        val userData = UserData(
                            uid = uid,
                            email = email,
                            displayName = displayName,
                            photoUrl = photoUrl,
                            boxIds = boxIds,
                            isPasswordSet = isPasswordSet
                        )

                        continuation.resume(userData)
                    } else {
                        continuation.resumeWithException(Exception("사용자 정보를 찾을 수 없습니다"))
                    }
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    private fun generateSecureToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
    }
}
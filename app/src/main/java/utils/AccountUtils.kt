package com.example.deliverybox.utils

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/**
 * 계정 관련 유틸리티 클래스
 * 임시 계정 삭제 및 가입 상태 확인 등 계정 관련 기능을 담당
 */
object AccountUtils {

    private const val TAG = "AccountUtils"
    private const val DELETION_TIMEOUT_SECONDS = 10L
    private const val UNVERIFIED_ACCOUNT_LIMIT_DAYS = 7L

    /**
     * 임시 계정을 안전하게 삭제하고 로그아웃 처리
     * 앱 시작시 호출하여 미인증 계정 정리
     */
    fun deleteTempAccountAndSignOut(callback: (() -> Unit)? = null) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Log.d(TAG, "로그인된 사용자 없음")
            callback?.invoke()
            return
        }

        // 현재 인증되지 않은 계정이라면 삭제 처리
        if (!currentUser.isEmailVerified) {
            try {
                val uid = currentUser.uid
                val db = FirebaseFirestore.getInstance()

                db.collection("users").document(uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val createdAt = document.getTimestamp("createdAt")

                            if (createdAt != null) {
                                val now = System.currentTimeMillis()
                                val createdTime = createdAt.toDate().time
                                val elapsedDays = TimeUnit.MILLISECONDS.toDays(now - createdTime)

                                if (elapsedDays >= UNVERIFIED_ACCOUNT_LIMIT_DAYS) {
                                    deleteUserAccount(currentUser, uid, callback)
                                } else {
                                    Log.d(TAG, "미인증 계정 유예 기간 내: $uid (${elapsedDays}일 경과)")
                                    auth.signOut()
                                    callback?.invoke()
                                }
                            } else {
                                Log.w(TAG, "계정 생성 시간 정보 없음, 삭제 보류: $uid")
                                auth.signOut()
                                callback?.invoke()
                            }
                        } else {
                            Log.d(TAG, "사용자 문서 없는 미인증 계정, 삭제: $uid")
                            deleteUserAccount(currentUser, uid, callback)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firestore 조회 실패: ${e.message}")
                        auth.signOut()
                        callback?.invoke()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "미인증 계정 처리 중 오류: ${e.message}")
                auth.signOut()
                callback?.invoke()
            }
        } else {
            Log.d(TAG, "인증된 계정, 삭제하지 않음: ${currentUser.email}")
            callback?.invoke()
        }
    }

    /**
     * 사용자 계정 및 데이터 삭제
     * Firestore 문서 삭제 후 계정 삭제
     */
    private fun deleteUserAccount(user: FirebaseUser, uid: String, callback: (() -> Unit)?) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("users").document(uid)
                .delete()
                .addOnCompleteListener {
                    if (it.isSuccessful) Log.d(TAG, "사용자 문서 삭제 성공: $uid")
                    else Log.e(TAG, "문서 삭제 실패: ${it.exception?.message}")
                    try {
                        val deleteTask = user.delete()
                        Tasks.await(deleteTask, DELETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        Log.d(TAG, "계정 삭제 성공: ${user.email}")
                    } catch (e: Exception) {
                        Log.e(TAG, "계정 삭제 실패: ${e.message}")
                    } finally {
                        auth.signOut()
                        callback?.invoke()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "계정 삭제 중 오류: ${e.message}")
            auth.signOut()
            callback?.invoke()
        }
    }

    /**
     * 오래된 미인증 계정 일괄 정리
     * 서버단에서 주기적으로 실행할 로직
     */
    fun cleanupOldUnverifiedAccounts(onComplete: ((Int) -> Unit)? = null) {
        val db = FirebaseFirestore.getInstance()
        try {
            val cutoff = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -UNVERIFIED_ACCOUNT_LIMIT_DAYS.toInt())
            }.time
            db.collection("users")
                .whereEqualTo("emailVerified", false)
                .whereLessThan("createdAt", cutoff)
                .get()
                .addOnSuccessListener { documents ->
                    var deletedCount = 0
                    val totalCount = documents.size()
                    if (totalCount == 0) { onComplete?.invoke(0); return@addOnSuccessListener }
                    for (doc in documents) {
                        doc.reference.delete()
                            .addOnSuccessListener {
                                deletedCount++
                                if (deletedCount == totalCount) onComplete?.invoke(deletedCount)
                            }
                            .addOnFailureListener {
                                deletedCount++
                                if (deletedCount == totalCount) onComplete?.invoke(deletedCount)
                            }
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "일괄 정리 실패: ${it.message}")
                    onComplete?.invoke(0)
                }
        } catch (e: Exception) {
            Log.e(TAG, "오래된 계정 정리 오류: ${e.message}")
            onComplete?.invoke(0)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    /**
     * 현재 사용자 가입 상태 확인
     * 로그인되지 않음 / 이메일 인증 완료 / 비밀번호 설정 완료 상태로 분류
     */
    fun checkSignupState(callback: (SignupState) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            callback(SignupState.NOT_LOGGED_IN)
            return
        }
        if (!user.isEmailVerified) {
            deleteTempAccountAndSignOut { callback(SignupState.NOT_LOGGED_IN) }
            return
        }
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                val emailVerified = doc?.getBoolean("emailVerified") ?: false
                val passwordSet = doc?.getBoolean("passwordSet") ?: false
                when {
                    emailVerified && passwordSet -> callback(SignupState.COMPLETED)
                    emailVerified -> callback(SignupState.EMAIL_VERIFIED)
                    else -> db.collection("users").document(user.uid)
                        .update("emailVerified", true)
                        .addOnSuccessListener { callback(SignupState.EMAIL_VERIFIED) }
                        .addOnFailureListener { auth.signOut(); callback(SignupState.NOT_LOGGED_IN) }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "사용자 정보 조회 실패: ${it.message}")
                auth.signOut()
                callback(SignupState.NOT_LOGGED_IN)
            }
    }

    /**
     * 가입 상태를 나타내는 enum
     */
    enum class SignupState {
        NOT_LOGGED_IN,
        EMAIL_VERIFIED,
        COMPLETED
    }

    /**
     * 예외 처리 결과를 담는 데이터 클래스
     */
    data class ExceptionResult(
        val state: SignupState,
        val email: String? = null
    )

    /**
     * 예외 상황(가입 흐름) 확인 후 callback으로 결과 전달
     */
    fun checkExceptionCases(callback: (ExceptionResult) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            callback(ExceptionResult(SignupState.NOT_LOGGED_IN))
            return
        }
        user.reload().addOnCompleteListener { reloadTask ->
            if (!reloadTask.isSuccessful) {
                auth.signOut()
                callback(ExceptionResult(SignupState.NOT_LOGGED_IN))
                return@addOnCompleteListener
            }
            val updated = auth.currentUser
            if (updated == null) {
                callback(ExceptionResult(SignupState.NOT_LOGGED_IN))
                return@addOnCompleteListener
            }
            if (!updated.isEmailVerified) {
                callback(ExceptionResult(SignupState.EMAIL_VERIFIED, updated.email))
                return@addOnCompleteListener
            }
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(updated.uid)
                .get()
                .addOnSuccessListener { doc ->
                    val pwSet = doc.getBoolean("passwordSet") ?: false
                    if (pwSet) {
                        callback(ExceptionResult(SignupState.COMPLETED))
                    } else {
                        callback(ExceptionResult(SignupState.EMAIL_VERIFIED, updated.email))
                    }
                }
                .addOnFailureListener {
                    auth.signOut()
                    callback(ExceptionResult(SignupState.NOT_LOGGED_IN))
                }
        }
    }
    // ─────────────────────────────────────────────────────────────────
}

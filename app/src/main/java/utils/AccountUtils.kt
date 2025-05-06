package com.example.deliverybox.utils

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/**
 * 계정 관련 유틸리티 클래스
 * 임시 계정 삭제 및 가입 상태 확인 등을 담당
 */
object AccountUtils {

    private const val TAG = "AccountUtils"
    private const val DELETION_TIMEOUT_SECONDS = 10L
    private const val UNVERIFIED_ACCOUNT_LIMIT_DAYS = 7L

    /**
     * 임시 계정을 안전하게 삭제하고 로그아웃 처리
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
                                    // 7일 이상 경과한 미인증 계정 삭제
                                    Log.d(TAG, "기간 만료된 미인증 계정 삭제: $uid (${elapsedDays}일 경과)")
                                    deleteUserAccount(currentUser, uid, callback)
                                } else {
                                    // 아직 유예 기간 내 미인증 계정
                                    Log.d(TAG, "미인증 계정 유예 기간 내: $uid (${elapsedDays}일 경과)")
                                    callback?.invoke()
                                }
                            } else {
                                // 생성 시간 정보 없음 - 삭제 대상
                                Log.w(TAG, "계정 생성 시간 정보 없음, 삭제: $uid")
                                deleteUserAccount(currentUser, uid, callback)
                            }
                        } else {
                            // 사용자 문서 없음 - 삭제 대상
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
            // 이미 인증된 계정
            Log.d(TAG, "인증된 계정, 삭제하지 않음: ${currentUser.email}")
            callback?.invoke()
        }
    }

    /**
     * 사용자 계정 및 데이터 삭제
     */
    private fun deleteUserAccount(user: FirebaseUser, uid: String, callback: (() -> Unit)?) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        try {
            db.collection("users").document(uid)
                .delete()
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d(TAG, "사용자 문서 삭제 성공: $uid")
                    } else {
                        Log.e(TAG, "문서 삭제 실패: ${it.exception?.message}")
                    }

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

                    if (totalCount == 0) {
                        Log.d(TAG, "삭제할 오래된 미인증 계정 없음")
                        onComplete?.invoke(0)
                        return@addOnSuccessListener
                    }

                    Log.d(TAG, "오래된 미인증 계정 ${totalCount}개 정리 시작")

                    for (doc in documents) {
                        doc.reference.delete()
                            .addOnSuccessListener {
                                deletedCount++
                                Log.d(TAG, "오래된 계정 정리 ${deletedCount}/${totalCount}: ${doc.id}")
                                if (deletedCount == totalCount) onComplete?.invoke(deletedCount)
                            }
                            .addOnFailureListener {
                                deletedCount++
                                Log.e(TAG, "계정 정리 실패 ${doc.id}: ${it.message}")
                                if (deletedCount == totalCount) onComplete?.invoke(deletedCount)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "일괄 정리 실패: ${e.message}")
                    onComplete?.invoke(0)
                }

        } catch (e: Exception) {
            Log.e(TAG, "오래된 계정 정리 오류: ${e.message}")
            onComplete?.invoke(0)
        }
    }

    /**
     * 현재 사용자 가입 상태 확인
     */
    fun checkSignupState(callback: (SignupState) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            callback(SignupState.NOT_LOGGED_IN)
            return
        }

        // 이메일 인증 확인
        if (!user.isEmailVerified) {
            // 미인증 계정은 NEW_ACCOUNT로 판단
            callback(SignupState.NEW_ACCOUNT)
            return
        }

        // 인증 완료된 계정에 대해 비밀번호 설정 여부 확인
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                val isPasswordSet = doc?.getBoolean("passwordSet") ?: false
                if (isPasswordSet) {
                    callback(SignupState.COMPLETED)
                } else {
                    callback(SignupState.EMAIL_VERIFIED)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "계정 상태 확인 실패: ${e.message}")
                callback(SignupState.NOT_LOGGED_IN)
            }
    }

    /**
     * 임시 계정 유효성 검사
     */
    fun checkTempAccountValidity(uid: String, callback: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val createdAt = doc.getTimestamp("createdAt")

                    if (createdAt != null) {
                        val now = System.currentTimeMillis()
                        val createdTime = createdAt.toDate().time
                        val elapsedDays = TimeUnit.MILLISECONDS.toDays(now - createdTime)

                        // 7일 이내면 유효한 계정으로 간주
                        callback(elapsedDays < UNVERIFIED_ACCOUNT_LIMIT_DAYS)
                    } else {
                        // 생성 시간 없으면 무효
                        callback(false)
                    }
                } else {
                    // 문서 없으면 무효
                    callback(false)
                }
            }
            .addOnFailureListener {
                // 조회 실패 시 무효
                callback(false)
            }
    }

    /**
     * 가입 상태 enum
     */
    enum class SignupState {
        NOT_LOGGED_IN,     // 로그인 필요
        NEW_ACCOUNT,       // 신규 계정 (이메일 인증 필요)
        EMAIL_VERIFIED,    // 이메일 인증 완료, 비밀번호 설정 필요
        COMPLETED          // 가입 완료
    }
}
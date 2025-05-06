package com.example.deliverybox.utils

import android.content.Context
import android.util.Log
import com.example.deliverybox.MyApplication
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

    // 회원가입 진행 상태 저장 (앱 내에서 일시적으로 사용)
    private var signupState: SignupState = SignupState.NOT_LOGGED_IN
    private var signupEmail: String? = null
    private var tempPassword: String? = null

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
                        .addOnFailureListener {
                            auth.signOut()
                            callback(SignupState.NOT_LOGGED_IN)
                        }
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
     * 아래 메소드로 완전히 교체됨
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

            // 수정된 부분: 이메일이 인증되지 않은 경우
            if (!updated.isEmailVerified) {
                // NOT_LOGGED_IN 상태로 변경하고 이메일 정보 제공
                callback(ExceptionResult(SignupState.NOT_LOGGED_IN, updated.email))
                return@addOnCompleteListener
            }

            // 이메일이 인증된 경우 비밀번호 설정 여부 확인
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

    /**
     * 회원가입 상태 저장
     * 아래 메소드로 완전히 교체됨
     */
    fun saveSignupState(state: SignupState, email: String?, password: String? = null) {
        try {
            signupState = state
            signupEmail = email
            tempPassword = password

            // 로그 추가 - 상태 저장 확인
            Log.d(TAG, "회원가입 상태 저장: $state, 이메일: $email")

            // SharedPreferences에도 백업 저장하여 앱 강제 종료 시에도 상태 유지
            val context = MyApplication.getAppContext()
            if (context != null) {
                val prefs = context.getSharedPreferences("signup_state", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("state", state.name)
                    putString("email", email)
                    // 비밀번호는 보안상 저장하지 않음
                    apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "상태 저장 중 오류: ${e.message}", e)
        }
    }

    /**
     * SharedPreferences에서 불러오는 복원 메소드
     */
    fun restoreSignupStateFromPrefs(context: Context): Triple<SignupState, String?, String?> {
        try {
            val prefs = context.getSharedPreferences("signup_state", Context.MODE_PRIVATE)
            val stateStr = prefs.getString("state", null)
            val email = prefs.getString("email", null)

            val state = if (stateStr != null) {
                try {
                    SignupState.valueOf(stateStr)
                } catch (e: Exception) {
                    SignupState.NOT_LOGGED_IN
                }
            } else {
                SignupState.NOT_LOGGED_IN
            }

            // 메모리 변수 업데이트
            signupState = state
            signupEmail = email

            return Triple(state, email, null)
        } catch (e: Exception) {
            Log.e(TAG, "상태 복원 중 오류: ${e.message}", e)
            return Triple(SignupState.NOT_LOGGED_IN, null, null)
        }
    }

    /**
     * 기존 restoreSignupState(callback) 메소드도 그대로 유지
     */
    fun restoreSignupState(callback: (SignupState, String?, String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            callback(SignupState.NOT_LOGGED_IN, null, null)
            return
        }

        if (signupEmail != null) {
            callback(signupState, signupEmail, tempPassword)
            return
        }

        currentUser.reload().addOnCompleteListener {
            val user = auth.currentUser
            if (user == null) {
                callback(SignupState.NOT_LOGGED_IN, null, null)
                return@addOnCompleteListener
            }

            if (!user.isEmailVerified) {
                callback(SignupState.NOT_LOGGED_IN, user.email, null)
                return@addOnCompleteListener
            }

            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { doc ->
                    val passwordSet = doc.getBoolean("passwordSet") ?: false
                    if (passwordSet) {
                        callback(SignupState.COMPLETED, user.email, null)
                    } else {
                        callback(SignupState.EMAIL_VERIFIED, user.email, null)
                    }
                }
                .addOnFailureListener {
                    callback(SignupState.NOT_LOGGED_IN, null, null)
                }
        }
    }

    /**
     * 앱 시작시 인증되지 않은 계정 정리 및 상태 확인 통합 메소드
     * 앱 처음 시작할 때 호출해야 함
     */
    fun handleAppStartup(context: Context, callback: (Boolean) -> Unit) {
        try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            if (currentUser == null) {
                Log.d(TAG, "로그인된 사용자 없음")
                callback(false)
                return
            }

            // 이미 로그인된 사용자의 상태 확인을 위해 새로고침
            currentUser.reload()
                .addOnSuccessListener {
                    val refreshedUser = auth.currentUser

                    if (refreshedUser == null) {
                        callback(false)
                        return@addOnSuccessListener
                    }

                    // 인증되지 않은 계정인 경우 정리
                    if (!refreshedUser.isEmailVerified) {
                        // Firebase DB에서 생성 시간 확인하여 유예 기간 이상 지난 계정은 삭제
                        checkAndCleanupTempAccount(callback)
                    } else {
                        // 세션 유효성 업데이트
                        SharedPrefsHelper.setLastLoginTime(context, System.currentTimeMillis())
                        callback(true)
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "사용자 정보 새로고침 실패: ${it.message}")
                    auth.signOut()
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "앱 시작 처리 중 오류: ${e.message}")
            callback(false)
        }
    }

    /**
     * 임시 계정 판단 및 정리 로직
     */
    private fun checkAndCleanupTempAccount(callback: (Boolean) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return

        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val createdAt = doc.getTimestamp("createdAt")
                        if (createdAt != null) {
                            val now = System.currentTimeMillis()
                            val created = createdAt.toDate().time
                            val daysPassed = TimeUnit.MILLISECONDS.toDays(now - created)

                            if (daysPassed >= UNVERIFIED_ACCOUNT_LIMIT_DAYS) {
                                user.delete().addOnCompleteListener {
                                    auth.signOut()
                                    callback(false)
                                }
                            } else {
                                callback(false)
                            }
                        } else {
                            callback(false)
                        }
                    } else {
                        user.delete().addOnCompleteListener {
                            auth.signOut()
                            callback(false)
                        }
                    }
                }
                .addOnFailureListener {
                    auth.signOut()
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "계정 확인 중 오류: ${e.message}")
            auth.signOut()
            callback(false)
        }
    }

    /**
     * 인증 완료 후 다음 단계로 이동
     * EmailVerificationActivity 내에서 사용
     */
    fun handleVerificationComplete(
        email: String,
        tempPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        auth.signInWithEmailAndPassword(email, tempPassword)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                db.collection("users").document(uid)
                    .update("emailVerified", true)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e ->
                        onError("상태 업데이트 실패: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                onError("로그인 실패: ${e.message}")
            }
    }
}

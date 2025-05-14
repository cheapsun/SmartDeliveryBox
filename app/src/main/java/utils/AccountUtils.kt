package com.example.deliverybox.utils

import android.content.Context
import android.util.Log
import com.example.deliverybox.MyApplication
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

object AccountUtils {

    private const val TAG = "AccountUtils"
    private const val DELETION_TIMEOUT_SECONDS = 10L
    private const val UNVERIFIED_ACCOUNT_LIMIT_DAYS = 7L

    // 회원가입 상태 저장
    enum class SignupState {
        NOT_LOGGED_IN,
        EMAIL_VERIFIED,
        COMPLETED
    }

    data class ExceptionResult(
        val state: SignupState,
        val email: String? = null
    )

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

        if (!currentUser.isEmailVerified) {
            try {
                val uid = currentUser.uid
                val db = FirebaseFirestore.getInstance()

                db.runTransaction { transaction ->
                    val userDoc = transaction.get(db.collection("users").document(uid))

                    if (!userDoc.exists()) {
                        Log.d(TAG, "사용자 문서 없음, 계정 삭제 진행")
                        return@runTransaction true
                    }

                    val createdAt = userDoc.getTimestamp("createdAt")
                    if (createdAt == null) {
                        Log.w(TAG, "계정 생성 시간 정보 없음, 삭제 진행")
                        return@runTransaction true
                    }

                    val now = System.currentTimeMillis()
                    val createdTime = createdAt.toDate().time
                    val elapsedDays = TimeUnit.MILLISECONDS.toDays(now - createdTime)

                    if (elapsedDays >= UNVERIFIED_ACCOUNT_LIMIT_DAYS) {
                        Log.d(TAG, "미인증 계정 유효기간 만료: $elapsedDays 일")
                        return@runTransaction true
                    } else {
                        Log.d(TAG, "미인증 계정 유효기간 만료: $elapsedDays 일")
                        return@runTransaction false
                    }
                }.addOnSuccessListener { shouldDelete ->
                    if (shouldDelete) {
                        deleteUserAccount(currentUser, uid) {
                            auth.signOut()
                            callback?.invoke()
                        }
                    } else {
                        auth.signOut()
                        callback?.invoke()
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "트랜잭션 실패: ${e.message}")
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
     * Firestore 문서 삭제 후 계정 삭제 - 트랜잭션 방식으로 개선
     */
    private fun deleteUserAccount(user: com.google.firebase.auth.FirebaseUser, uid: String, callback: (() -> Unit)?) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        try {
            db.runTransaction { transaction ->
                // 1. 사용자가 소유한 모든 박스의 참조 가져오기
                val userDoc = transaction.get(db.collection("users").document(uid))
                val boxIds = userDoc.get("boxIds") as? List<String> ?: emptyList()

                // 2. 각 박스에서 사용자 제거
                for (boxId in boxIds) {
                    val boxDoc = transaction.get(db.collection("boxes").document(boxId))
                    val members = boxDoc.get("members") as? Map<String, Any> ?: mapOf()
                    val sharedUserUids = boxDoc.get("sharedUserUids") as? List<String> ?: emptyList()

                    // 멤버에서 사용자 제거
                    val updatedMembers = members.toMutableMap()
                    updatedMembers.remove(uid)

                    // 공유 사용자 목록에서 제거
                    val updatedSharedUsers = sharedUserUids.filter { it != uid }

                    transaction.update(db.collection("boxes").document(boxId),
                        mapOf(
                            "members" to updatedMembers,
                            "sharedUserUids" to updatedSharedUsers
                        )
                    )

                    // 소유자가 삭제되는 경우 처리
                    if (boxDoc.getString("ownerUid") == uid && updatedMembers.isNotEmpty()) {
                        val newOwnerUid = updatedMembers.keys.first()
                        transaction.update(db.collection("boxes").document(boxId), "ownerUid", newOwnerUid)
                    }
                }

                // 3. 사용자 문서 삭제
                transaction.delete(db.collection("users").document(uid))

                return@runTransaction true
            }.addOnSuccessListener {
                // 4. Firebase 계정 삭제
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
            }.addOnFailureListener { e ->
                Log.e(TAG, "사용자 데이터 삭제 실패: ${e.message}")
                auth.signOut()
                callback?.invoke()
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
        try {
            val db = FirebaseFirestore.getInstance()
            val cutoff = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -UNVERIFIED_ACCOUNT_LIMIT_DAYS.toInt())
            }.time

            // 미인증 및 오래된 계정 쿼리
            val userQuery = db.collection("users")
                .whereEqualTo("emailVerified", false)
                .whereLessThan("createdAt", cutoff)

            userQuery.get().addOnSuccessListener { querySnapshot ->
                val userDocs = querySnapshot.documents
                if (userDocs.isEmpty()) {
                    Log.d(TAG, "정리할 미인증 계정이 없습니다")
                    onComplete?.invoke(0)
                    return@addOnSuccessListener
                }

                db.runTransaction { transaction ->
                    // 각 사용자에 대한 처리
                    for (userDoc in userDocs) {
                        val uid = userDoc.id

                        // 사용자가 소유한 박스 찾기
                        val boxIds = userDoc.get("boxIds") as? List<String> ?: emptyList()

                        // 각 박스에서 사용자 제거
                        for (boxId in boxIds) {
                            val boxRef = db.collection("boxes").document(boxId)
                            val boxDoc = transaction.get(boxRef)
                            if (!boxDoc.exists()) continue

                            // 멤버 및 공유 사용자 목록 업데이트
                            val members = boxDoc.get("members") as? Map<String, Any> ?: mapOf()
                            val sharedUserUids = boxDoc.get("sharedUserUids") as? List<String> ?: emptyList()

                            val updatedMembers = members.toMutableMap()
                            updatedMembers.remove(uid)

                            val updatedSharedUsers = sharedUserUids.filter { it != uid }

                            transaction.update(boxRef,
                                mapOf(
                                    "members" to updatedMembers,
                                    "sharedUserUids" to updatedSharedUsers
                                )
                            )
                        }

                        // 사용자 문서 삭제
                        transaction.delete(db.collection("users").document(uid))
                    }

                    // 트랜잭션 결과 반환
                    return@runTransaction userDocs.size
                }.addOnSuccessListener { count ->
                    Log.d(TAG, "$(count)개의 미인증 계정 정리 완료")
                    onComplete?.invoke(count)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "일괄 정리 실패: ${e.message}")
                    onComplete?.invoke(0)
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "오래된 계정 쿼리 오류: ${e.message}")
                onComplete?.invoke(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "오래된 계정 정리 오류: ${e.message}")
            onComplete?.invoke(0)
        }
    }

    /**
     * 로그아웃 및 세션 정리
     */
    fun logout(context: Context, callback: (() -> Unit)? = null) {
        try {
            // Firebase 로그아웃
            FirebaseAuth.getInstance().signOut()
            // 자동 로그인 설정 해제
            SharedPrefsHelper.setAutoLogin(context, false)
            // 세션 정보 삭제
            SharedPrefsHelper.clearLoginSession(context)
            Log.d(TAG, "로그아웃 성공")
            callback?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "로그아웃 중 오류: ${e.message}")
            callback?.invoke()
        }
    }

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
                callback(ExceptionResult(SignupState.NOT_LOGGED_IN, updated.email))
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

    /**
     * 회원가입 상태 저장
     */
    fun saveSignupState(state: SignupState, email: String?, tempPassword: String? = null) {
        try {
            Log.d(TAG, "회원가입 상태 저장: $state, 이메일: $email")

            val context = MyApplication.getAppContext()
            if (context != null) {
                val prefs = context.getSharedPreferences("signup_state", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("state", state.name)
                    putString("email", email)
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

            val state = stateStr?.let { runCatching { SignupState.valueOf(it) }.getOrNull() } ?: SignupState.NOT_LOGGED_IN

            return Triple(state, email, null)
        } catch (e: Exception) {
            Log.e(TAG, "상태 복원 중 오류: ${e.message}", e)
            return Triple(SignupState.NOT_LOGGED_IN, null, null)
        }
    }

    /**
     * 앱 시작시 인증되지 않은 계정 정리 및 상태 확인 통합 메소드
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

            currentUser.reload()
                .addOnSuccessListener {
                    val refreshedUser = auth.currentUser

                    if (refreshedUser == null) {
                        callback(false)
                        return@addOnSuccessListener
                    }

                    if (!refreshedUser.isEmailVerified) {
                        checkAndCleanupTempAccount(callback)
                    } else {
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
     * 안전한 토큰 생성
     */
    fun generateSecureToken(length: Int = 16): String {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
    }
}
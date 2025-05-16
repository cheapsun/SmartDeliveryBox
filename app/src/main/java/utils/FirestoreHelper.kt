package utils

import android.util.Log
import com.example.deliverybox.model.UserData
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.TimeUnit

object FirestoreHelper {

    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "FirestoreHelper"

    // Firestore 작업 타임아웃 (초)
    private const val FIRESTORE_TIMEOUT_SECONDS = 15L

    // 재시도 최대 횟수
    private const val MAX_RETRIES = 3

    /**
     * 사용자 문서 생성 - 오류 처리 및 재시도 로직 강화
     */
    fun createUserDocument(uid: String, email: String, onComplete: (Boolean) -> Unit) {
        createUserDocumentWithRetry(uid, email, 0, onComplete)
    }

    /**
     * 사용자 문서 생성 - 재시도 로직 포함
     */
    private fun createUserDocumentWithRetry(uid: String, email: String, retryCount: Int, onComplete: (Boolean) -> Unit) {
        if (retryCount >= MAX_RETRIES) {
            Log.e(TAG, "사용자 문서 생성 최대 재시도 횟수 초과: $uid")
            onComplete(false)
            return
        }

        val userData = hashMapOf(
            "email" to email,
            "createdAt" to FieldValue.serverTimestamp(),
            "nickname" to null,
            "isAdmin" to false,
            "emailVerified" to false,
            "passwordSet" to false,
            "lastLoginAt" to FieldValue.serverTimestamp(),
            "retryCount" to retryCount  // 디버깅용 재시도 카운트 저장
        )

        try {
            // 제한 시간 설정
            val docRef = db.collection("users").document(uid)
            val task = docRef.set(userData)

            try {
                // 제한 시간 내 작업 완료 시도
                Tasks.await(task, FIRESTORE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                Log.d(TAG, "사용자 문서 생성 성공: $uid")
                onComplete(true)
            } catch (e: Exception) {
                // 타임아웃 또는 다른 오류 발생 시 재시도
                Log.w(TAG, "사용자 문서 생성 실패, 재시도 중 (${retryCount + 1}/$MAX_RETRIES): $uid, 오류: ${e.message}")

                // 백오프 시간을 두고 재시도 (지수 백오프)
                val backoffDelay = (Math.pow(2.0, retryCount.toDouble()) * 1000).toLong()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    createUserDocumentWithRetry(uid, email, retryCount + 1, onComplete)
                }, backoffDelay)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore 접근 오류: ${e.message}")
            onComplete(false)
        }
    }

    /**
     * FCM 토큰 업데이트 - 콜백 추가 및 오류 처리 개선
     */
    fun updateFcmToken(uid: String, token: String, onComplete: ((Boolean) -> Unit)? = null) {
        try {
            val updates = mapOf(
                "fcmToken" to token,
                "tokenUpdatedAt" to FieldValue.serverTimestamp(),
                "deviceInfo" to getDeviceInfo()  // 기기 정보 추가
            )

            db.collection("users").document(uid)
                .update(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM 토큰 업데이트 성공: $uid")
                    onComplete?.invoke(true)
                }
                .addOnFailureListener { e ->
                    // 문서가 없으면 생성 시도
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                        db.collection("users").document(uid)
                            .set(updates, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d(TAG, "FCM 토큰 업데이트 (문서 생성) 성공: $uid")
                                onComplete?.invoke(true)
                            }
                            .addOnFailureListener { innerE ->
                                Log.e(TAG, "FCM 토큰 저장 실패 (문서 생성 시도): $uid, 오류: ${innerE.message}")
                                onComplete?.invoke(false)
                            }
                    } else {
                        Log.e(TAG, "FCM 토큰 업데이트 실패: $uid, 오류: ${e.message}")
                        onComplete?.invoke(false)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "FCM 토큰 업데이트 중 예외 발생: ${e.message}")
            onComplete?.invoke(false)
        }
    }

    /**
     * 사용자 데이터 가져오기 - 오류 처리 및 캐시 기능 추가
     */
    fun getUserData(uid: String, callback: (UserData?) -> Unit) {
        try {
            // 오프라인 캐시 사용 활성화
            val docRef = db.collection("users").document(uid)
            docRef.get(com.google.firebase.firestore.Source.CACHE)
                .addOnSuccessListener { cacheDoc ->
                    // 캐시에서 데이터 가져오기 성공
                    if (cacheDoc != null && cacheDoc.exists()) {
                        Log.d(TAG, "사용자 데이터 캐시에서 로드: $uid")
                        parseAndReturnUserData(cacheDoc, uid, callback)

                        // 백그라운드에서 최신 데이터 가져오기 시도
                        refreshUserDataInBackground(uid)
                    } else {
                        // 캐시에 없으면 서버에서 시도
                        docRef.get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener { serverDoc ->
                                if (serverDoc != null && serverDoc.exists()) {
                                    Log.d(TAG, "사용자 데이터 서버에서 로드: $uid")
                                    parseAndReturnUserData(serverDoc, uid, callback)
                                } else {
                                    Log.e(TAG, "사용자 문서 없음: $uid")
                                    callback(null)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "사용자 데이터 서버 로드 실패: $uid, 오류: ${e.message}")
                                callback(null)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    // 캐시 접근 실패 시 서버에서 직접 시도
                    Log.w(TAG, "사용자 데이터 캐시 접근 실패: $uid, 서버 시도 중, 오류: ${e.message}")

                    docRef.get(com.google.firebase.firestore.Source.SERVER)
                        .addOnSuccessListener { serverDoc ->
                            if (serverDoc != null && serverDoc.exists()) {
                                parseAndReturnUserData(serverDoc, uid, callback)
                            } else {
                                callback(null)
                            }
                        }
                        .addOnFailureListener { serverE ->
                            Log.e(TAG, "사용자 데이터 서버 로드 실패: $uid, 오류: ${serverE.message}")
                            callback(null)
                        }
                }
        } catch (e: Exception) {
            Log.e(TAG, "사용자 데이터 조회 중 예외 발생: ${e.message}")
            callback(null)
        }
    }

    /**
     * 사용자 문서에서 데이터 파싱
     */
    private fun parseAndReturnUserData(document: com.google.firebase.firestore.DocumentSnapshot, uid: String, callback: (UserData?) -> Unit) {
        try {
            val email = document.getString("email") ?: ""
            val displayName = document.getString("nickname") ?: ""
            val photoUrl = document.getString("photoUrl") ?: ""
            val boxIds = document.get("boxIds") as? List<String> ?: emptyList()
            val isPasswordSet = document.getBoolean("passwordSet") ?: false

            // boxAliases 추가
            val boxAliases = document.get("boxAliases") as? Map<String, String> ?: emptyMap()

            // mainBoxId 추가
            val mainBoxId = document.getString("mainBoxId") ?: ""

            // 로그인 시간 업데이트 (백그라운드)
            db.collection("users").document(uid)
                .update("lastLoginAt", FieldValue.serverTimestamp())
                .addOnFailureListener { e ->
                    Log.w(TAG, "로그인 시간 업데이트 실패: $uid, 오류: ${e.message}")
                }

            // 수정된 UserData 객체 생성 (모든 인자 전달 또는 명명된 매개변수 사용)
            callback(UserData(
                uid = uid,
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                boxIds = boxIds,
                boxAliases = boxAliases,
                mainBoxId = mainBoxId,
                isPasswordSet = isPasswordSet
            ))
        } catch (e: Exception) {
            Log.e(TAG, "사용자 데이터 파싱 실패: $uid, 오류: ${e.message}")
            callback(null)
        }
    }

    /**
     * 백그라운드에서 최신 사용자 데이터 가져오기
     */
    private fun refreshUserDataInBackground(uid: String) {
        try {
            db.collection("users").document(uid)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener { document ->
                    Log.d(TAG, "사용자 데이터 백그라운드 갱신 성공: $uid")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "사용자 데이터 백그라운드 갱신 실패: $uid, 오류: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "사용자 데이터 백그라운드 갱신 중 예외 발생: ${e.message}")
        }
    }

    /**
     * 이메일 인증 상태 업데이트
     */
    fun updateEmailVerification(uid: String, verified: Boolean, onComplete: (Boolean) -> Unit) {
        try {
            val updates = mapOf(
                "emailVerified" to verified,
                "emailVerifiedAt" to if (verified) FieldValue.serverTimestamp() else null
            )

            db.collection("users").document(uid)
                .update(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "이메일 인증 상태 업데이트 성공: $uid, verified: $verified")
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "이메일 인증 상태 업데이트 실패: $uid, 오류: ${e.message}")
                    onComplete(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "이메일 인증 상태 업데이트 중 예외 발생: ${e.message}")
            onComplete(false)
        }
    }

    /**
     * 임시 비밀번호 해시 검증
     * 보안을 위해 비밀번호 자체가 아닌 해시값만 저장하고 검증
     */
    fun verifyTempPasswordHash(uid: String, tempPassword: String, callback: (Boolean) -> Unit) {
        try {
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val storedHash = doc?.getLong("tempPasswordHash")

                    if (storedHash != null && storedHash.toInt() == tempPassword.hashCode()) {
                        callback(true)
                    } else {
                        callback(false)
                    }
                }
                .addOnFailureListener {
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "임시 비밀번호 검증 실패: ${e.message}")
            callback(false)
        }
    }

    /**
     * 현재 기기 정보 가져오기
     */
    private fun getDeviceInfo(): Map<String, String> {
        return try {
            mapOf(
                "manufacturer" to android.os.Build.MANUFACTURER,
                "model" to android.os.Build.MODEL,
                "androidVersion" to android.os.Build.VERSION.RELEASE,
                "sdkVersion" to android.os.Build.VERSION.SDK_INT.toString()
            )
        } catch (e: Exception) {
            mapOf("error" to "기기 정보 가져오기 실패: ${e.message}")
        }
    }
}
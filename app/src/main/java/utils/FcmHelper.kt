package com.example.deliverybox.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * FCM 관련 유틸리티 클래스
 * 디바이스 토큰 관리 및 오류 처리
 */
object FcmHelper {

    private const val TAG = "FcmHelper"
    private const val TOKEN_TIMEOUT_SECONDS = 10L

    /**
     * FCM 토큰을 안전하게 가져와서 Firestore에 저장
     * @param uid 사용자 UID
     * @param onComplete 완료 콜백 (성공 여부)
     */
    fun updateFcmTokenSafely(uid: String, onComplete: ((Boolean) -> Unit)? = null) {
        try {
            // 제한 시간 있는 토큰 요청
            val tokenTask = FirebaseMessaging.getInstance().token

            try {
                // 토큰 가져오기 시도 (제한 시간 내)
                val token = Tasks.await(tokenTask, TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)

                // 토큰이 null이거나 비어있지 않은 경우에만 저장
                if (!token.isNullOrEmpty()) {
                    FirestoreHelper.updateFcmToken(uid, token) { success ->
                        if (success) {
                            Log.d(TAG, "FCM 토큰 업데이트 성공: $uid")
                        } else {
                            Log.e(TAG, "FCM 토큰 저장 실패: $uid")
                        }
                        onComplete?.invoke(success)
                    }
                } else {
                    Log.e(TAG, "FCM 토큰이 비어있음: $uid")
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM 토큰 가져오기 실패: ${e.message}")

                // 백그라운드에서 재시도 (선택적)
                scheduleTokenRetry(uid)

                onComplete?.invoke(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "FCM 초기화 실패: ${e.message}")
            onComplete?.invoke(false)
        }
    }

    /**
     * 토큰 갱신 실패 시 나중에 재시도 (WorkManager 사용 가능)
     */
    private fun scheduleTokenRetry(uid: String) {
        // 여기서 WorkManager를 사용하여 나중에 재시도 로직 구현 가능
        // 현재는 로그만 남김
        Log.d(TAG, "FCM 토큰 업데이트 나중에 재시도 예정: $uid")
    }
}
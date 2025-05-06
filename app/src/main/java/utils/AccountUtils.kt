package com.example.deliverybox.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * 계정 관련 유틸리티 클래스
 * 임시 계정 삭제 및 로그아웃 처리를 담당
 */
object AccountUtils {

    private const val TAG = "AccountUtils"

    /**
     * 임시 계정을 삭제하고 로그아웃 처리
     * @param callback 삭제 완료 후 호출될 콜백 (선택)
     */
    fun deleteTempAccountAndSignOut(callback: (() -> Unit)? = null) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // 현재 인증되지 않은 계정이라면 삭제
            if (!currentUser.isEmailVerified) {
                currentUser.delete()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "임시 계정 삭제 성공: ${currentUser.email}")
                        } else {
                            Log.e(TAG, "임시 계정 삭제 실패: ${task.exception?.message}")
                        }
                        // 계정 삭제 시도 후 항상 로그아웃
                        auth.signOut()
                        callback?.invoke()
                    }
            } else {
                // 이미 인증된 계정은 삭제하지 않고 로그아웃만 수행
                auth.signOut()
                callback?.invoke()
            }
        } else {
            // 로그인된 계정이 없으면 콜백만 호출
            callback?.invoke()
        }
    }
}
package com.example.deliverybox.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * 계정 관련 유틸리티 클래스
 * 가입 프로세스 흐름에서 발생하는 예외 케이스 및 임시 계정 관리
 */
object AccountUtils {

    private const val TAG = "AccountUtils"
    private const val PASSWORD_SET_FLAG = "password_set"

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
                Log.d(TAG, "미인증 계정 발견: ${currentUser.email}, 삭제 진행...")
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
                // 이미 인증된 계정은 삭제하지 않음
                Log.d(TAG, "인증된 계정 발견: ${currentUser.email}, 계정 유지")
                callback?.invoke()
            }
        } else {
            // 로그인된 계정이 없으면 콜백만 호출
            Log.d(TAG, "로그인된 계정 없음")
            callback?.invoke()
        }
    }

    /**
     * 비밀번호 설정 여부를 저장
     * 비밀번호 업데이트 후 앱이 종료되는 경우를 대비해 Firestore에 설정 완료 여부 저장
     */
    fun setPasswordFlag(uid: String, isSet: Boolean, callback: ((Boolean) -> Unit)? = null) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .update(PASSWORD_SET_FLAG, isSet)
            .addOnSuccessListener {
                Log.d(TAG, "비밀번호 설정 플래그 업데이트: $isSet")
                callback?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "비밀번호 설정 플래그 업데이트 실패: ${e.message}")
                callback?.invoke(false)
            }
    }

    /**
     * 비밀번호 설정 여부 확인
     * @return 비밀번호가 설정되었으면 true, 아니면 false 또는 null(오류)
     */
    fun checkPasswordSet(uid: String, callback: (Boolean?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val isPasswordSet = document.getBoolean(PASSWORD_SET_FLAG) ?: false
                    Log.d(TAG, "비밀번호 설정 여부: $isPasswordSet")
                    callback(isPasswordSet)
                } else {
                    Log.d(TAG, "사용자 문서 없음")
                    callback(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "비밀번호 설정 확인 실패: ${e.message}")
                callback(null)
            }
    }

    /**
     * 가입 상태 확인 및 적절한 화면으로 분기
     * 앱 시작 시 사용자의 인증 상태를 확인하고 적절한 화면 이동
     */
    fun checkSignupState(callback: (SignupState) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            // 로그인된 사용자 없음
            Log.d(TAG, "로그인된 사용자 없음 -> 로그인 화면으로")
            callback(SignupState.NOT_LOGGED_IN)
            return
        }

        // 사용자는 있지만 이메일 인증이 안된 경우
        if (!currentUser.isEmailVerified) {
            Log.d(TAG, "사용자 이메일 미인증 -> 계정 삭제 후 로그인 화면으로")
            // 미인증 계정 삭제 후 로그인 화면으로
            deleteTempAccountAndSignOut {
                callback(SignupState.NOT_LOGGED_IN)
            }
            return
        }

        // 이메일은 인증되었지만 비밀번호 설정 여부 확인
        checkPasswordSet(currentUser.uid) { isPasswordSet ->
            if (isPasswordSet == true) {
                // 비밀번호 설정 완료 - 정상 사용자
                Log.d(TAG, "완전한 가입 완료 -> 메인 화면으로")
                callback(SignupState.COMPLETED)
            } else {
                // 비밀번호 설정이 안되었거나 확인 실패
                Log.d(TAG, "이메일 인증 완료, 비밀번호 미설정 -> 비밀번호 설정 화면으로")
                callback(SignupState.EMAIL_VERIFIED)
            }
        }
    }

    /**
     * 가입 상태를 정의하는 열거형
     */
    enum class SignupState {
        NOT_LOGGED_IN,   // 로그인된 사용자 없음 -> 로그인 화면으로
        EMAIL_VERIFIED,  // 이메일 인증만 완료 -> 비밀번호 설정 화면으로
        COMPLETED        // 가입 완료 -> 메인 화면으로
    }
}
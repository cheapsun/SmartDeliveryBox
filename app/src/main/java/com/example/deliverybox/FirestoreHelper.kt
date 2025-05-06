package com.example.deliverybox.utils

import android.util.Log
import com.example.deliverybox.model.UserData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Firestore 데이터베이스 관련 유틸리티 클래스
 * 사용자 정보 관리 및 저장 담당
 */
object FirestoreHelper {

    private const val TAG = "FirestoreHelper"
    private const val PASSWORD_SET_FLAG = "password_set"

    /**
     * 새로운 사용자 문서 생성
     * 회원가입 시 호출됨
     */
    fun createUserDocument(uid: String, email: String, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        val userData = hashMapOf(
            "email" to email,
            "createdAt" to FieldValue.serverTimestamp(),
            "nickname" to null,
            "isAdmin" to false,
            PASSWORD_SET_FLAG to false, // 비밀번호 설정 여부 플래그 추가
            "boxIds" to emptyList<String>()
        )

        db.collection("users").document(uid)
            .set(userData)
            .addOnSuccessListener {
                Log.d(TAG, "사용자 문서 생성 성공: $uid")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "사용자 문서 생성 실패: ${e.message}")
                onComplete(false)
            }
    }

    /**
     * FCM 토큰 업데이트 (푸시 알림용)
     */
    fun updateFcmToken(uid: String, token: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM 토큰 업데이트 성공")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "FCM 토큰 업데이트 실패: ${e.message}")
            }
    }

    /**
     * 비밀번호 설정 완료 플래그 업데이트
     * @param uid 사용자 ID
     * @param isSet 비밀번호 설정 여부 (true: 설정됨, false: 설정 안됨)
     * @param callback 결과 콜백 (성공/실패)
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
     * @param uid 사용자 ID
     * @param callback 결과 콜백 (true: 설정됨, false: 설정 안됨, null: 오류)
     */
    fun checkPasswordSet(uid: String, callback: (Boolean?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val isPasswordSet = document.getBoolean(PASSWORD_SET_FLAG) ?: false
                    Log.d(TAG, "비밀번호 설정 여부 확인: $isPasswordSet")
                    callback(isPasswordSet)
                } else {
                    Log.d(TAG, "사용자 문서가 존재하지 않음")
                    callback(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "비밀번호 설정 여부 확인 실패: ${e.message}")
                callback(null)
            }
    }

    /**
     * 사용자 데이터 가져오기
     * @param uid 사용자 ID
     * @param callback 결과 콜백 (UserData 객체 또는 null)
     */
    fun getUserData(uid: String, callback: (UserData?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val email = document.getString("email") ?: ""
                    val displayName = document.getString("nickname") ?: ""
                    val photoUrl = document.getString("photoUrl") ?: ""
                    val boxIds = document.get("boxIds") as? List<String> ?: emptyList()

                    // 비밀번호 설정 여부 확인
                    val isPasswordSet = document.getBoolean(PASSWORD_SET_FLAG) ?: false

                    Log.d(TAG, "사용자 데이터 불러오기 성공: $email")
                    val userData = UserData(uid, email, displayName, photoUrl, boxIds)
                    userData.isPasswordSet = isPasswordSet

                    callback(userData)
                } else {
                    Log.e(TAG, "사용자 문서가 존재하지 않음")
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "사용자 데이터 로드 실패: ${e.message}")
                callback(null)
            }
    }

    /**
     * 회원 탈퇴 시 사용자 데이터 삭제
     * @param uid 사용자 ID
     * @param callback 결과 콜백 (성공/실패)
     */
    fun deleteUserData(uid: String, callback: ((Boolean) -> Unit)? = null) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "사용자 데이터 삭제 성공: $uid")
                callback?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "사용자 데이터 삭제 실패: ${e.message}")
                callback?.invoke(false)
            }
    }
}
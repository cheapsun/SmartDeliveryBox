package com.example.deliverybox.utils

import com.example.deliverybox.model.UserData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreHelper {

    private val db = FirebaseFirestore.getInstance()

    fun createUserDocument(uid: String, email: String, onComplete: (Boolean) -> Unit) {
        val userData = hashMapOf(
            "email" to email,
            "createdAt" to FieldValue.serverTimestamp(),
            "nickname" to null,
            "isAdmin" to false
        )

        db.collection("users").document(uid)
            .set(userData)
            .addOnSuccessListener {
                onComplete(true)
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    fun updateFcmToken(uid: String, token: String) {
        db.collection("users").document(uid)
            .update("fcmToken", token)
            .addOnSuccessListener {
                // 성공
            }
            .addOnFailureListener {
                // 실패
            }
    }

    // ✅ 추가: Firestore에서 사용자 데이터 읽어오기
    fun getUserData(uid: String, callback: (UserData?) -> Unit) {
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val email = document.getString("email") ?: ""
                    val displayName = document.getString("nickname") ?: ""
                    val photoUrl = document.getString("photoUrl") ?: ""
                    val boxIds = document.get("boxIds") as? List<String> ?: emptyList()

                    callback(UserData(uid, email, displayName, photoUrl, boxIds))
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }
}

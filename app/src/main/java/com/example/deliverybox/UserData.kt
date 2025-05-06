package com.example.deliverybox.model

data class UserData(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String,
    val boxIds: List<String>,
    var isPasswordSet: Boolean = false // 비밀번호 설정 여부 필드 추가
)

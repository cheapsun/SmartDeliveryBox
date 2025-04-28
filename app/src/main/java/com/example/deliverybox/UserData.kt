package com.example.deliverybox.model

data class UserData(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String,
    val boxIds: List<String>
)

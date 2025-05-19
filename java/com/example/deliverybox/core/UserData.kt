package com.example.deliverybox.core

data class UserData(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String,
    val boxIds: List<String> = emptyList(),
    val boxAliases: Map<String, String> = emptyMap(),
    val mainBoxId: String = "",
    var isPasswordSet: Boolean = false
)
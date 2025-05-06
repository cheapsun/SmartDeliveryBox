package com.example.deliverybox.model

data class BoxInfo(
    val boxId: String,
    val alias: String,
    val boxName: String = "" // ✅ 기본값을 설정하여 오류 방지
)

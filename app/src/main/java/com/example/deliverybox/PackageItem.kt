package com.example.deliverybox

data class PackageItem(
    val trackingNumber: String = "",
    val courierCompany: String = "",
    val info: String = "",
    val category: String = "",
    val origin: String = "",
    val createdAt: Long = 0L, // 🔹 삭제 제한 조건에 필요
    val docId: String = ""    // 🔹 문서 수정/삭제에 필요
)

package com.example.deliverybox.model

// Firestore의 택배 문서 데이터
data class Package(
    val trackingNumber: String = "",
    val info: String = "",
    val courierCompany: String = "",
    val category: String = "",
    val origin: String = "",
    val destination: String = "",
    val createdDate: String = "",
    val createdTime: String = "",
    val createdAt: Long = 0L,
    val valid: Boolean = true
)

// 문서 ID + 실제 데이터 묶는 구조 (리스트/편집용)
data class PackageItem(
    val id: String,
    val data: Package
)

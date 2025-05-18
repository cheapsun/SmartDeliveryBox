package com.example.deliverybox.delivery.adapter

import com.example.deliverybox.delivery.PackageInfo

/**
 * 패키지 리스트 아이템 데이터 클래스
 */
data class PackageItem(
    val id: String,
    val data: PackageInfo
)
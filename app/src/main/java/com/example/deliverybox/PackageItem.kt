package com.example.deliverybox.model

// Firestore 문서 내용만 담는 순수 데이터 클래스
data class Package(
    val trackingNumber: String = "",       // 운송장 번호
    val info: String = "",                 // 택배 설명 (예: '테스트00')
    val courierCompany: String = "",       // 택배사 이름 (예: 'CJ대한통운')
    val category: String = "",             // 상품 분류 (예: '생활용품')
    val origin: String = "",               // 발송지 (예: '광주 조선대')
    val destination: String = "",          // 도착지 (예: '광주광역시 동구 필문대로 309')
    val createdDate: String = "",          // 등록 날짜 (예: '2025-04-13')
    val createdTime: String = "",          // 등록 시간 (예: '15:05:31')
    val createdAt: Long = 0L,              // 등록 타임스탬프 (정렬 및 삭제 시간 판단용)
    val valid: Boolean = true              // 유효 여부 (사용된 QR 여부 등)
)

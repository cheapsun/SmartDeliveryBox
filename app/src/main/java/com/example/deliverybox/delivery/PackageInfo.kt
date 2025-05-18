package com.example.deliverybox.delivery

import com.example.deliverybox.R

// 배송 상태 enum
enum class DeliveryStatus {
    REGISTERED,      // 등록됨
    PICKED_UP,       // 접수
    IN_TRANSIT,      // 이동중
    OUT_FOR_DELIVERY,// 배송출발
    IN_BOX,          // 보관중 (택배함)
    DELIVERED        // 수령완료
}

// 상태 한글 표시
fun DeliveryStatus.toKorean(): String = when (this) {
    DeliveryStatus.REGISTERED -> "등록"
    DeliveryStatus.PICKED_UP -> "접수"
    DeliveryStatus.IN_TRANSIT -> "배송중"
    DeliveryStatus.OUT_FOR_DELIVERY -> "배송출발"
    DeliveryStatus.IN_BOX -> "보관중"
    DeliveryStatus.DELIVERED -> "수령완료"
}

// 상태별 색상 정보
fun DeliveryStatus.getColorRes(): Int = when (this) {
    DeliveryStatus.REGISTERED -> R.color.gray_500
    DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT -> R.color.primary_blue
    DeliveryStatus.OUT_FOR_DELIVERY, DeliveryStatus.IN_BOX -> R.color.warning
    DeliveryStatus.DELIVERED -> R.color.success
}

// 상태별 이모지 반환 함수
fun DeliveryStatus.getEmoji(): String = when (this) {
    DeliveryStatus.REGISTERED        -> "📦"
    DeliveryStatus.PICKED_UP         -> "🏪"
    DeliveryStatus.IN_TRANSIT        -> "🚚"
    DeliveryStatus.OUT_FOR_DELIVERY  -> "🚀"
    DeliveryStatus.IN_BOX            -> "📮"
    DeliveryStatus.DELIVERED         -> "✅"
}

// 배송 단계 정보
data class DeliveryStep(
    val id: String = "",
    val stepType: String = "",
    val description: String = "",
    val location: String? = null,
    val timestamp: Long = 0L,
    val isCompleted: Boolean = false
)

/**
 * 택배 정보 통합 모델
 * 기존 PackageItem.kt의 PackageInfo 클래스를 확장
 */
data class PackageInfo(
    val id: String = "",
    val trackingNumber: String = "",
    val courierCompany: String = "",
    val itemName: String? = null,
    val category: String = "",
    val memo: String? = null,

    // 배송 정보
    val origin: String = "",
    val destination: String = "",
    val status: DeliveryStatus = DeliveryStatus.REGISTERED,
    val deliverySteps: List<DeliveryStep> = emptyList(),

    // 메타데이터
    val registeredAt: Long = System.currentTimeMillis(),
    val registeredBy: String = "",
    val boxId: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),

    // 배송 완료 정보
    val isDelivered: Boolean = false,
    val deliveredAt: Long? = null,
    val estimatedDelivery: Long? = null,

    // 자동 등록 관련
    val isAutoDetected: Boolean = false,
    val confidence: Float = 1.0f
) {
    // 편의 메서드들
    fun isInProgress(): Boolean = status in listOf(
        DeliveryStatus.REGISTERED,
        DeliveryStatus.PICKED_UP,
        DeliveryStatus.IN_TRANSIT,
        DeliveryStatus.OUT_FOR_DELIVERY
    )

    fun isCompleted(): Boolean = status in listOf(
        DeliveryStatus.DELIVERED,
        DeliveryStatus.IN_BOX
    )

    fun getFormattedTrackingNumber(): String {
        return trackingNumber.chunked(4).joinToString(" ")
    }
}

/**
 * Firebase Firestore와 호환되는 Package 데이터 클래스
 * PackageEditActivity에서 사용
 */
data class Package(
    val trackingNumber: String = "",
    val info: String = "",
    val courierCompany: String = "",
    val category: String = "",
    val origin: String = "",
    val createdAt: Long = 0L,
    val id: String = "",
    val registeredBy: String = ""
) {
    // Firestore 요구사항: 매개변수 없는 생성자 필요
    constructor() : this("", "", "", "", "", 0L, "", "")
}
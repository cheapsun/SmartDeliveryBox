package com.example.deliverybox.delivery

// 배송 상태 enum
enum class DeliveryStatus {
    REGISTERED,      // 등록됨
    PICKED_UP,       // 접수
    IN_TRANSIT,      // 이동중
    OUT_FOR_DELIVERY,// 배송출발
    IN_BOX,          // 보관중 (택배함)
    DELIVERED        // 수령완료
}

// 상태값을 한글로 표시하는 확장 함수
fun DeliveryStatus.toKorean(): String = when (this) {
    DeliveryStatus.REGISTERED -> "등록"
    DeliveryStatus.PICKED_UP -> "접수"
    DeliveryStatus.IN_TRANSIT -> "배송중"
    DeliveryStatus.OUT_FOR_DELIVERY -> "배송출발"
    DeliveryStatus.IN_BOX -> "보관중"
    DeliveryStatus.DELIVERED -> "수령완료"
}

// 배송 단계 정보
data class DeliveryStep(
    val stepType: String,
    val description: String,
    val location: String? = null,
    val timestamp: Long,
    val isCompleted: Boolean = false
)

// 개선된 Package 모델
data class PackageInfo(
    val id: String = "",
    val trackingNumber: String = "",
    val courierCompany: String = "",
    val itemName: String? = null,
    val category: String = "",
    val origin: String = "",
    val destination: String = "",

    // 상태 관리
    val status: DeliveryStatus = DeliveryStatus.REGISTERED,
    val deliverySteps: List<DeliveryStep> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),

    // 자동 등록 관련
    val isAutoDetected: Boolean = false,
    val confidence: Float = 1.0f,

    // 메타데이터
    val registeredAt: Long = System.currentTimeMillis(),
    val registeredBy: String = "",
    val boxId: String = "",
    val isDelivered: Boolean = false,
    val deliveredAt: Long? = null,

    // 예상 배송 정보
    val estimatedDelivery: Long? = null,
    val memo: String? = null
)


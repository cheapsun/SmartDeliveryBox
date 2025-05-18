package com.example.deliverybox.delivery

data class DeliveryApiResponse(
    val trackingNumber: String?,
    val carrier: CarrierInfo?,
    val state: StateInfo?,
    val progresses: List<ProgressInfo>?,
    val estimatedDelivery: String?
)

data class CarrierInfo(
    val id: String?,
    val name: String?,
    val tel: String?
)

data class StateInfo(
    val id: String?,
    val text: String?
)

data class ProgressInfo(
    val time: String?,
    val status: StatusInfo?,
    val location: LocationInfo?,
    val description: String?
)

data class StatusInfo(
    val id: String?,
    val text: String?
)

data class LocationInfo(
    val name: String?,
    val detail: String?
)

// 내부 사용 모델
data class TrackingInfo(
    val trackingNumber: String,
    val courierCompany: String,
    val currentStatus: DeliveryStatus,
    val deliverySteps: List<DeliveryStep>,
    val estimatedDelivery: Long?,
    val lastUpdated: Long
)
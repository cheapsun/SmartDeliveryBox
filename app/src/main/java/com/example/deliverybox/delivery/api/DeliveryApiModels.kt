package com.example.deliverybox.delivery.api

import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.DeliveryStep

// API 응답 모델 (모든 클래스명에 Api 접두사 추가로 중복 방지)
data class ApiTrackingResponse(
    val trackingNumber: String?,
    val carrier: ApiCarrierInfo?,
    val state: ApiStateInfo?,
    val progresses: List<ApiProgressInfo>?,
    val estimatedDelivery: String?
)

data class ApiCarrierInfo(
    val id: String?,
    val name: String?,
    val tel: String?
)

data class ApiStateInfo(
    val id: String?,
    val text: String?
)

data class ApiProgressInfo(
    val time: String?,
    val status: ApiStatusInfo?,
    val location: ApiLocationInfo?,
    val description: String?
)

data class ApiStatusInfo(
    val id: String?,
    val text: String?
)

data class ApiLocationInfo(
    val name: String?,
    val detail: String?
)

// 내부 사용 모델
data class InternalTrackingInfo(
    val trackingNumber: String,
    val courierCompany: String,
    val currentStatus: DeliveryStatus,
    val deliverySteps: List<DeliveryStep>,
    val estimatedDelivery: Long?,
    val lastUpdated: Long
)

// API 요청 모델
data class ApiTrackingRequest(
    val trackingNumber: String,
    val carrierCode: String? = null
)

// API 에러 응답 모델
data class ApiErrorResponse(
    val code: String?,
    val message: String?,
    val details: String?
)
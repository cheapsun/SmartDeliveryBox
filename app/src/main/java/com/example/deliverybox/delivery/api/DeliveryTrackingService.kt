package com.example.deliverybox.delivery.api

import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.DeliveryStep
import com.example.deliverybox.delivery.TrackingInfo
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale

interface DeliveryTrackingService {
    suspend fun trackPackage(courierCode: String, trackingNumber: String): Result<TrackingInfo>
    suspend fun getSupportedCouriers(): List<CourierInfo>
}

class DeliveryTrackerService(
    private val apiKey: String = "" // ✅ BuildConfig 대신 기본값 설정
) : DeliveryTrackingService {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://apis.tracker.delivery/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(DeliveryTrackerApi::class.java)

    // 택배사 코드 매핑
    companion object {
        private val COURIER_CODES = mapOf(
            "CJ대한통운" to "kr.cjlogistics",
            "한진택배" to "kr.hanjin",
            "롯데택배" to "kr.lotte",
            "우체국택배" to "kr.epost",
            "로젠택배" to "kr.logen",
            "GS택배" to "kr.gspostbox",
            "대신택배" to "kr.daesin",
            "경동택배" to "kr.kdexp"
        )
    }

    override suspend fun trackPackage(
        courierCode: String,
        trackingNumber: String
    ): Result<TrackingInfo> {
        return try {
            // 1. API 호출
            val response = api.track(
                carrierId = COURIER_CODES[courierCode] ?: courierCode,
                trackId = trackingNumber
            )

            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!

                // 2. 응답 데이터 변환
                val trackingInfo = mapToTrackingInfo(apiResponse)
                Result.success(trackingInfo)
            } else {
                Result.failure(Exception("배송 조회 실패: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSupportedCouriers(): List<CourierInfo> {
        return COURIER_CODES.map { (name, code) ->
            CourierInfo(
                code = code,
                name = name,
                isSupported = true
            )
        }
    }

    private fun mapToTrackingInfo(apiResponse: DeliveryApiResponse): TrackingInfo {
        // 배송 단계 변환
        val steps = apiResponse.progresses?.map { progress ->
            DeliveryStep(
                id = "step_${progress.hashCode()}", // ✅ id 추가
                stepType = mapStatusToStepType(progress.status?.text ?: ""),
                description = progress.description ?: "",
                location = buildLocationString(progress.location),
                timestamp = parseDateTime(progress.time),
                isCompleted = true
            )
        }?.sortedBy { it.timestamp } ?: emptyList()

        val currentStatus = determineCurrentStatus(steps)

        return TrackingInfo(
            currentStatus = currentStatus,
            deliverySteps = steps,
            lastUpdated = System.currentTimeMillis(),
            estimatedDelivery = apiResponse.estimatedDelivery?.let { parseDateTime(it) },
            trackingUrl = null // ✅ 기본값 설정
        )
    }

    // 상태 텍스트를 열거형으로 변환
    private fun mapStatusToStepType(status: String): String {
        return when {
            status.contains("접수") || status.contains("운송장 발급") -> "REGISTERED"
            status.contains("상차") || status.contains("수거") -> "PICKED_UP"
            status.contains("간선상차") || status.contains("이동중") -> "IN_TRANSIT"
            status.contains("배달출발") || status.contains("배송출발") -> "OUT_FOR_DELIVERY"
            status.contains("배달완료") || status.contains("배송완료") -> "DELIVERED"
            else -> "REGISTERED"
        }
    }

    // 현재 배송 상태 결정
    private fun determineCurrentStatus(steps: List<DeliveryStep>): DeliveryStatus {
        if (steps.isEmpty()) return DeliveryStatus.REGISTERED

        val latestStep = steps.maxByOrNull { it.timestamp }
        return when (latestStep?.stepType) {
            "REGISTERED" -> DeliveryStatus.REGISTERED
            "PICKED_UP" -> DeliveryStatus.PICKED_UP
            "IN_TRANSIT" -> DeliveryStatus.IN_TRANSIT
            "OUT_FOR_DELIVERY" -> DeliveryStatus.OUT_FOR_DELIVERY
            "DELIVERED" -> DeliveryStatus.DELIVERED
            else -> DeliveryStatus.REGISTERED
        }
    }

    // 위치 정보 문자열 생성
    private fun buildLocationString(location: LocationInfo?): String {
        return when {
            location?.name != null && location.detail != null ->
                "${location.name} ${location.detail}"
            location?.name != null -> location.name
            else -> ""
        }
    }

    // 날짜 시간 파싱
    private fun parseDateTime(timeString: String?): Long {
        if (timeString.isNullOrEmpty()) return System.currentTimeMillis()

        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            format.parse(timeString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}

// API 인터페이스
interface DeliveryTrackerApi {
    @GET("carriers/{carrierId}/tracks/{trackId}")
    suspend fun track(
        @Path("carrierId") carrierId: String,
        @Path("trackId") trackId: String
    ): Response<DeliveryApiResponse>
}

// ✅ 응답 데이터 클래스들
data class DeliveryApiResponse(
    val trackingNumber: String?,
    val carrier: CarrierInfo?,
    val progresses: List<ProgressInfo>?,
    val estimatedDelivery: String?
)

data class CarrierInfo(
    val id: String?,
    val name: String?,
    val tel: String?
)

data class ProgressInfo(
    val time: String?,
    val status: StatusInfo?,
    val description: String?,
    val location: LocationInfo?
)

data class StatusInfo(
    val id: String?,
    val text: String?
)

data class LocationInfo(
    val name: String?,
    val detail: String?
)

data class CourierInfo(
    val code: String,
    val name: String,
    val isSupported: Boolean
)
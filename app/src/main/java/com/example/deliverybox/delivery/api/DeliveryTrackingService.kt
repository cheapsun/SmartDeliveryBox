package com.example.deliverybox.delivery.api

import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.DeliveryStep
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
    private val apiKey: String = BuildConfig.DELIVERY_TRACKER_API_KEY
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

    private fun mapToTrackingInfo(apiResponse: DeliveryApiResponse): TrackingInfo {
        // 배송 단계 변환
        val steps = apiResponse.progresses?.map { progress ->
            DeliveryStep(
                stepType = mapStatusToStepType(progress.status?.text ?: ""),
                description = progress.description ?: "",
                location = buildLocationString(progress.location),
                timestamp = parseDateTime(progress.time),
                isCompleted = true
            )
        }?.sortedBy { it.timestamp } ?: emptyList()

        val currentStatus = determineCurrentStatus(steps)

        return TrackingInfo(
            trackingNumber = apiResponse.trackingNumber ?: "",
            courierCompany = apiResponse.carrier?.name ?: "",
            currentStatus = currentStatus,
            deliverySteps = steps,
            estimatedDelivery = apiResponse.estimatedDelivery?.let { parseDateTime(it) },
            lastUpdated = System.currentTimeMillis()
        )
    }

    // 상태 텍스트를 열거형으로 변환
    private fun mapStatusToStepType(status: String): String {
        return when {
            status.contains("접수") || status.contains("운송장 발급") -> "ORDER_PLACED"
            status.contains("상차") || status.contains("수거") -> "PICKED_UP"
            status.contains("간선상차") || status.contains("이동중") -> "IN_TRANSIT"
            status.contains("배달출발") || status.contains("배송출발") -> "OUT_FOR_DELIVERY"
            status.contains("배달완료") || status.contains("배송완료") -> "DELIVERED"
            else -> "UNKNOWN"
        }
    }

    // 현재 배송 상태 결정
    private fun determineCurrentStatus(steps: List<DeliveryStep>): DeliveryStatus {
        if (steps.isEmpty()) return DeliveryStatus.REGISTERED

        val latestStep = steps.maxByOrNull { it.timestamp }
        return when (latestStep?.stepType) {
            "ORDER_PLACED" -> DeliveryStatus.PICKED_UP
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
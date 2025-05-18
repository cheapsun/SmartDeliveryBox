package com.example.deliverybox.delivery

/**
 * 택배 추적 정보 데이터 클래스
 */
data class TrackingInfo(
    // 현재 배송 상태
    val currentStatus: DeliveryStatus,

    // 배송 단계 목록
    val deliverySteps: List<DeliveryStep>,

    // 마지막 업데이트 시간
    val lastUpdated: Long,

    // 예상 배송일 (옵션)
    val estimatedDelivery: Long? = null,

    // 택배사 추적 URL (옵션)
    val trackingUrl: String? = null,

    // 추가 정보
    val additionalInfo: String? = null,

    // 배송 완료 여부
    val isDelivered: Boolean = currentStatus == DeliveryStatus.DELIVERED,

    // 진행률 (0-100)
    val progressPercentage: Int = calculateProgress(currentStatus)
) {
    companion object {
        /**
         * 배송 상태에 따른 진행률 계산
         */
        private fun calculateProgress(status: DeliveryStatus): Int {
            return when (status) {
                DeliveryStatus.REGISTERED -> 10
                DeliveryStatus.PICKED_UP -> 25
                DeliveryStatus.IN_TRANSIT -> 50
                DeliveryStatus.OUT_FOR_DELIVERY -> 75
                DeliveryStatus.IN_BOX -> 90
                DeliveryStatus.DELIVERED -> 100
            }
        }

        /**
         * 빈 추적 정보 생성
         */
        fun empty(): TrackingInfo {
            return TrackingInfo(
                currentStatus = DeliveryStatus.REGISTERED,
                deliverySteps = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )
        }

        /**
         * PackageInfo로부터 TrackingInfo 생성
         */
        fun fromPackageInfo(packageInfo: PackageInfo): TrackingInfo {
            return TrackingInfo(
                currentStatus = packageInfo.status,
                deliverySteps = packageInfo.deliverySteps,
                lastUpdated = packageInfo.lastUpdated,
                estimatedDelivery = packageInfo.estimatedDelivery,
                trackingUrl = generateTrackingUrl(packageInfo),
                isDelivered = packageInfo.isDelivered
            )
        }

        /**
         * 택배사별 추적 URL 생성
         */
        private fun generateTrackingUrl(packageInfo: PackageInfo): String? {
            val trackingNumber = packageInfo.trackingNumber

            return when (packageInfo.courierCompany.lowercase().replace(" ", "")) {
                "cj대한통운", "cj", "cjlogistics" ->
                    "https://www.cjlogistics.com/ko/tool/parcel/tracking?gnbInvcNo=$trackingNumber"

                "한진택배", "한진", "hanjin" ->
                    "https://www.hanjin.co.kr/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2=$trackingNumber"

                "롯데택배", "롯데", "lotte" ->
                    "https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=$trackingNumber"

                "로젠택배", "로젠", "logen" ->
                    "https://www.ilogen.com/web/personal/trace/$trackingNumber"

                "우체국택배", "우체국", "koreapost" ->
                    "https://tracking.epost.go.kr/추적결과.jsp?sid1=$trackingNumber"

                "대신택배", "대신", "daesin" ->
                    "https://www.ds3211.co.kr/freight/internalFreightSearch.kor?billno=$trackingNumber"

                "경동택배", "경동", "kdexp" ->
                    "https://kdexp.com/service/delivery/delivery_result.asp?barcode=$trackingNumber"

                "천일택배", "천일", "chunil" ->
                    "http://www.chunil.co.kr/HTrace/HTrace.jsp?transno=$trackingNumber"

                "gspostbox", "gs" ->
                    "https://www.gspostbox.kr/m/trace/$trackingNumber"

                else -> null
            }
        }
    }

    /**
     * 배송 완료까지 남은 단계 수
     */
    fun getRemainingSteps(): Int {
        return deliverySteps.count { !it.isCompleted }
    }

    /**
     * 다음 예상 단계 반환
     */
    fun getNextStep(): DeliveryStep? {
        return deliverySteps.firstOrNull { !it.isCompleted }
    }

    /**
     * 최근 완료된 단계 반환
     */
    fun getLatestCompletedStep(): DeliveryStep? {
        return deliverySteps.lastOrNull { it.isCompleted }
    }

    /**
     * 예상 배송까지 남은 시간 (밀리초)
     */
    fun getTimeUntilDelivery(): Long? {
        return estimatedDelivery?.let { estimated ->
            (estimated - System.currentTimeMillis()).coerceAtLeast(0)
        }
    }

    /**
     * 배송이 지연되었는지 확인
     */
    fun isDelayed(): Boolean {
        return estimatedDelivery?.let { estimated ->
            System.currentTimeMillis() > estimated && !isDelivered
        } ?: false
    }

    /**
     * 상태별 메시지 반환
     */
    fun getStatusMessage(): String {
        return when (currentStatus) {
            DeliveryStatus.REGISTERED -> "택배가 등록되었습니다"
            DeliveryStatus.PICKED_UP -> "택배사에서 수거를 완료했습니다"
            DeliveryStatus.IN_TRANSIT -> "택배가 배송 중입니다"
            DeliveryStatus.OUT_FOR_DELIVERY -> "배송기사가 배송을 시작했습니다"
            DeliveryStatus.IN_BOX -> "택배함에 안전하게 보관되었습니다"
            DeliveryStatus.DELIVERED -> "배송이 완료되었습니다"
        }
    }
}
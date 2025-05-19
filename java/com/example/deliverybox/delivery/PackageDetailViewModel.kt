package com.example.deliverybox.delivery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deliverybox.domain.repositories.PackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PackageDetailViewModel @Inject constructor(
    private val packageRepository: PackageRepository
) : ViewModel() {

    // 패키지 정보
    private val _packageInfo = MutableLiveData<PackageInfo?>()
    val packageInfo: LiveData<PackageInfo?> = _packageInfo

    // 추적 정보
    private val _trackingInfo = MutableLiveData<TrackingInfo?>()
    val trackingInfo: LiveData<TrackingInfo?> = _trackingInfo

    // UI 상태
    private val _uiState = MutableLiveData<PackageDetailUiState>(PackageDetailUiState.Idle)
    val uiState: LiveData<PackageDetailUiState> = _uiState

    // 작업 결과 (성공/실패, 메시지)
    private val _operationResult = MutableLiveData<Pair<Boolean, String>?>()
    val operationResult: LiveData<Pair<Boolean, String>?> = _operationResult

    // 현재 패키지 및 박스 ID
    private var currentBoxId: String = ""
    private var currentPackageId: String = ""

    /**
     * 패키지 상세 정보 로드
     */
    fun loadPackageDetail(boxId: String, packageId: String) {
        currentBoxId = boxId
        currentPackageId = packageId

        _uiState.value = PackageDetailUiState.Loading

        viewModelScope.launch {
            try {
                // 패키지 기본 정보 로드
                val packageInfo = packageRepository.getPackageById(boxId, packageId)

                if (packageInfo != null) {
                    _packageInfo.value = packageInfo

                    // 추적 정보도 함께 로드
                    loadTrackingInfo(packageInfo)

                    _uiState.value = PackageDetailUiState.Success()
                } else {
                    _uiState.value = PackageDetailUiState.Error("패키지 정보를 찾을 수 없습니다.")
                }
            } catch (e: Exception) {
                _uiState.value = PackageDetailUiState.Error("패키지 정보 로드 실패: ${e.message}")
            }
        }
    }

    /**
     * 추적 정보 로드 (내부용)
     */
    private suspend fun loadTrackingInfo(packageInfo: PackageInfo) {
        try {
            // 배송 스텝 정보가 있다면 TrackingInfo 생성
            if (packageInfo.deliverySteps.isNotEmpty()) {
                val trackingInfo = TrackingInfo(
                    currentStatus = packageInfo.status,
                    deliverySteps = packageInfo.deliverySteps,
                    lastUpdated = packageInfo.lastUpdated,
                    estimatedDelivery = packageInfo.estimatedDelivery,
                    trackingUrl = generateTrackingUrl(packageInfo)
                )
                _trackingInfo.value = trackingInfo
            } else {
                // 기본 추적 정보 생성
                _trackingInfo.value = createDefaultTrackingInfo(packageInfo)
            }
        } catch (e: Exception) {
            // 추적 정보 로드 실패시에도 기본 정보는 표시
            _trackingInfo.value = createDefaultTrackingInfo(packageInfo)
        }
    }

    /**
     * 추적 정보 새로고침
     */
    fun refreshTrackingInfo() {
        if (currentBoxId.isEmpty() || currentPackageId.isEmpty()) return

        _uiState.value = PackageDetailUiState.Loading

        viewModelScope.launch {
            try {
                // 실제 배송 추적 API 호출 (5단계에서 구현 예정)
                val result = packageRepository.refreshTrackingInfo(currentBoxId, currentPackageId)

                result.fold(
                    onSuccess = { updatedPackageInfo ->
                        _packageInfo.value = updatedPackageInfo
                        loadTrackingInfo(updatedPackageInfo)
                        _uiState.value = PackageDetailUiState.Success("추적 정보가 업데이트되었습니다.")
                    },
                    onFailure = { exception ->
                        // 새로고침 실패시 기존 정보 유지하고 오류 메시지만 표시
                        _operationResult.value = Pair(false, "추적 정보 새로고침 실패: ${exception.message}")
                        _uiState.value = PackageDetailUiState.Success()
                    }
                )
            } catch (e: Exception) {
                _operationResult.value = Pair(false, "추적 정보 새로고침 실패: ${e.message}")
                _uiState.value = PackageDetailUiState.Success()
            }
        }
    }

    /**
     * 수령 확인 처리
     */
    fun markAsReceived() {
        if (currentBoxId.isEmpty() || currentPackageId.isEmpty()) return

        viewModelScope.launch {
            try {
                val result = packageRepository.markAsDelivered(
                    boxId = currentBoxId,
                    packageId = currentPackageId,
                    deliveredAt = System.currentTimeMillis()
                )

                result.fold(
                    onSuccess = {
                        // 패키지 정보 다시 로드하여 상태 업데이트
                        loadPackageDetail(currentBoxId, currentPackageId)
                        _operationResult.value = Pair(true, "수령 확인이 완료되었습니다.")
                    },
                    onFailure = { exception ->
                        _operationResult.value = Pair(false, "수령 확인 실패: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                _operationResult.value = Pair(false, "수령 확인 실패: ${e.message}")
            }
        }
    }

    /**
     * 패키지 삭제
     */
    fun deletePackage() {
        if (currentBoxId.isEmpty() || currentPackageId.isEmpty()) return

        viewModelScope.launch {
            try {
                val result = packageRepository.deletePackage(currentBoxId, currentPackageId)

                result.fold(
                    onSuccess = {
                        _operationResult.value = Pair(true, "패키지가 삭제되었습니다.")
                    },
                    onFailure = { exception ->
                        _operationResult.value = Pair(false, "패키지 삭제 실패: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                _operationResult.value = Pair(false, "패키지 삭제 실패: ${e.message}")
            }
        }
    }

    /**
     * 패키지 상태 업데이트
     */
    fun updatePackageStatus(newStatus: DeliveryStatus) {
        if (currentBoxId.isEmpty() || currentPackageId.isEmpty()) return

        viewModelScope.launch {
            try {
                val result = packageRepository.updatePackageStatus(currentBoxId, currentPackageId, newStatus)

                result.fold(
                    onSuccess = {
                        // 상태 업데이트 후 정보 다시 로드
                        loadPackageDetail(currentBoxId, currentPackageId)
                        _operationResult.value = Pair(true, "상태가 업데이트되었습니다.")
                    },
                    onFailure = { exception ->
                        _operationResult.value = Pair(false, "상태 업데이트 실패: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                _operationResult.value = Pair(false, "상태 업데이트 실패: ${e.message}")
            }
        }
    }

    /**
     * 작업 결과 클리어
     */
    fun clearOperationResult() {
        _operationResult.value = null
    }

    /**
     * 기본 추적 정보 생성
     */
    private fun createDefaultTrackingInfo(packageInfo: PackageInfo): TrackingInfo {
        val defaultSteps = listOf(
            DeliveryStep(
                id = "step_1",
                stepType = "REGISTERED",
                description = "택배 접수",
                location = packageInfo.origin ?: "발송지",
                timestamp = packageInfo.registeredAt,
                isCompleted = true
            ),
            DeliveryStep(
                id = "step_2",
                stepType = "IN_TRANSIT",
                description = "배송 중",
                location = "집배 센터",
                timestamp = packageInfo.registeredAt + (24 * 60 * 60 * 1000), // 1일 후 가정
                isCompleted = packageInfo.status.ordinal >= DeliveryStatus.IN_TRANSIT.ordinal
            ),
            DeliveryStep(
                id = "step_3",
                stepType = "OUT_FOR_DELIVERY",
                description = "배송 출발",
                location = packageInfo.destination ?: "배송지 근처",
                timestamp = packageInfo.registeredAt + (48 * 60 * 60 * 1000), // 2일 후 가정
                isCompleted = packageInfo.status.ordinal >= DeliveryStatus.OUT_FOR_DELIVERY.ordinal
            ),
            DeliveryStep(
                id = "step_4",
                stepType = if (packageInfo.status == DeliveryStatus.IN_BOX) "IN_BOX" else "DELIVERED",
                description = if (packageInfo.status == DeliveryStatus.IN_BOX) "택배함 보관" else "배송 완료",
                location = packageInfo.destination ?: "배송 완료",
                timestamp = packageInfo.deliveredAt ?: (packageInfo.registeredAt + (72 * 60 * 60 * 1000)), // 3일 후 가정
                isCompleted = packageInfo.status == DeliveryStatus.DELIVERED || packageInfo.status == DeliveryStatus.IN_BOX
            )
        )

        return TrackingInfo(
            currentStatus = packageInfo.status,
            deliverySteps = defaultSteps,
            lastUpdated = packageInfo.lastUpdated,
            estimatedDelivery = packageInfo.estimatedDelivery,
            trackingUrl = generateTrackingUrl(packageInfo)
        )
    }

    /**
     * 추적 URL 생성
     */
    private fun generateTrackingUrl(packageInfo: PackageInfo): String? {
        return when (packageInfo.courierCompany.lowercase()) {
            "cj대한통운", "cj" -> "https://www.cjlogistics.com/ko/tool/parcel/tracking?gnbInvcNo=${packageInfo.trackingNumber}"
            "한진택배", "한진" -> "https://www.hanjin.co.kr/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2=${packageInfo.trackingNumber}"
            "롯데택배", "롯데" -> "https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=${packageInfo.trackingNumber}"
            "로젠택배", "로젠" -> "https://www.ilogen.com/web/personal/trace/${packageInfo.trackingNumber}"
            else -> null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 리소스 정리가 필요한 경우 여기에 구현
    }
}
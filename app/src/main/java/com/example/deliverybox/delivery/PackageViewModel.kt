package com.example.deliverybox.delivery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PackageViewModel(
    private val packageRepository: PackageRepository,
    private val trackingService: DeliveryTrackingService
) : ViewModel() {

    private val _packageInfo = MutableLiveData<PackageInfo>()
    val packageInfo: LiveData<PackageInfo> = _packageInfo

    private val _trackingInfo = MutableLiveData<TrackingInfo?>()
    val trackingInfo: LiveData<TrackingInfo?> = _trackingInfo

    private val _uiState = MutableLiveData<PackageDetailUiState>()
    val uiState: LiveData<PackageDetailUiState> = _uiState

    fun loadPackageDetail(boxId: String, packageId: String) {
        viewModelScope.launch {
            _uiState.value = PackageDetailUiState.Loading

            try {
                val packageInfo = packageRepository.getPackageById(boxId, packageId)

                if (packageInfo != null) {
                    _packageInfo.value = packageInfo

                    // 배송 추적 정보 로드
                    loadTrackingInfo(packageInfo)

                    _uiState.value = PackageDetailUiState.Success
                } else {
                    _uiState.value = PackageDetailUiState.Error("패키지를 찾을 수 없습니다")
                }
            } catch (e: Exception) {
                _uiState.value = PackageDetailUiState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    private suspend fun loadTrackingInfo(packageInfo: PackageInfo) {
        trackingService.trackPackage(
            packageInfo.courierCompany,
            packageInfo.trackingNumber
        ).fold(
            onSuccess = { trackingInfo ->
                _trackingInfo.value = trackingInfo

                // 상태가 변경된 경우 업데이트
                if (trackingInfo.currentStatus != packageInfo.status) {
                    updatePackageStatus(packageInfo, trackingInfo)
                }
            },
            onFailure = { error ->
                Log.w("PackageDetailViewModel", "추적 정보 로드 실패: ${error.message}")
                _trackingInfo.value = null
            }
        )
    }

    private suspend fun updatePackageStatus(packageInfo: PackageInfo, trackingInfo: TrackingInfo) {
        val updatedPackage = packageInfo.copy(
            status = trackingInfo.currentStatus,
            deliverySteps = trackingInfo.deliverySteps,
            lastUpdated = System.currentTimeMillis()
        )

        packageRepository.updatePackage(packageInfo.boxId, updatedPackage)
            .onSuccess {
                _packageInfo.value = updatedPackage
            }
    }

    fun refreshTrackingInfo() {
        val packageInfo = _packageInfo.value ?: return

        viewModelScope.launch {
            _uiState.value = PackageDetailUiState.Loading

            try {
                loadTrackingInfo(packageInfo)
                _uiState.value = PackageDetailUiState.Success
            } catch (e: Exception) {
                _uiState.value = PackageDetailUiState.Error("새로고침 실패: ${e.message}")
            }
        }
    }

    fun markAsReceived() {
        val packageInfo = _packageInfo.value ?: return

        viewModelScope.launch {
            try {
                val updatedPackage = packageInfo.copy(
                    status = DeliveryStatus.DELIVERED,
                    deliveredAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis()
                )

                packageRepository.updatePackage(packageInfo.boxId, updatedPackage)
                    .onSuccess {
                        _packageInfo.value = updatedPackage
                    }
                    .onFailure {
                        _uiState.value = PackageDetailUiState.Error("수령 확인 실패")
                    }
            } catch (e: Exception) {
                _uiState.value = PackageDetailUiState.Error(e.message ?: "수령 확인 실패")
            }
        }
    }
}

sealed class PackageDetailUiState {
    object Loading : PackageDetailUiState()
    object Success : PackageDetailUiState()
    data class Error(val message: String) : PackageDetailUiState()
}
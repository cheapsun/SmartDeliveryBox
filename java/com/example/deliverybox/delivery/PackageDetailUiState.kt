package com.example.deliverybox.delivery

/**
 * 패키지 상세 화면의 UI 상태를 나타내는 sealed class
 */
sealed class PackageDetailUiState {
    /**
     * 초기 상태 (아무 작업도 하지 않은 상태)
     */
    object Idle : PackageDetailUiState()

    /**
     * 로딩 중 상태
     */
    object Loading : PackageDetailUiState()

    /**
     * 성공 상태
     * @param message 선택적 성공 메시지
     */
    data class Success(val message: String? = null) : PackageDetailUiState()

    /**
     * 오류 상태
     * @param message 오류 메시지
     */
    data class Error(val message: String) : PackageDetailUiState()
}
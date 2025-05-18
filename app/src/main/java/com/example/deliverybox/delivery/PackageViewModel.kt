package com.example.deliverybox.delivery

import androidx.lifecycle.*
import com.example.deliverybox.domain.usecases.GetPackagesUseCase
import com.example.deliverybox.domain.repositories.PackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PackageViewModel @Inject constructor(
    private val getPackagesUseCase: GetPackagesUseCase,
    private val repository: PackageRepository
) : ViewModel() {

    // UI 상태 정의 (내부 sealed class)
    sealed class UiState {
        object Loading : UiState()
        object Empty : UiState()
        data class Success(val packages: List<PackageInfo>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    // 검색어와 필터 상태 보관
    private var currentQuery: String = ""
    private var currentFilter: DeliveryStatus? = null

    // 전체 로드 함수
    fun loadPackages(boxId: String) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            getPackagesUseCase(boxId)
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "알 수 없는 오류") }
                .collect { list ->
                    if (list.isEmpty()) {
                        _uiState.value = UiState.Empty
                    } else {
                        _uiState.value = UiState.Success(list)
                    }
                }
        }
    }

    // 상태 변경
    fun updateStatus(boxId: String, packageId: String, status: DeliveryStatus) {
        viewModelScope.launch {
            repository.updatePackageStatus(boxId, packageId, status.name)
            loadPackages(boxId)
        }
    }

    // 삭제
    fun deletePackage(boxId: String, packageId: String) {
        viewModelScope.launch {
            repository.deletePackage(boxId, packageId)
            loadPackages(boxId)
        }
    }

    // 검색어 설정
    fun search(query: String, boxId: String) {
        currentQuery = query
        loadPackages(boxId)
    }

    // 필터 설정
    fun filter(status: DeliveryStatus?, boxId: String) {
        currentFilter = status
        loadPackages(boxId)
    }

    // 리스트 필터링 (내부 혹은 Fragment에서 처리 가능)
    fun applyFilters(list: List<PackageInfo>): List<PackageInfo> = list
        .filter { item ->
            (currentFilter == null || item.status == currentFilter) &&
                    (currentQuery.isBlank() ||
                            item.trackingNumber.contains(currentQuery, ignoreCase = true) ||
                            (item.itemName?.contains(currentQuery, ignoreCase = true) == true))
        }
}


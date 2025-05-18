package com.example.deliverybox.domain.repositories

import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.PackageInfo
import com.example.deliverybox.model.PackageInfo
import kotlinx.coroutines.flow.Flow


typealias ResultCallback<T> = Result<T>

/**
 * Domain 계층에서 사용되는 Repository 인터페이스
 */
interface PackageRepository {

    /** 특정 박스(boxId) 내 모든 패키지를 Flow 형태로 반환 */
    fun getAllPackages(boxId: String): Flow<List<PackageInfo>>

    suspend fun getPackageById(boxId: String, packageId: String): PackageInfo?

    suspend fun savePackage(info: PackageInfo): ResultCallback<String>

    /** 배송 상태 업데이트 */
    suspend fun updatePackageStatus(boxId: String, packageId: String, newStatus: String)

    /** 패키지 삭제 처리 */
    suspend fun deletePackage(boxId: String, packageId: String)

    suspend fun searchPackages(query: String): List<PackageInfo>

    suspend fun getPackagesByStatus(status: DeliveryStatus): List<PackageInfo>

    suspend fun syncWithRemote(): ResultCallback<Unit> // 선택사항
}

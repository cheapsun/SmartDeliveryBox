package com.example.deliverybox.domain.repositories

import com.example.deliverybox.delivery.PackageInfo
import com.example.deliverybox.delivery.DeliveryStatus
import kotlinx.coroutines.flow.Flow

/**
 * 택배 정보 Repository 인터페이스
 * Clean Architecture의 Domain Layer
 */
interface PackageRepository {

    // 조회 관련
    fun getAllPackages(boxId: String): Flow<List<PackageInfo>>
    suspend fun getPackageById(boxId: String, packageId: String): PackageInfo?
    suspend fun searchPackages(boxId: String, query: String): List<PackageInfo>
    suspend fun getPackagesByStatus(boxId: String, status: DeliveryStatus): List<PackageInfo>
    suspend fun getActivePackages(boxId: String): List<PackageInfo>

    // 저장/수정 관련
    suspend fun savePackage(packageInfo: PackageInfo): Result<String>
    suspend fun updatePackage(packageInfo: PackageInfo): Result<Unit>
    suspend fun updatePackageStatus(boxId: String, packageId: String, status: DeliveryStatus): Result<Unit>
    suspend fun markAsDelivered(boxId: String, packageId: String, deliveredAt: Long = System.currentTimeMillis()): Result<Unit>

    // 삭제 관련
    suspend fun deletePackage(boxId: String, packageId: String): Result<Unit>
    suspend fun hidePackage(boxId: String, packageId: String): Result<Unit>

    // 배송 추적 관련
    suspend fun updateDeliverySteps(boxId: String, packageId: String, steps: List<com.example.deliverybox.delivery.DeliveryStep>): Result<Unit>
    suspend fun refreshTrackingInfo(boxId: String, packageId: String): Result<PackageInfo>

    // 동기화 관련
    suspend fun syncWithRemote(boxId: String): Result<Unit>
    suspend fun invalidateCache(boxId: String)

    // 검증 관련
    suspend fun findByTrackingNumber(boxId: String, trackingNumber: String): PackageInfo?
    suspend fun validatePackageData(packageInfo: PackageInfo): Result<Unit>
}
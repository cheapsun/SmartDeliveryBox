package com.example.deliverybox.data.repositories

import com.example.deliverybox.domain.repositories.PackageRepository
import com.example.deliverybox.delivery.PackageInfo
import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.DeliveryStep
import com.example.deliverybox.data.local.dao.PackageDao
import com.example.deliverybox.data.local.entity.PackageEntity
import com.example.deliverybox.data.local.mapper.toPackageInfo
import com.example.deliverybox.data.local.mapper.toEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val packageDao: PackageDao
) : PackageRepository {

    // 조회 관련
    override fun getAllPackages(boxId: String): Flow<List<PackageInfo>> {
        return packageDao.getAllPackages(boxId).map { entities ->
            entities.map { convertEntityToPackageInfo(it) }
        }
    }

    override suspend fun getPackageById(boxId: String, packageId: String): PackageInfo? {
        return try {
            // Local First (Room DB에서 조회)
            packageDao.getPackageById(packageId)?.let { entity ->
                return convertEntityToPackageInfo(entity)
            }

            // Fallback to Remote (Firebase에서 조회)
            val doc = firestore.collection("boxes")
                .document(boxId)
                .collection("packages")
                .document(packageId)
                .get()
                .await()

            if (doc.exists()) {
                doc.data?.let { data ->
                    val packageInfo = convertFirebaseDataToPackageInfo(data, doc.id, boxId)
                    // Cache to local
                    packageDao.insertOrUpdatePackage(convertPackageInfoToEntity(packageInfo))
                    packageInfo
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun searchPackages(boxId: String, query: String): List<PackageInfo> {
        return try {
            val searchQuery = "%$query%"
            packageDao.searchPackages(searchQuery)
                .filter { it.boxId == boxId }
                .map { convertEntityToPackageInfo(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPackagesByStatus(boxId: String, status: DeliveryStatus): List<PackageInfo> {
        return try {
            packageDao.getPackagesByStatus(status.name)
                .filter { it.boxId == boxId }
                .map { convertEntityToPackageInfo(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getActivePackages(boxId: String): List<PackageInfo> {
        return try {
            val activeStatuses = listOf("REGISTERED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY")

            // Local First - 각 상태별로 조회해서 합치기
            val localPackages = mutableListOf<PackageEntity>()
            for (status in activeStatuses) {
                localPackages.addAll(packageDao.getPackagesByStatus(status))
            }

            val filteredPackages = localPackages.filter { it.boxId == boxId }

            if (filteredPackages.isNotEmpty()) {
                return filteredPackages.map { convertEntityToPackageInfo(it) }
            }

            // Fallback to Remote
            val packages = mutableListOf<PackageInfo>()
            for (status in activeStatuses) {
                val docs = firestore.collection("boxes")
                    .document(boxId)
                    .collection("packages")
                    .whereEqualTo("status", status)
                    .get()
                    .await()

                docs.documents.forEach { doc ->
                    doc.data?.let { data ->
                        val packageInfo = convertFirebaseDataToPackageInfo(data, doc.id, boxId)
                        packages.add(packageInfo)
                        // Cache to local
                        packageDao.insertOrUpdatePackage(convertPackageInfoToEntity(packageInfo))
                    }
                }
            }

            packages
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 저장/수정 관련
    override suspend fun savePackage(packageInfo: PackageInfo): Result<String> {
        return try {
            val saveData = convertPackageInfoToFirebaseMap(packageInfo)

            // Save to Remote
            firestore.collection("boxes")
                .document(packageInfo.boxId)
                .collection("packages")
                .document(packageInfo.id)
                .set(saveData)
                .await()

            // Save to Local
            packageDao.insertOrUpdatePackage(convertPackageInfoToEntity(packageInfo))

            Result.success(packageInfo.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePackage(packageInfo: PackageInfo): Result<Unit> {
        return try {
            val updateData = convertPackageInfoToFirebaseMap(packageInfo)

            // Update Remote
            firestore.collection("boxes")
                .document(packageInfo.boxId)
                .collection("packages")
                .document(packageInfo.id)
                .set(updateData)
                .await()

            // Update Local
            packageDao.insertOrUpdatePackage(convertPackageInfoToEntity(packageInfo))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePackageStatus(
        boxId: String,
        packageId: String,
        status: DeliveryStatus
    ): Result<Unit> {
        return try {
            val updateData = mapOf(
                "status" to status.name,
                "lastUpdated" to System.currentTimeMillis()
            )

            // Update Remote
            firestore.collection("boxes")
                .document(boxId)
                .collection("packages")
                .document(packageId)
                .update(updateData)
                .await()

            // Update Local - 기존 엔티티를 조회해서 상태 업데이트 후 다시 저장
            packageDao.getPackageById(packageId)?.let { entity ->
                val updatedEntity = entity.copy(
                    status = status.name
                    // TODO: lastUpdated 필드가 PackageEntity에 있다면 추가
                    // lastUpdated = System.currentTimeMillis()
                )
                packageDao.insertOrUpdatePackage(updatedEntity)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markAsDelivered(boxId: String, packageId: String, deliveredAt: Long): Result<Unit> {
        return try {
            val updateData = mapOf(
                "status" to DeliveryStatus.DELIVERED.name,
                "isDelivered" to true,
                "deliveredAt" to deliveredAt,
                "lastUpdated" to System.currentTimeMillis()
            )

            // Update Remote
            firestore.collection("boxes")
                .document(boxId)
                .collection("packages")
                .document(packageId)
                .update(updateData)
                .await()

            // Update Local - 기존 엔티티를 조회해서 배송 완료로 업데이트
            packageDao.getPackageById(packageId)?.let { entity ->
                val updatedEntity = entity.copy(
                    status = DeliveryStatus.DELIVERED.name
                    // TODO: 다음 필드들이 PackageEntity에 있다면 추가
                    // isDelivered = true,
                    // deliveredAt = deliveredAt,
                    // lastUpdated = System.currentTimeMillis()
                )
                packageDao.insertOrUpdatePackage(updatedEntity)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 삭제 관련
    override suspend fun deletePackage(boxId: String, packageId: String): Result<Unit> {
        return try {
            // Delete from Remote
            firestore.collection("boxes")
                .document(boxId)
                .collection("packages")
                .document(packageId)
                .delete()
                .await()

            // Delete from Local - 먼저 엔티티를 조회한 후 삭제
            packageDao.getPackageById(packageId)?.let { entity ->
                packageDao.deletePackage(entity)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun hidePackage(boxId: String, packageId: String): Result<Unit> {
        return try {
            val updateData = mapOf(
                "isHidden" to true,
                "lastUpdated" to System.currentTimeMillis()
            )

            // Update Remote
            firestore.collection("boxes")
                .document(boxId)
                .collection("packages")
                .document(packageId)
                .update(updateData)
                .await()

            // Update Local - TODO: PackageEntity에 isHidden 필드가 있다면 업데이트
            packageDao.getPackageById(packageId)?.let { entity ->
                // TODO: PackageEntity에 isHidden 필드가 있다면 업데이트
                // val updatedEntity = entity.copy(isHidden = true)
                // packageDao.insertOrUpdatePackage(updatedEntity)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 배송 추적 관련
    override suspend fun updateDeliverySteps(boxId: String, packageId: String, steps: List<DeliveryStep>): Result<Unit> {
        return try {
            // Remote 업데이트는 제외하고 Local만 처리 (DAO에 insertDeliveryStep이 있음)
            steps.forEach { step ->
                // TODO: DeliveryStep을 DeliveryStepEntity로 변환하는 로직 필요
                // val stepEntity = convertDeliveryStepToEntity(step, packageId)
                // packageDao.insertDeliveryStep(stepEntity)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshTrackingInfo(boxId: String, packageId: String): Result<PackageInfo> {
        return Result.failure(NotImplementedError("Will be implemented with tracking API"))
    }

    // 동기화 관련
    override suspend fun syncWithRemote(boxId: String): Result<Unit> {
        return try {
            // Remote에서 모든 패키지 조회
            val docs = firestore.collection("boxes")
                .document(boxId)
                .collection("packages")
                .get()
                .await()

            // Local에 저장
            docs.documents.forEach { doc ->
                doc.data?.let { data ->
                    val packageInfo = convertFirebaseDataToPackageInfo(data, doc.id, boxId)
                    packageDao.insertOrUpdatePackage(convertPackageInfoToEntity(packageInfo))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun invalidateCache(boxId: String) {
        // TODO: Local 캐시 무효화 로직 구현 (현재는 별도 처리 없음)
    }

    // 검증 관련
    override suspend fun findByTrackingNumber(boxId: String, trackingNumber: String): PackageInfo? {
        return try {
            // Local에서 먼저 검색
            val searchQuery = "%$trackingNumber%"
            packageDao.searchPackages(searchQuery)
                .find { it.trackingNumber == trackingNumber && it.boxId == boxId }
                ?.let { return convertEntityToPackageInfo(it) }

            // Remote에서 검색
            val docs = firestore.collection("boxes")
                .document(boxId)
                .collection("packages")
                .whereEqualTo("trackingNumber", trackingNumber)
                .get()
                .await()

            docs.documents.firstOrNull()?.let { doc ->
                doc.data?.let { data ->
                    val packageInfo = convertFirebaseDataToPackageInfo(data, doc.id, boxId)
                    // Cache to local
                    packageDao.insertOrUpdatePackage(convertPackageInfoToEntity(packageInfo))
                    packageInfo
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun validatePackageData(packageInfo: PackageInfo): Result<Unit> {
        return try {
            // 기본 검증 로직
            if (packageInfo.trackingNumber.isBlank()) {
                return Result.failure(IllegalArgumentException("Tracking number is required"))
            }
            if (packageInfo.courierCompany.isBlank()) {
                return Result.failure(IllegalArgumentException("Courier company is required"))
            }
            if (packageInfo.boxId.isBlank()) {
                return Result.failure(IllegalArgumentException("Box ID is required"))
            }

            // 중복 검증
            findByTrackingNumber(packageInfo.boxId, packageInfo.trackingNumber)?.let {
                if (it.id != packageInfo.id) {
                    return Result.failure(IllegalArgumentException("Package with this tracking number already exists"))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 변환 함수들 (실제로는 mapper 확장 함수 사용)
    private fun convertFirebaseDataToPackageInfo(data: Map<String, Any>, id: String, boxId: String): PackageInfo {
        return PackageInfo(
            id = id,
            trackingNumber = data["trackingNumber"] as? String ?: "",
            courierCompany = data["courierCompany"] as? String ?: "",
            itemName = data["itemName"] as? String,
            category = data["category"] as? String ?: "",
            memo = data["memo"] as? String,
            origin = data["origin"] as? String ?: "",
            destination = data["destination"] as? String ?: "",
            status = DeliveryStatus.valueOf(data["status"] as? String ?: "REGISTERED"),
            registeredAt = data["registeredAt"] as? Long ?: System.currentTimeMillis(),
            registeredBy = data["registeredBy"] as? String ?: "",
            boxId = boxId,
            lastUpdated = data["lastUpdated"] as? Long ?: System.currentTimeMillis(),
            isDelivered = data["isDelivered"] as? Boolean ?: false,
            deliveredAt = data["deliveredAt"] as? Long,
            estimatedDelivery = data["estimatedDelivery"] as? Long
        )
    }

    private fun convertPackageInfoToFirebaseMap(packageInfo: PackageInfo): Map<String, Any?> {
        return mapOf(
            "trackingNumber" to packageInfo.trackingNumber,
            "courierCompany" to packageInfo.courierCompany,
            "itemName" to packageInfo.itemName,
            "category" to packageInfo.category,
            "memo" to packageInfo.memo,
            "origin" to packageInfo.origin,
            "destination" to packageInfo.destination,
            "status" to packageInfo.status.name,
            "registeredAt" to packageInfo.registeredAt,
            "registeredBy" to packageInfo.registeredBy,
            "lastUpdated" to System.currentTimeMillis(),
            "isDelivered" to packageInfo.isDelivered,
            "deliveredAt" to packageInfo.deliveredAt,
            "estimatedDelivery" to packageInfo.estimatedDelivery
        )
    }

    // TODO: 실제로는 mapper 확장 함수 사용
    private fun convertEntityToPackageInfo(entity: PackageEntity): PackageInfo {
        // Room Entity -> PackageInfo 변환
        // 실제 구현시 entity.toPackageInfo() 확장 함수 사용
        return entity.toPackageInfo()
    }

    private fun convertPackageInfoToEntity(packageInfo: PackageInfo): PackageEntity {
        // PackageInfo -> Room Entity 변환
        // 실제 구현시 packageInfo.toEntity() 확장 함수 사용
        return packageInfo.toEntity()
    }
}
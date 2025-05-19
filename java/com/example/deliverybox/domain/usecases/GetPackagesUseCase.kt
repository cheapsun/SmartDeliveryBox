package com.example.deliverybox.domain.usecases

import com.example.deliverybox.domain.repositories.PackageRepository
import com.example.deliverybox.delivery.PackageInfo
import com.example.deliverybox.delivery.DeliveryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetPackagesUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    operator fun invoke(boxId: String): Flow<List<PackageInfo>> {
        return repository.getAllPackages(boxId)
    }

    fun getByStatus(boxId: String, status: DeliveryStatus): Flow<List<PackageInfo>> {
        return repository.getAllPackages(boxId).map { packages ->
            packages.filter { it.status == status }
        }
    }

    fun search(boxId: String, query: String): Flow<List<PackageInfo>> {
        return repository.getAllPackages(boxId).map { packages ->
            packages.filter { packageInfo ->
                packageInfo.trackingNumber.contains(query, ignoreCase = true) ||
                        packageInfo.itemName?.contains(query, ignoreCase = true) == true ||
                        packageInfo.courierCompany.contains(query, ignoreCase = true)
            }
        }
    }
}

// 추가 UseCase들
class SavePackageUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    suspend operator fun invoke(packageInfo: PackageInfo): Result<String> {
        return repository.validatePackageData(packageInfo).fold(
            onSuccess = { repository.savePackage(packageInfo) },
            onFailure = { Result.failure(it) }
        )
    }
}

class UpdatePackageStatusUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    suspend operator fun invoke(boxId: String, packageId: String, status: DeliveryStatus): Result<Unit> {
        return repository.updatePackageStatus(boxId, packageId, status)
    }
}

class DeletePackageUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    suspend operator fun invoke(boxId: String, packageId: String): Result<Unit> {
        return repository.deletePackage(boxId, packageId)
    }
}
package com.example.deliverybox.domain.usecases

import com.example.deliverybox.domain.repositories.PackageRepository
import com.example.deliverybox.model.PackageInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase: 특정 박스(boxId) 내 모든 패키지를 불러오는 역할
 */
class GetPackagesUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    operator fun invoke(boxId: String): Flow<List<PackageInfo>> {
        return repository.getAllPackages(boxId)
    }
}
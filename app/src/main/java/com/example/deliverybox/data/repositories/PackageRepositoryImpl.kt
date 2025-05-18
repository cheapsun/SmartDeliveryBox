package com.example.deliverybox.data.repositories

import com.example.deliverybox.domain.repositories.PackageRepository
import com.example.deliverybox.model.PackageInfo
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Data 계층 구현체: Firestore로부터 데이터 읽기/조작
 */
class PackageRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : PackageRepository {

    override fun getAllPackages(boxId: String): Flow<List<PackageInfo>> = flow {
        val snapshot = firestore.collection("boxes")
            .document(boxId)
            .collection("packages")
            .get()
            .await()
        val packages = snapshot.documents.mapNotNull { it.toObject(PackageInfo::class.java) }
        emit(packages)
    }

    override suspend fun deletePackage(boxId: String, packageId: String) {
        firestore.collection("boxes")
            .document(boxId)
            .collection("packages")
            .document(packageId)
            .delete()
            .await()
    }

    override suspend fun updatePackageStatus(boxId: String, packageId: String, newStatus: String) {
        firestore.collection("boxes")
            .document(boxId)
            .collection("packages")
            .document(packageId)
            .update("status", newStatus)
            .await()
    }
}
package com.example.deliverybox.repository

interface PackageRepository {
    suspend fun getAllPackages(boxId: String): Flow<List<PackageInfo>>
    suspend fun getPackageById(boxId: String, packageId: String): PackageInfo?
    suspend fun savePackage(boxId: String, packageInfo: PackageInfo): Result<String>
    suspend fun updatePackageStatus(boxId: String, packageId: String, status: DeliveryStatus): Result<Unit>
    suspend fun deletePackage(boxId: String, packageId: String): Result<Unit>
    suspend fun searchPackages(boxId: String, query: String): List<PackageInfo>
    suspend fun getPackagesByStatus(boxId: String, status: DeliveryStatus): List<PackageInfo>
}

class FirestorePackageRepository : PackageRepository {
    private val db = FirebaseFirestore.getInstance()

    override suspend fun getAllPackages(boxId: String): Flow<List<PackageInfo>> = callbackFlow {
        val listener = db.collection("boxes").document(boxId)
            .collection("packages")
            .whereEqualTo("isDelivered", false)
            .orderBy("registeredAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val packages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PackageInfo::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(packages)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun savePackage(boxId: String, packageInfo: PackageInfo): Result<String> {
        return try {
            val docRef = db.collection("boxes").document(boxId)
                .collection("packages").document()

            docRef.set(packageInfo.copy(id = docRef.id)).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
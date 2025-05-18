package com.example.deliverybox.data.local.dao

import androidx.room.*
import com.example.deliverybox.data.local.entity.PackageEntity
import com.example.deliverybox.data.local.entity.DeliveryStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {

    @Query("SELECT * FROM packages WHERE boxId = :boxId ORDER BY createdAt DESC")
    fun getAllPackages(boxId: String): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE id = :id")
    suspend fun getPackageById(id: String): PackageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePackage(pkg: PackageEntity)

    @Delete
    suspend fun deletePackage(pkg: PackageEntity)

    @Query("SELECT * FROM packages WHERE trackingNumber LIKE :query OR itemName LIKE :query")
    suspend fun searchPackages(query: String): List<PackageEntity>

    @Query("SELECT * FROM packages WHERE status = :status")
    suspend fun getPackagesByStatus(status: String): List<PackageEntity>

    @Transaction
    suspend fun insertWithSteps(pkg: PackageEntity, steps: List<DeliveryStepEntity>) {
        insertOrUpdatePackage(pkg)
        steps.forEach { insertDeliveryStep(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeliveryStep(step: DeliveryStepEntity)

    @Query("SELECT * FROM delivery_steps WHERE packageId = :packageId ORDER BY timestamp ASC")
    suspend fun getDeliverySteps(packageId: String): List<DeliveryStepEntity>
}

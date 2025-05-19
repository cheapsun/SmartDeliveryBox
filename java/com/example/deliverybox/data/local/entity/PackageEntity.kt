package com.example.deliverybox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packages")
data class PackageEntity(
    @PrimaryKey val id: String,
    val trackingNumber: String,
    val courierCompany: String,
    val itemName: String,
    val category: String,
    val status: String, // DeliveryStatus.name 사용
    val boxId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDelivered: Boolean = false,
    val deliveredAt: Long? = null
)

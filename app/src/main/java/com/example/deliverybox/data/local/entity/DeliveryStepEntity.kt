package com.example.deliverybox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "delivery_steps")
data class DeliveryStepEntity(
    @PrimaryKey val id: String,
    val packageId: String,
    val stepType: String,
    val description: String,
    val location: String?,
    val timestamp: Long,
    val isCompleted: Boolean
)

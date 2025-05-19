package com.example.deliverybox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boxes")
data class BoxEntity(
    @PrimaryKey val id: String,
    val name: String,
    val location: String,
    val ownerId: String,
    val createdAt: Long
)

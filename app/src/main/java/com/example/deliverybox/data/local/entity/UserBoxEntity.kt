package com.example.deliverybox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "user_boxes",
    primaryKeys = ["userId", "boxId"],
    foreignKeys = [
        ForeignKey(
            entity = BoxEntity::class,
            parentColumns = ["id"],
            childColumns = ["boxId"],
            onDelete = ForeignKey.CASCADE
        ) // ✅ 콤마가 필요 없음 (마지막 요소)
    ]
)
data class UserBoxEntity(
    val userId: String,
    val boxId: String,
    val alias: String,
    val isMain: Boolean = false,
    val joinedAt: Long
)


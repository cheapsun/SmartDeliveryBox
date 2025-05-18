package com.example.deliverybox.data.local.mapper

import com.example.deliverybox.data.local.entity.PackageEntity
import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.PackageInfo

// Entity → PackageInfo 변환
fun PackageEntity.toPackageInfo(): PackageInfo =
    PackageInfo(
        id = id,
        trackingNumber = trackingNumber,
        courierCompany = courierCompany,
        itemName = itemName,
        category = category,
        origin = "", // Room에는 저장되지 않음
        destination = "",
        status = DeliveryStatus.valueOf(status),
        deliverySteps = emptyList(), // 별도 쿼리 필요
        lastUpdated = updatedAt,
        isAutoDetected = false,
        confidence = 1f,
        registeredAt = createdAt,
        registeredBy = "",
        boxId = boxId,
        isDelivered = isDelivered,
        deliveredAt = deliveredAt,
        estimatedDelivery = null,
        memo = null
    )

// PackageInfo → Entity 변환
fun PackageInfo.toEntity(): PackageEntity =
    PackageEntity(
        id = id,
        trackingNumber = trackingNumber,
        courierCompany = courierCompany,
        itemName = itemName ?: "",
        category = category,
        status = status.name,
        boxId = boxId,
        createdAt = registeredAt,
        updatedAt = System.currentTimeMillis(),
        isDelivered = isDelivered,
        deliveredAt = deliveredAt
    )

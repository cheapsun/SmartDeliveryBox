package com.example.deliverybox.data.local.mapper

import com.example.deliverybox.data.local.entity.PackageEntity
import com.example.deliverybox.data.local.entity.DeliveryStepEntity
import com.example.deliverybox.delivery.PackageInfo
import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.DeliveryStep

// 기존 Entity → PackageInfo 변환 (수정)
fun PackageEntity.toPackageInfo(): PackageInfo =
    PackageInfo(
        id = id,
        trackingNumber = trackingNumber,
        courierCompany = courierCompany,
        itemName = itemName.takeIf { it.isNotBlank() },
        category = category,
        status = try {
            DeliveryStatus.valueOf(status)
        } catch (e: Exception) {
            DeliveryStatus.REGISTERED
        },
        boxId = boxId,
        registeredAt = createdAt,
        lastUpdated = updatedAt,
        isDelivered = isDelivered,
        deliveredAt = deliveredAt,
        deliverySteps = emptyList() // 별도 쿼리 필요
    )

// 새로 추가: PackageInfo → Entity 변환
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

// 새로 추가: DeliveryStepEntity 매퍼들
fun DeliveryStepEntity.toDeliveryStep(): DeliveryStep =
    DeliveryStep(
        id = id,
        stepType = stepType,
        description = description,
        location = location,
        timestamp = timestamp,
        isCompleted = isCompleted
    )

fun DeliveryStep.toEntity(packageId: String): DeliveryStepEntity =
    DeliveryStepEntity(
        id = id.ifEmpty { "${packageId}_${stepType}_$timestamp" },
        packageId = packageId,
        stepType = stepType,
        description = description,
        location = location,
        timestamp = timestamp,
        isCompleted = isCompleted
    )

// 새로 추가: Firebase 매퍼들
fun Map<String, Any?>.toPackageInfo(): PackageInfo {
    return PackageInfo(
        id = (this["id"] as? String) ?: "",
        trackingNumber = (this["trackingNumber"] as? String) ?: "",
        courierCompany = (this["courierCompany"] as? String) ?: "",
        itemName = this["itemName"] as? String,
        category = (this["category"] as? String) ?: "",
        memo = this["memo"] as? String,
        origin = (this["origin"] as? String) ?: "",
        destination = (this["destination"] as? String) ?: "",
        status = try {
            DeliveryStatus.valueOf((this["status"] as? String) ?: "REGISTERED")
        } catch (e: Exception) {
            DeliveryStatus.REGISTERED
        },
        registeredAt = (this["registeredAt"] as? Long) ?: System.currentTimeMillis(),
        registeredBy = (this["registeredBy"] as? String) ?: "",
        boxId = (this["boxId"] as? String) ?: "",
        lastUpdated = (this["lastUpdated"] as? Long) ?: System.currentTimeMillis(),
        isDelivered = (this["isDelivered"] as? Boolean) ?: false,
        deliveredAt = this["deliveredAt"] as? Long,
        estimatedDelivery = this["estimatedDelivery"] as? Long,
        isAutoDetected = (this["isAutoDetected"] as? Boolean) ?: false,
        confidence = (this["confidence"] as? Double)?.toFloat() ?: 1.0f
    )
}

fun PackageInfo.toFirebaseMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "trackingNumber" to trackingNumber,
        "courierCompany" to courierCompany,
        "itemName" to itemName,
        "category" to category,
        "memo" to memo,
        "origin" to origin,
        "destination" to destination,
        "status" to status.name,
        "registeredAt" to registeredAt,
        "registeredBy" to registeredBy,
        "boxId" to boxId,
        "lastUpdated" to lastUpdated,
        "isDelivered" to isDelivered,
        "deliveredAt" to deliveredAt,
        "estimatedDelivery" to estimatedDelivery,
        "isAutoDetected" to isAutoDetected,
        "confidence" to confidence
    )
}
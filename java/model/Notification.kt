package com.example.deliverybox.model

data class Notification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    val type: NotificationType = NotificationType.GENERAL,
    val boxId: String = "",
    val read: Boolean = false,
    val additionalData: Map<String, String> = mapOf()
)

enum class NotificationType {
    GENERAL,
    PACKAGE_ARRIVED,
    DOORLOCK_CONTROL,
    SYSTEM
}
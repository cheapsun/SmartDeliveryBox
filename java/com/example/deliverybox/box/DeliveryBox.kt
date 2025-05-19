package com.example.deliverybox.box

import com.example.deliverybox.R
import java.text.SimpleDateFormat
import java.util.*

data class DeliveryBox(
    val boxId: String = "",
    val boxName: String = "",
    val alias: String = "",
    val ownerId: String = "",
    val status: BoxStatus = BoxStatus.INACTIVE,
    val batchName: String = "",
    val qrCodeBase64: String? = null,
    val registeredAt: Long = 0,
    val packageCount: Int = 0,
    val doorLocked: Boolean = true,
    val lastUpdated: Long = 0,
    val members: Map<String, String> = emptyMap()
) {
    fun getDisplayName(): String = alias.ifEmpty { boxName.ifEmpty { "택배함" } }

    fun isOwnedBy(userId: String): Boolean = ownerId == userId

    fun getStatusText(): String = when (status) {
        BoxStatus.AVAILABLE -> "등록 가능"
        BoxStatus.REGISTERED -> "등록됨"
        BoxStatus.INACTIVE -> "비활성"
    }

    fun getLockStatusIcon(): Int = if (doorLocked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open

    fun getLastUpdateText(): String = TimeUtils.getTimeAgoText(lastUpdated)
}

enum class BoxStatus {
    AVAILABLE,
    REGISTERED,
    INACTIVE
}

object TimeUtils {
    fun getTimeAgoText(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60 * 1000 -> "방금 전"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}분 전"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}시간 전"
            else -> SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
package com.example.deliverybox.box

data class BoxInfo(
    val boxId: String,
    val alias: String,
    val boxName: String = "",
    val packageCount: Int = 0,
    val doorLocked: Boolean = true,
    val lastUpdated: Long = 0
)
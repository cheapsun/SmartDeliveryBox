package com.example.deliverybox.delivery

// 확장 함수들
private val Int.dp: Float
    get() = this * Resources.getSystem().displayMetrics.density

private val Int.sp: Float
    get() = this * Resources.getSystem().displayMetrics.scaledDensity
package com.example.deliverybox.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DeliveryBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 전역 초기화 로직
    }
}
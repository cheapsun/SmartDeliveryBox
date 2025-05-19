package com.example.deliverybox.app

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DeliveryBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ✅ 이모지 호환성 초기화
        val config = BundledEmojiCompatConfig(this)
        EmojiCompat.init(config)

        // 다른 전역 초기화 로직 (예: 로그, 네트워크 등)
    }
}

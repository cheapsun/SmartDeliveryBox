package com.example.deliverybox

import android.app.Application
import com.google.firebase.FirebaseApp

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Firebase SDK 초기화
        FirebaseApp.initializeApp(this)
    }
}

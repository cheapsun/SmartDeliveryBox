package com.example.deliverybox

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.deliverybox.utils.AccountUtils
import com.google.firebase.FirebaseApp

class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // Firebase 초기화
        FirebaseApp.initializeApp(this)

        // 앱 최초 실행 시 계정 상태 확인 (백그라운드/포그라운드 전환 시)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                Log.d(TAG, "앱 포그라운드 진입 - 계정 상태 확인")
                checkAccountState()
            }
        })
    }

    /**
     * 계정 상태 확인 및 처리
     * 이 메서드는 앱이 시작될 때마다 호출됨
     */
    private fun checkAccountState() {
        // 계정 상태에 따라 적절히 처리
        // 실제 화면 전환은 SplashActivity에서 수행
        AccountUtils.checkSignupState { state ->
            when (state) {
                AccountUtils.SignupState.NOT_LOGGED_IN -> {
                    Log.d(TAG, "계정 상태: 로그인 필요")
                    // SplashActivity에서 LoginActivity로 이동
                }
                AccountUtils.SignupState.EMAIL_VERIFIED -> {
                    Log.d(TAG, "계정 상태: 이메일 인증 완료, 비밀번호 설정 필요")
                    // SplashActivity에서 SignupPasswordActivity로 이동
                }
                AccountUtils.SignupState.COMPLETED -> {
                    Log.d(TAG, "계정 상태: 가입 완료")
                    // SplashActivity에서 MainActivity로 이동
                }
            }
        }
    }
}
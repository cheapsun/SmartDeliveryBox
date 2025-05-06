package com.example.deliverybox

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.deliverybox.utils.AccountUtils
import com.example.deliverybox.workers.AccountCleanupWorker
import com.google.firebase.FirebaseApp
import java.util.concurrent.TimeUnit

/**
 * 애플리케이션 클래스
 * 앱 전역 초기화 및 상태 관리
 */
class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"

        // 싱글톤 인스턴스
        private lateinit var instance: MyApplication

        fun getInstance(): MyApplication {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Firebase 초기화
        FirebaseApp.initializeApp(this)

        // 미인증 계정 정리 작업 예약
        scheduleAccountCleanup()

        // 앱 상태 감시자 등록 (앱이 포그라운드로 올 때마다 확인)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // 앱이 포그라운드로 왔을 때 계정 상태 확인
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
        // 미완료 임시 계정 확인 및 정리
        AccountUtils.deleteTempAccountAndSignOut {
            // 계정 상태에 따라 적절히 처리
            // 실제 화면 전환은 SplashActivity에서 수행
            AccountUtils.checkSignupState { state ->
                when (state) {
                    AccountUtils.SignupState.NOT_LOGGED_IN -> {
                        Log.d(TAG, "계정 상태: 로그인 필요")
                        // SplashActivity에서 LoginActivity로 이동
                    }
                    AccountUtils.SignupState.NEW_ACCOUNT -> {
                        Log.d(TAG, "계정 상태: 신규 계정 (인증 필요)")
                        // SplashActivity에서 EmailVerificationActivity로 이동
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

    /**
     * 주기적인 미인증 계정 정리 작업 예약
     */
    private fun scheduleAccountCleanup() {
        // 7일마다 실행되는 미인증 계정 정리 작업 예약
        val accountCleanupWork = PeriodicWorkRequestBuilder<AccountCleanupWorker>(
            7, TimeUnit.DAYS
        )
            .setInitialDelay(1, TimeUnit.DAYS)  // 첫 실행은 1일 후
            .build()

        // 작업 등록 (이미 있으면 유지)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "account_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            accountCleanupWork
        )

        Log.d(TAG, "미인증 계정 정리 작업 예약 완료")
    }
}
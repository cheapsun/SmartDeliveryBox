package com.example.deliverybox

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.deliverybox.utils.AccountUtils
import com.example.deliverybox.workers.AccountCleanupWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


import java.util.concurrent.TimeUnit

class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // Firebase 초기화
        FirebaseApp.initializeApp(this)

        // 주기적인 미인증 계정 정리 작업 스케줄링
        scheduleAccountCleanup()

        // 앱 최초 실행 시 계정 상태 확인 (백그라운드/포그라운드 전환 시)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Log.d(TAG, "앱 포그라운드 진입 - 계정 상태 확인")
                checkAccountState()
            }
        })
    }

    /**
     * 계정 정리 작업 스케줄링
     * 일주일에 한 번 실행
     */
    private fun scheduleAccountCleanup() {
        try {
            val cleanupRequest = PeriodicWorkRequestBuilder<AccountCleanupWorker>(
                7, TimeUnit.DAYS
            )
                .setInitialDelay(1, TimeUnit.DAYS) // 앱 설치 후 하루 뒤 첫 실행
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "account_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )

            Log.d(TAG, "계정 정리 작업 스케줄링 완료")
        } catch (e: Exception) {
            Log.e(TAG, "계정 정리 작업 스케줄링 실패: ${e.message}")
        }
    }

    /**
     * 계정 상태 확인 및 처리
     * 이 메서드는 앱이 시작될 때마다 호출됨
     */

    private fun checkAccountState() {
        AccountUtils.checkExceptionCases { exceptionResult ->
            when (exceptionResult.state) {
                AccountUtils.SignupState.NOT_LOGGED_IN -> {
                    Log.d(TAG, "계정 상태: 로그인 필요")
                }
                AccountUtils.SignupState.EMAIL_VERIFIED -> {
                    Log.d(TAG, "계정 상태: 이메일 인증 완료, 비밀번호 설정 필요")
                }
                AccountUtils.SignupState.COMPLETED -> {
                    Log.d(TAG, "계정 상태: 가입 완료")
                }
            }
        }

    }



}
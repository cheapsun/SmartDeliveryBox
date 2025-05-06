package com.example.deliverybox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.deliverybox.utils.AccountUtils
import com.example.deliverybox.workers.AccountCleanupWorker
import com.google.firebase.FirebaseApp

import java.util.concurrent.TimeUnit

class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"

        // 추가: 애플리케이션 컨텍스트 접근용 변수와 메소드
        private var appContext: Context? = null

        fun getAppContext(): Context? {
            return appContext
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 추가: 애플리케이션 컨텍스트 저장
        appContext = applicationContext

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
                    // 로그인 필요시 특별한 처리 없음, SplashActivity에서 처리됨
                }
                AccountUtils.SignupState.EMAIL_VERIFIED -> {
                    Log.d(TAG, "계정 상태: 이메일 인증 완료, 비밀번호 설정 필요")

                    // 앱이 이미 실행 중일 때 비밀번호 설정 화면으로 이동하는 알림 표시
                    val context = applicationContext
                    if (exceptionResult.email != null) {
                        // 백그라운드에서 알림 발생
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                        // 알림 채널 생성 (Android 8.0 이상)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            val channel = NotificationChannel(
                                "signup_channel",
                                "회원가입 알림",
                                NotificationManager.IMPORTANCE_HIGH
                            )
                            notificationManager.createNotificationChannel(channel)
                        }

                        // 비밀번호 설정 화면으로 이동하는 PendingIntent
                        val intent = Intent(context, SignupPasswordActivity::class.java).apply {
                            putExtra("email", exceptionResult.email)
                            putExtra("fromVerification", true)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
                        )

                        // 알림 생성
                        val notification = NotificationCompat.Builder(context, "signup_channel")
                            .setContentTitle("회원가입 완료하기")
                            .setContentText("비밀번호 설정이 필요합니다")
                            .setSmallIcon(R.drawable.ic_mail_outline)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .build()

                        notificationManager.notify(1001, notification)
                    }
                }
                AccountUtils.SignupState.COMPLETED -> {
                    Log.d(TAG, "계정 상태: 가입 완료")
                    // 완료 상태는 특별한 처리 없음
                }
            }
        }
    }
}
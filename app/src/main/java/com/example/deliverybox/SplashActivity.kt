package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.utils.AccountUtils
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Firebase 인증 객체 초기화
        auth = FirebaseAuth.getInstance()

        // 앱 시작 시 임시 계정 정리
        checkAndCleanupTempAccount()
    }

    /**
     * 미완료 임시 계정 확인 및 정리
     * 인증되지 않은 계정은 삭제 처리
     */
    private fun checkAndCleanupTempAccount() {
        val currentUser = auth.currentUser

        // 로그인된 사용자가 있고, 이메일이 인증되지 않은 상태라면
        if (currentUser != null && !currentUser.isEmailVerified) {
            // 임시 계정 삭제 처리
            AccountUtils.deleteTempAccountAndSignOut {
                // 계정 정리 후 정상 플로우 진행
                startNormalFlow()
            }
        } else {
            // 계정 정리가 필요 없으면 바로 정상 플로우 진행
            startNormalFlow()
        }
    }

    /**
     * 앱 정상 시작 플로우
     */
    private fun startNormalFlow() {
        // 약간의 지연 후 (2초) 로그인 여부 체크
        Handler(Looper.getMainLooper()).postDelayed({
            checkLoginStatus()
        }, 2000)
    }

    /**
     * 현재 로그인 상태를 체크해서
     *    - 로그인되어 있으면 MainActivity로 이동
     *    - 로그인 안 되어 있으면 LoginActivity로 이동
     */
    private fun checkLoginStatus() {
        val currentUser = auth.currentUser

        if (currentUser != null && currentUser.isEmailVerified) {
            // 이미 로그인 되어 있고 인증 완료 → MainActivity로 이동
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            // 로그인 안 되어 있음 → LoginActivity로 이동
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
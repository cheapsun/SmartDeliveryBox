package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Firebase 인증 객체 초기화
        auth = FirebaseAuth.getInstance()

        // 약간의 지연 후 (2초) 로그인 여부 체크
        Handler(Looper.getMainLooper()).postDelayed({
            checkLoginStatus()
        }, 2000)
    }

    /**
     * ✅ 현재 로그인 상태를 체크해서
     *    - 로그인되어 있으면 MainActivity로 이동
     *    - 로그인 안 되어 있으면 LoginActivity로 이동
     */
    private fun checkLoginStatus() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // 이미 로그인 되어 있음 → MainActivity로 이동
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

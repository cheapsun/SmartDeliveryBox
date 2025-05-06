package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.utils.AccountUtils
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val TAG = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Firebase 인증 객체 초기화
        auth = FirebaseAuth.getInstance()

        // 가입 상태 확인 및 적절한 화면으로 이동
        checkSignupStateAndNavigate()
    }

    /**
     * 가입 상태를 확인하고 적절한 화면으로 이동
     */
    private fun checkSignupStateAndNavigate() {
        // 약간의 지연 후 가입 상태 확인 (스플래시 화면 표시를 위해)
        Handler(Looper.getMainLooper()).postDelayed({
            AccountUtils.checkSignupState { state ->
                when (state) {
                    AccountUtils.SignupState.NOT_LOGGED_IN -> {
                        // 로그인 화면으로 이동
                        Log.d(TAG, "로그인 화면으로 이동")
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                    AccountUtils.SignupState.EMAIL_VERIFIED -> {
                        // 비밀번호 설정 화면으로 이동
                        Log.d(TAG, "비밀번호 설정 화면으로 이동")
                        val intent = Intent(this, SignupPasswordActivity::class.java)
                        intent.putExtra("email", auth.currentUser?.email)
                        startActivity(intent)
                        finish()
                    }
                    AccountUtils.SignupState.COMPLETED -> {
                        // 메인 화면으로 이동
                        Log.d(TAG, "메인 화면으로 이동")
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }
            }
        }, 2000) // 2초 지연
    }
}
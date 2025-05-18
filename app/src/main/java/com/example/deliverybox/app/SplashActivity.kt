package com.example.deliverybox.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.example.deliverybox.databinding.ActivitySplashBinding
import com.example.deliverybox.utils.AccountUtils
import com.example.deliverybox.utils.NetworkUtils
import com.example.deliverybox.utils.SharedPrefsHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.auth.EmailVerificationActivity
import com.example.deliverybox.auth.LoginActivity
import com.example.deliverybox.auth.SignupPasswordActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val TAG = "SplashActivity"
    private val SPLASH_DELAY_MILLIS = 2000L  // 최소 스플래시 표시 시간
    private val SESSION_TIMEOUT_HOURS = 72L  // 세션 유효 시간 (3일)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 앱 버전 표시
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvAppVersion.text = "v${pInfo.versionName}"
        } catch (e: Exception) {
            binding.tvAppVersion.visibility = View.GONE
        }

        // 네트워크 연결 확인
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showNetworkError()
            return
        }

        // 미완료 임시 계정 확인 및 정리
        checkAndCleanupTempAccount()
    }

    private fun showNetworkError() {
        binding.progressSplash.visibility = View.GONE
        binding.tvNetworkError.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.VISIBLE

        binding.btnRetry.setOnClickListener {
            binding.progressSplash.visibility = View.VISIBLE
            binding.tvNetworkError.visibility = View.GONE
            binding.btnRetry.visibility = View.GONE

            Handler(Looper.getMainLooper()).postDelayed({
                if (NetworkUtils.isNetworkAvailable(this)) {
                    checkAndCleanupTempAccount()
                } else {
                    showNetworkError()
                }
            }, 1000)
        }
    }

    private fun checkAndCleanupTempAccount() {
        val startTime = System.currentTimeMillis()

        AccountUtils.checkExceptionCases { result ->
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < SPLASH_DELAY_MILLIS) {
                Handler(Looper.getMainLooper()).postDelayed({
                    handleAccountState(result)
                }, SPLASH_DELAY_MILLIS - elapsedTime)
            } else {
                handleAccountState(result)
            }
        }
    }

    // ← 수정된 부분: 로그 추가 및 처리 순서 동일
    private fun handleAccountState(result: AccountUtils.ExceptionResult) {
        when (result.state) {
            AccountUtils.SignupState.NOT_LOGGED_IN -> {
                // 로그인 필요 상태
                if (result.email != null) {
                    Log.d(TAG, "이메일 인증 필요 상태 감지: ${result.email}")
                    val intent = Intent(this, EmailVerificationActivity::class.java)
                    intent.putExtra("email", result.email)
                    startActivity(intent)
                } else {
                    navigateToLogin()
                }
                finish()
            }
            AccountUtils.SignupState.EMAIL_VERIFIED -> {
                // 이메일 인증은 되었으나 비밀번호 설정 필요
                if (result.email != null) {
                    Log.d(TAG, "비밀번호 설정 필요 상태 감지: ${result.email}")
                    val intent = Intent(this, SignupPasswordActivity::class.java)
                    intent.putExtra("email", result.email)
                    intent.putExtra("fromVerification", true)
                    startActivity(intent)
                    finish()
                } else {
                    auth.signOut()
                    navigateToLogin()
                }
            }
            AccountUtils.SignupState.COMPLETED -> {
                // 가입 완료 - 세션 확인 후 메인으로
                checkLoginSession()
            }
        }
    }

    // 수정된 checkLoginSession 메서드
    private fun checkLoginSession() {
        val currentUser = auth.currentUser

        // Firebase에 이미 로그인된 상태이고 자동 로그인 설정이 켜져 있으면
        if (currentUser != null && SharedPrefsHelper.isAutoLoginEnabled(this)) {
            // 세션 유효성 확인
            currentUser.getIdToken(true)
                .addOnSuccessListener { tokenResult ->
                    // 토큰 갱신 성공 = 세션 유효
                    Log.d(TAG, "Firebase 토큰 유효, 자동 로그인 진행")
                    updateLoginSession()
                    navigateToMain()
                }
                .addOnFailureListener { e ->
                    // 토큰 갱신 실패 = 세션 만료
                    Log.d(TAG, "Firebase 토큰 만료: ${e.message}")
                    auth.signOut()
                    SharedPrefsHelper.clearLoginSession(this)
                    navigateToLogin()
                }
        }
        // 자동 로그인 설정이 꺼져 있거나 세션이 만료된 경우
        else if (!isSessionValid() || currentUser == null) {
            Log.d(TAG, "세션 만료 또는 자동 로그인 비활성화")
            auth.signOut()
            SharedPrefsHelper.clearLoginSession(this)
            navigateToLogin()
        }
        // 이미 로그인되어 있고 세션이 유효한 경우
        else {
            updateLoginSession()
            navigateToMain()
        }
    }

    private fun isSessionValid(): Boolean {
        val lastLoginTime = SharedPrefsHelper.getLastLoginTime(this)
        if (lastLoginTime <= 0) return false
        val now = System.currentTimeMillis()
        val elapsedHours = (now - lastLoginTime) / (1000 * 60 * 60)
        return elapsedHours < SESSION_TIMEOUT_HOURS
    }

    private fun updateLoginSession() {
        SharedPrefsHelper.setLastLoginTime(this, System.currentTimeMillis())
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

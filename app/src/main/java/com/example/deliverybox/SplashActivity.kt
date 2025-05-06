package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivitySplashBinding
import com.example.deliverybox.utils.AccountUtils
import com.example.deliverybox.utils.FirestoreHelper
import com.example.deliverybox.utils.NetworkUtils
import com.example.deliverybox.utils.SharedPrefsHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

        // Firebase 인증 객체 초기화
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

    /**
     * 네트워크 오류 표시
     */
    private fun showNetworkError() {
        binding.progressSplash.visibility = View.GONE
        binding.tvNetworkError.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.VISIBLE

        binding.btnRetry.setOnClickListener {
            binding.progressSplash.visibility = View.VISIBLE
            binding.tvNetworkError.visibility = View.GONE
            binding.btnRetry.visibility = View.GONE

            // 네트워크 재확인
            Handler(Looper.getMainLooper()).postDelayed({
                if (NetworkUtils.isNetworkAvailable(this)) {
                    checkAndCleanupTempAccount()
                } else {
                    showNetworkError()
                }
            }, 1000)
        }
    }

    /**
     * 미완료 임시 계정 확인 및 정리
     * 인증되지 않은 계정은 삭제 처리
     */
    private fun checkAndCleanupTempAccount() {
        val startTime = System.currentTimeMillis()

        AccountUtils.checkExceptionCases { result ->
            // 계정 정리 후 정상 플로우 진행
            val elapsedTime = System.currentTimeMillis() - startTime

            // 최소 표시 시간 보장
            if (elapsedTime < SPLASH_DELAY_MILLIS) {
                Handler(Looper.getMainLooper()).postDelayed({
                    handleAccountState(result)
                }, SPLASH_DELAY_MILLIS - elapsedTime)
            } else {
                handleAccountState(result)
            }
        }
    }

    /**
     * 계정 상태에 따른 처리
     */
    private fun handleAccountState(result: AccountUtils.ExceptionResult) {
        when (result.state) {
            AccountUtils.SignupState.NOT_LOGGED_IN -> {
                // 로그인 필요 상태
                if (result.email != null) {
                    // 이메일 인증이 필요한 경우
                    val intent = Intent(this, EmailVerificationActivity::class.java)
                    intent.putExtra("email", result.email)
                    startActivity(intent)
                } else {
                    // 일반 로그인 화면으로
                    navigateToLogin()
                }
                finish()
            }
            AccountUtils.SignupState.EMAIL_VERIFIED -> {
                // 이메일 인증은 되었으나 비밀번호 설정 필요
                if (result.email != null) {
                    val intent = Intent(this, SignupPasswordActivity::class.java)
                    intent.putExtra("email", result.email)
                    intent.putExtra("fromVerification", true)
                    startActivity(intent)
                    finish()
                } else {
                    // 이메일 정보가 없는 경우 로그아웃 처리
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

    /**
     * 현재 로그인 세션의 유효성 확인
     */
    private fun checkLoginSession() {
        // 세션 유효성 확인
        if (!isSessionValid()) {
            // 세션 만료 - 자동 로그아웃
            Log.d(TAG, "세션 만료, 자동 로그아웃")
            auth.signOut()
            SharedPrefsHelper.clearLoginSession(this)
            navigateToLogin()
            return
        }

        // 세션 갱신 및 메인 화면으로 이동
        updateLoginSession()
        navigateToMain()
    }

    /**
     * 세션 유효성 확인
     */
    private fun isSessionValid(): Boolean {
        val lastLoginTime = SharedPrefsHelper.getLastLoginTime(this)
        if (lastLoginTime <= 0) return false

        val now = System.currentTimeMillis()
        val elapsedHours = (now - lastLoginTime) / (1000 * 60 * 60)

        return elapsedHours < SESSION_TIMEOUT_HOURS
    }

    /**
     * 로그인 세션 갱신
     */
    private fun updateLoginSession() {
        val now = System.currentTimeMillis()
        SharedPrefsHelper.setLastLoginTime(this, now)
    }

    /**
     * 로그인 화면으로 이동
     */
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * 메인 화면으로 이동
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
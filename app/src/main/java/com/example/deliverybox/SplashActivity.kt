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

        AccountUtils.deleteTempAccountAndSignOut {
            // 계정 정리 후 정상 플로우 진행
            val elapsedTime = System.currentTimeMillis() - startTime

            // 최소 표시 시간 보장
            if (elapsedTime < SPLASH_DELAY_MILLIS) {
                Handler(Looper.getMainLooper()).postDelayed({
                    startNormalFlow()
                }, SPLASH_DELAY_MILLIS - elapsedTime)
            } else {
                startNormalFlow()
            }
        }
    }

    /**
     * 앱 정상 시작 플로우
     */
    private fun startNormalFlow() {
        // 로그인 여부 체크
        checkLoginStatus()
    }

    /**
     * 현재 로그인 상태를 체크해서
     *    - 로그인되어 있으면 MainActivity로 이동
     *    - 로그인 안 되어 있으면 LoginActivity로 이동
     */
    private fun checkLoginStatus() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // 사용자 로그인 되어 있음
            val uid = currentUser.uid

            // 세션 유효성 확인
            if (!isSessionValid()) {
                // 세션 만료 - 자동 로그아웃
                Log.d(TAG, "세션 만료, 자동 로그아웃")
                auth.signOut()
                SharedPrefsHelper.clearLoginSession(this)
                navigateToLogin()
                return
            }

            // 이메일 인증 확인
            if (!currentUser.isEmailVerified) {
                // 인증되지 않은 이메일
                Log.d(TAG, "이메일 미인증 계정: ${currentUser.email}")
                navigateToEmailVerification(currentUser.email ?: "")
                return
            }

            // 사용자 정보 가져오기
            FirestoreHelper.getUserData(uid) { userData ->
                if (userData != null) {
                    // 세션 갱신
                    updateLoginSession()

                    // 메인 화면으로 이동
                    navigateToMain()
                } else {
                    // 사용자 데이터를 가져오지 못함 - 로그아웃 처리
                    Log.e(TAG, "사용자 데이터 로드 실패, 로그아웃: $uid")
                    auth.signOut()
                    SharedPrefsHelper.clearLoginSession(this)
                    Toast.makeText(this, "사용자 정보를 불러올 수 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                    navigateToLogin()
                }
            }
        } else {
            // 로그인 안 되어 있음
            navigateToLogin()
        }
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
     * 이메일 인증 화면으로 이동
     */
    private fun navigateToEmailVerification(email: String) {
        val intent = Intent(this, EmailVerificationActivity::class.java)
        intent.putExtra("email", email)
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
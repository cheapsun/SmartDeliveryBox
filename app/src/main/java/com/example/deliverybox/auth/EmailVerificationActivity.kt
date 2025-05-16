package com.example.deliverybox.auth

import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deliverybox.databinding.ActivityEmailVerificationBinding
import com.example.deliverybox.repository.FirebaseAuthRepository
import com.example.deliverybox.utils.AccountUtils
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class EmailVerificationActivity : AppCompatActivity() {

    private val TAG = "EmailVerificationActivity"

    private lateinit var binding: ActivityEmailVerificationBinding
    private lateinit var authRepository: FirebaseAuthRepository
    private var countDownTimer: CountDownTimer? = null
    private var timeRemaining: Long = 0

    // 인증 대기 시간 (5분)
    private val VERIFICATION_TIMEOUT = TimeUnit.MINUTES.toMillis(5)

    private var email: String = ""
    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = FirebaseAuthRepository()

        // 이전 화면에서 전달받은 이메일과 토큰
        email = intent.getStringExtra("email") ?: ""
        token = intent.getStringExtra("token")

        if (email.isEmpty()) {
            Toast.makeText(this, "이메일 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 이메일 표시
        binding.tvEmail.text = email
        binding.tvTitle.text = "이메일 인증"
        binding.tvInfo.text = "다음 이메일로 인증 링크를 보냈습니다. 메일함을 확인하여 링크를 클릭하고 '인증 확인' 버튼을 누르세요."

        // 초기 타이머 시간 설정
        timeRemaining = VERIFICATION_TIMEOUT

        // 뒤로가기 버튼 설정
        binding.ibBack.setOnClickListener {
            showCancelDialog()
        }

        // 인증 메일 재전송 버튼
        binding.btnSendCode.setOnClickListener { resendVerificationEmail() }

        // 인증 확인 버튼
        binding.btnVerify.setOnClickListener { checkEmailVerification() }

        // 타이머 시작
        startCountdownTimer()
    }

    override fun onResume() {
        super.onResume()
        checkEmailVerification()
    }

    private fun resendVerificationEmail() {
        binding.btnSendCode.isEnabled = false
        binding.progressVerifying.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = authRepository.signup(email)

                result.fold(
                    onSuccess = { newToken ->
                        binding.progressVerifying.visibility = View.GONE
                        token = newToken
                        Toast.makeText(this@EmailVerificationActivity, "인증 메일이 재전송되었습니다", Toast.LENGTH_SHORT).show()
                        timeRemaining = VERIFICATION_TIMEOUT
                        startCountdownTimer()
                    },
                    onFailure = { e ->
                        binding.progressVerifying.visibility = View.GONE
                        binding.btnSendCode.isEnabled = true
                        Toast.makeText(this@EmailVerificationActivity, "메일 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                binding.progressVerifying.visibility = View.GONE
                binding.btnSendCode.isEnabled = true
                Toast.makeText(this@EmailVerificationActivity, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 이메일 인증 확인
    private fun checkEmailVerification() {
        binding.progressVerifying.visibility = View.VISIBLE
        binding.btnVerify.isEnabled = false

        lifecycleScope.launch {
            try {
                val tokenToVerify = token ?: ""

                val result = authRepository.verifyEmail(tokenToVerify, email)

                binding.progressVerifying.visibility = View.GONE
                binding.btnVerify.isEnabled = true

                result.fold(
                    onSuccess = { verified ->
                        if (verified) {
                            // 인증 성공 시 처리
                            Toast.makeText(this@EmailVerificationActivity,
                                "이메일 인증이 완료되었습니다!", Toast.LENGTH_SHORT).show()

                            // 비밀번호 설정 화면으로 이동
                            val intent = Intent(this@EmailVerificationActivity,
                                SignupPasswordActivity::class.java).apply {
                                putExtra("email", email)
                                putExtra("fromVerification", true)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@EmailVerificationActivity,
                                "이메일 인증에 실패했습니다. 다시 시도해주세요.",
                                Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { e ->
                        // 인증 실패 시 친절한 안내
                        if (e.message?.contains("이메일 인증이 완료되지 않았습니다") == true) {
                            Toast.makeText(this@EmailVerificationActivity,
                                "아직 이메일 인증이 완료되지 않았습니다. 메일함을 확인해주세요.",
                                Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@EmailVerificationActivity,
                                "인증 확인 중 오류가 발생했습니다: ${e.message}",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } catch (e: Exception) {
                binding.progressVerifying.visibility = View.GONE
                binding.btnVerify.isEnabled = true
                Toast.makeText(this@EmailVerificationActivity,
                    "인증 확인 중 오류가 발생했습니다: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 타이머 시작
    private fun startCountdownTimer() {
        countDownTimer?.cancel()
        if (timeRemaining <= 0) timeRemaining = VERIFICATION_TIMEOUT

        countDownTimer = object : CountDownTimer(timeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                binding.tvTimer.text = String.format("남은 시간: %02d:%02d", minutes, seconds)

                // 15초 이하로 남으면 빨간색으로 표시
                if (millisUntilFinished <= 15000) {
                    binding.tvTimer.setTextColor(Color.RED)
                } else {
                    binding.tvTimer.setTextColor(Color.parseColor("#666666"))
                }
            }

            override fun onFinish() {
                binding.tvTimer.text = "제한 시간이 만료되었습니다"
                binding.tvTimer.setTextColor(Color.RED)
                binding.btnSendCode.isEnabled = true
                binding.btnSendCode.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#6A8DFF"))
                binding.btnSendCode.setTextColor(Color.WHITE)
            }
        }.start()
    }

    private fun showCancelDialog() {
        AlertDialog.Builder(this)
            .setTitle("인증 취소")
            .setMessage("인증을 취소하시면 처음부터 다시 시작해야 합니다. 취소하시겠습니까?")
            .setPositiveButton("취소") { _, _ ->
                // 여기서 바로 logout()을 호출하면 안 되고,
                // 코루틴 스코프 안에서 호출해야 합니다.
                lifecycleScope.launch {
                    try {
                        // 1) 세션 정리 (suspend 함수)
                        val result = authRepository.logout()

                        result.fold(
                            onSuccess = { success ->
                                if (success) {
                                    // 2) 로그인 화면으로 이동
                                    val intent = Intent(
                                        this@EmailVerificationActivity,
                                        LoginActivity::class.java
                                    ).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    startActivity(intent)
                                    finish()
                                } else {
                                    // 만약 logout 자체는 실패하지 않았지만 결과가 false라면
                                    Log.w(TAG, "logout returned false")
                                }
                            },
                            onFailure = { e ->
                                Log.e(TAG, "logout 중 예외", e)
                                Toast.makeText(
                                    this@EmailVerificationActivity,
                                    "로그아웃 실패: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "세션 정리 중 오류", e)
                        finish()
                    }
                }
            }
            .setNegativeButton("계속 진행", null)
            .create()
            .show()
    }


    override fun onBackPressed() {
        showCancelDialog()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
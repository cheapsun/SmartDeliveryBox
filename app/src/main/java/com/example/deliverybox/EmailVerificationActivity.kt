package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivityEmailVerificationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class EmailVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmailVerificationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var countDownTimer: CountDownTimer? = null
    private var timeRemaining: Long = 0

    // 인증 대기 시간 (5분)
    private val VERIFICATION_TIMEOUT = TimeUnit.MINUTES.toMillis(5)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 이전 화면에서 전달받은 이메일
        val email = intent.getStringExtra("email") ?: ""
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
            finish()
        }

        // 인증 메일 재전송 버튼
        binding.btnSendCode.setOnClickListener {
            resendVerificationEmail()
        }

        // 인증 확인 버튼 추가
        binding.btnVerify.setOnClickListener {
            checkEmailVerification()
        }

        // 타이머 시작
        startCountdownTimer()
    }

    /**
     * 인증 이메일 재전송
     */
    private fun resendVerificationEmail() {
        val user = auth.currentUser ?: return

        binding.btnSendCode.isEnabled = false

        user.sendEmailVerification()
            .addOnSuccessListener {
                Toast.makeText(this, "인증 메일이 재전송되었습니다", Toast.LENGTH_SHORT).show()
                timeRemaining = VERIFICATION_TIMEOUT
                startCountdownTimer()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "메일 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.btnSendCode.isEnabled = true
            }
    }

    /**
     * 이메일 인증 상태 확인
     */
    private fun checkEmailVerification() {
        val user = auth.currentUser ?: return

        binding.progressVerifying.visibility = View.VISIBLE

        // 인증 상태를 새로고침하기 위해 사용자 토큰 갱신
        user.reload().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // 갱신된 사용자 정보 가져오기
                val updatedUser = auth.currentUser

                if (updatedUser?.isEmailVerified == true) {
                    // 인증 성공
                    Toast.makeText(this, "이메일 인증 완료", Toast.LENGTH_SHORT).show()

                    // 인증 완료 상태를 Firestore에 기록
                    val uid = updatedUser.uid
                    db.collection("users").document(uid)
                        .update("emailVerified", true)
                        .addOnSuccessListener {
                            // 메인 화면으로 이동
                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                } else {
                    // 아직 인증되지 않음
                    binding.progressVerifying.visibility = View.GONE
                    Toast.makeText(this, "아직 이메일 인증이 완료되지 않았습니다", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 사용자 정보 갱신 실패
                binding.progressVerifying.visibility = View.GONE
                Toast.makeText(this, "사용자 정보 갱신 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 카운트다운 타이머 시작
     */
    private fun startCountdownTimer() {
        // 기존 타이머 취소
        countDownTimer?.cancel()

        // 남은 시간이 0이면 재설정
        if (timeRemaining <= 0) {
            timeRemaining = VERIFICATION_TIMEOUT
        }

        countDownTimer = object : CountDownTimer(timeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                binding.tvTimer.text = String.format("남은 시간: %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.tvTimer.text = "제한 시간이 만료되었습니다"
                binding.btnSendCode.isEnabled = true
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
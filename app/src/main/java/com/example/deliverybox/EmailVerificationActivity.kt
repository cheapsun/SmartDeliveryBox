package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
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
    private lateinit var email: String
    private var verificationCode: String = ""
    private var countDownTimer: CountDownTimer? = null
    private var timeRemaining: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FirebaseAuth 초기화
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 이전 화면에서 전달받은 이메일
        email = intent.getStringExtra("email") ?: ""
        if (email.isEmpty()) {
            Toast.makeText(this, "이메일 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 이메일 표시
        binding.tvEmail.text = email

        // 뒤로가기 버튼 설정
        binding.ibBack.setOnClickListener {
            finish()
        }

        // 인증 코드 입력 감지
        setupVerificationCodeWatcher()

        // 인증 코드 다시 받기 버튼 클릭 리스너
        binding.btnSendCode.setOnClickListener {
            sendVerificationCode()
        }

        // 자동으로 인증 코드 전송
        sendVerificationCode()
    }

    /**
     * 인증 코드 입력 감지 설정
     */
    private fun setupVerificationCodeWatcher() {
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val code = binding.etVerificationCode.text.toString()
                if (code.length == 6) {
                    verifyCode(code)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.etVerificationCode.addTextChangedListener(textWatcher)
    }

    /**
     * 인증 코드 전송
     */
    private fun sendVerificationCode() {
        binding.btnSendCode.isEnabled = false
        binding.btnSendCode.visibility = View.GONE

        // 6자리 랜덤 코드 생성
        verificationCode = generateRandomCode()

        // Firestore에 인증 코드 저장 (실제로는 서버 측에서 처리해야 함)
        val verificationData = hashMapOf(
            "email" to email,
            "code" to verificationCode,
            "expiresAt" to System.currentTimeMillis() + (5 * 60 * 1000) // 5분 후 만료
        )

        db.collection("verificationCodes").document(email)
            .set(verificationData)
            .addOnSuccessListener {
                // 실제 앱에서는 이메일로 인증 코드 전송 (Firebase Cloud Functions 등 이용)
                // 여기서는 토스트로 코드 표시 (테스트용)
                Toast.makeText(this, "인증 코드: $verificationCode", Toast.LENGTH_LONG).show()

                binding.etVerificationCode.hint = "_ _ _ _ _ _"
                startCountdownTimer()
            }
            .addOnFailureListener { e ->
                binding.btnSendCode.isEnabled = true
                binding.btnSendCode.visibility = View.VISIBLE
                Toast.makeText(this, "인증 코드 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * 인증 코드 확인
     */
    private fun verifyCode(inputCode: String) {
        binding.progressVerifying.visibility = View.VISIBLE

        // 코드 일치 여부 확인
        if (inputCode == verificationCode) {
            // 인증 성공 처리
            Toast.makeText(this, "인증이 완료되었습니다", Toast.LENGTH_SHORT).show()

            // 사용자 이메일 인증 상태 업데이트
            db.collection("users").whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val userDoc = documents.documents[0]
                        db.collection("users").document(userDoc.id)
                            .update("emailVerified", true)
                            .addOnSuccessListener {
                                // 인증 완료 후 다음 화면으로 이동
                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
                    }
                }
        } else {
            // 인증 실패
            binding.progressVerifying.visibility = View.GONE
            Toast.makeText(this, "인증 코드가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
            binding.etVerificationCode.setText("")
        }
    }

    /**
     * 카운트다운 타이머 시작
     */
    private fun startCountdownTimer() {
        // 기존 타이머 취소
        countDownTimer?.cancel()

        // 5분(300초) 타이머 시작
        timeRemaining = 300 * 1000

        countDownTimer = object : CountDownTimer(timeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                binding.tvTimer.text = "${minutes}분 ${seconds}초 후에 다시 가능합니다"
            }

            override fun onFinish() {
                binding.tvTimer.text = ""
                binding.btnSendCode.isEnabled = true
                binding.btnSendCode.visibility = View.VISIBLE
            }
        }.start()
    }

    /**
     * 6자리 랜덤 코드 생성
     */
    private fun generateRandomCode(): String {
        return (100000..999999).random().toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 타이머 해제
        countDownTimer?.cancel()
    }
}
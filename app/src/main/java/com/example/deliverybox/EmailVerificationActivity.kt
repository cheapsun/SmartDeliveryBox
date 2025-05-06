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

    // 로그인 재시도 최대 횟수
    private val MAX_LOGIN_ATTEMPTS = 3
    private var loginAttempts = 0

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
            // 인증 취소 확인 다이얼로그
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("인증 취소")
                .setMessage("인증을 취소하시면 처음부터 다시 시작해야 합니다. 취소하시겠습니까?")
                .setPositiveButton("취소") { _, _ ->
                    // 미인증 계정 삭제
                    auth.currentUser?.delete()
                    auth.signOut()
                    finish()
                }
                .setNegativeButton("계속 진행", null)
                .create()
                .show()
        }

        // 인증 메일 재전송 버튼
        binding.btnSendCode.setOnClickListener {
            resendVerificationEmail()
        }

        // 인증 확인 버튼
        binding.btnVerify.setOnClickListener {
            checkEmailVerification()
        }

        // 타이머 시작
        startCountdownTimer()

        // 화면 진입 시 바로 확인
        if (auth.currentUser != null) {
            auth.currentUser?.reload()?.addOnSuccessListener {
                if (auth.currentUser?.isEmailVerified == true) {
                    // 이미 인증된 경우 자동으로 다음 단계 진행
                    val tempPassword = intent.getStringExtra("tempPassword") ?: ""
                    loginWithTempPassword(email, tempPassword)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 액티비티 재개시 인증 상태 재확인 (사용자가 이메일에서 돌아온 경우)
        if (auth.currentUser != null) {
            auth.currentUser?.reload()?.addOnSuccessListener {
                if (auth.currentUser?.isEmailVerified == true) {
                    // 이미 인증된 경우 자동으로 다음 단계 진행
                    val email = binding.tvEmail.text.toString()
                    val tempPassword = intent.getStringExtra("tempPassword") ?: ""
                    loginWithTempPassword(email, tempPassword)
                }
            }
        }
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
     * 인증 완료된 경우 임시 비밀번호로 로그인 후 비밀번호 설정 화면으로 이동
     */
    private fun checkEmailVerification() {
        val user = auth.currentUser ?: return
        val email = binding.tvEmail.text.toString()
        val tempPassword = intent.getStringExtra("tempPassword") ?: ""

        binding.progressVerifying.visibility = View.VISIBLE

        // 인증 상태를 새로고침하기 위해 사용자 토큰 갱신
        user.reload().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // 갱신된 사용자 정보 가져오기
                val updatedUser = auth.currentUser

                if (updatedUser?.isEmailVerified == true) {
                    // 인증 성공 - 임시 비밀번호로 로그인
                    loginWithTempPassword(email, tempPassword)
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
     * 임시 비밀번호로 로그인
     * 인증이 완료된 계정에 대해 임시 비밀번호로 로그인 시도
     * 성공 시 비밀번호 설정 화면으로 이동
     */
    private fun loginWithTempPassword(email: String, tempPassword: String) {
        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            // 최대 시도 횟수 초과
            binding.progressVerifying.visibility = View.GONE
            Toast.makeText(this, "로그인 시도 횟수가 초과되었습니다. 다시 시작해주세요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loginAttempts++

        // Firebase로 로그인 시도
        auth.signInWithEmailAndPassword(email, tempPassword)
            .addOnSuccessListener {
                // 인증 완료 상태를 Firestore에 기록
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                db.collection("users").document(uid)
                    .update("emailVerified", true)
                    .addOnSuccessListener {
                        binding.progressVerifying.visibility = View.GONE

                        // 비밀번호 설정 화면으로 이동
                        val intent = Intent(this, SignupPasswordActivity::class.java)
                        intent.putExtra("email", email)
                        intent.putExtra("fromVerification", true) // 인증 후 진행임을 표시
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        binding.progressVerifying.visibility = View.GONE
                        Toast.makeText(this, "상태 업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressVerifying.visibility = View.GONE
                Toast.makeText(this, "로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()

                // 로그인 실패시 Firebase 인증은 되었지만 로그인은 안되는 상태이므로
                // 비밀번호 분실 상황으로 처리
                showPasswordResetPrompt(email)
            }
    }

    /**
     * 비밀번호 재설정 안내 대화상자
     * 임시 비밀번호로 로그인 실패 시 표시
     */
    private fun showPasswordResetPrompt(email: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("로그인 실패")
            .setMessage("계정 인증은 완료되었으나 로그인에 실패했습니다. 비밀번호를 재설정하시겠습니까?")
            .setPositiveButton("재설정") { _, _ ->
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(this, "비밀번호 재설정 메일이 전송되었습니다", Toast.LENGTH_SHORT).show()
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "재설정 메일 발송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("취소") { _, _ ->
                // 다시 인증 확인 시도
            }
            .create()
            .show()
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
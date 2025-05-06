package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivityEmailVerificationBinding
import com.example.deliverybox.utils.AccountUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class EmailVerificationActivity : AppCompatActivity() {

    private val TAG = "EmailVerificationActivity"

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
            AlertDialog.Builder(this)
                .setTitle("인증 취소")
                .setMessage("인증을 취소하시면 처음부터 다시 시작해야 합니다. 취소하시겠습니까?")
                .setPositiveButton("취소") { _, _ ->
                    auth.currentUser?.delete()
                    auth.signOut()
                    finish()
                }
                .setNegativeButton("계속 진행", null)
                .create()
                .show()
        }

        // 인증 메일 재전송 버튼
        binding.btnSendCode.setOnClickListener { resendVerificationEmail() }

        // 인증 확인 버튼
        binding.btnVerify.setOnClickListener { checkEmailVerification() }

        // 타이머 시작
        startCountdownTimer()

        // 화면 진입 시 바로 확인
        auth.currentUser?.reload()?.addOnSuccessListener {
            if (auth.currentUser?.isEmailVerified == true) {
                val tempPassword = intent.getStringExtra("tempPassword") ?: ""
                loginWithTempPassword(email, tempPassword)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        auth.currentUser?.reload()?.addOnSuccessListener {
            if (auth.currentUser?.isEmailVerified == true) {
                val email = binding.tvEmail.text.toString()
                val tempPassword = intent.getStringExtra("tempPassword") ?: ""
                loginWithTempPassword(email, tempPassword)
            }
        }
    }

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

    private fun checkEmailVerification() {
        val user = auth.currentUser ?: return
        val email = binding.tvEmail.text.toString()
        val tempPassword = intent.getStringExtra("tempPassword") ?: ""

        binding.progressVerifying.visibility = View.VISIBLE

        user.reload().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                if (auth.currentUser?.isEmailVerified == true) {
                    loginWithTempPassword(email, tempPassword)
                } else {
                    binding.progressVerifying.visibility = View.GONE
                    Toast.makeText(this, "아직 이메일 인증이 완료되지 않았습니다", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.progressVerifying.visibility = View.GONE
                Toast.makeText(this, "사용자 정보 갱신 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // → 여기에 수정된 loginWithTempPassword 메소드 적용
    private fun loginWithTempPassword(email: String, tempPassword: String) {
        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            binding.progressVerifying.visibility = View.GONE
            Toast.makeText(this, "로그인 시도 횟수가 초과되었습니다. 다시 시작해주세요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loginAttempts++
        binding.progressVerifying.visibility = View.VISIBLE

        try {
            // 이메일 인증 상태 저장
            AccountUtils.saveSignupState(
                AccountUtils.SignupState.EMAIL_VERIFIED,
                email
            )

            auth.signInWithEmailAndPassword(email, tempPassword)
                .addOnSuccessListener {
                    try {
                        val uid = auth.currentUser?.uid
                        if (uid == null) {
                            binding.progressVerifying.visibility = View.GONE
                            Toast.makeText(this, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        db.collection("users").document(uid)
                            .update("emailVerified", true)
                            .addOnSuccessListener {
                                try {
                                    binding.progressVerifying.visibility = View.GONE
                                    val intent = Intent(this, SignupPasswordActivity::class.java).apply {
                                        putExtra("email", email)
                                        putExtra("fromVerification", true)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    Log.d(TAG, "비밀번호 설정 화면으로 이동: $email")
                                    startActivity(intent)
                                    finish()
                                } catch (e: Exception) {
                                    Log.e(TAG, "화면 전환 중 오류: ${e.message}", e)
                                    binding.progressVerifying.visibility = View.GONE
                                    Toast.makeText(this, "화면 전환 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "상태 업데이트 실패: ${e.message}", e)
                                binding.progressVerifying.visibility = View.GONE
                                Toast.makeText(this, "상태 업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "인증 완료 처리 중 오류: ${e.message}", e)
                        binding.progressVerifying.visibility = View.GONE
                        Toast.makeText(this, "인증 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "로그인 실패: ${e.message}", e)
                    binding.progressVerifying.visibility = View.GONE
                    Toast.makeText(this, "로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    showPasswordResetPrompt(email)
                }
        } catch (e: Exception) {
            Log.e(TAG, "로그인 시도 중 예외 발생: ${e.message}", e)
            binding.progressVerifying.visibility = View.GONE
            Toast.makeText(this, "로그인 시도 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPasswordResetPrompt(email: String) {
        AlertDialog.Builder(this)
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
            .setNegativeButton("취소", null)
            .create()
            .show()
    }

    private fun startCountdownTimer() {
        countDownTimer?.cancel()
        if (timeRemaining <= 0) timeRemaining = VERIFICATION_TIMEOUT

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

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("인증 취소")
            .setMessage("인증을 취소하시면 처음부터 다시 시작해야 합니다. 취소하시겠습니까?")
            .setPositiveButton("취소") { _, _ ->
                val user = auth.currentUser
                if (user != null && !user.isEmailVerified) {
                    user.delete().addOnCompleteListener {
                        auth.signOut()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    auth.signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
            .setNegativeButton("계속 진행", null)
            .create()
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}

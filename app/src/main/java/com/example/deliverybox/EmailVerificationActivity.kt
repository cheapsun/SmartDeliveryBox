package com.example.deliverybox

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivityEmailVerificationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/**
 * 2단계: 이메일 인증
 */
class EmailVerificationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EmailVerifyActivity"
    }

    private lateinit var binding: ActivityEmailVerificationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var countDownTimer: CountDownTimer? = null
    private var timeRemaining: Long = 0
    private var tempPasswordHash: String? = null
    private var emailVerified = false

    // 인증 대기 시간 (5분)
    private val VERIFICATION_TIMEOUT = TimeUnit.MINUTES.toMillis(5)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 이전 화면에서 전달받은 이메일과 임시 비밀번호 해시
        val email = intent.getStringExtra("email") ?: ""
        tempPasswordHash = intent.getStringExtra("tempPasswordHash")

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
        binding.btnSendCode.setOnClickListener {
            resendVerificationEmail()
        }

        // 인증 확인 버튼 추가
        binding.btnVerify.setOnClickListener {
            if (emailVerified) {
                // 이미 인증 완료된 경우 - 비밀번호 설정 화면으로 이동
                proceedToPasswordSetting()
            } else {
                // 아직 인증되지 않은 경우 - 인증 상태 확인
                checkEmailVerification()
            }
        }

        // 타이머 시작
        startCountdownTimer()
    }

    override fun onResume() {
        super.onResume()

        // 앱이 다시 포그라운드로 돌아올 때 인증 상태 확인
        // (사용자가 이메일에서 인증 링크를 클릭한 후 돌아온 경우)
        val user = auth.currentUser
        if (user != null) {
            binding.progressVerifying.visibility = View.VISIBLE

            user.reload().addOnCompleteListener { task ->
                binding.progressVerifying.visibility = View.GONE

                if (task.isSuccessful && user.isEmailVerified) {
                    // 이미 인증이 완료된 상태로 돌아온 경우
                    emailVerified = true
                    updateVerificationUI(true)
                }
            }
        }
    }

    override fun onBackPressed() {
        // 뒤로가기 버튼 눌렀을 때 취소 다이얼로그 표시
        showCancelDialog()
    }

    /**
     * 인증 UI 업데이트
     */
    private fun updateVerificationUI(verified: Boolean) {
        if (verified) {
            binding.btnVerify.text = "다음 단계로"
            binding.btnVerify.isEnabled = true
            binding.btnVerify.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#448AFF"))
            binding.tvInfo.text = "이메일 인증이 완료되었습니다. 다음 단계로 진행해 주세요."
            binding.btnSendCode.isEnabled = false
            countDownTimer?.cancel()
            binding.tvTimer.text = "인증 완료"
        }
    }

    /**
     * 인증 취소 확인 다이얼로그
     */
    private fun showCancelDialog() {
        AlertDialog.Builder(this)
            .setTitle("인증 취소")
            .setMessage("이메일 인증을 취소하시겠습니까? 가입 과정을 처음부터 다시 시작해야 합니다.")
            .setPositiveButton("취소") { _, _ ->
                // 현재 사용자 삭제 및 초기 화면으로 이동
                val user = auth.currentUser
                if (user != null) {
                    deleteUserAndReturn(user.uid)
                } else {
                    finish() // SignupEmailActivity로 돌아감
                }
            }
            .setNegativeButton("계속 진행", null)
            .create()
            .show()
    }

    /**
     * 사용자 계정 삭제 및 돌아가기
     */
    private fun deleteUserAndReturn(uid: String) {
        val user = auth.currentUser ?: return

        db.collection("users").document(uid)
            .delete()
            .addOnCompleteListener {
                user.delete().addOnCompleteListener {
                    auth.signOut()
                    finish() // SignupEmailActivity로 돌아감
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
                    emailVerified = true
                    Toast.makeText(this, "이메일 인증 완료", Toast.LENGTH_SHORT).show()

                    // 인증 완료 상태를 Firestore에 기록
                    val uid = updatedUser.uid
                    db.collection("users").document(uid)
                        .update("emailVerified", true)
                        .addOnSuccessListener {
                            // UI 업데이트
                            binding.progressVerifying.visibility = View.GONE
                            updateVerificationUI(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "인증 상태 저장 실패: ${e.message}")
                            binding.progressVerifying.visibility = View.GONE
                            updateVerificationUI(true)
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
     * 비밀번호 설정 화면으로 이동
     */
    private fun proceedToPasswordSetting() {
        val user = auth.currentUser ?: return

        // 비밀번호 설정 화면으로 이동
        val intent = Intent(this, SignupPasswordActivity::class.java)
        intent.putExtra("email", user.email)
        intent.putExtra("isVerified", true)
        intent.putExtra("tempPasswordHash", tempPasswordHash)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
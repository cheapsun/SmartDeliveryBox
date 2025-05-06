package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.deliverybox.databinding.ActivityEmailVerificationBinding
import com.example.deliverybox.utils.AccountUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EmailVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmailVerificationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var email: String
    private var countDownTimer: CountDownTimer? = null
    private var timeRemaining: Long = 0
    private var isResentEmail = false
    private val TAG = "EmailVerification"

    // 인증 상태를 추적하는 열거형
    enum class VerificationState {
        SENT,      // 이메일 전송됨
        VERIFYING, // 인증 대기/확인 중
        SUCCESS    // 인증 성공
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase 인증 초기화
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 사용자가 없으면 로그인 화면으로 이동
        if (auth.currentUser == null) {
            Log.e(TAG, "인증 정보 없음, 로그인 화면으로 이동")
            Toast.makeText(this, "인증 정보가 없습니다. 처음부터 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 이전 화면에서 전달받은 이메일
        email = intent.getStringExtra("email") ?: auth.currentUser?.email ?: ""
        if (email.isEmpty()) {
            Log.e(TAG, "이메일 정보 없음")
            Toast.makeText(this, "이메일 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 이메일 표시
        binding.tvEmail.text = email

        // 사용자가 이미 인증을 완료했는지 확인
        checkIfAlreadyVerified()

        // 뒤로가기 버튼 설정
        binding.ibBack.setOnClickListener {
            showCancelConfirmationDialog()
        }

        // 초기 상태 설정 - 이메일 전송 상태
        updateVerificationState(VerificationState.SENT)

        // 타이머 시작
        startCountdownTimer(300) // 5분(300초) 타이머

        // 인증 확인 버튼 클릭 리스너
        binding.btnVerify.setOnClickListener {
            updateVerificationState(VerificationState.VERIFYING)
            checkEmailVerification()
        }

        // 인증 메일 다시 받기 버튼 클릭 리스너
        binding.btnSendCode.setOnClickListener {
            resendVerificationEmail()
        }
    }

    /**
     * 사용자가 이미 이메일 인증을 완료했는지 확인
     */
    private fun checkIfAlreadyVerified() {
        auth.currentUser?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                if (auth.currentUser?.isEmailVerified == true) {
                    Log.d(TAG, "이미 인증 완료됨, 비밀번호 설정 화면으로 이동")
                    // 이미 인증된 경우 비밀번호 설정 화면으로 이동
                    val intent = Intent(this, SignupPasswordActivity::class.java)
                    intent.putExtra("email", email)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    /**
     * 취소 확인 다이얼로그 표시
     */
    private fun showCancelConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("인증 취소")
            .setMessage("이메일 인증을 취소하시겠습니까? 인증을 취소하면 처음부터 다시 시작해야 합니다.")
            .setPositiveButton("확인") { _, _ ->
                // 임시 계정 삭제 후 이전 화면으로 이동
                AccountUtils.deleteTempAccountAndSignOut {
                    finish()
                }
            }
            .setNegativeButton("계속 진행", null)
            .show()
    }

    /**
     * 인증 상태에 따라 UI 업데이트
     */
    private fun updateVerificationState(state: VerificationState) {
        when (state) {
            VerificationState.SENT -> {
                binding.ivVerificationStatus.setImageResource(R.drawable.ic_mail_outline)
                binding.ivVerificationStatus.clearColorFilter()
                binding.tvStatusMessage.text = "인증 이메일이 발송되었습니다"
                binding.tvStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.blue_600))
                binding.progressVerifying.visibility = View.GONE
                binding.btnVerify.isEnabled = true
            }

            VerificationState.VERIFYING -> {
                binding.ivVerificationStatus.setImageResource(R.drawable.ic_hourglass_empty)
                binding.ivVerificationStatus.clearColorFilter()
                binding.tvStatusMessage.text = "인증 확인 중..."
                binding.tvStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.blue_600))
                binding.progressVerifying.visibility = View.VISIBLE
                binding.btnVerify.isEnabled = false
            }

            VerificationState.SUCCESS -> {
                binding.ivVerificationStatus.setImageResource(R.drawable.ic_check_circle)
                binding.ivVerificationStatus.setColorFilter(ContextCompat.getColor(this, R.color.green_success))
                binding.tvStatusMessage.text = "인증이 성공적으로 완료되었습니다"
                binding.tvStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.green_success))
                binding.progressVerifying.visibility = View.GONE
                binding.btnVerify.visibility = View.GONE
                binding.btnSendCode.visibility = View.GONE
            }
        }
    }

    /**
     * 이메일 인증 상태 확인
     */
    private fun checkEmailVerification() {
        if (auth.currentUser == null) {
            binding.progressVerifying.visibility = View.GONE
            binding.btnVerify.isEnabled = true
            Toast.makeText(this, "인증을 진행할 수 없습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            updateVerificationState(VerificationState.SENT)
            return
        }

        // Firebase에서 사용자 정보 새로고침
        auth.currentUser?.reload()?.addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful) {
                // 이메일 인증 여부 확인
                if (auth.currentUser?.isEmailVerified == true) {
                    // 인증 성공 - 상태 업데이트
                    updateVerificationState(VerificationState.SUCCESS)
                    Log.d(TAG, "이메일 인증 완료!")

                    // 잠시 대기 후 비밀번호 설정 화면으로 이동
                    binding.root.postDelayed({
                        // 비밀번호 설정 화면으로 이동
                        val intent = Intent(this, SignupPasswordActivity::class.java)
                        intent.putExtra("email", email)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent)
                        finish()
                    }, 1500) // 1.5초 대기 (성공 화면 표시)
                } else {
                    // 아직 인증되지 않음
                    binding.progressVerifying.visibility = View.GONE
                    binding.btnVerify.isEnabled = true
                    updateVerificationState(VerificationState.SENT) // 다시 전송 상태로
                    Toast.makeText(this, "아직 이메일 인증이 완료되지 않았습니다. 이메일을 확인해주세요.", Toast.LENGTH_LONG).show()
                }
            } else {
                // 새로고침 실패
                binding.progressVerifying.visibility = View.GONE
                binding.btnVerify.isEnabled = true
                updateVerificationState(VerificationState.SENT) // 다시 전송 상태로
                Toast.makeText(this, "인증 상태 확인 실패: ${reloadTask.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 인증 이메일 재전송
     */
    private fun resendVerificationEmail() {
        binding.btnSendCode.isEnabled = false

        if (auth.currentUser == null) {
            binding.btnSendCode.isEnabled = true
            Toast.makeText(this, "인증 메일을 전송할 수 없습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 이메일 인증 메일 재전송
        auth.currentUser?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateVerificationState(VerificationState.SENT) // 이메일 재전송 상태로 업데이트
                    Toast.makeText(this, "인증 메일이 재전송되었습니다.", Toast.LENGTH_SHORT).show()
                    isResentEmail = true

                    // 타이머 재시작
                    startCountdownTimer(300)
                } else {
                    binding.btnSendCode.isEnabled = true
                    Toast.makeText(this, "인증 메일 전송 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    /**
     * 카운트다운 타이머 시작
     */
    private fun startCountdownTimer(seconds: Int) {
        // 기존 타이머 취소
        countDownTimer?.cancel()

        // 타이머 시작 (초 단위로 매개변수 전달)
        timeRemaining = seconds * 1000L

        countDownTimer = object : CountDownTimer(timeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                binding.tvTimer.text = "남은 시간: ${minutes}분 ${seconds}초"

                // 재전송 버튼 비활성화 유지
                binding.btnSendCode.isEnabled = false
            }

            override fun onFinish() {
                binding.tvTimer.text = "인증 시간이 만료되었습니다. 재전송 버튼을 눌러 다시 인증해주세요."
                binding.btnSendCode.isEnabled = true
            }
        }.start()
    }

    /**
     * 인증 상태 주기적 확인 (onResume에서 실행)
     * 앱이 백그라운드에서 포그라운드로 전환될 때 인증 상태 확인
     */
    override fun onResume() {
        super.onResume()

        if (auth.currentUser == null) {
            Log.e(TAG, "사용자 정보 없음, 로그인 화면으로 이동")
            Toast.makeText(this, "인증 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 현재 인증 상태 확인 (사용자가 이메일에서 인증 링크를 클릭한 경우 처리)
        checkIfAlreadyVerified()
    }

    /**
     * 뒤로가기 버튼 처리
     */
    override fun onBackPressed() {
        showCancelConfirmationDialog()
    }

    /**
     * 액티비티 종료 시 타이머 해제
     */
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
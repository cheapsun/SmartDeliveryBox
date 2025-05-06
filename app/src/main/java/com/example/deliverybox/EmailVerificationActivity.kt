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

class EmailVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmailVerificationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var email: String
    private var countDownTimer: CountDownTimer? = null
    private var timeRemaining: Long = 60000  // 1분 (예시)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 전달받은 이메일
        email = intent.getStringExtra("email") ?: ""
        if (email.isEmpty()) {
            Toast.makeText(this, "이메일 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvEmail.text = email

        binding.ibBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnSendCode.setOnClickListener {
            sendVerificationEmail()
        }
    }

    private fun sendVerificationEmail() {
        binding.progressVerifying.visibility = View.VISIBLE
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                binding.progressVerifying.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(this, "인증 이메일을 전송했습니다.", Toast.LENGTH_SHORT).show()
                    startCountdown()
                } else {
                    Toast.makeText(this, "이메일 전송 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun startCountdown() {
        binding.btnSendCode.isEnabled = false
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(timeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                binding.tvCountdown.text = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.btnSendCode.isEnabled = true
                binding.tvCountdown.text = "재전송 가능"
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}

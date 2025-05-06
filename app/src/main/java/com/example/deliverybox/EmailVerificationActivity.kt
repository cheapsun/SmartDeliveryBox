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
    private lateinit var email: String
    private var countDownTimer: CountDownTimer? = null
    private var timeRemaining: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 이전 화면에서 전달받은 이메일
        if (email.isEmpty()) {
            Toast.makeText(this, "이메일 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 이메일 표시
        binding.tvEmail.text = email

        // 뒤로가기 버튼 설정
        binding.ibBack.setOnClickListener {
        }


        binding.btnSendCode.setOnClickListener {
        }
    }

    /**
     */
                }
            }
    }

    /**
     */
            }

                binding.progressVerifying.visibility = View.VISIBLE


                        startActivity(intent)
                } else {
                    binding.progressVerifying.visibility = View.GONE
            }
    }

    /**
     * 카운트다운 타이머 시작
     */
        // 기존 타이머 취소
        countDownTimer?.cancel()


        countDownTimer = object : CountDownTimer(timeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
            }

            override fun onFinish() {
                binding.btnSendCode.isEnabled = true
            }
        }.start()
    }

    /**
     */
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
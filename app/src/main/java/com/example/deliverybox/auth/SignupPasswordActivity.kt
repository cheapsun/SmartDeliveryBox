package com.example.deliverybox.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deliverybox.databinding.ActivitySignupPasswordBinding
import com.example.deliverybox.repository.FirebaseAuthRepository
import com.example.deliverybox.utils.AccountUtils
import com.example.deliverybox.utils.PasswordStrengthEvaluator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import com.example.deliverybox.core.MainActivity

class SignupPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupPasswordBinding

    // 입력 필드
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText

    // 3단계 Strength Bar 세그먼트
    private lateinit var segWeak: View
    private lateinit var segMedium: View
    private lateinit var segStrong: View

    // Strength 레이블
    private lateinit var tvStrength: TextView

    // 안내 문구, 버튼, 프로그레스
    private lateinit var tvInfo: TextView
    private lateinit var btnConfirm: Button
    private lateinit var progressBarLogin: ProgressBar

    // Repository
    private lateinit var authRepository: FirebaseAuthRepository

    // 상태
    private var email: String = ""
    private var fromVerification: Boolean = false

    private val TAG = "SignupPasswordActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = FirebaseAuthRepository()

        // Intent에서 email, fromVerification 처리
        email = intent.getStringExtra("email") ?: ""
        fromVerification = intent.getBooleanExtra("fromVerification", false)

        // 저장된 상태에서 email 복원
        if (email.isEmpty()) {
            val (state, savedEmail, _) = AccountUtils.restoreSignupStateFromPrefs(this)
            if (state == AccountUtils.SignupState.EMAIL_VERIFIED && !savedEmail.isNullOrEmpty()) {
                email = savedEmail
                fromVerification = true
                Log.d(TAG, "이메일 복원: $email")
            } else {
                Toast.makeText(this, "이메일 정보가 없습니다", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
        }

        // 세션 만료 시 자동 로그인 안내
        if (fromVerification && authRepository.getCurrentUser() == null) {
            Log.w(TAG, "세션 만료 - 자동 로그인")
            Intent(this, LoginActivity::class.java).apply {
                putExtra("email", email)
                putExtra("autoLogin", true)
                startActivity(this)
            }
            finish()
            return
        }

        // 뷰 바인딩
        etPassword        = binding.etPassword
        etConfirmPassword = binding.etConfirmPassword
        segWeak           = binding.segWeak
        segMedium         = binding.segMedium
        segStrong         = binding.segStrong
        tvStrength        = binding.tvStrength
        tvInfo            = binding.tvInfo
        btnConfirm        = binding.btnConfirm
        progressBarLogin  = binding.progressLogin

        // 안내 문구
        tvInfo.text = "비밀번호는 8~16자, 숫자·문자·특수문자를 조합해주세요."

        // TextWatcher 등록
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validatePasswordAndUpdateUI()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etPassword.addTextChangedListener(watcher)
        etConfirmPassword.addTextChangedListener(watcher)

        // 확인 버튼 클릭
        btnConfirm.setOnClickListener {
            val pw = etPassword.text.toString()
            val confirm = etConfirmPassword.text.toString()

            if (!validatePassword(pw)) {
                Toast.makeText(this, "안전한 비밀번호를 입력해 주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pw != confirm) {
                etConfirmPassword.error = "비밀번호가 일치하지 않습니다"
                return@setOnClickListener
            }

            setPassword(pw)
        }

        // 제목/버튼 텍스트 변경
        if (fromVerification) {
            binding.tvTitle.text = "비밀번호 설정"
            btnConfirm.text = "설정 완료"
        }
    }

    private fun validatePasswordAndUpdateUI() {
        val pw = binding.etPassword.text.toString()
        val confirm = binding.etConfirmPassword.text.toString()

        // 강도 평가 및 UI 업데이트
        val strength = PasswordStrengthEvaluator.evaluate(pw)
        updateStrengthUI(strength)

        // 버튼 활성화 로직
        val isMatch = pw.isNotEmpty() && pw == confirm
        val isSufficient = strength != com.example.deliverybox.utils.PasswordStrength.WEAK
        val enabled = isMatch && isSufficient

        btnConfirm.isEnabled = enabled
        btnConfirm.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                if (enabled) Color.parseColor("#6A8DFF")
                else Color.parseColor("#AABEFF")
            )

        // 비밀번호 일치 여부 확인
        if (confirm.isNotEmpty() && !isMatch) {
            binding.etConfirmPassword.error = "비밀번호가 일치하지 않습니다"
        } else {
            binding.etConfirmPassword.error = null
        }
    }

    private fun updateStrengthUI(str: com.example.deliverybox.utils.PasswordStrength) {
        val gray = Color.parseColor("#E0E0E0")
        binding.segWeak.setBackgroundColor(gray)
        binding.segMedium.setBackgroundColor(gray)
        binding.segStrong.setBackgroundColor(gray)

        when (str) {
            com.example.deliverybox.utils.PasswordStrength.WEAK -> segWeak.setBackgroundColor(str.color)
            com.example.deliverybox.utils.PasswordStrength.MEDIUM -> {
                segWeak.setBackgroundColor(str.color)
                segMedium.setBackgroundColor(str.color)
            }
            com.example.deliverybox.utils.PasswordStrength.STRONG -> {
                segWeak.setBackgroundColor(str.color)
                segMedium.setBackgroundColor(str.color)
                segStrong.setBackgroundColor(str.color)
            }
        }

        tvStrength.text = str.label
        tvStrength.setTextColor(str.color)
    }

    private fun validatePassword(password: String): Boolean {
        val strength = PasswordStrengthEvaluator.evaluate(password)
        return strength != com.example.deliverybox.utils.PasswordStrength.WEAK
    }

    private fun setPassword(password: String) {
        btnConfirm.isEnabled = false
        progressBarLogin.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = authRepository.setPassword(password)

                result.fold(
                    onSuccess = {
                        progressBarLogin.visibility = View.GONE
                        AccountUtils.saveSignupState(AccountUtils.SignupState.COMPLETED, email)

                        Toast.makeText(this@SignupPasswordActivity, "비밀번호 설정 완료!", Toast.LENGTH_SHORT).show()
                        Intent(this@SignupPasswordActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(this)
                        }
                        finish()
                    },
                    onFailure = { e ->
                        progressBarLogin.visibility = View.GONE
                        btnConfirm.isEnabled = true
                        Toast.makeText(this@SignupPasswordActivity, "비밀번호 설정 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                progressBarLogin.visibility = View.GONE
                btnConfirm.isEnabled = true
                Log.e(TAG, "비밀번호 설정 중 오류: ${e.message}", e)
                Toast.makeText(this@SignupPasswordActivity, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("비밀번호 설정 취소")
            .setMessage("취소하면 나중에 다시 설정해야 합니다.")
            .setPositiveButton("취소") { _, _ ->
                if (fromVerification) {
                    // suspend 함수는 코루틴 안에서 호출
                    lifecycleScope.launch {
                        try {
                            authRepository.logout()  // 이제 에러 없이 호출 가능

                            // 로그아웃 후 로그인 화면으로 이동
                            val intent = Intent(
                                this@SignupPasswordActivity,
                                LoginActivity::class.java
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            Log.e(TAG, "로그아웃 중 오류", e)
                            finish()
                        }
                    }
                } else {
                    super.onBackPressed()
                }
            }
            .setNegativeButton("계속 진행", null)
            .show()
    }

}
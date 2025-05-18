package com.example.deliverybox.auth

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deliverybox.databinding.ActivitySignupEmailBinding
import com.example.deliverybox.utils.AccountUtils
import kotlinx.coroutines.launch
import com.example.deliverybox.R

class SignupEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupEmailBinding
    private lateinit var etEmail: EditText
    private lateinit var checkBoxTerms: CheckBox
    private lateinit var btnNext: TextView
    private lateinit var progressEmailCheck: ProgressBar

    private val authRepository = FirebaseAuthRepository()
    private var isCheckingEmail = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 뷰 초기화
        etEmail = binding.etEmail
        checkBoxTerms = binding.checkboxTerms
        btnNext = binding.btnNextStep
        progressEmailCheck = binding.progressEmailCheck

        // 툴바 뒤로가기 설정
        binding.toolbarSignup.setNavigationOnClickListener {
            onBackPressed()
        }

        // 이메일 & 약관 체크 시 다음 버튼 활성화 상태 변경
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateEmailAndUpdateUI()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etEmail.addTextChangedListener(watcher)
        checkBoxTerms.setOnCheckedChangeListener { _, _ ->
            validateEmailAndUpdateUI()
        }

        // 약관 보기 링크 클릭
        binding.tvTermsLink.setOnClickListener {
            showTermsDialog()
        }

        // 다음 버튼 클릭 -> 이메일 중복 확인 및 계정 생성
        btnNext.setOnClickListener {
            if (isCheckingEmail) return@setOnClickListener

            val email = etEmail.text.toString().trim()

            // 이메일 형식 확인
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "올바른 이메일 형식이 아닙니다"
                return@setOnClickListener
            }

            // 이메일 중복 확인
            checkEmailExists(email)
        }
    }

    /**
     * 이메일 유효성 검사 및 UI 업데이트
     * 이메일 형식이 유효하고 약관에 동의한 경우에만 다음 버튼 활성화
     */
    private fun validateEmailAndUpdateUI() {
        val email = binding.etEmail.text.toString().trim()
        val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val isEnabled = isEmailValid && binding.checkboxTerms.isChecked && !isCheckingEmail

        // 버튼 상태 업데이트
        binding.btnNextStep.isEnabled = isEnabled
        if (isEnabled) {
            // 입력이 맞을 때 → 진한 파란색
            binding.btnNextStep.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#6A8DFF"))
        } else {
            // 입력이 안 맞을 때 → 연하늘색
            binding.btnNextStep.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#AABEFF"))
        }

        // 이메일 형식 에러 표시
        if (email.isNotEmpty() && !isEmailValid) {
            binding.layoutEmail.error = "올바른 이메일 형식이 아닙니다"
        } else {
            binding.layoutEmail.error = null
        }
    }

    /**
     * 이메일 중복 확인
     * 사용 가능한 이메일인 경우 임시 계정 생성 진행
     */
    private fun checkEmailExists(email: String) {
        isCheckingEmail = true
        progressEmailCheck.visibility = View.VISIBLE
        btnNext.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = authRepository.signup(email)

                result.fold(
                    onSuccess = { token ->
                        // 상태 저장
                        AccountUtils.saveSignupState(
                            AccountUtils.SignupState.NOT_LOGGED_IN,
                            email
                        )

                        // 인증 화면으로 이동
                        val intent = Intent(this@SignupEmailActivity, EmailVerificationActivity::class.java).apply {
                            putExtra("email", email)
                            putExtra("token", token)
                        }
                        startActivity(intent)
                    },
                    onFailure = { e ->
                        isCheckingEmail = false
                        progressEmailCheck.visibility = View.GONE
                        btnNext.isEnabled = true

                        Toast.makeText(this@SignupEmailActivity, "이메일 확인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                isCheckingEmail = false
                progressEmailCheck.visibility = View.GONE
                btnNext.isEnabled = true

                Toast.makeText(this@SignupEmailActivity, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 이용약관 다이얼로그 표시
     */
    private fun showTermsDialog() {
        val termsText = getString(R.string.terms_of_service)

        AlertDialog.Builder(this)
            .setTitle("이용약관 및 개인정보 처리방침")
            .setMessage(termsText)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    /**
     * 뒤로가기 처리
     */
    override fun onBackPressed() {
        // 커스텀 뒤로가기 처리
        AlertDialog.Builder(this)
            .setTitle("회원가입 취소")
            .setMessage("회원가입을 취소하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                // 회원가입 과정 취소, 로그인 화면으로 이동
                super.onBackPressed()
            }
            .setNegativeButton("아니오", null)
            .show()
    }
}
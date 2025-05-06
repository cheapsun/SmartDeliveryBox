package com.example.deliverybox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupEmailActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var checkBoxTerms: CheckBox
    private lateinit var btnNext: Button
    private lateinit var progressEmailCheck: ProgressBar
    private lateinit var tvTermsLink: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var isCheckingEmail = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_email)

        // Firebase 초기화
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // UI 요소 연결
        etEmail = findViewById(R.id.et_email)
        checkBoxTerms = findViewById(R.id.checkbox_terms)
        btnNext = findViewById(R.id.btn_next_step)
        progressEmailCheck = findViewById(R.id.progress_email_check)
        tvTermsLink = findViewById(R.id.tv_terms_link)

        // 이메일 & 약관 체크 시 다음 버튼 활성화 + 색상 변경
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

        // 다음 버튼 클릭 -> 비밀번호 설정 화면으로 이동
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

        // 약관 보기 링크 클릭
        tvTermsLink.setOnClickListener {
            showTermsDialog()
        }
    }

    /**
     * 이메일 유효성 검사 및 UI 업데이트
     */
    private fun validateEmailAndUpdateUI() {
        val email = etEmail.text.toString().trim()
        val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val isEnabled = isEmailValid && checkBoxTerms.isChecked && !isCheckingEmail

        btnNext.isEnabled = isEnabled
        if (isEnabled) {
            // 입력이 맞을 때 → 진한 파란색
            btnNext.setBackgroundColor(Color.parseColor("#448AFF"))
        } else {
            // 입력이 안 맞을 때 → 연하늘색
            btnNext.setBackgroundColor(Color.parseColor("#AABEFF"))
        }

        // 이메일 형식이 맞지 않으면 오류 표시
        if (email.isNotEmpty() && !isEmailValid) {
            etEmail.error = "올바른 이메일 형식이 아닙니다"
        } else {
            etEmail.error = null
        }
    }

    /**
     * 이메일 중복 확인
     */
    private fun checkEmailExists(email: String) {
        isCheckingEmail = true
        progressEmailCheck.visibility = View.VISIBLE
        btnNext.isEnabled = false

        // Firebase에서 이메일 중복 확인
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                isCheckingEmail = false
                progressEmailCheck.visibility = View.GONE

                if (task.isSuccessful) {
                    val signInMethods = task.result?.signInMethods
                    if (signInMethods != null && signInMethods.isNotEmpty()) {
                        // 이미 등록된 이메일
                        etEmail.error = "이미 등록된 이메일입니다"
                        btnNext.isEnabled = false
                    } else {
                        // 사용 가능한 이메일 - 다음 화면으로 이동
                        proceedToPasswordScreen(email)
                    }
                } else {
                    // 확인 실패
                    Toast.makeText(this, "이메일 확인 중 오류가 발생했습니다: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                    validateEmailAndUpdateUI()
                }
            }
    }

    /**
     * 비밀번호 설정 화면으로 이동
     */
    private fun proceedToPasswordScreen(email: String) {
        val intent = Intent(this, SignupPasswordActivity::class.java)
        intent.putExtra("email", email)
        startActivity(intent)
    }

    /**
     * 이용약관 다이얼로그 표시
     */
    private fun showTermsDialog() {
        AlertDialog.Builder(this)
            .setTitle("이용약관 및 개인정보 처리방침")
            .setMessage(getString(R.string.terms_of_service))
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}
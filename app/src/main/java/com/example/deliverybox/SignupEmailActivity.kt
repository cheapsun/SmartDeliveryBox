package com.example.deliverybox

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
import com.example.deliverybox.databinding.ActivitySignupEmailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class SignupEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupEmailBinding
    private lateinit var etEmail: EditText
    private lateinit var checkBoxTerms: CheckBox
    private lateinit var btnNext: TextView
    private lateinit var progressEmailCheck: ProgressBar

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

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

        // Firebase 초기화
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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
     * 사용 가능한 이메일인 경우 임시 계정 생성 진행
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
                        // 사용 가능한 이메일 - 임시 계정 생성
                        createTemporaryAccount(email)
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
     * 임시 비밀번호로 계정 생성
     * 임시 비밀번호는 16자리 랜덤 문자열로 생성
     * 계정 생성 후 이메일 인증 메일 발송
     */
    private fun createTemporaryAccount(email: String) {
        // 임시 비밀번호 생성
        val tempPassword = generateTempPassword()

        // 로딩 표시
        progressEmailCheck.visibility = View.VISIBLE

        // Firebase 계정 생성
        auth.createUserWithEmailAndPassword(email, tempPassword)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    // Firestore에 사용자 정보 저장
                    val userData = hashMapOf(
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "nickname" to null,
                        "isAdmin" to false,
                        "emailVerified" to false,
                        "passwordSet" to false, // 비밀번호 설정 여부
                        "tempPasswordHash" to tempPassword.hashCode() // 임시 비밀번호 해시값 저장 (보안용)
                    )

                    db.collection("users").document(user.uid)
                        .set(userData)
                        .addOnSuccessListener {
                            // 이메일 인증 메일 전송
                            user.sendEmailVerification()
                                .addOnSuccessListener {
                                    progressEmailCheck.visibility = View.GONE

                                    // 인증 화면으로 이동
                                    val intent = Intent(this, EmailVerificationActivity::class.java)
                                    intent.putExtra("email", email)
                                    intent.putExtra("tempPassword", tempPassword) // 임시 비밀번호 전달
                                    startActivity(intent)
                                }
                                .addOnFailureListener { e ->
                                    progressEmailCheck.visibility = View.GONE
                                    Toast.makeText(this, "인증 메일 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                    // 실패 시 계정 삭제
                                    user.delete()
                                    btnNext.isEnabled = true
                                }
                        }
                        .addOnFailureListener { e ->
                            progressEmailCheck.visibility = View.GONE
                            Toast.makeText(this, "사용자 정보 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            // 실패 시 계정 삭제
                            user.delete()
                            btnNext.isEnabled = true
                        }
                }
            }
            .addOnFailureListener { e ->
                progressEmailCheck.visibility = View.GONE
                Toast.makeText(this, "계정 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                btnNext.isEnabled = true
            }
    }

    /**
     * 임시 비밀번호 생성
     * 16자리 랜덤 문자열 (영문 대소문자, 숫자, 특수문자)
     */
    private fun generateTempPassword(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + "!@#$%^&*()_-+=<>?"
        return (1..16).map { allowedChars.random() }.joinToString("")
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
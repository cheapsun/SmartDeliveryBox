package com.example.deliverybox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

/**
 * 1단계: 이메일 입력 및 임시 계정 생성
 */
class SignupEmailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SignupEmailActivity"
    }

    private lateinit var toolbar: Toolbar
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
        toolbar = findViewById(R.id.toolbar_signup)
        etEmail = findViewById(R.id.et_email)
        checkBoxTerms = findViewById(R.id.checkbox_terms)
        btnNext = findViewById(R.id.btn_next_step)
        progressEmailCheck = findViewById(R.id.progress_email_check)
        tvTermsLink = findViewById(R.id.tv_terms_link)

        // 툴바 설정
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

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

        // 다음 버튼 클릭 -> 임시 계정 생성
        btnNext.setOnClickListener {
            if (isCheckingEmail) return@setOnClickListener

            val email = etEmail.text.toString().trim()

            // 이메일 형식 확인
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "올바른 이메일 형식이 아닙니다"
                return@setOnClickListener
            }

            // 이메일 중복 확인 및 임시 계정 생성
            checkEmailAndCreateTempAccount(email)
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
     * 이메일 중복 확인 및 임시 계정 생성
     */
    private fun checkEmailAndCreateTempAccount(email: String) {
        isCheckingEmail = true
        progressEmailCheck.visibility = View.VISIBLE
        btnNext.isEnabled = false

        // Firebase에서 이메일 중복 확인
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val signInMethods = task.result?.signInMethods
                    if (signInMethods != null && signInMethods.isNotEmpty()) {
                        // 이미 등록된 이메일
                        etEmail.error = "이미 등록된 이메일입니다"
                        isCheckingEmail = false
                        progressEmailCheck.visibility = View.GONE
                        btnNext.isEnabled = false
                    } else {
                        // 사용 가능한 이메일 - 임시 계정 생성
                        createTempAccount(email)
                    }
                } else {
                    // 확인 실패
                    Toast.makeText(this, "이메일 확인 중 오류가 발생했습니다: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                    isCheckingEmail = false
                    progressEmailCheck.visibility = View.GONE
                    validateEmailAndUpdateUI()
                }
            }
    }

    /**
     * 임시 비밀번호로 계정 생성
     */
    private fun createTempAccount(email: String) {
        // 임시 비밀번호 생성 (보안을 위해 강력한 랜덤 비밀번호 사용)
        val tempPassword = generateTempPassword()

        // Firebase 계정 생성
        auth.createUserWithEmailAndPassword(email, tempPassword)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    // 이메일 인증 메일 전송
                    user.sendEmailVerification()
                        .addOnSuccessListener {
                            // Firestore에 사용자 정보 저장
                            val userData = hashMapOf(
                                "email" to email,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "emailVerified" to false,
                                "passwordSet" to false,
                                "tempPasswordHash" to tempPassword.hashCode(), // 해시값만 저장 (보안)
                                "nickname" to null,
                                "isAdmin" to false
                            )

                            db.collection("users").document(user.uid)
                                .set(userData)
                                .addOnSuccessListener {
                                    // 인증 화면으로 이동
                                    Toast.makeText(this, "인증 메일이 발송되었습니다. 메일함을 확인해주세요.", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this, EmailVerificationActivity::class.java)
                                    intent.putExtra("email", email)
                                    intent.putExtra("tempPasswordHash", tempPassword.hashCode().toString())
                                    startActivity(intent)

                                    // 로딩 상태 해제
                                    isCheckingEmail = false
                                    progressEmailCheck.visibility = View.GONE
                                }
                                .addOnFailureListener { e ->
                                    // 사용자 정보 저장 실패 처리
                                    Log.e(TAG, "사용자 정보 저장 실패: ${e.message}")
                                    Toast.makeText(this, "사용자 정보 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                    isCheckingEmail = false
                                    progressEmailCheck.visibility = View.GONE
                                    btnNext.isEnabled = true
                                }
                        }
                        .addOnFailureListener { e ->
                            // 인증 메일 발송 실패 처리
                            Log.e(TAG, "인증 메일 전송 실패: ${e.message}")
                            Toast.makeText(this, "인증 메일 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            isCheckingEmail = false
                            progressEmailCheck.visibility = View.GONE
                            btnNext.isEnabled = true

                            // 실패 시 생성된 계정 삭제 (정리)
                            user.delete().addOnCompleteListener {
                                if (it.isSuccessful) {
                                    Log.d(TAG, "임시 계정 생성 실패로 인한 계정 삭제 완료")
                                }
                            }
                        }
                } else {
                    // 계정 생성 실패
                    Toast.makeText(this, "계정 생성 실패", Toast.LENGTH_SHORT).show()
                    isCheckingEmail = false
                    progressEmailCheck.visibility = View.GONE
                    btnNext.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                // 계정 생성 실패 처리
                Log.e(TAG, "계정 생성 실패: ${e.message}")
                Toast.makeText(this, "계정 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                isCheckingEmail = false
                progressEmailCheck.visibility = View.GONE
                btnNext.isEnabled = true
            }
    }

    /**
     * 임시 비밀번호 생성
     */
    private fun generateTempPassword(): String {
        // 임시 비밀번호는 16자 이상의 강력한 랜덤 문자열로 생성
        // Firebase 비밀번호 정책 준수 (최소 6자, 대소문자+숫자 포함)
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return "Temp$uuid" // 앞에 Temp를 붙여 대문자 포함하도록 함
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
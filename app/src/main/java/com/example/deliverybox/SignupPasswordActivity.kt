package com.example.deliverybox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.deliverybox.utils.FirestoreHelper
import com.google.firebase.auth.FirebaseAuth

class SignupPasswordActivity : AppCompatActivity() {

    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvStrength: TextView
    private lateinit var tvInfo: TextView
    private lateinit var btnConfirm: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var email: String

    // 비밀번호 강도 레벨
    enum class PasswordStrength(val text: String, val color: Int) {
        WEAK("약함", Color.RED),
        MEDIUM("중간", Color.rgb(255, 165, 0)), // 주황색
        STRONG("강함", Color.GREEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_password)

        auth = FirebaseAuth.getInstance()

        // 이전 화면에서 전달받은 이메일
        email = intent.getStringExtra("email") ?: ""
        if (email.isEmpty()) {
            Toast.makeText(this, "이메일 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // UI 요소 연결
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        tvStrength = findViewById(R.id.tv_strength)
        tvInfo = findViewById(R.id.tv_info)
        btnConfirm = findViewById(R.id.btn_confirm)

        // 비밀번호 안내 문구 업데이트
        tvInfo.text = "비밀번호는 8~16자. 숫자, 문자, 특수문자를 모두 포함해야 합니다."

        // TextWatcher 설정
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validatePasswordAndUpdateUI()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etPassword.addTextChangedListener(watcher)
        etConfirmPassword.addTextChangedListener(watcher)

        // 회원가입 완료 버튼
        btnConfirm.setOnClickListener {
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            // 최종 유효성 검사
            if (!validatePassword(password)) {
                Toast.makeText(this, "안전한 비밀번호를 입력해 주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                etConfirmPassword.error = "비밀번호가 일치하지 않습니다"
                return@setOnClickListener
            }

            // Firebase에 계정 생성
            createAccount(email, password)
        }
    }

    /**
     * 비밀번호 유효성 검사 및 UI 업데이트
     */
    private fun validatePasswordAndUpdateUI() {
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        // 비밀번호 강도 평가
        val passwordStrength = getPasswordStrength(password)

        // 강도 표시 업데이트
        tvStrength.text = "비밀번호 보안 수준: ${passwordStrength.text}"
        tvStrength.setTextColor(passwordStrength.color)

        // 비밀번호 확인 일치 여부
        val isMatching = password.isNotEmpty() && password == confirmPassword

        // 강도가 적어도 중간 이상이고, 비밀번호가 일치할 때만 버튼 활성화
        val isStrengthSufficient = passwordStrength != PasswordStrength.WEAK
        val isEnabled = isStrengthSufficient && isMatching

        btnConfirm.isEnabled = isEnabled
        if (isEnabled) {
            btnConfirm.setBackgroundColor(Color.parseColor("#448AFF"))
        } else {
            btnConfirm.setBackgroundColor(Color.parseColor("#AABEFF"))
        }

        // 비밀번호 불일치 오류 표시
        if (confirmPassword.isNotEmpty() && !isMatching) {
            etConfirmPassword.error = "비밀번호가 일치하지 않습니다"
        } else {
            etConfirmPassword.error = null
        }
    }

    /**
     * 비밀번호 강도 평가
     */
    private fun getPasswordStrength(password: String): PasswordStrength {
        if (password.length < 8) return PasswordStrength.WEAK

        var hasLetter = false
        var hasDigit = false
        var hasSpecial = false

        for (c in password) {
            when {
                c.isLetter() -> hasLetter = true
                c.isDigit() -> hasDigit = true
                !c.isLetterOrDigit() -> hasSpecial = true
            }
        }

        return when {
            hasLetter && hasDigit && hasSpecial -> PasswordStrength.STRONG
            hasLetter && hasDigit -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }
    }

    /**
     * 비밀번호 유효성 검사
     */
    private fun validatePassword(password: String): Boolean {
        if (password.length < 8) return false

        var hasLetter = false
        var hasDigit = false
        var hasSpecial = false

        for (c in password) {
            when {
                c.isLetter() -> hasLetter = true
                c.isDigit() -> hasDigit = true
                !c.isLetterOrDigit() -> hasSpecial = true
            }
        }

        return (hasLetter && hasDigit)  // 최소 문자와 숫자는 필수
    }

    /**
     * Firebase 계정 생성
     */
    private fun createAccount(email: String, password: String) {
        btnConfirm.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    // 이메일 인증 메일 전송
                    user.sendEmailVerification()
                        .addOnSuccessListener {
                            // Firestore에 사용자 정보 저장
                            FirestoreHelper.createUserDocument(user.uid, email) { success ->
                                if (success) {
                                    Toast.makeText(this, "회원가입 완료! 이메일을 확인해주세요.", Toast.LENGTH_SHORT).show()

                                    // 이메일 인증 화면으로 이동
                                    val intent = Intent(this, EmailVerificationActivity::class.java)
                                    intent.putExtra("email", email)
                                    startActivity(intent)
                                    finishAffinity()  // 이전 액티비티 모두 종료
                                } else {
                                    Toast.makeText(this, "사용자 정보 저장 실패", Toast.LENGTH_SHORT).show()
                                    btnConfirm.isEnabled = true
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "인증 메일 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            btnConfirm.isEnabled = true
                        }
                } else {
                    Toast.makeText(this, "계정 생성 실패", Toast.LENGTH_SHORT).show()
                    btnConfirm.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "회원가입 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                btnConfirm.isEnabled = true
            }
    }
}
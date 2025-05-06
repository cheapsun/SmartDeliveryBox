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
import com.example.deliverybox.databinding.ActivitySignupPasswordBinding
import com.example.deliverybox.utils.FirestoreHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class SignupPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupPasswordBinding
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvStrength: TextView
    private lateinit var tvInfo: TextView
    private lateinit var btnConfirm: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var email: String
    private var fromVerification = false

    // 비밀번호 강도 레벨
    enum class PasswordStrength(val text: String, val color: Int) {
        WEAK("약함", Color.RED),
        MEDIUM("중간", Color.rgb(255, 165, 0)), // 주황색
        STRONG("강함", Color.GREEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 이전 화면에서 전달받은 이메일
        email = intent.getStringExtra("email") ?: ""
        // 인증 화면에서 넘어왔는지 여부
        fromVerification = intent.getBooleanExtra("fromVerification", false)

        if (email.isEmpty()) {
            Toast.makeText(this, "이메일 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 인증 후 진행이고 로그인 상태가 아니면 오류
        if (fromVerification && auth.currentUser == null) {
            Toast.makeText(this, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // UI 요소 초기화
        etPassword = binding.etPassword
        etConfirmPassword = binding.etConfirmPassword
        tvStrength = binding.tvStrength
        tvInfo = binding.tvInfo
        btnConfirm = binding.btnConfirm

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

            if (fromVerification) {
                // 인증 후 비밀번호 설정 (이미 로그인된 상태)
                updatePassword(auth.currentUser!!, password)
            } else {
                // 이전 방식: 인증 전 계정 생성 (기존 코드 흐름)
                createAccount(email, password)
            }
        }

        // 인증 흐름이면 제목 변경
        if (fromVerification) {
            binding.tvTitle.text = "비밀번호 설정"
            btnConfirm.text = "설정 완료"
        }
    }

    /**
     * 비밀번호 유효성 검사 및 UI 업데이트
     * 비밀번호 강도와 일치 여부에 따라 버튼 활성화 및 색상 변경
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
     * 길이와 문자 구성에 따라 약함/중간/강함 판단
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
     * 길이 8자 이상 및 문자+숫자 조합 필수
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
     * 계정 생성 (이전 방식)
     * 이메일과 비밀번호로 Firebase 계정 생성 후 이메일 인증 메일 발송
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

    /**
     * 비밀번호 업데이트 (인증 후 설정)
     * 이메일 인증이 완료된 계정의 비밀번호를 설정하는 메서드
     */
    private fun updatePassword(user: FirebaseUser, newPassword: String) {
        btnConfirm.isEnabled = false

        // 프로그레스 표시 추가
        val progressBar = binding.root.findViewById<View>(R.id.progress_login)
        if (progressBar != null) progressBar.visibility = View.VISIBLE

        user.updatePassword(newPassword)
            .addOnSuccessListener {
                // 비밀번호 설정 완료 표시
                db.collection("users").document(user.uid)
                    .update(
                        mapOf(
                            "passwordSet" to true,
                            "tempPasswordHash" to null // 임시 비밀번호 해시 삭제
                        )
                    )
                    .addOnSuccessListener {
                        if (progressBar != null) progressBar.visibility = View.GONE
                        Toast.makeText(this, "비밀번호 설정 완료!", Toast.LENGTH_SHORT).show()

                        // 메인 화면으로 이동
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        if (progressBar != null) progressBar.visibility = View.GONE
                        Toast.makeText(this, "상태 업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        btnConfirm.isEnabled = true
                    }
            }
            .addOnFailureListener { e ->
                if (progressBar != null) progressBar.visibility = View.GONE
                Toast.makeText(this, "비밀번호 업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                btnConfirm.isEnabled = true
            }
    }
}
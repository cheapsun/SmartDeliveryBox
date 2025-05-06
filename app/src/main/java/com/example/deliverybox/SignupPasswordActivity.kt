package com.example.deliverybox

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
import com.example.deliverybox.databinding.ActivitySignupPasswordBinding
import com.example.deliverybox.utils.AccountUtils
import com.example.deliverybox.utils.FirestoreHelper
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

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

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // 상태
    private var email: String = ""
    private var fromVerification: Boolean = false

    private val TAG = "SignupPasswordActivity"

    enum class PasswordStrength(val color: Int, val label: String) {
        WEAK(Color.RED, "약함"),
        MEDIUM(Color.parseColor("#FFA500"), "중간"),
        STRONG(Color.GREEN, "강함")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

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
        if (fromVerification && auth.currentUser == null) {
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
            if (fromVerification) updatePassword(auth.currentUser!!, pw)
            else createAccount(email, pw)
        }

        // 제목/버튼 텍스트 변경
        if (fromVerification) {
            binding.tvTitle.text = "비밀번호 설정"
            btnConfirm.text = "설정 완료"
        }
    }

    private fun validatePasswordAndUpdateUI() {
        val pw = etPassword.text.toString()
        val confirm = etConfirmPassword.text.toString()

        // 강도 평가 및 UI 업데이트
        val strength = evaluateStrength(pw)
        updateStrengthUI(strength)

        // 버튼 활성화 로직
        val isMatch = pw.isNotEmpty() && pw == confirm
        val isSufficient = strength != PasswordStrength.WEAK
        val enabled = isMatch && isSufficient

        btnConfirm.isEnabled = enabled
        btnConfirm.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                if (enabled) Color.parseColor("#448AFF")
                else Color.parseColor("#AABEFF")
            )

        if (confirm.isNotEmpty() && !isMatch) {
            etConfirmPassword.error = "비밀번호가 일치하지 않습니다"
        } else {
            etConfirmPassword.error = null
        }
    }

    private fun evaluateStrength(pw: String): PasswordStrength {
        val hasLetter  = pw.any { it.isLetter() }
        val hasDigit   = pw.any { it.isDigit() }
        val hasSpecial = pw.any { !it.isLetterOrDigit() }

        return when {
            pw.length < 8                          -> PasswordStrength.WEAK
            hasLetter && hasDigit && hasSpecial   -> PasswordStrength.STRONG
            hasLetter && hasDigit                  -> PasswordStrength.MEDIUM
            else                                   -> PasswordStrength.WEAK
        }
    }

    private fun updateStrengthUI(str: PasswordStrength) {
        val gray = Color.parseColor("#E0E0E0")
        segWeak.setBackgroundColor(gray)
        segMedium.setBackgroundColor(gray)
        segStrong.setBackgroundColor(gray)

        when (str) {
            PasswordStrength.WEAK -> segWeak.setBackgroundColor(str.color)
            PasswordStrength.MEDIUM -> {
                segWeak.setBackgroundColor(str.color)
                segMedium.setBackgroundColor(str.color)
            }
            PasswordStrength.STRONG -> {
                segWeak.setBackgroundColor(str.color)
                segMedium.setBackgroundColor(str.color)
                segStrong.setBackgroundColor(str.color)
            }
        }

        tvStrength.text = str.label
        tvStrength.setTextColor(str.color)
    }

    private fun validatePassword(password: String): Boolean {
        if (password.length < 8) return false
        val hasL = password.any { it.isLetter() }
        val hasD = password.any { it.isDigit() }
        return hasL && hasD
    }

    private fun createAccount(email: String, password: String) {
        btnConfirm.isEnabled = false
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                result.user?.let { user ->
                    user.sendEmailVerification()
                        .addOnSuccessListener {
                            FirestoreHelper.createUserDocument(user.uid, email) { success ->
                                if (success) {
                                    Toast.makeText(this, "회원가입 완료! 이메일을 확인해주세요.", Toast.LENGTH_SHORT).show()
                                    Intent(this, EmailVerificationActivity::class.java).apply {
                                        putExtra("email", email)
                                        startActivity(this)
                                    }
                                    finishAffinity()
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
                } ?: run {
                    Toast.makeText(this, "계정 생성 실패", Toast.LENGTH_SHORT).show()
                    btnConfirm.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "회원가입 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                btnConfirm.isEnabled = true
            }
    }

    private fun updatePassword(user: FirebaseUser, newPassword: String) {
        btnConfirm.isEnabled = false
        progressBarLogin.visibility = View.VISIBLE

        user.updatePassword(newPassword)
            .addOnSuccessListener {
                db.collection("users").document(user.uid)
                    .update(
                        mapOf(
                            "passwordSet" to true,
                            "tempPasswordHash" to null
                        )
                    )
                    .addOnSuccessListener {
                        progressBarLogin.visibility = View.GONE
                        AccountUtils.saveSignupState(
                            AccountUtils.SignupState.COMPLETED, email
                        )
                        Toast.makeText(this, "비밀번호 설정 완료!", Toast.LENGTH_SHORT).show()
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(this)
                        }
                        finish()
                    }
                    .addOnFailureListener { e ->
                        progressBarLogin.visibility = View.GONE
                        Toast.makeText(this, "상태 업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        btnConfirm.isEnabled = true
                    }
            }
            .addOnFailureListener { e ->
                progressBarLogin.visibility = View.GONE
                Toast.makeText(this, "비밀번호 업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                btnConfirm.isEnabled = true
            }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("비밀번호 설정 취소")
            .setMessage("취소하면 나중에 다시 설정해야 합니다.")
            .setPositiveButton("취소") { _, _ ->
                if (fromVerification) {
                    auth.signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } else super.onBackPressed()
            }
            .setNegativeButton("계속 진행", null)
            .show()
    }
}

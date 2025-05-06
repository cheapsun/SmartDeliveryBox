package com.example.deliverybox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.firestore.FirebaseFirestore

/**
 * 3단계: 비밀번호 설정
 */
class SignupPasswordActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SignupPasswordActivity"
    }

    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvStrength: TextView
    private lateinit var tvInfo: TextView
    private lateinit var btnConfirm: Button
    private lateinit var progressLogin: View

    // 비밀번호 강도 바 추가
    private lateinit var strengthBarLayout: LinearLayout
    private lateinit var segWeak: View
    private lateinit var segMedium: View
    private lateinit var segStrong: View

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var email: String
    private var isVerified: Boolean = false
    private var tempPasswordHash: String? = null

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
        db = FirebaseFirestore.getInstance()

        // 이전 화면에서 전달받은 정보
        email = intent.getStringExtra("email") ?: ""
        isVerified = intent.getBooleanExtra("isVerified", false)
        tempPasswordHash = intent.getStringExtra("tempPasswordHash")

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
        progressLogin = findViewById(R.id.progress_login)

        // 비밀번호 강도 바 요소 연결
        strengthBarLayout = findViewById(R.id.ll_strength_bar)
        segWeak = findViewById(R.id.seg_weak)
        segMedium = findViewById(R.id.seg_medium)
        segStrong = findViewById(R.id.seg_strong)

        // 인증 상태에 따라 제목/안내 문구 변경
        if (isVerified) {
            findViewById<TextView>(R.id.tv_title).text = "비밀번호 설정"
            tvInfo.text = "이제 실제 사용할 비밀번호를 입력해 주세요. (8자 이상, 숫자+문자 조합)"
        } else {
            // 비정상 접근 처리
            Toast.makeText(this, "비정상적인 접근입니다. 처음부터 다시 시작해주세요.", Toast.LENGTH_SHORT).show()
            auth.signOut()
            startActivity(Intent(this, SignupEmailActivity::class.java))
            finish()
            return
        }

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

        // 완료 버튼 클릭
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

            // 인증 중단 방지 다이얼로그
            showConfirmationDialog(password)
        }

        // 초기 상태 설정 (모든 세그먼트 회색)
        resetStrengthBar()
    }

    /**
     * 비밀번호 설정 확인 다이얼로그
     */
    private fun showConfirmationDialog(password: String) {
        AlertDialog.Builder(this)
            .setTitle("비밀번호 설정")
            .setMessage("이 비밀번호로 설정하시겠습니까? 앱 로그인에 사용됩니다.")
            .setPositiveButton("확인") { _, _ ->
                updateUserPassword(password)
            }
            .setNegativeButton("취소", null)
            .create()
            .show()
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

        // 강도 바 업데이트
        updateStrengthBar(passwordStrength)

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
     * 비밀번호 강도 바 초기화
     */
    private fun resetStrengthBar() {
        segWeak.setBackgroundColor(Color.parseColor("#E0E0E0"))
        segMedium.setBackgroundColor(Color.parseColor("#E0E0E0"))
        segStrong.setBackgroundColor(Color.parseColor("#E0E0E0"))
    }

    /**
     * 비밀번호 강도에 따라 강도 바 업데이트
     */
    private fun updateStrengthBar(strength: PasswordStrength) {
        // 초기화
        resetStrengthBar()

        when (strength) {
            PasswordStrength.WEAK -> {
                // 약함: 첫 번째 세그먼트만 빨간색
                segWeak.setBackgroundColor(PasswordStrength.WEAK.color)
            }
            PasswordStrength.MEDIUM -> {
                // 중간: 첫 번째, 두 번째 세그먼트 주황색
                segWeak.setBackgroundColor(PasswordStrength.MEDIUM.color)
                segMedium.setBackgroundColor(PasswordStrength.MEDIUM.color)
            }
            PasswordStrength.STRONG -> {
                // 강함: 모든 세그먼트 녹색
                segWeak.setBackgroundColor(PasswordStrength.STRONG.color)
                segMedium.setBackgroundColor(PasswordStrength.STRONG.color)
                segStrong.setBackgroundColor(PasswordStrength.STRONG.color)
            }
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

        for (c in password) {
            when {
                c.isLetter() -> hasLetter = true
                c.isDigit() -> hasDigit = true
            }
        }

        return (hasLetter && hasDigit)  // 최소 문자와 숫자는 필수
    }

    /**
     * 사용자 비밀번호 업데이트
     */
    private fun updateUserPassword(newPassword: String) {
        val user = auth.currentUser
        if (user == null) {
            // 로그인 상태가 아님 - 세션 만료
            Toast.makeText(this, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        progressLogin.visibility = View.VISIBLE
        btnConfirm.isEnabled = false

        // 비밀번호 업데이트 시도
        try {
            user.updatePassword(newPassword)
                .addOnSuccessListener {
                    // 비밀번호 설정 완료 상태 Firestore에 저장
                    db.collection("users").document(user.uid)
                        .update(
                            mapOf(
                                "passwordSet" to true,
                                "tempPasswordHash" to null // 임시 비밀번호 해시 제거
                            )
                        )
                        .addOnSuccessListener {
                            Toast.makeText(this, "비밀번호 설정 완료! 회원가입이 완료되었습니다.", Toast.LENGTH_SHORT).show()

                            // 메인 화면으로 이동
                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            // Firestore 업데이트 실패 - 메인 화면으로 이동 (비밀번호는 이미 업데이트됨)
                            Log.e(TAG, "Firestore 업데이트 실패: ${e.message}")
                            Toast.makeText(this, "회원가입 완료! (일부 정보 저장 실패)", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                }
                .addOnFailureListener { e ->
                    progressLogin.visibility = View.GONE

                    // 업데이트 실패 - 재인증 필요한 경우 (세션 만료)
                    if (e is FirebaseAuthInvalidCredentialsException) {
                        Log.e(TAG, "업데이트 실패 - 재인증 필요: ${e.message}")
                        Toast.makeText(this, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()

                        // 사용자 로그아웃 및 로그인 화면으로 이동
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    } else {
                        Log.e(TAG, "비밀번호 업데이트 실패: ${e.message}")
                        Toast.makeText(this, "비밀번호 업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        btnConfirm.isEnabled = true
                    }
                }
        } catch (e: Exception) {
            progressLogin.visibility = View.GONE
            Log.e(TAG, "비밀번호 업데이트 예외: ${e.message}")
            Toast.makeText(this, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            btnConfirm.isEnabled = true
        }
    }

    /**
     * 뒤로가기 버튼 처리 (인증 중단 방지)
     */
    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("비밀번호 설정 취소")
            .setMessage("비밀번호 설정을 취소하시겠습니까? 가입 과정을 다시 시작해야 합니다.")
            .setPositiveButton("취소") { _, _ ->
                // 현재 사용자 삭제 및 초기 화면으로 이동
                val user = auth.currentUser
                if (user != null) {
                    db.collection("users").document(user.uid)
                        .delete()
                        .addOnCompleteListener {
                            user.delete().addOnCompleteListener {
                                auth.signOut()
                                startActivity(Intent(this, SignupEmailActivity::class.java))
                                finish()
                            }
                        }
                } else {
                    startActivity(Intent(this, SignupEmailActivity::class.java))
                    finish()
                }
            }
            .setNegativeButton("계속 진행", null)
            .create()
            .show()
    }
}
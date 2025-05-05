package com.example.deliverybox

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivitySignupPasswordBinding
import com.example.deliverybox.utils.FirestoreHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class SignupPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupPasswordBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var email: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 뷰 바인딩 초기화
        binding = ActivitySignupPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase 인증 초기화
        auth = FirebaseAuth.getInstance()

        // 이전 화면에서 전달받은 이메일
        email = intent.getStringExtra("email") ?: ""
        if (email.isEmpty()) {
            Toast.makeText(this, "오류가 발생했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 뒤로가기 버튼 설정
        binding.toolbarPassword.setNavigationOnClickListener {
            finish()
        }

        // 비밀번호 입력 감지 설정
        setupPasswordWatcher()

        // 회원가입 버튼 클릭 리스너
        binding.btnConfirm.setOnClickListener {
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            // 유효성 검사
            if (!validatePassword(password, confirmPassword)) return@setOnClickListener

            // 네트워크 연결 확인
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "인터넷 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 회원가입 진행
            registerUser(email, password)
        }
    }

    /**
     * 비밀번호 입력 감지 설정
     */
    private fun setupPasswordWatcher() {
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = binding.etPassword.text.toString()
                val confirmPassword = binding.etConfirmPassword.text.toString()

                // 비밀번호 강도 측정
                val passwordStrength = getPasswordStrength(password)

                // 비밀번호 일치 확인 및 버튼 활성화 설정
                updateConfirmButtonState(password, confirmPassword)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.etPassword.addTextChangedListener(textWatcher)
        binding.etConfirmPassword.addTextChangedListener(textWatcher)
    }

    /**
     * 비밀번호 강도 측정
     * @return 0-100 사이의 점수
     */
    private fun getPasswordStrength(password: String): Int {
        var score = 0

        // 길이 점수 (최대 40점)
        when {
            password.length >= 14 -> score += 40
            password.length >= 12 -> score += 30
            password.length >= 10 -> score += 20
            password.length >= 8 -> score += 10
        }

        // 문자 구성 점수 (각 20점, 최대 60점)
        if (password.any { it.isUpperCase() }) score += 20
        if (password.any { it.isLowerCase() }) score += 20
        if (password.any { it.isDigit() }) score += 20

        // 특수문자 (최대 20점)
        if (password.any { !it.isLetterOrDigit() }) score += 20

        // 강도별 색상 및 텍스트 설정
        val strengthColor = when {
            score >= 80 -> Color.parseColor("#00C851") // 강함 (녹색)
            score >= 50 -> Color.parseColor("#FFBB33") // 중간 (주황색)
            else -> Color.parseColor("#FF4444") // 약함 (빨간색)
        }

        binding.progressPasswordStrength.progress = score
        binding.progressPasswordStrength.progressTintList = ColorStateList.valueOf(strengthColor)

        val strengthText = when {
            score >= 80 -> "강함"
            score >= 50 -> "중간"
            else -> "약함"
        }

        binding.tvStrength.text = "비밀번호 보안 수준: $strengthText"
        binding.tvStrength.setTextColor(strengthColor)

        return score
    }

    /**
     * 회원가입 버튼 상태 업데이트
     */
    private fun updateConfirmButtonState(password: String, confirmPassword: String) {
        val isValid = password.length >= 8 && password == confirmPassword

        binding.btnConfirm.isEnabled = isValid
        if (isValid) {
            binding.btnConfirm.setBackgroundColor(Color.parseColor("#448AFF")) // 활성화
        } else {
            binding.btnConfirm.setBackgroundColor(Color.parseColor("#AABEFF")) // 비활성화
        }

        // 비밀번호 일치 오류 표시
        if (confirmPassword.isNotEmpty() && password != confirmPassword) {
            binding.layoutConfirmPassword.error = "비밀번호가 일치하지 않습니다"
        } else {
            binding.layoutConfirmPassword.error = null
        }
    }

    /**
     * 비밀번호 유효성 검사
     */
    private fun validatePassword(password: String, confirmPassword: String): Boolean {
        var isValid = true

        // 비밀번호 길이 검사
        if (password.length < 8) {
            binding.layoutPassword.error = "비밀번호는 최소 8자 이상이어야 합니다"
            isValid = false
        } else {
            binding.layoutPassword.error = null
        }

        // 비밀번호 일치 검사
        if (password != confirmPassword) {
            binding.layoutConfirmPassword.error = "비밀번호가 일치하지 않습니다"
            isValid = false
        } else {
            binding.layoutConfirmPassword.error = null
        }

        return isValid
    }

    /**
     * 회원가입 처리
     */
    private fun registerUser(email: String, password: String) {
        // 로딩 표시
        binding.progressSignup.visibility = View.VISIBLE
        binding.btnConfirm.isEnabled = false

        // Firebase Auth로 사용자 생성
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener

                // Firestore에 사용자 정보 저장
                FirestoreHelper.createUserDocument(uid, email) { success ->
                    binding.progressSignup.visibility = View.GONE

                    if (success) {
                        // 이메일 인증 메일 발송
                        auth.currentUser?.sendEmailVerification()
                            ?.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(this, "회원가입 완료! 이메일 인증을 진행해주세요.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, "회원가입은 완료되었으나 인증 메일 발송에 실패했습니다.", Toast.LENGTH_LONG).show()
                                }

                                // 로그인 화면으로 이동
                                startActivity(Intent(this, LoginActivity::class.java))
                                finishAffinity()
                            }
                    } else {
                        binding.btnConfirm.isEnabled = true
                        Toast.makeText(this, "사용자 정보 저장 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                binding.progressSignup.visibility = View.GONE
                binding.btnConfirm.isEnabled = true

                // 오류 종류에 따른 메시지 처리
                val errorMessage = when (e) {
                    is FirebaseAuthUserCollisionException -> "이미 사용 중인 이메일입니다"
                    is FirebaseAuthWeakPasswordException -> "비밀번호가 너무 약합니다"
                    else -> "회원가입 실패: ${e.message}"
                }

                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * 네트워크 연결 상태 확인
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
package com.example.deliverybox

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivityLoginBinding
import com.example.deliverybox.utils.FirestoreHelper
import com.example.deliverybox.utils.NetworkUtils
import com.example.deliverybox.utils.SharedPrefsHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 뷰 바인딩 초기화
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase 인증 객체 초기화
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 로그인 버튼 상태 설정
        setupLoginButtonState()

        // "가입하기" 텍스트의 색상 스타일 설정
        setSignupText()

        // 버튼 클릭 리스너 설정
        setupClickListeners()
    }

    /**
     * 이메일과 비밀번호 입력값에 따라 로그인 버튼 활성화 상태 및 색상 설정
     */
    private fun setupLoginButtonState() {
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString().trim()
                binding.btnLogin.isEnabled = email.isNotEmpty() && password.isNotEmpty()

                // 버튼 색상 변경
                if (binding.btnLogin.isEnabled) {
                    binding.btnLogin.setBackgroundColor(Color.parseColor("#448AFF")) // 활성화 색상
                } else {
                    binding.btnLogin.setBackgroundColor(Color.parseColor("#AABEFF")) // 비활성화 색상
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.etEmail.addTextChangedListener(watcher)
        binding.etPassword.addTextChangedListener(watcher)
    }

    /**
     * 모든 클릭 리스너 설정
     */
    private fun setupClickListeners() {
        // 로그인 버튼 클릭
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            // 네트워크 연결 확인 후 로그인 진행
            checkNetworkAndProceed {
                login(email, password)
            }
        }

        // 회원가입 텍스트 클릭
        binding.tvSignupFull.setOnClickListener {
            startActivity(Intent(this, SignupEmailActivity::class.java))
        }

        // 비밀번호 찾기 텍스트 클릭
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    /**
     * 입력값 유효성 검사
     */
    private fun validateInputs(): Boolean {
        var isValid = true

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // 이메일 검사
        if (email.isEmpty()) {
            binding.layoutEmail.error = "이메일을 입력해주세요"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = "올바른 이메일 형식이 아닙니다"
            isValid = false
        } else {
            binding.layoutEmail.error = null
        }

        // 비밀번호 검사
        if (password.isEmpty()) {
            binding.layoutPassword.error = "비밀번호를 입력해주세요"
            isValid = false
        } else {
            binding.layoutPassword.error = null
        }

        return isValid
    }

    /**
     * 로그인 처리
     * 로그인 성공 후 사용자 상태에 따라 다른 화면으로 이동
     */
    private fun login(email: String, password: String) {
        // 입력값 검증
        if (!validateInputs()) return

        // 로딩 표시 시작
        binding.progressLogin.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        try {
            // Firebase로 로그인 시도
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user

                    // 이메일 인증 확인
                    if (user != null && !user.isEmailVerified) {
                        // 인증되지 않은 이메일
                        binding.progressLogin.visibility = View.GONE
                        binding.btnLogin.isEnabled = true

                        // 이메일 인증 화면으로 이동할지 묻기
                        showEmailVerificationPrompt(email)
                        return@addOnSuccessListener
                    }

                    val uid = user?.uid ?: return@addOnSuccessListener

                    // Firestore에서 사용자 상태 확인
                    db.collection("users").document(uid)
                        .get()
                        .addOnSuccessListener { doc ->
                            binding.progressLogin.visibility = View.GONE

                            val emailVerified = doc?.getBoolean("emailVerified") ?: false
                            val passwordSet = doc?.getBoolean("passwordSet") ?: false

                            when {
                                // 가입 완료 - 메인으로 이동
                                emailVerified && passwordSet -> {
                                    // FCM 토큰 업데이트
                                    updateFcmToken(uid)

                                    // 세션 정보 저장
                                    SharedPrefsHelper.saveUserSession(
                                        this,
                                        uid,
                                        auth.currentUser?.getIdToken(false)?.result?.token ?: ""
                                    )

                                    Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, MainActivity::class.java))
                                    finish()
                                }
                                // 이메일만 인증 - 비밀번호 설정으로 이동
                                emailVerified -> {
                                    Toast.makeText(this, "비밀번호 설정이 필요합니다", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this, SignupPasswordActivity::class.java)
                                    intent.putExtra("email", email)
                                    intent.putExtra("fromVerification", true)
                                    startActivity(intent)
                                }
                                // 상태 불일치 - 데이터베이스 업데이트
                                else -> {
                                    // Firebase Auth에는 인증됐지만 Firestore에는 아직 반영 안된 경우
                                    db.collection("users").document(uid)
                                        .update("emailVerified", true)
                                        .addOnSuccessListener {
                                            // 비밀번호 설정 화면으로 이동
                                            val intent = Intent(this, SignupPasswordActivity::class.java)
                                            intent.putExtra("email", email)
                                            intent.putExtra("fromVerification", true)
                                            startActivity(intent)
                                        }
                                        .addOnFailureListener {
                                            binding.btnLogin.isEnabled = true
                                            Toast.makeText(this, "상태 업데이트 실패", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                        }
                        .addOnFailureListener {
                            binding.progressLogin.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            Toast.makeText(this, "사용자 정보 로드 실패", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    binding.progressLogin.visibility = View.GONE
                    binding.btnLogin.isEnabled = true

                    // 오류 유형에 따른 맞춤 메시지 표시
                    val errorMessage = when (e) {
                        is FirebaseAuthInvalidUserException -> "존재하지 않는 계정입니다"
                        is FirebaseAuthInvalidCredentialsException -> "이메일 또는 비밀번호가 일치하지 않습니다"
                        is FirebaseNetworkException -> "네트워크 오류가 발생했습니다"
                        else -> "로그인 실패: ${e.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            binding.progressLogin.visibility = View.GONE
            binding.btnLogin.isEnabled = true
            Log.e(TAG, "로그인 처리 중 예외 발생: ${e.message}")
            Toast.makeText(this, "로그인 처리 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 이메일 인증 안내 대화상자 표시
     */
    private fun showEmailVerificationPrompt(email: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("이메일 인증 필요")
            .setMessage("로그인하려면 이메일 인증이 필요합니다. 인증 화면으로 이동하시겠습니까?")
            .setPositiveButton("이동") { _, _ ->
                // 이메일 인증 화면으로 이동
                val intent = Intent(this, EmailVerificationActivity::class.java)
                intent.putExtra("email", email)
                startActivity(intent)
            }
            .setNegativeButton("취소", null)
            .create()
            .show()
    }

    /**
     * FCM 토큰 업데이트 - 푸시 알림용
     */
    private fun updateFcmToken(uid: String) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    FirestoreHelper.updateFcmToken(uid, token)
                }
                .addOnFailureListener { e ->
                    // 토큰 가져오기 실패 처리 - 로그만 남기고 진행 (심각한 오류 아님)
                    Log.e(TAG, "FCM 토큰 가져오기 실패: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "FCM 토큰 처리 중 예외 발생: ${e.message}")
        }
    }

    /**
     * "회원가입" 텍스트에 색상 스타일 적용
     */
    private fun setSignupText() {
        val fullText = "계정이 없으신가요? 가입하기"
        val spannableString = SpannableString(fullText)
        val startIndex = fullText.indexOf("가입하기")
        val endIndex = startIndex + "가입하기".length

        spannableString.setSpan(
            ForegroundColorSpan(Color.parseColor("#007BFF")),
            startIndex,
            endIndex,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.tvSignupFull.text = spannableString
    }

    /**
     * 비밀번호 찾기 다이얼로그 표시
     */
    private fun showForgotPasswordDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val etDialogEmail = dialogView.findViewById<android.widget.EditText>(R.id.et_dialog_email)

        builder.setView(dialogView)
            .setTitle("비밀번호 재설정")
            .setPositiveButton("전송") { dialog, _ ->
                val email = etDialogEmail.text.toString().trim()
                if (email.isNotEmpty()) {
                    checkNetworkAndProceed {
                        sendPasswordResetEmail(email)
                    }
                } else {
                    Toast.makeText(this, "이메일을 입력해주세요", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    /**
     * 비밀번호 재설정 이메일 전송
     */
    private fun sendPasswordResetEmail(email: String) {
        try {
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "비밀번호 재설정 이메일이 전송되었습니다", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "이메일 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "비밀번호 재설정 메일 전송 중 예외 발생: ${e.message}")
            Toast.makeText(this, "재설정 메일 발송 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 네트워크 연결 확인 후 작업 수행
     */
    private fun checkNetworkAndProceed(action: () -> Unit) {
        if (NetworkUtils.isNetworkAvailable(this)) {
            action()
        } else {
            Toast.makeText(this, "인터넷 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
        }
    }
}
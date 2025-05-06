package com.example.deliverybox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivityLoginBinding
import com.example.deliverybox.utils.FirestoreHelper
import com.example.deliverybox.utils.NetworkUtils
import com.example.deliverybox.utils.SharedPrefsHelper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
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

        // Intent로 전달된 자동 로그인 정보 처리
        val intentEmail = intent.getStringExtra("email")
        val autoLogin = intent.getBooleanExtra("autoLogin", false)
        if (autoLogin && !intentEmail.isNullOrEmpty()) {
            Toast.makeText(this, "이전 세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
            binding.etEmail.setText(intentEmail)
        }

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
                binding.btnLogin.setBackgroundColor(
                    if (binding.btnLogin.isEnabled) Color.parseColor("#448AFF")
                    else Color.parseColor("#AABEFF")
                )
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
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            checkNetworkAndProceed { login(email, password) }
        }
        binding.tvSignupFull.setOnClickListener {
            startActivity(Intent(this, SignupEmailActivity::class.java))
        }
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    /**
     * 로그인 처리
     */
    private fun login(email: String, password: String) {
        if (!validateInputs()) return
        binding.progressLogin.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        try {
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user
                    // 이메일 인증 확인
                    if (user != null && !user.isEmailVerified) {
                        binding.progressLogin.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        showEmailVerificationPrompt(email)
                        return@addOnSuccessListener
                    }
                    val uid = user?.uid ?: return@addOnSuccessListener
                    // Firestore에서 사용자 상태 확인
                    db.collection("users").document(uid)
                        .get()
                        .addOnSuccessListener { doc ->
                            binding.progressLogin.visibility = View.GONE
                            handlePostLogin(email, uid, doc.getBoolean("emailVerified") ?: false, doc.getBoolean("passwordSet") ?: false)
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
     * 로그인 이후 상태에 따른 분기 처리
     */
    private fun handlePostLogin(email: String, uid: String, emailVerified: Boolean, passwordSet: Boolean) {
        when {
            emailVerified && passwordSet -> {
                updateFcmToken(uid)
                SharedPrefsHelper.saveUserSession(this, uid, auth.currentUser?.getIdToken(false)?.result?.token ?: "")
                Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            emailVerified -> {
                Toast.makeText(this, "비밀번호 설정이 필요합니다", Toast.LENGTH_SHORT).show()
                Intent(this, SignupPasswordActivity::class.java).apply {
                    putExtra("email", email)
                    putExtra("fromVerification", true)
                    startActivity(this)
                }
            }
            else -> {
                // Auth side only verified → update Firestore
                db.collection("users").document(uid)
                    .update("emailVerified", true)
                    .addOnSuccessListener {
                        Intent(this, SignupPasswordActivity::class.java).apply {
                            putExtra("email", email)
                            putExtra("fromVerification", true)
                            startActivity(this)
                        }
                    }
                    .addOnFailureListener {
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(this, "상태 업데이트 실패", Toast.LENGTH_SHORT).show()
                    }
            }
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
                Intent(this, EmailVerificationActivity::class.java).apply {
                    putExtra("email", email)
                    startActivity(this)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * FCM 토큰 업데이트
     */
    private fun updateFcmToken(uid: String) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> FirestoreHelper.updateFcmToken(uid, token) }
                .addOnFailureListener { e -> Log.e(TAG, "FCM 토큰 가져오기 실패: ${e.message}") }
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
        val start = fullText.indexOf("가입하기")
        val end = start + "가입하기".length
        spannableString.setSpan(
            ForegroundColorSpan(Color.parseColor("#007BFF")),
            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
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
                if (email.isNotEmpty()) checkNetworkAndProceed { sendPasswordResetEmail(email) }
                else Toast.makeText(this, "이메일을 입력해주세요", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("취소", null)
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
     * 입력값 유효성 검사
     */
    private fun validateInputs(): Boolean {
        var valid = true
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (email.isEmpty()) {
            binding.layoutEmail.error = "이메일을 입력해주세요"
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = "올바른 이메일 형식이 아닙니다"
            valid = false
        } else {
            binding.layoutEmail.error = null
        }
        if (password.isEmpty()) {
            binding.layoutPassword.error = "비밀번호를 입력해주세요"
            valid = false
        } else {
            binding.layoutPassword.error = null
        }
        return valid
    }

    /**
     * 네트워크 연결 확인 후 작업 수행
     */
    private fun checkNetworkAndProceed(action: () -> Unit) {
        if (NetworkUtils.isNetworkAvailable(this)) action()
        else Toast.makeText(this, "인터넷 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
    }
}

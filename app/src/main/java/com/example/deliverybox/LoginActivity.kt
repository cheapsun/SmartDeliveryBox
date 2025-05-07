package com.example.deliverybox

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.example.deliverybox.utils.SharedPrefsHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // 자동 로그인 확인
        if (SharedPrefsHelper.isAutoLoginEnabled(this) && auth.currentUser != null) {
            navigateToMain()
            return
        }

        setupLoginButtonState()
        setSignupText()
        setupClickListeners()
    }

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

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            login(email, password)
        }
        binding.tvSignupFull.setOnClickListener {
            startActivity(Intent(this, SignupEmailActivity::class.java))
        }
        binding.tvForgotPassword.setOnClickListener { showForgotPasswordDialog() }
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (email.isEmpty()) {
            binding.layoutEmail.error = "이메일을 입력해주세요"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = "올바른 이메일 형식이 아닙니다"
            isValid = false
        } else {
            binding.layoutEmail.error = null
        }
        if (password.isEmpty()) {
            binding.layoutPassword.error = "비밀번호를 입력해주세요"
            isValid = false
        } else {
            binding.layoutPassword.error = null
        }
        return isValid
    }

    private fun login(email: String, password: String) {
        if (!validateInputs()) return
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "인터넷 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressLogin.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null && !user.isEmailVerified) {
                    binding.progressLogin.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    showEmailVerificationPrompt(email)
                    return@addOnSuccessListener
                }
                val uid = user?.uid ?: return@addOnSuccessListener
                onLoginSuccess(uid)
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
    }

    // 로그인 성공 후 처리 메서드
    private fun onLoginSuccess(uid: String) {
        try {
            // FCM 토큰 업데이트
            updateFcmToken(uid)
            // 자동 로그인 활성화
            SharedPrefsHelper.setAutoLogin(this, true)
            // 마지막 로그인 시간 저장
            SharedPrefsHelper.setLastLoginTime(this, System.currentTimeMillis())

            FirestoreHelper.getUserData(uid) { userData ->
                binding.progressLogin.visibility = View.GONE
                if (userData != null) {
                    Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this, "사용자 정보 불러오기 실패", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "로그인 성공 처리 중 오류: ${e.message}")
            binding.progressLogin.visibility = View.GONE
            binding.btnLogin.isEnabled = true
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }

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
            .create()
            .show()
    }

    private fun updateFcmToken(uid: String) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> FirestoreHelper.updateFcmToken(uid, token) }
            .addOnFailureListener { e -> Log.e("LoginActivity", "FCM 토큰 가져오기 실패: ${e.message}") }
    }

    private fun setSignupText() {
        val fullText = "계정이 없으신가요? 가입하기"
        val spannableString = SpannableString(fullText)
        val start = fullText.indexOf("가입하기")
        spannableString.setSpan(
            ForegroundColorSpan(Color.parseColor("#007BFF")),
            start,
            start + "가입하기".length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvSignupFull.text = spannableString
    }

    private fun showForgotPasswordDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val etEmail = view.findViewById<android.widget.EditText>(R.id.et_dialog_email)
        builder.setView(view)
            .setTitle("비밀번호 재설정")
            .setPositiveButton("전송") { dialog, _ ->
                val email = etEmail.text.toString().trim()
                if (email.isNotEmpty()) sendPasswordResetEmail(email) else Toast.makeText(this, "이메일을 입력해주세요", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { Toast.makeText(this, "비밀번호 재설정 이메일이 전송되었습니다", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { e -> Toast.makeText(this, "이메일 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

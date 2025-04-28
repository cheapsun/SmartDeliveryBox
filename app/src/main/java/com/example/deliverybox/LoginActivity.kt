package com.example.deliverybox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.model.UserData
import com.example.deliverybox.utils.FirestoreHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val tvSignupFull = findViewById<TextView>(R.id.tv_signup_full)
        val tvForgotPassword = findViewById<TextView>(R.id.tv_forgot_password)

        setupLoginButtonState(etEmail, etPassword, btnLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid ?: return@addOnSuccessListener
                        updateFcmToken(uid)

                        // ✅ Firestore 사용자 데이터 가져오기
                        FirestoreHelper.getUserData(uid) { userData ->
                            if (userData != null) {
                                Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this, "사용자 정보 불러오기 실패", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        setSignupText(tvSignupFull)

        tvSignupFull.setOnClickListener {
            startActivity(Intent(this, SignupEmailActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "비밀번호 찾기 기능은 추후 지원됩니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLoginButtonState(
        emailField: EditText,
        passwordField: EditText,
        loginButton: Button
    ) {
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val email = emailField.text.toString().trim()
                val password = passwordField.text.toString().trim()
                loginButton.isEnabled = email.isNotEmpty() && password.isNotEmpty()
                if (loginButton.isEnabled) {
                    loginButton.setBackgroundColor(Color.parseColor("#448AFF")) // 진한 파란색
                } else {
                    loginButton.setBackgroundColor(Color.parseColor("#AABEFF")) // 연한 파란색
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        emailField.addTextChangedListener(watcher)
        passwordField.addTextChangedListener(watcher)
    }

    private fun updateFcmToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirestoreHelper.updateFcmToken(uid, token)
        }
    }

    private fun setSignupText(textView: TextView) {
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

        textView.text = spannableString
    }
}

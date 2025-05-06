package com.example.deliverybox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.utils.FirestoreHelper
import com.google.firebase.auth.FirebaseAuth

class SignupPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_password)

        auth = FirebaseAuth.getInstance()

        val etPassword = findViewById<EditText>(R.id.et_password)
        val etConfirm = findViewById<EditText>(R.id.et_confirm_password)
        val tvStrength = findViewById<TextView>(R.id.tv_strength)
        val btnConfirm = findViewById<Button>(R.id.btn_confirm)

        val email = intent.getStringExtra("email") ?: ""

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val pw = etPassword.text.toString()
                val confirm = etConfirm.text.toString()

                val isEnabled = pw.length >= 8 && pw == confirm

                btnConfirm.isEnabled = isEnabled
                if (isEnabled) {
                    // 비밀번호 조건 충족 → 진한 파란색
                    btnConfirm.setBackgroundColor(Color.parseColor("#448AFF"))
                } else {
                    // 비밀번호 조건 미충족 → 연한 파란색
                    btnConfirm.setBackgroundColor(Color.parseColor("#AABEFF"))
                }

                // 비밀번호 강도 표시
                tvStrength.text = "비밀번호 보안 수준: ${getStrengthText(pw)}"
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etPassword.addTextChangedListener(watcher)
        etConfirm.addTextChangedListener(watcher)

        btnConfirm.setOnClickListener {
            val password = etPassword.text.toString()

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener
                    FirestoreHelper.createUserDocument(uid, email) { success ->
                        if (success) {
                            Toast.makeText(this, "회원가입 완료!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finishAffinity()
                        } else {
                            Toast.makeText(this, "Firestore 저장 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "회원가입 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun getStrengthText(password: String): String {
        return when {
            password.length < 8 -> "약함"
            password.matches(Regex("^(?=.*[A-Za-z])(?=.*\\d).{8,}\$")) -> "중간"
            password.matches(Regex("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#\$%^&*]).{8,}\$")) -> "강함"
            else -> "약함"
        }
    }
}

package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_login)

            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            val etEmail = findViewById<EditText>(R.id.et_email)
            val etPassword = findViewById<EditText>(R.id.et_password)
            val btnLogin = findViewById<Button>(R.id.btn_login)
            val btnRegister = findViewById<Button>(R.id.btn_go_to_register)
            val tvLoginError = findViewById<TextView>(R.id.tv_login_error)

            tvLoginError.visibility = View.GONE

            btnRegister.setOnClickListener {
                startActivity(Intent(this, RegisterActivity::class.java))
            }

            btnLogin.setOnClickListener {
                // 키보드 숨기기
                currentFocus?.let {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                }

                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()
                tvLoginError.visibility = View.GONE

                if (email.isEmpty() || password.isEmpty()) {
                    tvLoginError.text = "이메일과 비밀번호를 모두 입력해주세요."
                    tvLoginError.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val uid = auth.currentUser?.uid ?: return@addOnSuccessListener

                        // FCM 토큰 저장
                        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            db.collection("users").document(uid)
                                .update("fcmToken", token)
                        }

                        // 사용자 정보 조회
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { userDoc ->
                                val boxId = userDoc.getString("boxId")
                                if (boxId.isNullOrEmpty()) {
                                    tvLoginError.text = "연결된 택배함 정보가 없습니다."
                                    tvLoginError.visibility = View.VISIBLE
                                    return@addOnSuccessListener
                                }

                                // 박스 권한 확인
                                db.collection("boxes").document(boxId).get()
                                    .addOnSuccessListener { boxDoc ->
                                        val ownerUid = boxDoc.getString("ownerUid")
                                        val sharedUids = boxDoc.get("sharedUserUids") as? List<*> ?: emptyList<String>()

                                        if (uid == ownerUid || sharedUids.contains(uid)) {
                                            startActivity(Intent(this, MainActivity::class.java))
                                            finish()
                                        } else {
                                            tvLoginError.text = "해당 택배함에 대한 접근 권한이 없습니다."
                                            tvLoginError.visibility = View.VISIBLE
                                        }
                                    }
                                    .addOnFailureListener {
                                        tvLoginError.text = "택배함 정보 불러오기 실패: ${it.message}"
                                        tvLoginError.visibility = View.VISIBLE
                                    }
                            }
                            .addOnFailureListener {
                                tvLoginError.text = "사용자 정보 불러오기 실패: ${it.message}"
                                tvLoginError.visibility = View.VISIBLE
                            }
                    }
                    .addOnFailureListener {
                        tvLoginError.text = "로그인 실패: 이메일 또는 비밀번호를 확인해주세요."
                        tvLoginError.visibility = View.VISIBLE
                    }
            }

        } catch (e: Exception) {
            Log.e("LoginActivity", "🔥 로그인 액티비티에서 예외 발생: ${e.message}", e)
            Toast.makeText(this, "에러 발생: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

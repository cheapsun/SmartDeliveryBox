package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val etBoxName = findViewById<EditText>(R.id.et_box_name)
        val btnRegister = findViewById<Button>(R.id.btn_register)

        val tvEmailError = findViewById<TextView>(R.id.tv_email_error)
        val tvPasswordError = findViewById<TextView>(R.id.tv_password_error)
        val tvBoxError = findViewById<TextView>(R.id.tv_box_error)

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val boxId = etBoxName.text.toString().trim()

            // 초기화
            tvEmailError.visibility = View.GONE
            tvPasswordError.visibility = View.GONE
            tvBoxError.visibility = View.GONE

            var hasError = false

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tvEmailError.text = "올바른 이메일 형식으로 입력해주세요."
                tvEmailError.visibility = View.VISIBLE
                hasError = true
            }

            if (password.length < 6) {
                tvPasswordError.text = "비밀번호는 최소 6자리 이상이어야 합니다."
                tvPasswordError.visibility = View.VISIBLE
                hasError = true
            }

            if (boxId.isEmpty()) {
                tvBoxError.text = "택배함 이름을 입력해주세요."
                tvBoxError.visibility = View.VISIBLE
                hasError = true
            }

            if (hasError) return@setOnClickListener

            // boxId 확인
            db.collection("boxes").document(boxId).get().addOnSuccessListener { boxDoc ->
                if (!boxDoc.exists()) {
                    tvBoxError.text = "존재하지 않는 택배함입니다."
                    tvBoxError.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                val ownerUid = boxDoc.getString("ownerUid") ?: ""
                val ownerEmail = boxDoc.getString("ownerEmail") ?: ""
                val sharedEmails = boxDoc.get("sharedUserEmails") as? List<*> ?: listOf<Any>()

                val isBoxFree = ownerUid.isEmpty()
                val isPermittedEmail = (email == ownerEmail || sharedEmails.contains(email))

                if (!isBoxFree && !isPermittedEmail) {
                    tvBoxError.text = "해당 택배함에 대한 접근 권한이 없습니다."
                    tvBoxError.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // 회원가입
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val uid = auth.currentUser?.uid ?: return@addOnSuccessListener

                        val userData = mapOf(
                            "email" to email,
                            "boxId" to boxId
                        )

                        db.collection("users").document(uid).set(userData)
                            .addOnSuccessListener {
                                val boxRef = db.collection("boxes").document(boxId)
                                val updates = mutableMapOf<String, Any>()

                                if (isBoxFree) {
                                    updates["ownerUid"] = uid
                                    updates["ownerEmail"] = email
                                } else {
                                    updates["sharedUserUids"] = FieldValue.arrayUnion(uid)
                                    updates["sharedUserEmails"] = FieldValue.arrayRemove(email) // ✅ 자동 전환
                                }

                                boxRef.update(updates)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                                        startActivity(Intent(this, LoginActivity::class.java))
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        auth.currentUser?.delete()
                                        tvBoxError.text = "회원 정보 저장 실패: ${e.message}"
                                        tvBoxError.visibility = View.VISIBLE
                                    }
                            }
                            .addOnFailureListener { e ->
                                auth.currentUser?.delete()
                                tvBoxError.text = "회원 정보 저장 실패: ${e.message}"
                                tvBoxError.visibility = View.VISIBLE
                            }
                    }
                    .addOnFailureListener {
                        if (it is FirebaseAuthUserCollisionException) {
                            tvEmailError.text = "이미 사용 중인 이메일입니다."
                        } else {
                            tvEmailError.text = "회원가입 실패: ${it.message}"
                        }
                        tvEmailError.visibility = View.VISIBLE
                    }

            }.addOnFailureListener {
                tvBoxError.text = "택배함 확인 실패: ${it.message}"
                tvBoxError.visibility = View.VISIBLE
            }
        }
    }
}

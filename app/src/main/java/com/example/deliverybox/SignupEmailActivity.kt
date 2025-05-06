package com.example.deliverybox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SignupEmailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_email)

        val etEmail = findViewById<EditText>(R.id.et_email)
        val checkBoxTerms = findViewById<CheckBox>(R.id.checkbox_terms)
        val btnNext = findViewById<Button>(R.id.btn_next_step)

        // 🔹 이메일 & 약관 체크 시 다음 버튼 활성화 + 색상 변경
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val email = etEmail.text.toString().trim()
                val isEnabled = email.isNotEmpty() && checkBoxTerms.isChecked

                btnNext.isEnabled = isEnabled
                if (isEnabled) {
                    // 입력이 맞을 때 → 진한 파란색
                    btnNext.setBackgroundColor(Color.parseColor("#448AFF")) // 진한 파란색
                } else {
                    // 입력이 안 맞을 때 → 연하늘색
                    btnNext.setBackgroundColor(Color.parseColor("#AABEFF")) // 연하늘색
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etEmail.addTextChangedListener(watcher)
        checkBoxTerms.setOnCheckedChangeListener { _, _ ->
            watcher.afterTextChanged(null)
        }

        // 🔹 다음 버튼 클릭 -> 비밀번호 설정 화면으로 이동
        btnNext.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val intent = Intent(this, SignupPasswordActivity::class.java)
            intent.putExtra("email", email)
            startActivity(intent)
        }
    }
}

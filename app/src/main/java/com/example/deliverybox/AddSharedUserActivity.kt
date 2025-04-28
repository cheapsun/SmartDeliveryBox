package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class AddSharedUserActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private var isEmailValid: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_shared_user)

        etEmail = findViewById(R.id.et_email)
        etMessage = findViewById(R.id.et_message)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_add_shared_user)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = "공유 사용자 추가"

        setupEmailValidation()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_add_shared_user, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_send)?.isEnabled = isEmailValid
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_send -> {
                sendInvite()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupEmailValidation() {
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val email = s.toString().trim()
                isEmailValid = email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                invalidateOptionsMenu()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun sendInvite() {
        val email = etEmail.text.toString().trim()
        val message = etMessage.text.toString().trim()

        if (email.isEmpty()) {
            Toast.makeText(this, "이메일을 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔹 초대 성공 처리
        val resultIntent = Intent()
        resultIntent.putExtra("invite_email", email)  // ✅ 이메일을 결과로 전달
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

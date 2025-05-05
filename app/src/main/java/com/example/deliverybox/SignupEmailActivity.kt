package com.example.deliverybox

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivitySignupEmailBinding
import com.google.firebase.auth.FirebaseAuth

class SignupEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupEmailBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 뷰 바인딩 초기화
        binding = ActivitySignupEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase 인증 초기화
        auth = FirebaseAuth.getInstance()

        // 뒤로가기 버튼 설정
        binding.toolbarSignup.setNavigationOnClickListener {
            finish()
        }

        // 약관 보기 클릭 리스너
        binding.tvTermsLink.setOnClickListener {
            showTermsDialog()
        }

        // 텍스트 입력 및 체크박스 상태 변경 감지
        setupInputWatcher()

        // 다음 버튼 클릭 리스너
        binding.btnNextStep.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()

            // 이메일 유효성 검사
            if (!validateEmail(email)) return@setOnClickListener

            // 네트워크 연결 확인
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "인터넷 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 이메일 중복 확인
            checkEmailAvailability(email)
        }
    }

    /**
     * 이메일 및 약관 동의 상태에 따른 버튼 활성화 설정
     */
    private fun setupInputWatcher() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateNextButtonState()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.etEmail.addTextChangedListener(watcher)
        binding.checkboxTerms.setOnCheckedChangeListener { _, _ ->
            updateNextButtonState()
        }
    }

    /**
     * 다음 버튼 상태 업데이트
     */
    private fun updateNextButtonState() {
        val email = binding.etEmail.text.toString().trim()
        val isEnabled = email.isNotEmpty() && binding.checkboxTerms.isChecked

        binding.btnNextStep.isEnabled = isEnabled
        if (isEnabled) {
            binding.btnNextStep.setBackgroundColor(Color.parseColor("#448AFF")) // 활성화 색상
        } else {
            binding.btnNextStep.setBackgroundColor(Color.parseColor("#AABEFF")) // 비활성화 색상
        }
    }

    /**
     * 이메일 유효성 검사
     */
    private fun validateEmail(email: String): Boolean {
        if (email.isEmpty()) {
            binding.layoutEmail.error = "이메일을 입력해주세요"
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = "올바른 이메일 형식이 아닙니다"
            return false
        }

        binding.layoutEmail.error = null
        return true
    }

    /**
     * 이메일 중복 확인
     */
    private fun checkEmailAvailability(email: String) {
        binding.progressEmailCheck.visibility = View.VISIBLE
        binding.btnNextStep.isEnabled = false

        auth.fetchSignInMethodsForEmail(email).addOnCompleteListener { task ->
            binding.progressEmailCheck.visibility = View.GONE
            binding.btnNextStep.isEnabled = true

            if (task.isSuccessful) {
                val signInMethods = task.result?.signInMethods
                if (!signInMethods.isNullOrEmpty()) {
                    // 이미 가입된 이메일
                    binding.layoutEmail.error = "이미 가입된 이메일입니다"
                } else {
                    // 사용 가능한 이메일, 다음 단계로 진행
                    binding.layoutEmail.error = null
                    proceedToPasswordScreen(email)
                }
            } else {
                // 확인 실패
                Toast.makeText(this, "이메일 확인 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 비밀번호 설정 화면으로 이동
     */
    private fun proceedToPasswordScreen(email: String) {
        val intent = Intent(this, SignupPasswordActivity::class.java)
        intent.putExtra("email", email)
        startActivity(intent)
    }

    /**
     * 약관 다이얼로그 표시
     */
    private fun showTermsDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("이용약관")
            .setMessage(R.string.terms_of_service)  // strings.xml에 약관 내용 정의 필요
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
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
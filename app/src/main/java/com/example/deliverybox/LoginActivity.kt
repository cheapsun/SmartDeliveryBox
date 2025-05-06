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
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivityLoginBinding
import com.example.deliverybox.utils.AccountUtils
import com.example.deliverybox.utils.FirestoreHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 뷰 바인딩 초기화
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase 인증 객체 초기화
        auth = FirebaseAuth.getInstance()

        // 기존 세션 확인 및 정리
        checkExistingSession()

        // 로그인 버튼 상태 설정
        setupLoginButtonState()

        // "가입하기" 텍스트의 색상 스타일 설정
        setSignupText()

        // 버튼 클릭 리스너 설정
        setupClickListeners()
    }

    /**
     * 기존 세션 확인 및 정리
     * 세션은 있지만 미인증 상태인 계정 처리
     */
    private fun checkExistingSession() {
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.isEmailVerified) {
            // 미인증 계정 발견 - 삭제 처리
            Log.d(TAG, "미인증 계정 발견, 정리 중...")
            AccountUtils.deleteTempAccountAndSignOut()
        }
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
            login(email, password)
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
     */
    private fun login(email: String, password: String) {
        // 입력값 검증
        if (!validateInputs()) return

        // 네트워크 연결 확인
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "인터넷 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        // 로딩 표시 시작
        binding.progressLogin.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        // Firebase로 로그인 시도
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                val isEmailVerified = result.user?.isEmailVerified ?: false

                Log.d(TAG, "로그인 성공. 이메일 인증 여부: $isEmailVerified")

                // 이메일 인증 여부 확인
                if (!isEmailVerified) {
                    binding.progressLogin.visibility = View.GONE
                    binding.btnLogin.isEnabled = true

                    // 이메일 인증 안된 계정 - 인증 화면으로 이동 또는 재인증 메일 발송 제안
                    showVerificationRequiredDialog(email)
                    return@addOnSuccessListener
                }

                // FCM 토큰 업데이트 (푸시 알림용)
                updateFcmToken(uid)

                // Firestore에서 사용자 데이터 가져오기 & 비밀번호 설정 여부 확인
                FirestoreHelper.getUserData(uid) { userData ->
                    binding.progressLogin.visibility = View.GONE

                    if (userData != null) {
                        if (userData.isPasswordSet) {
                            // 정상 로그인 - 메인으로 이동
                            Log.d(TAG, "로그인 및 비밀번호 설정 완료, 메인으로 이동")
                            Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        } else {
                            // 비밀번호 설정이 필요한 경우 - 설정 화면으로 이동
                            Log.d(TAG, "로그인 성공, 비밀번호 설정 필요")
                            binding.btnLogin.isEnabled = true
                            Toast.makeText(this, "비밀번호 설정이 필요합니다.", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this, SignupPasswordActivity::class.java)
                            intent.putExtra("email", email)
                            startActivity(intent)
                        }
                    } else {
                        // 사용자 데이터 로드 실패
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(this, "사용자 정보 불러오기 실패", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "사용자 데이터 로드 실패")
                    }
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
                Log.e(TAG, "로그인 실패: $errorMessage")
            }
    }

    /**
     * 이메일 인증 필요 대화상자 표시
     */
    private fun showVerificationRequiredDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("이메일 인증 필요")
            .setMessage("계정을 사용하기 위해서는 이메일 인증이 필요합니다. 인증 메일을 다시 받으시겠습니까?")
            .setPositiveButton("인증 메일 다시 받기") { _, _ ->
                // 인증 메일 재발송
                auth.currentUser?.sendEmailVerification()
                    ?.addOnSuccessListener {
                        Toast.makeText(this, "인증 메일이 재발송되었습니다.", Toast.LENGTH_SHORT).show()

                        // 인증 화면으로 이동
                        val intent = Intent(this, EmailVerificationActivity::class.java)
                        intent.putExtra("email", email)
                        startActivity(intent)
                    }
                    ?.addOnFailureListener { e ->
                        Toast.makeText(this, "인증 메일 발송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "인증 메일 발송 실패: ${e.message}")
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * FCM 토큰 업데이트 - 푸시 알림용
     */
    private fun updateFcmToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirestoreHelper.updateFcmToken(uid, token)
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
                    sendPasswordResetEmail(email)
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
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, "비밀번호 재설정 이메일이 전송되었습니다", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "비밀번호 재설정 메일 발송 성공: $email")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "이메일 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "비밀번호 재설정 메일 발송 실패: ${e.message}")
            }
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

    /**
     * 앱 재시작 시 인증 상태 확인 (onResume에서 호출)
     */
    override fun onResume() {
        super.onResume()

        // 현재 사용자 확인
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // 사용자 상태 새로고침
            currentUser.reload().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // 이메일 인증 및 비밀번호 설정 여부 확인
                    if (currentUser.isEmailVerified) {
                        FirestoreHelper.checkPasswordSet(currentUser.uid) { isPasswordSet ->
                            if (isPasswordSet == true) {
                                // 인증됨 + 비밀번호 설정 완료 - 메인으로 자동 이동
                                Log.d(TAG, "자동 로그인: 인증 및 비밀번호 설정 완료")
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}
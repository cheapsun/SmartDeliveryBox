package com.example.deliverybox

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivitySignupPasswordBinding
import com.example.deliverybox.utils.AccountUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class SignupPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupPasswordBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var email: String
    private val TAG = "SignupPasswordActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase 인증 초기화
        auth = FirebaseAuth.getInstance()

        // 이전 화면에서 전달된 데이터 확인
        email = intent.getStringExtra("email") ?: auth.currentUser?.email ?: ""

        // 계정 정보 확인
        if (email.isEmpty() || auth.currentUser == null) {
            Log.e(TAG, "인증 정보 없음, 로그인 화면으로 이동")
            Toast.makeText(this, "인증 정보가 없습니다. 처음부터 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 사용자가 이메일 인증을 완료했는지 확인
        auth.currentUser?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                if (!auth.currentUser?.isEmailVerified!!) {
                    Log.e(TAG, "이메일 미인증, 인증 화면으로 이동")
                    Toast.makeText(this, "이메일 인증이 완료되지 않았습니다.", Toast.LENGTH_SHORT).show()
                    // 인증 화면으로 이동
                    val intent = Intent(this, EmailVerificationActivity::class.java)
                    intent.putExtra("email", email)
                    startActivity(intent)
                    finish()
                    return@addOnCompleteListener
                }
            }
        }

        // 뒤로가기 버튼 설정
        binding.toolbarPassword.setNavigationOnClickListener {
            showCancelConfirmationDialog()
        }

        // 비밀번호 요구사항 체크리스트 초기화
        initPasswordRequirements()

        // 비밀번호 입력 감지 설정
        setupPasswordWatcher()

        // 회원가입 버튼 클릭 리스너
        binding.btnConfirm.setOnClickListener {
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            // 유효성 검사
            if (!validatePassword(password, confirmPassword)) return@setOnClickListener

            // 네트워크 연결 확인
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "인터넷 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 비밀번호 업데이트
            updatePassword(password)
        }

        // 앱 재시작 후 진입 시 비밀번호 설정 여부 확인
        checkPasswordSetState()
    }

    /**
     * 비밀번호 설정 여부 확인
     * 앱이 재시작된 경우 이미 비밀번호가 설정되어 있을 수 있음
     */
    private fun checkPasswordSetState() {
        val uid = auth.currentUser?.uid ?: return

        AccountUtils.checkPasswordSet(uid) { isPasswordSet ->
            if (isPasswordSet == true) {
                Log.d(TAG, "이미 비밀번호 설정 완료, 메인 화면으로 이동")
                // 이미 비밀번호가 설정되어 있으면 메인으로 이동
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    /**
     * 취소 확인 다이얼로그 표시
     */
    private fun showCancelConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("비밀번호 설정 취소")
            .setMessage("비밀번호 설정을 취소하시겠습니까? 처음부터 다시 시작해야 합니다.")
            .setPositiveButton("확인") { _, _ ->
                // 임시 계정 삭제 후 이전 화면으로 이동
                AccountUtils.deleteTempAccountAndSignOut {
                    finish()
                }
            }
            .setNegativeButton("계속 진행", null)
            .show()
    }

    /**
     * 비밀번호 요구사항 체크리스트 초기화
     */
    private fun initPasswordRequirements() {
        // 모든 요구사항 항목을 비활성 상태로 초기화
        binding.tvReqLength.setTextColor(Color.parseColor("#AAAAAA"))
        binding.tvReqUppercase.setTextColor(Color.parseColor("#AAAAAA"))
        binding.tvReqLowercase.setTextColor(Color.parseColor("#AAAAAA"))
        binding.tvReqDigit.setTextColor(Color.parseColor("#AAAAAA"))
        binding.tvReqSpecial.setTextColor(Color.parseColor("#AAAAAA"))
    }

    // ... 기존 코드 (setupPasswordWatcher, updatePasswordRequirements, getPasswordStrength 등) ...

    /**
     * 비밀번호 업데이트
     */
    private fun updatePassword(password: String) {
        // 로딩 표시
        binding.progressSignup.visibility = View.VISIBLE
        binding.btnConfirm.isEnabled = false

        // 비밀번호 업데이트
        auth.currentUser?.updatePassword(password)
            ?.addOnSuccessListener {
                // 비밀번호 설정 완료 플래그 저장
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                AccountUtils.setPasswordFlag(uid, true) { success ->
                    binding.progressSignup.visibility = View.GONE

                    if (success) {
                        Toast.makeText(this, "회원가입이 완료되었습니다!", Toast.LENGTH_LONG).show()

                        // 메인 화면으로 이동
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        binding.btnConfirm.isEnabled = true
                        Toast.makeText(this, "회원가입은 완료되었으나 상태 저장에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ?.addOnFailureListener { e ->
                binding.progressSignup.visibility = View.GONE
                binding.btnConfirm.isEnabled = true

                // 오류 종류에 따른 메시지 처리
                val errorMessage = when (e) {
                    is FirebaseAuthWeakPasswordException -> "비밀번호가 너무 약합니다"
                    else -> "비밀번호 설정 실패: ${e.message}"
                }

                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * 비밀번호 설정 이후 앱이 종료된 경우 처리 (onResume에서 확인)
     */
    override fun onResume() {
        super.onResume()

        if (auth.currentUser == null) {
            // 로그인 화면으로 이동
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 사용자 정보 새로고침 및 인증 상태 확인
        auth.currentUser?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                if (!auth.currentUser?.isEmailVerified!!) {
                    // 이메일 미인증 시 인증 화면으로 이동
                    val intent = Intent(this, EmailVerificationActivity::class.java)
                    intent.putExtra("email", email)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    /**
     * 뒤로가기 버튼 처리
     */
    override fun onBackPressed() {
        showCancelConfirmationDialog()
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
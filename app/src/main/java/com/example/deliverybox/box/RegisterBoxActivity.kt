package com.example.deliverybox.box

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.home.MainActivity
import com.example.deliverybox.databinding.ActivityRegisterBoxBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.UUID
import com.example.deliverybox.box.QrCodeValidationService
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
import com.example.deliverybox.auth.LoginActivity
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View  // 추가 필요한 import

class RegisterBoxActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBoxBinding
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var validationService: QrCodeValidationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBoxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        validationService = QrCodeValidationService()

        // QR 코드로 전달된 경우 처리
        val qrCode = intent.getStringExtra("qr_code")
            ?: intent.getStringExtra(QrScanActivity.RESULT_QR_CODE)
            ?: intent.getStringExtra("RESULT_QR_CODE")
        val fromQrScan = intent.getBooleanExtra("from_qr_scan", false)
        val alreadyValidated = intent.getBooleanExtra("already_validated", false)

        Log.d("RegisterBoxActivity", "=== Intent 데이터 디버그 ===")
        Log.d("RegisterBoxActivity", "qrCode: $qrCode")
        Log.d("RegisterBoxActivity", "fromQrScan: $fromQrScan")
        Log.d("RegisterBoxActivity", "alreadyValidated: $alreadyValidated")
        Log.d("RegisterBoxActivity", "전체 Intent extras: ${intent.extras}")

        if (fromQrScan && !qrCode.isNullOrEmpty()) {
            Log.d("RegisterBoxActivity", "QR 스캔에서 온 경우 - 코드 설정: $qrCode")

            // QR 스캔으로 온 경우 코드 필드에 설정하고 별칭만 입력받도록
            binding.etBoxCode.setText(qrCode)
            binding.etBoxCode.isEnabled = false
            binding.layoutBoxCode.hint = "택배함 코드 (QR 스캔됨: $qrCode)"

            // 코드 필드를 명시적으로 보이도록 설정
            binding.etBoxCode.visibility = View.VISIBLE
            binding.layoutBoxCode.visibility = View.VISIBLE

            // 포커스를 별칭 입력 필드로 이동
            binding.etBoxAlias.requestFocus()

            // 키보드 자동 표시
            binding.etBoxAlias.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.etBoxAlias, InputMethodManager.SHOW_IMPLICIT)
            }, 200)

            // 버튼 텍스트 변경
            binding.btnRegisterOrClaim.text = "택배함 등록"

            Log.d("RegisterBoxActivity", "UI 설정 완료 - EditText 값: ${binding.etBoxCode.text}")
        } else {
            Log.w("RegisterBoxActivity", "QR 스캔 데이터가 없거나 유효하지 않음")
            Log.w("RegisterBoxActivity", "fromQrScan: $fromQrScan, qrCode: $qrCode")
        }

        // 툴바 뒤로가기 버튼
        binding.toolbarRegisterBox.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // 등록 버튼 클릭 - 수정된 로직
        binding.btnRegisterOrClaim.setOnClickListener {
            val code = binding.etBoxCode.text.toString().trim()
            val alias = binding.etBoxAlias.text.toString().trim()

            Log.d("RegisterBoxActivity", "등록 버튼 클릭:")
            Log.d("RegisterBoxActivity", "- code: '$code'")
            Log.d("RegisterBoxActivity", "- alias: '$alias'")
            Log.d("RegisterBoxActivity", "- fromQrScan: $fromQrScan")
            Log.d("RegisterBoxActivity", "- alreadyValidated: $alreadyValidated")

            when {
                code.isEmpty() -> {
                    // 코드가 비어있으면 새 박스 생성
                    createNewBox(alias)
                }
                fromQrScan && alreadyValidated -> {
                    // QR 스캔으로 온 경우 (이미 검증됨) - 바로 등록
                    registerPreValidatedBox(code, alias)
                }
                else -> {
                    // 수동 입력의 경우 검증 후 등록 (중복 방지)
                    registerBoxWithValidationOnly(code, alias)
                }
            }
        }
    }

    /** QR 스캔으로 이미 검증된 박스 등록 (중복 방지) */
    private fun registerPreValidatedBox(code: String, alias: String) {
        Log.d("RegisterBoxActivity", "이미 검증된 박스 등록 시작: $code, $alias")

        // 유효성 검사
        if (alias.isEmpty()) {
            binding.etBoxAlias.error = "별칭을 입력해주세요"
            return
        }

        if (alias.length < 2) {
            binding.etBoxAlias.error = "별칭은 2자 이상 입력해주세요"
            return
        }

        // 네트워크 및 인증 상태 확인
        if (!checkNetworkAndAuth()) {
            return
        }

        // 에러 메시지 초기화
        binding.layoutBoxCode.error = null
        binding.layoutBoxAlias.error = null

        // 버튼 비활성화 및 로딩 표시
        binding.btnRegisterOrClaim.isEnabled = false
        binding.btnRegisterOrClaim.text = "등록 중..."

        // ValidationService를 통한 등록 (한 번만 시도, 실패시 claimBox 호출 안함)
        lifecycleScope.launch {
            try {
                Log.d("RegisterBoxActivity", "ValidationService를 통한 등록 시작")
                val result = validationService.registerValidatedBox(code, alias)

                result.fold(
                    onSuccess = { message ->
                        Log.d("RegisterBoxActivity", "등록 성공: $message")
                        Toast.makeText(this@RegisterBoxActivity, message, Toast.LENGTH_SHORT).show()

                        // MainActivity로 이동하며 목록 새로고침
                        val intent = Intent(this@RegisterBoxActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("refresh_boxes", true)
                            putExtra("show_success_message", true)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onFailure = { error ->
                        Log.e("RegisterBoxActivity", "ValidationService 등록 실패", error)
                        Toast.makeText(this@RegisterBoxActivity,
                            "등록 실패: ${error.message}",
                            Toast.LENGTH_SHORT).show()
                        resetButtonState()
                        // QR 스캔된 박스는 claimBox로 재시도하지 않음
                    }
                )
            } catch (e: Exception) {
                Log.e("RegisterBoxActivity", "네트워크 오류", e)
                Toast.makeText(this@RegisterBoxActivity,
                    "네트워크 오류가 발생했습니다.",
                    Toast.LENGTH_SHORT).show()
                resetButtonState()
            }
        }
    }

    /** 수동 입력된 코드 검증 및 등록 (중복 방지) */
    private fun registerBoxWithValidationOnly(code: String, alias: String) {
        Log.d("RegisterBoxActivity", "수동 입력 검증 및 등록 시작: $code, $alias")

        // 인증 상태 확인
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 네트워크 및 인증 상태 확인
        if (!checkNetworkAndAuth()) {
            return
        }

        // 유효성 검사
        if (alias.isEmpty()) {
            binding.etBoxAlias.error = "별칭을 입력해주세요"
            return
        }

        if (alias.length < 2) {
            binding.etBoxAlias.error = "별칭은 2자 이상 입력해주세요"
            return
        }

        // 에러 메시지 초기화
        binding.layoutBoxCode.error = null
        binding.layoutBoxAlias.error = null

        // 버튼 비활성화 및 로딩 표시
        binding.btnRegisterOrClaim.isEnabled = false
        binding.btnRegisterOrClaim.text = "등록 중..."

        // ValidationService로 먼저 시도, 실패시 claimBox 호출하지 않음
        lifecycleScope.launch {
            try {
                Log.d("RegisterBoxActivity", "수동 입력 ValidationService 등록 시작: $code")
                val result = validationService.registerValidatedBox(code, alias)

                result.fold(
                    onSuccess = { message ->
                        Log.d("RegisterBoxActivity", "등록 성공: $message")
                        Toast.makeText(this@RegisterBoxActivity, message, Toast.LENGTH_SHORT).show()

                        // MainActivity로 이동하며 목록 새로고침
                        val intent = Intent(this@RegisterBoxActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("refresh_boxes", true)
                            putExtra("show_success_message", true)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onFailure = { error ->
                        Log.e("RegisterBoxActivity", "ValidationService 등록 실패", error)
                        // 중복 등록 방지: claimBox 호출 제거
                        Toast.makeText(this@RegisterBoxActivity,
                            "등록 실패: ${error.message}",
                            Toast.LENGTH_SHORT).show()
                        resetButtonState()
                    }
                )
            } catch (e: Exception) {
                Log.e("RegisterBoxActivity", "네트워크 오류", e)
                // 중복 등록 방지: claimBox 호출 제거
                Toast.makeText(this@RegisterBoxActivity,
                    "네트워크 오류가 발생했습니다.",
                    Toast.LENGTH_SHORT).show()
                resetButtonState()
            }
        }
    }

    /** 기존 registerBoxWithValidation 메서드 삭제 및 대체 */
    // 기존 메서드는 registerBoxWithValidationOnly로 교체됨

    /** 버튼 상태 복구 */
    private fun resetButtonState() {
        binding.btnRegisterOrClaim.isEnabled = true
        binding.btnRegisterOrClaim.text = "등록하기"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            super.onBackPressed()
        }
    }

    // 등록 성공 후 MainActivity로 이동하는 부분에 추가 플래그
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("refresh_boxes", true)
            putExtra("show_success_message", true)
            putExtra("dismiss_dialog", true)  // 🆕 다이얼로그 닫기 플래그 추가
        }
        startActivity(intent)
        finish()
    }

    /** 박스 코드로 등록 (기존 방식 - 필요시에만 사용) */
    private fun claimBox(code: String, alias: String) {
        val uid = auth.currentUser?.uid ?: return
        val codeRef = db.collection("boxCodes").document(code)
        val userRef = db.collection("users").document(uid)

        db.runTransaction { tx ->
            // 모든 read 작업을 먼저 실행
            val codeSnap = tx.get(codeRef)
            val userDoc = tx.get(userRef)

            // validation (모든 read 완료 후)
            if (!codeSnap.exists()) throw Exception("존재하지 않는 코드입니다.")
            if (codeSnap.getBoolean("active") == false) throw Exception("이미 사용된 코드입니다.")
            val boxId = codeSnap.getString("boxId") ?: throw Exception("boxId 없음")

            // 기존 boxAliases 처리 (이미 read된 데이터 사용)
            val existingAliases = userDoc.get("boxAliases") as? Map<String, String> ?: emptyMap()
            val newAliases = existingAliases.toMutableMap()

            if (alias.isNotEmpty()) {
                newAliases[boxId] = alias
            }

            // 이제 모든 write 작업 실행
            // 코드 비활성화
            tx.update(codeRef, "active", false)

            // 박스에 사용자 등록
            val boxRef = db.collection("boxes").document(boxId)
            tx.set(
                boxRef,
                mapOf(
                    "members.$uid" to "owner",
                    "ownerUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

            // 사용자 정보 업데이트
            tx.set(userRef, mapOf(
                "mainBoxId" to boxId,
                "boxAliases" to newAliases
            ), SetOptions.merge())

            boxId
        }.addOnSuccessListener { boxId ->
            resetButtonState()
            Toast.makeText(this, "박스($boxId) 등록 완료!", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            resetButtonState()
            Toast.makeText(this, it.message ?: "등록 실패", Toast.LENGTH_SHORT).show()
        }
    }

    /** 코드 없이 새 박스 생성 */
    private fun createNewBox(alias: String) {
        if (alias.isEmpty()) {
            binding.etBoxAlias.error = "새 박스를 만들려면 별칭이 필요합니다"
            return
        }

        val uid = auth.currentUser?.uid ?: return
        val boxId = UUID.randomUUID().toString()
        val batch = db.batch()

        // 박스 문서 생성
        val boxRef = db.collection("boxes").document(boxId)
        batch.set(
            boxRef,
            mapOf(
                "ownerUid" to uid,
                "members" to mapOf(uid to "owner"),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        // boxAliases를 맵 형태로 저장
        val userRef = db.collection("users").document(uid)
        batch.set(userRef, mapOf(
            "mainBoxId" to boxId,
            "boxAliases" to mapOf(boxId to alias)  // 맵 형태로 저장
        ), SetOptions.merge())

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "새 박스 생성 완료!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "생성 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 네트워크 상태 확인 메서드
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            Log.e("RegisterBoxActivity", "네트워크 상태 확인 실패", e)
            false
        }
    }

    // registerBoxWithValidation 메서드 시작 부분에 추가할 네트워크 체크
    private fun checkNetworkAndAuth(): Boolean {
        // 네트워크 상태 확인
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "인터넷 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
            resetButtonState()
            return false
        }

        // 인증 상태 확인
        val currentUser = auth.currentUser
        Log.d("RegisterBoxActivity", "현재 사용자: ${currentUser?.uid}")
        Log.d("RegisterBoxActivity", "사용자 이메일: ${currentUser?.email}")

        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
            // LoginActivity 경로 확인 필요
            try {
                startActivity(Intent(this, LoginActivity::class.java))
            } catch (e: Exception) {
                Log.e("RegisterBoxActivity", "LoginActivity를 찾을 수 없습니다", e)
                Toast.makeText(this, "로그인 화면으로 이동할 수 없습니다", Toast.LENGTH_SHORT).show()
            }
            resetButtonState()
            finish()
            return false
        }

        return true
    }
}
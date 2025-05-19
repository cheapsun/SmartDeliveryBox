package com.example.deliverybox.box

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Vibrator
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.deliverybox.R
import com.example.deliverybox.databinding.ActivityQrScanBinding
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.delay
import android.util.Log
import android.content.Context
import kotlinx.coroutines.launch
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestoreException

class QrScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScanBinding
    private var barcodeView: DecoratedBarcodeView? = null
    private var isScanning = true
    private var isTorchOn = false
    private var lastScanTime = 0L
    private val scanCooldownMs = 3000L // 3초 중복 스캔 방지
    private var loadingDialog: AlertDialog? = null

    companion object {
        const val REQUEST_CAMERA_PERMISSION = 1001
        const val RESULT_QR_CODE = "qr_code"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        barcodeView = binding.zxingBarcodeScanner

        setupUI()
        checkCameraPermission()
    }

    private fun setupUI() {
        // 툴바 설정
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 플래시 토글 버튼
        binding.btnFlashToggle.setOnClickListener {
            toggleFlash()
        }

        // 수동 입력 버튼
        binding.btnManualInput.setOnClickListener {
            val intent = Intent(this, RegisterBoxActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun toggleFlash() {
        barcodeView?.let { decoratedView ->
            try {
                val innerBarcodeView = decoratedView.barcodeView
                isTorchOn = !isTorchOn
                innerBarcodeView.setTorch(isTorchOn)
                updateFlashIcon()
            } catch (e: Exception) {
                Log.w("QrScanActivity", "플래시 토글 실패", e)
                Toast.makeText(this, "이 기기에서는 플래시를 지원하지 않습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    showPermissionDeniedDialog()
                }
            }
        }
    }

    private fun startCamera() {
        barcodeView?.apply {

            // 스캔 콜백 설정
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) {
                    result?.let { handleScanResult(it) }
                }

                override fun possibleResultPoints(resultPoints: List<com.google.zxing.ResultPoint?>?) {
                    // 스캔 가이드라인 업데이트 (선택사항)
                }
            })

            // 카메라 설정 최적화
            cameraSettings.requestedCameraId = -1 // 기본 카메라
            cameraSettings.isAutoFocusEnabled = true
            cameraSettings.isContinuousFocusEnabled = true

            resume()
        }
    }

    private fun handleScanResult(result: BarcodeResult) {
        if (!isScanning) return

        // 네트워크 상태 확인 추가
        if (!isNetworkAvailable()) {
            showErrorDialog(
                title = "네트워크 오류",
                message = "인터넷 연결을 확인해주세요"
            ) {
                lifecycleScope.launch {
                    delay(3000)
                    isScanning = true
                }
            }
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < scanCooldownMs) {
            return
        }

        lastScanTime = currentTime
        isScanning = false

        provideScanFeedback()

        val scannedCode = result.text

        // QR 코드 검증
        lifecycleScope.launch {
            try {
                // 로딩 표시
                val validationService = QrCodeValidationService()
                val validationResult = validationService.validateScannedQrCode(scannedCode)


                validationResult.fold(
                    onSuccess = { validation ->
                        when {
                            validation.canRegister -> {
                                // 등록 가능한 경우 - RegisterBoxActivity로 이동
                                val intent = Intent(this@QrScanActivity, RegisterBoxActivity::class.java).apply {
                                    putExtra(RESULT_QR_CODE, scannedCode)
                                    putExtra("from_qr_scan", true)
                                    putExtra("already_validated", true)
                                }
                                startActivity(intent)
                                finish()
                            }
                            else -> {
                                // 등록 불가한 경우 - 정보 표시 후 다시 스캔
                                showValidationResultDialog(validation) {
                                    // 3초 후 스캔 재시작
                                    lifecycleScope.launch {
                                        delay(3000)
                                        isScanning = true
                                    }
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        showErrorDialog(
                            title = "QR 코드 오류",
                            message = error.message ?: "QR 코드를 인식할 수 없습니다"
                        ) {
                            lifecycleScope.launch {
                                delay(3000)
                                isScanning = true
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                hideLoadingIndicator()
                showErrorDialog(
                    title = "네트워크 오류",
                    message = "인터넷 연결을 확인해주세요"
                ) {
                    lifecycleScope.launch {
                        delay(3000)
                        isScanning = true
                    }
                }
            }
        }
    }

    private fun showValidationResultDialog(
        validation: BoxValidationResult,
        onDismiss: () -> Unit
    ) {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("택배함 정보")
                .setMessage("""
                택배함 코드: ${validation.boxCode}
                상태: ${validation.status}
                배치: ${validation.batchName}
                
                ${validation.message}
            """.trimIndent())
                .setPositiveButton("확인") { _, _ -> onDismiss() }
                .setCancelable(false)
                .show()
        }
    }

    private fun showErrorDialog(
        title: String,
        message: String,
        onDismiss: () -> Unit
    ) {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("확인") { _, _ -> onDismiss() }
                .setCancelable(false)
                .show()
        }
    }

    private fun provideScanFeedback() {
        // 진동 피드백
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(100)
        } catch (e: Exception) {
            // 진동 실패 시 무시
        }

        // 소리 피드백 (선택사항)
        // 시각적 피드백으로 스캔 영역 하이라이트 등
    }

    private fun isValidQrCode(qrCode: String): Boolean {
        // QR 코드 형식 검증
        return qrCode.isNotEmpty() && qrCode.length >= 6
    }

    private fun returnQrResult(qrCode: String) {
        val resultIntent = Intent()
        resultIntent.putExtra(RESULT_QR_CODE, qrCode)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun showInvalidQrError() {
        Toast.makeText(
            this,
            "유효하지 않은 QR 코드입니다. 다시 시도해주세요.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("카메라 권한 필요")
            .setMessage("QR 코드를 스캔하려면 카메라 권한이 필요합니다.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                // 설정 앱으로 이동
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
                finish()
            }
            .setNegativeButton("취소") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun updateFlashIcon() {
        try {
            binding.btnFlashToggle.setImageResource(
                if (isTorchOn) R.drawable.ic_flash_on
                else R.drawable.ic_flash_off
            )
        } catch (e: Exception) {
            // 아이콘 로드 실패 시 기본 아이콘 사용
            binding.btnFlashToggle.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView?.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView?.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return barcodeView?.onKeyDown(keyCode, event) ?: super.onKeyDown(keyCode, event)
    }

    // 네트워크 상태 확인
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    // 로딩 다이얼로그 표시
    private fun showLoadingIndicator() {
        if (loadingDialog?.isShowing == true) return

        loadingDialog = AlertDialog.Builder(this)
            .setMessage("QR 코드 확인 중...")
            .setCancelable(false)
            .create()
        loadingDialog?.show()
    }

    // 로딩 다이얼로그 숨김
    private fun hideLoadingIndicator() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()

        // 바코드 스캐너 정리
        barcodeView?.apply {
            pause()
            decodeContinuous(null) // 콜백 해제
        }
        barcodeView = null

        // 로딩 다이얼로그 정리
        loadingDialog?.dismiss()
        loadingDialog = null
    }
}
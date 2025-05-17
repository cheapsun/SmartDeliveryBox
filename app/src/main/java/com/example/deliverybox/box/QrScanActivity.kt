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
import kotlinx.coroutines.launch

class QrScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScanBinding
    private var barcodeView: DecoratedBarcodeView? = null
    private var isScanning = true
    private var isTorchOn = false
    private var lastScanTime = 0L
    private val scanCooldownMs = 3000L // 3초 중복 스캔 방지

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
                // DecoratedBarcodeView 내부의 BarcodeView 접근
                val innerBarcodeView = decoratedView.barcodeView
                isTorchOn = !isTorchOn
                innerBarcodeView.setTorch(isTorchOn)
                updateFlashIcon()
            } catch (e: Exception) {
                // 실패 시 사용자에게 알림
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

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < scanCooldownMs) {
            return // 중복 스캔 방지
        }

        lastScanTime = currentTime
        isScanning = false

        // 스캔 성공 피드백
        provideScanFeedback()

        // 결과 검증 및 처리
        val qrCode = result.text
        if (isValidQrCode(qrCode)) {
            returnQrResult(qrCode)
        } else {
            showInvalidQrError()
            // 3초 후 스캔 재시작
            lifecycleScope.launch {
                delay(3000)
                isScanning = true
            }
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
}
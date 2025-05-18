package com.example.deliverybox.box.dialog

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.example.deliverybox.box.QrScanActivity
import com.example.deliverybox.box.RegisterBoxActivity
import com.example.deliverybox.databinding.DialogRegisterBoxMethodBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.util.Log
import android.os.Handler
import android.os.Looper

class RegisterBoxMethodDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogRegisterBoxMethodBinding? = null
    private val binding get() = _binding!!

    private var listener: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // QR 스캔 결과 처리
    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            android.app.Activity.RESULT_OK -> {
                val qrCode = result.data?.getStringExtra(QrScanActivity.RESULT_QR_CODE)
                qrCode?.let {
                    // QR 코드로 등록 액티비티 시작
                    val intent = Intent(requireContext(), RegisterBoxActivity::class.java).apply {
                        putExtra("qr_code", it)
                        putExtra("from_qr_scan", true)
                        putExtra("already_validated", true)
                    }
                    startActivity(intent)
                    dismissSafely() // QR 스캔 성공 시 다이얼로그 닫기
                }
            }
            android.app.Activity.RESULT_CANCELED -> {
                // QR 스캔 취소 시 다이얼로그는 그대로 유지
                // 사용자가 다시 시도할 수 있도록 함
                Log.d("RegisterBoxMethodDialog", "QR 스캔 취소됨")
            }
            else -> {
                // 기타 오류 시에도 다이얼로그 유지
                Log.d("RegisterBoxMethodDialog", "QR 스캔 기타 결과: ${result.resultCode}")
            }
        }
    }

    fun setOnRegisterBoxSelectedListener(listener: () -> Unit) {
        this.listener = listener
        Log.d("RegisterBoxMethodDialog", "리스너 설정됨")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRegisterBoxMethodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("RegisterBoxMethodDialog", "다이얼로그 뷰 생성됨")

        // QR 스캔 카드 클릭
        binding.cardQrRegister.setOnClickListener {
            Log.d("RegisterBoxMethodDialog", "QR 스캔 카드 클릭됨")
            try {
                val intent = Intent(requireContext(), QrScanActivity::class.java)
                qrScanLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e("RegisterBoxMethodDialog", "QR 스캔 액티비티 시작 실패", e)
            }
        }

        // 수동 등록 카드 클릭
        binding.cardManualRegister.setOnClickListener {
            Log.d("RegisterBoxMethodDialog", "수동 등록 카드 클릭됨")

            // 클릭 중복 방지
            binding.cardManualRegister.isEnabled = false

            // Fragment 상태 확인
            if (!isAdded || isDetached || isRemoving) {
                Log.w("RegisterBoxMethodDialog", "Fragment 상태가 유효하지 않음")
                return@setOnClickListener
            }

            try {
                // 리스너가 있는지 확인
                val currentListener = listener
                if (currentListener == null) {
                    Log.w("RegisterBoxMethodDialog", "리스너가 null임")
                    binding.cardManualRegister.isEnabled = true
                    return@setOnClickListener
                }

                // ⭐ 순서 변경: 리스너를 먼저 호출하고 나서 다이얼로그 닫기
                Log.d("RegisterBoxMethodDialog", "리스너 호출 시도")
                currentListener.invoke()
                Log.d("RegisterBoxMethodDialog", "리스너 호출 완료")

                // 다이얼로그 먼저 닫기
                dismissSafely()

            } catch (e: Exception) {
                Log.e("RegisterBoxMethodDialog", "수동 등록 처리 중 오류", e)
                binding.cardManualRegister.isEnabled = true
            }
        }

        // 취소 버튼
        binding.btnCancel.setOnClickListener {
            Log.d("RegisterBoxMethodDialog", "취소 버튼 클릭됨")
            dismissSafely()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d("RegisterBoxMethodDialog", "다이얼로그 뷰 해제됨")
    }

    // 안전한 다이얼로그 닫기 메서드
    private fun dismissSafely() {
        try {
            if (isAdded && !isDetached && !isRemoving && dialog?.isShowing == true) {
                dismiss()
                Log.d("RegisterBoxMethodDialog", "다이얼로그 닫힘")
            } else {
                Log.d("RegisterBoxMethodDialog", "다이얼로그 이미 닫혀있거나 닫을 수 없는 상태")
            }
        } catch (e: Exception) {
            Log.e("RegisterBoxMethodDialog", "다이얼로그 닫기 실패", e)
        }
    }

    // 🆕 Activity가 다시 시작될 때 다이얼로그 상태 확인
    fun dismissIfRegistrationCompleted() {
        try {
            val activity = requireActivity()
            if (activity.intent?.getBooleanExtra("refresh_boxes", false) == true) {
                dismiss()
            }
        } catch (e: Exception) {
            // Fragment가 detached 상태일 수 있음
            Log.d("RegisterBoxMethodDialog", "dismissIfRegistrationCompleted 실패: ${e.message}")
        }
    }

    // 🆕 다이얼로그가 보여지고 있는지 확인하는 유틸 메서드
    fun isCurrentlyShowing(): Boolean {
        return isAdded && !isDetached && !isRemoving && dialog?.isShowing == true
    }

    override fun onResume() {
        super.onResume()
        // 등록 완료 확인 (보조적 역할)
        dismissIfRegistrationCompleted()
    }

    override fun onPause() {
        super.onPause()
        Log.d("RegisterBoxMethodDialog", "다이얼로그 paused")
    }
}
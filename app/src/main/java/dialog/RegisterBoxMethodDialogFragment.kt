package com.example.deliverybox.dialog

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

class RegisterBoxMethodDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogRegisterBoxMethodBinding? = null
    private val binding get() = _binding!!

    private var listener: (() -> Unit)? = null

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
                    dismiss() // QR 스캔 성공 시 다이얼로그 닫기
                }
            }
            android.app.Activity.RESULT_CANCELED -> {
                // QR 스캔 취소 시 다이얼로그는 그대로 유지
                // 사용자가 다시 시도할 수 있도록 함
            }
            else -> {
                // 기타 오류 시에도 다이얼로그 유지
            }
        }
    }

    fun setOnRegisterBoxSelectedListener(listener: () -> Unit) {
        this.listener = listener
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

        // QR 스캔 카드 클릭
        binding.cardQrRegister.setOnClickListener {
            val intent = Intent(requireContext(), QrScanActivity::class.java)
            qrScanLauncher.launch(intent)
        }

        // 수동 등록 카드 클릭
        binding.cardManualRegister.setOnClickListener {
            // 다이얼로그 먼저 닫기
            dismiss()

            // 약간의 지연 후 리스너 호출 (다이얼로그가 완전히 닫힌 후)
            view.postDelayed({
                listener?.invoke()
            }, 100)
        }

        // 취소 버튼
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
            android.util.Log.d("RegisterBoxMethodDialog", "dismissIfRegistrationCompleted 실패: ${e.message}")
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
}
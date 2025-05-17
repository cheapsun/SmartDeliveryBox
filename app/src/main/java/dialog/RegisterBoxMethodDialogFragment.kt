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
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val qrCode = result.data?.getStringExtra(QrScanActivity.RESULT_QR_CODE)
            qrCode?.let {
                // QR 코드로 등록 액티비티 시작
                val intent = Intent(requireContext(), RegisterBoxActivity::class.java).apply {
                    putExtra("qr_code", it)
                    putExtra("from_qr_scan", true)
                }
                startActivity(intent)
                listener?.invoke()
                dismiss()
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
            listener?.invoke()
            dismiss()
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
}
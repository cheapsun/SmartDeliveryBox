package com.example.deliverybox.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.deliverybox.databinding.DialogRegisterBoxMethodBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class RegisterBoxMethodDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogRegisterBoxMethodBinding? = null
    private val binding get() = _binding!!

    // ✅ 리스너 선언 및 연결 메서드 포함
    private var listener: (() -> Unit)? = null

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

        // 수동 등록 카드 클릭 시 리스너 실행 후 닫기
        binding.cardManualRegister.setOnClickListener {
            listener?.invoke()
            dismiss()
        }

        // QR 등록 카드 (추후 확장 가능)
        binding.cardQrRegister.setOnClickListener {
            // TODO: QR 등록 로직 추가 예정
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

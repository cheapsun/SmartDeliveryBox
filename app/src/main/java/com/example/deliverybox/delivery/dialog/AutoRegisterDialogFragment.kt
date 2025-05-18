package com.example.deliverybox.delivery.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.example.deliverybox.databinding.DialogAutoRegisterBinding
import com.example.deliverybox.delivery.ExtractedPackageInfo
import com.example.deliverybox.delivery.PackageFragment
import com.example.deliverybox.delivery.PackageInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AutoRegisterDialogFragment : DialogFragment() {

    private lateinit var binding: DialogAutoRegisterBinding
    private lateinit var extractedInfo: ExtractedPackageInfo

    companion object {
        fun newInstance(packageInfo: ExtractedPackageInfo): AutoRegisterDialogFragment {
            return AutoRegisterDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("package_info", packageInfo)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogAutoRegisterBinding.inflate(layoutInflater)
        extractedInfo = arguments?.getParcelable("package_info") ?: return super.onCreateDialog(savedInstanceState)

        setupViews()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun setupViews() {
        with(binding) {
            // 감지된 정보 표시
            tvTrackingNumber.text = extractedInfo.trackingNumber
            tvCourierCompany.text = extractedInfo.courierCompany

            // 신뢰도 표시
            val confidenceColor = when {
                extractedInfo.confidence >= 0.8f -> ContextCompat.getColor(requireContext(), R.color.success)
                extractedInfo.confidence >= 0.6f -> ContextCompat.getColor(requireContext(), R.color.warning)
                else -> ContextCompat.getColor(requireContext(), R.color.error)
            }

            val confidenceText = when {
                extractedInfo.confidence >= 0.8f -> "높음"
                extractedInfo.confidence >= 0.6f -> "보통"
                else -> "낮음"
            }

            tvConfidence.text = "정확도: $confidenceText"
            tvConfidence.setTextColor(confidenceColor)

            // 신뢰도가 낮으면 경고 메시지 표시
            if (extractedInfo.confidence < 0.6f) {
                tvWarning.visibility = View.VISIBLE
                tvWarning.text = "정확도가 낮습니다. 정보를 다시 확인해주세요."
            }

            // 버튼 이벤트
            btnCancel.setOnClickListener { dismiss() }
            btnRegister.setOnClickListener {
                registerPackage()
            }
        }
    }

    private fun registerPackage() {
        val itemName = binding.etItemName.text.toString().trim()
        val memo = binding.etMemo.text.toString().trim()

        val packageInfo = PackageInfo(
            trackingNumber = extractedInfo.trackingNumber,
            courierCompany = extractedInfo.courierCompany,
            itemName = itemName.takeIf { it.isNotEmpty() },
            memo = memo.takeIf { it.isNotEmpty() },
            isAutoDetected = true,
            confidence = extractedInfo.confidence
        )

        // ViewModel로 등록 요청
        (parentFragment as? PackageFragment)?.viewModel?.registerPackage(packageInfo)
        dismiss()
    }
}
package com.example.deliverybox.delivery.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.toKorean
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FilterDialogFragment(
    private val onFilterSelected: (DeliveryStatus?) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val statuses = DeliveryStatus.values()
        val displayNames = arrayOf("전체") + statuses.map { it.toKorean() }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("상태 필터 선택")
            .setItems(displayNames) { _, which ->
                val selectedStatus = if (which == 0) null else statuses[which - 1]
                onFilterSelected(selectedStatus)
            }
            .setNegativeButton("취소", null)
            .create()
    }
}
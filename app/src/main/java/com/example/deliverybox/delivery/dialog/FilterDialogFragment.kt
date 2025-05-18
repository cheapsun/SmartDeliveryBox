package com.example.deliverybox.delivery.dialog

import com.example.deliverybox.delivery.DeliveryStatus

class FilterDialogFragment(
    private val onFilterSelected: (DeliveryStatus?) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val statuses = DeliveryStatus.values().map { it.name }
        val displayNames = arrayOf("전체") + statuses.map { it.toKorean() }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("상태 필터 선택")
            .setItems(displayNames) { _, which ->
                val selectedStatus = if (which == 0) null else DeliveryStatus.valueOf(statuses[which - 1])
                onFilterSelected(selectedStatus)
            }
            .setNegativeButton("취소", null)
            .create()
    }
}
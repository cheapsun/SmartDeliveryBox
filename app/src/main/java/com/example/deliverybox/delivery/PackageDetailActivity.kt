package com.example.deliverybox.delivery

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.deliverybox.R
import com.example.deliverybox.databinding.ActivityPackageDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.observe
import com.example.deliverybox.delivery.PackageInfo
import com.example.deliverybox.delivery.TrackingInfo
import com.example.deliverybox.delivery.DeliveryStatus

class PackageDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPackageDetailBinding
    private lateinit var viewModel: PackageDetailViewModel
    private var packageId: String = ""
    private var boxId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPackageDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageId = intent.getStringExtra("packageId") ?: return
        boxId = intent.getStringExtra("boxId") ?: return

        setupViews()
        observeViewModel()

        viewModel.loadPackageDetail(boxId, packageId)
    }

    private fun setupViews() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnRefresh.setOnClickListener {
            viewModel.refreshTrackingInfo()
        }

        binding.btnMarkAsReceived.setOnClickListener {
            showReceiveConfirmDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.packageInfo.observe(this) { packageInfo ->
            updatePackageInfo(packageInfo)
        }

        viewModel.trackingInfo.observe(this) { trackingInfo ->
            updateTrackingInfo(trackingInfo)
        }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is PackageDetailUiState.Loading -> showLoading(true)
                is PackageDetailUiState.Success -> showLoading(false)
                is PackageDetailUiState.Error -> {
                    showLoading(false)
                    showError(state.message)
                }
            }
        }
    }

    private fun updatePackageInfo(packageInfo: PackageInfo) {
        with(binding) {
            tvTrackingNumber.text = packageInfo.trackingNumber
            tvCourierCompany.text = packageInfo.courierCompany
            tvItemName.text = packageInfo.itemName ?: "상품명 없음"
            tvRegisteredDate.text = formatDate(packageInfo.registeredAt)

            // 상태 배지 업데이트
            updateStatusBadge(packageInfo.status)

            // 수령 확인 버튼 표시 여부
            binding.btnMarkAsReceived.visibility = if (
                packageInfo.status == DeliveryStatus.DELIVERED ||
                packageInfo.status == DeliveryStatus.IN_BOX
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun updateTrackingInfo(trackingInfo: TrackingInfo?) {
        if (trackingInfo != null) {
            binding.timelineView.setDeliverySteps(trackingInfo.deliverySteps, trackingInfo.currentStatus)
            binding.tvLastUpdated.text = "마지막 업데이트: ${formatDateTime(trackingInfo.lastUpdated)}"

            // 예상 배송일 표시
            trackingInfo.estimatedDelivery?.let { estimatedDate ->
                binding.tvEstimatedDelivery.text = "예상 배송: ${formatDate(estimatedDate)}"
                binding.tvEstimatedDelivery.visibility = View.VISIBLE
            }
        }
    }

    private fun updateStatusBadge(status: DeliveryStatus) {
        val (text, colorRes) = when (status) {
            DeliveryStatus.REGISTERED -> "등록됨" to R.color.gray_500
            DeliveryStatus.PICKED_UP -> "접수" to R.color.primary_blue
            DeliveryStatus.IN_TRANSIT -> "배송중" to R.color.primary_blue
            DeliveryStatus.OUT_FOR_DELIVERY -> "배송출발" to R.color.warning
            DeliveryStatus.IN_BOX -> "보관중" to R.color.warning
            DeliveryStatus.DELIVERED -> "배송완료" to R.color.success
        }

        binding.statusBadge.text = text
        binding.statusBadge.setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    private fun showReceiveConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("수령 확인")
            .setMessage("택배를 수령하셨습니까? 수령 확인 후에는 취소할 수 없습니다.")
            .setPositiveButton("수령 확인") { _, _ ->
                viewModel.markAsReceived()
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
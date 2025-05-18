package com.example.deliverybox.delivery.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.example.deliverybox.R
import com.example.deliverybox.databinding.ItemPackageCardBinding
import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.PackageInfo
import com.example.deliverybox.delivery.toKorean
import com.example.deliverybox.delivery.getColorRes
import com.example.deliverybox.delivery.getEmoji
import java.text.SimpleDateFormat
import java.util.*

class PackageAdapter(
    private val onItemClick: (PackageItem) -> Unit,
    private val onStatusChange: (PackageItem, DeliveryStatus) -> Unit,
    private val onDeleteClick: (PackageItem) -> Unit  // 삭제 콜백 추가
) : ListAdapter<PackageItem, PackageAdapter.PackageViewHolder>(PackageDiffCallback()) {

    companion object {
        private const val MENU_ID_MARK_RECEIVED = 1001
        private const val MENU_ID_MARK_IN_BOX = 1002
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
        val binding = ItemPackageCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PackageViewHolder(binding, onItemClick, onStatusChange)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // 스와이프 처리를 위한 메서드들
    fun deleteItem(position: Int) {
        val item = getItem(position)
        onDeleteClick(item)
    }

    fun markAsReceived(position: Int) {
        val item = getItem(position)
        onStatusChange(item, DeliveryStatus.DELIVERED)
    }

    class PackageViewHolder(
        private val binding: ItemPackageCardBinding,
        private val onItemClick: (PackageItem) -> Unit,
        private val onStatusChange: (PackageItem, DeliveryStatus) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PackageItem) {
            val packageInfo = item.data

            // 카드 테두리 설정 (stroke)
            (binding.root as MaterialCardView).apply {
                strokeWidth = 1
                strokeColor = ContextCompat.getColor(context, R.color.badge_stroke)
            }

            // 기본 정보 설정
            binding.tvTrackingNumber.text = formatTrackingNumber(packageInfo.trackingNumber)
            binding.tvCourierCompany.text = packageInfo.courierCompany
            binding.tvItemName.text = packageInfo.itemName ?: "상품명 없음"
            binding.tvRegisteredDate.text = formatRegisteredDate(packageInfo.registeredAt)

            // 상태 배지 설정
            updateStatusBadge(packageInfo.status)

            // 배송 진행률 설정
            updateProgressIndicator(packageInfo.status)

            // 클릭 이벤트
            binding.root.setOnClickListener { onItemClick(item) }

            // 퀵 액션 버튼 (상태 변경 가능한 경우만)
            if (packageInfo.status == DeliveryStatus.DELIVERED ||
                packageInfo.status == DeliveryStatus.IN_BOX
            ) {
                binding.btnQuickAction.visibility = View.VISIBLE
                binding.btnQuickAction.setOnClickListener { showQuickActionMenu(item) }
            } else {
                binding.btnQuickAction.visibility = View.GONE
            }
        }

        private fun updateStatusBadge(status: DeliveryStatus) {
            // PackageInfo.kt의 확장 함수들 활용
            val text = status.toKorean()
            val colorRes = status.getColorRes()
            val emoji = status.getEmoji()

            // 이모지와 텍스트 함께 표시
            binding.tvStatusBadge.text = "$emoji $text"
            binding.tvStatusBadge.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(itemView.context, colorRes)
            )

            // 아이콘 대신 이모지만 사용하므로 아이콘 숨김
            binding.ivStatusIcon.visibility = View.GONE

            // 접근성 설명
            binding.tvStatusBadge.contentDescription = "배송 상태: $emoji $text"
        }

        private fun updateProgressIndicator(status: DeliveryStatus) {
            val progress = when (status) {
                DeliveryStatus.REGISTERED -> 10
                DeliveryStatus.PICKED_UP -> 25
                DeliveryStatus.IN_TRANSIT -> 50
                DeliveryStatus.OUT_FOR_DELIVERY -> 75
                DeliveryStatus.IN_BOX, DeliveryStatus.DELIVERED -> 100
            }

            binding.progressBar.progress = progress
            binding.progressBar.progressTintList = when {
                progress == 100 -> ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.success)
                )
                progress >= 75 -> ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.warning)
                )
                else -> ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.primary_blue)
                )
            }
        }

        private fun showQuickActionMenu(item: PackageItem) {
            val popup = PopupMenu(itemView.context, binding.btnQuickAction)

            // 프로그래매틱하게 메뉴 생성
            popup.menu.add(0, MENU_ID_MARK_RECEIVED, 0, "✅ 수령 완료")
            popup.menu.add(0, MENU_ID_MARK_IN_BOX, 1, "📮 보관함에 보관")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    MENU_ID_MARK_RECEIVED -> {
                        onStatusChange(item, DeliveryStatus.DELIVERED)
                        true
                    }
                    MENU_ID_MARK_IN_BOX -> {
                        onStatusChange(item, DeliveryStatus.IN_BOX)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        private fun formatTrackingNumber(number: String): String {
            return number.chunked(4).joinToString(" ")
        }

        private fun formatRegisteredDate(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 24 * 60 * 60 * 1000 -> "오늘"
                diff < 48 * 60 * 60 * 1000 -> "어제"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}일 전"
                else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}

// DiffUtil 콜백
class PackageDiffCallback : DiffUtil.ItemCallback<PackageItem>() {
    override fun areItemsTheSame(oldItem: PackageItem, newItem: PackageItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: PackageItem, newItem: PackageItem): Boolean {
        return oldItem == newItem
    }
}
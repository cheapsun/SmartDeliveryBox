package com.example.deliverybox.delivery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.getEmoji
import com.example.deliverybox.delivery.toKorean
import java.text.SimpleDateFormat
import java.util.*

class PackageListAdapter(
    private val packageList: List<PackageItem>,
    private val onItemClick: (PackageItem) -> Unit
) : RecyclerView.Adapter<PackageListAdapter.PackageViewHolder>() {

    inner class PackageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // XML 파일에 실제로 존재하는 ID들만 사용
        private val tvTrackingNumber: TextView = itemView.findViewById(R.id.tv_tracking_number)
        private val tvCourierCompany: TextView = itemView.findViewById(R.id.tv_courier_company)
        private val tvItemName: TextView = itemView.findViewById(R.id.tv_item_name)
        private val tvRegisteredDate: TextView = itemView.findViewById(R.id.tv_registered_date)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tv_status_badge)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)

        fun bind(item: PackageItem) {
            val pkg = item.data

            // 운송장 번호 (4자리씩 공백으로 구분)
            tvTrackingNumber.text = pkg.trackingNumber.chunked(4).joinToString(" ")

            // 택배사
            tvCourierCompany.text = pkg.courierCompany

            // 상품명
            tvItemName.text = pkg.itemName ?: "상품명 없음"

            // 등록일 (상대적 시간으로 표시)
            tvRegisteredDate.text = formatRelativeTime(pkg.registeredAt)

            // 상태 배지 (이모지 + 한글)
            tvStatusBadge.text = "${pkg.status.getEmoji()} ${pkg.status.toKorean()}"

            // 진행률
            progressBar.progress = getProgressByStatus(pkg.status)

            // 클릭 이벤트
            itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun formatRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 24 * 60 * 60 * 1000 -> "오늘"
                diff < 48 * 60 * 60 * 1000 -> "어제"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}일 전"
                else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
            }
        }

        private fun getProgressByStatus(status: DeliveryStatus): Int {
            return when (status) {
                DeliveryStatus.REGISTERED -> 10
                DeliveryStatus.PICKED_UP -> 25
                DeliveryStatus.IN_TRANSIT -> 50
                DeliveryStatus.OUT_FOR_DELIVERY -> 75
                DeliveryStatus.IN_BOX, DeliveryStatus.DELIVERED -> 100
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_package_card, parent, false)
        return PackageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        holder.bind(packageList[position])
    }

    override fun getItemCount(): Int = packageList.size
}
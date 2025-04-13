package com.example.deliverybox.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.model.PackageItem

class PackageAdapter(
    private val packageList: List<PackageItem>,               // 문서 ID + 데이터 포함된 리스트
    private val onItemClick: (PackageItem) -> Unit            // 클릭 콜백
) : RecyclerView.Adapter<PackageAdapter.PackageViewHolder>() {

    // ViewHolder 정의
    inner class PackageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTrackingNumber: TextView = itemView.findViewById(R.id.tv_tracking_number)
        private val tvCourierInfo: TextView = itemView.findViewById(R.id.tv_courier_info)

        fun bind(item: PackageItem) {
            val pkg = item.data
            tvTrackingNumber.text = pkg.trackingNumber
            tvCourierInfo.text = "${pkg.courierCompany} | ${pkg.category}"

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_package_info, parent, false)  // ← XML 이름 맞춰서 수정
        return PackageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        holder.bind(packageList[position])
    }

    override fun getItemCount(): Int = packageList.size
}

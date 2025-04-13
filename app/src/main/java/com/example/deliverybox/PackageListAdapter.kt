package com.example.deliverybox.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.model.PackageItem

class PackageAdapter(
    private val packageList: List<PackageItem>,
    private val onItemClick: (PackageItem) -> Unit
) : RecyclerView.Adapter<PackageAdapter.PackageViewHolder>() {

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
            .inflate(R.layout.item_package_info, parent, false)  // XML 파일명 확인 필요
        return PackageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        holder.bind(packageList[position])
    }

    override fun getItemCount(): Int = packageList.size
}

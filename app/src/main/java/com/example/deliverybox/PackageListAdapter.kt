package com.example.deliverybox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PackageListAdapter(
    private val packageList: List<PackageItem>,
    private val boxId: String,
    private val onItemClick: (PackageItem, String) -> Unit
) : RecyclerView.Adapter<PackageListAdapter.PackageViewHolder>() {

    class PackageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val trackingNumberTextView: TextView = itemView.findViewById(R.id.tv_tracking_number)
        val carrierTextView: TextView = itemView.findViewById(R.id.tv_carrier)
        val infoTextView: TextView = itemView.findViewById(R.id.tv_info)
        val categoryTextView: TextView = itemView.findViewById(R.id.tv_category)
        val originTextView: TextView = itemView.findViewById(R.id.tv_origin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_package_info, parent, false)
        return PackageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        val item = packageList[position]
        holder.trackingNumberTextView.text = "운송장번호: ${item.trackingNumber}"
        holder.carrierTextView.text = "택배사: ${item.courierCompany}"
        holder.infoTextView.text = "내용: ${item.info}"
        holder.categoryTextView.text = "분류: ${item.category}"
        holder.originTextView.text = "발송지: ${item.origin}"

        // ✅ 아이템 클릭 시 이벤트 실행
        holder.itemView.setOnClickListener {
            onItemClick(item, boxId)
        }
    }

    override fun getItemCount(): Int = packageList.size
}

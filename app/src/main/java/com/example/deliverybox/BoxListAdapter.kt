package com.example.deliverybox.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.databinding.ItemBoxBinding
import com.example.deliverybox.model.BoxInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BoxListAdapter(
    private val boxList: List<BoxInfo>,
    private val onItemClick: (BoxInfo) -> Unit
) : RecyclerView.Adapter<BoxListAdapter.BoxViewHolder>() {

    inner class BoxViewHolder(val binding: ItemBoxBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(boxInfo: BoxInfo) {
            binding.apply {
                // 택배함 이름 설정
                tvBoxAlias.text = boxInfo.alias

                // 택배함 정보 설정
                val packageText = if (boxInfo.packageCount > 0) {
                    "${boxInfo.packageCount}개의 택배"
                } else {
                    "보관 중인 택배 없음"
                }

                // 마지막 업데이트 시간 (예시)
                val lastUpdateText = if (boxInfo.lastUpdated > 0) {
                    "마지막 업데이트: ${getTimeAgoText(boxInfo.lastUpdated)}"
                } else {
                    ""
                }

                tvBoxInfo.text = if (lastUpdateText.isNotEmpty()) {
                    "$packageText | $lastUpdateText"
                } else {
                    packageText
                }

                // 도어락 상태 표시
                ivStatusIndicator.setImageResource(R.drawable.ic_doorlock)
                ivStatusIndicator.setColorFilter(
                    root.context.getColor(
                        if (boxInfo.doorLocked) R.color.red else R.color.green_success
                    )
                )

                // 아이템 클릭 리스너
                root.setOnClickListener {
                    onItemClick(boxInfo)
                }
            }
        }

        private fun getTimeAgoText(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60 * 1000 -> "방금 전"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}분 전"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}시간 전"
                else -> {
                    val format = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                    format.format(Date(timestamp))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoxViewHolder {
        val binding = ItemBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BoxViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BoxViewHolder, position: Int) {
        holder.bind(boxList[position])
    }

    override fun getItemCount(): Int = boxList.size
}
package com.example.deliverybox.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.databinding.ItemLogBinding
import com.example.deliverybox.model.LogItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(
    private val logList: List<LogItem>
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    inner class LogViewHolder(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(logItem: LogItem) {
            binding.apply {
                // 이벤트 종류에 따라 텍스트 및 아이콘 설정
                val eventText = when (logItem.event) {
                    "OPEN" -> "${logItem.userName}님이 도어락을 열었습니다"
                    "CLOSE" -> "${logItem.userName}님이 도어락을 잠갔습니다"
                    "PACKAGE_ADDED" -> "${logItem.userName}님이 택배를 등록했습니다"
                    "PACKAGE_DELIVERED" -> "택배가 도착했습니다"
                    else -> "${logItem.userName}님이 ${logItem.event} 작업을 수행했습니다"
                }

                tvLogEvent.text = eventText

                // 타임스탬프를 가독성 있는 형태로 변환
                tvLogTimestamp.text = formatTimestamp(logItem.timestamp)

                // 이벤트 종류에 따라 아이콘 설정
                ivLogIcon.setImageResource(
                    when (logItem.event) {
                        "OPEN" -> R.drawable.ic_doorlock
                        "CLOSE" -> R.drawable.ic_doorlock
                        "PACKAGE_ADDED" -> R.drawable.ic_package
                        "PACKAGE_DELIVERED" -> R.drawable.ic_package
                        else -> R.drawable.ic_notification
                    }
                )

                // 아이콘 색상 설정
                ivLogIcon.setColorFilter(
                    root.context.getColor(
                        when (logItem.event) {
                            "OPEN" -> R.color.green_success
                            "CLOSE" -> R.color.red
                            "PACKAGE_ADDED" -> R.color.blue_primary
                            "PACKAGE_DELIVERED" -> R.color.amber_warning
                            else -> R.color.gray
                        }
                    )
                )
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60 * 1000 -> "방금 전"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}분 전"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}시간 전"
                else -> {
                    val format = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                    format.format(Date(timestamp))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logList[position])
    }

    override fun getItemCount(): Int = logList.size
}
package adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.databinding.ItemBoxBinding
import com.example.deliverybox.box.BoxInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.View


class BoxListAdapter(
    private val boxList: List<BoxInfo>,
    private val onItemClick: (BoxInfo) -> Unit
) : RecyclerView.Adapter<BoxListAdapter.BoxViewHolder>() {

    // 메인 박스 ID를 저장할 속성 추가
    private var mainBoxId: String = ""

    // mainBoxId 업데이트 메소드 추가
    fun updateMainBoxId(boxId: String) {
        mainBoxId = boxId
        // 데이터가 변경되었음을 알림 (옵션)
        // notifyDataSetChanged()
    }

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

                // 메인 박스 여부에 따른 시각적 표시
                val isMainBox = boxInfo.boxId == mainBoxId

                // 메인 표시 뱃지 표시/숨김
                tvMainBoxBadge.visibility = if (isMainBox) View.VISIBLE else View.GONE

                // 제목 스타일 변경
                if (isMainBox) {
                    tvBoxAlias.setTextColor(ContextCompat.getColor(root.context, R.color.primary_blue))
                    tvBoxAlias.setTypeface(null, Typeface.BOLD)
                } else {
                    tvBoxAlias.setTextColor(ContextCompat.getColor(root.context, R.color.text_primary))
                    tvBoxAlias.setTypeface(null, Typeface.NORMAL)
                }

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
        val boxInfo = boxList[position]  // 변수 추가
        holder.bind(boxInfo)  // 한 번만 바인딩

        // 주석 슬래시와 중복 바인딩 제거
        if (boxInfo.boxId == mainBoxId) {
            // 메인 박스 배경 스타일 적용 (실제 존재하는 리소스로 변경)
            holder.itemView.setBackgroundResource(R.drawable.bg_item_highlighted)
        } else {
            // 일반 박스 배경 스타일 적용 (실제 존재하는 리소스로 변경)
            holder.itemView.setBackgroundResource(R.drawable.bg_item_normal)
        }
    }

    override fun getItemCount(): Int = boxList.size
}
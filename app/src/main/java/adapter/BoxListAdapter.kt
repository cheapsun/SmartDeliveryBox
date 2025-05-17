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
import android.util.Log

class BoxListAdapter(
    private val boxList: List<BoxInfo>,
    private val onItemClick: (BoxInfo) -> Unit,
    private val onMainBoxToggle: ((BoxInfo, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<BoxListAdapter.BoxViewHolder>() {

    // 메인 박스 ID를 저장할 속성 추가
    private var mainBoxId: String = ""

    // mainBoxId 업데이트 메소드 추가
    fun updateMainBoxId(boxId: String) {
        val oldMainBoxId = mainBoxId
        mainBoxId = boxId

        // 변경된 경우에만 UI 업데이트
        if (oldMainBoxId != boxId) {
            Log.d("BoxListAdapter", "메인 박스 ID 업데이트: $oldMainBoxId -> $boxId")
            notifyDataSetChanged()
        }
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
                    tvBoxAlias.setTextColor(
                        ContextCompat.getColor(root.context, R.color.primary_blue)
                    )
                    tvBoxAlias.setTypeface(null, Typeface.BOLD)
                } else {
                    tvBoxAlias.setTextColor(
                        ContextCompat.getColor(root.context, R.color.text_primary)
                    )
                    tvBoxAlias.setTypeface(null, Typeface.NORMAL)
                }

                // 기존 리스너 제거
                root.setOnClickListener(null)
                tvMainBoxBadge.setOnClickListener(null)
                tvBoxAlias.setOnLongClickListener(null)

                Log.d("BoxListAdapter", "🎭 ${boxInfo.alias} 바인딩 - 메인 여부: $isMainBox")

                // 아이템 클릭 리스너
                root.setOnClickListener {
                    Log.d("BoxListAdapter", "👆 아이템 클릭: ${boxInfo.alias}")
                    onItemClick(boxInfo)
                }

                // 메인 박스 뱃지 클릭 리스너 - 메인 박스 해제
                if (isMainBox) {
                    tvMainBoxBadge.setOnClickListener { view ->
                        Log.d("BoxListAdapter", "🏷️ 메인 뱃지 클릭: ${boxInfo.alias} - 해제 요청")

                        // 이벤트 전파 완전 차단
                        view.setOnClickListener(null)  // 임시 제거
                        view.postDelayed({
                            if (view.isAttachedToWindow) {
                                // 리스너 다시 설정
                                view.setOnClickListener { v ->
                                    Log.d("BoxListAdapter", "🏷️ 메인 뱃지 재클릭: ${boxInfo.alias}")
                                    onMainBoxToggle?.invoke(boxInfo, false)
                                }
                            }
                        }, 1000)

                        // 부모 클릭 이벤트 전파 방지
                        view.parent?.let { parent ->
                            if (parent is View) {
                                parent.isClickable = false
                                parent.postDelayed({
                                    if (parent.isAttachedToWindow) {
                                        parent.isClickable = true
                                    }
                                }, 1000)
                            }
                        }

                        // 메인 박스 해제
                        onMainBoxToggle?.let { callback ->
                            Log.d("BoxListAdapter", "🔄 콜백 호출: 메인 박스 해제")
                            callback(boxInfo, false)
                        } ?: run {
                            Log.w("BoxListAdapter", "❌ onMainBoxToggle 콜백이 null임")
                        }
                    }
                } else {
                    tvMainBoxBadge.setOnClickListener(null)
                }

                // 박스 제목 롱클릭으로 메인 박스 설정/해제
                tvBoxAlias.setOnLongClickListener { view ->
                    Log.d("BoxListAdapter", "👆🔒 제목 롱클릭: ${boxInfo.alias}, 현재 메인: $isMainBox")

                    // 햅틱 피드백 추가
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

                    // 부모 클릭 이벤트 전파 방지
                    view.parent?.let { parent ->
                        if (parent is View) {
                            parent.isClickable = false
                            parent.postDelayed({
                                if (parent.isAttachedToWindow) {
                                    parent.isClickable = true
                                }
                            }, 1000)
                        }
                    }

                    // 메인 박스 토글
                    onMainBoxToggle?.let { callback ->
                        Log.d("BoxListAdapter", "🔄 콜백 호출: 메인 박스 토글 ($isMainBox -> ${!isMainBox})")
                        callback(boxInfo, !isMainBox)
                    } ?: run {
                        Log.w("BoxListAdapter", "❌ onMainBoxToggle 콜백이 null임")
                    }

                    true
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
        val boxInfo = boxList[position]
        val isMainBox = boxInfo.boxId == mainBoxId

        // 주석 슬래시와 중복 바인딩 제거
        if (isMainBox) {
            // 메인 박스 배경 스타일 적용 (실제 존재하는 리소스로 변경)
            holder.itemView.setBackgroundResource(R.drawable.bg_item_highlighted)
        } else {
            // 일반 박스 배경 스타일 적용 (실제 존재하는 리소스로 변경)
            holder.itemView.setBackgroundResource(R.drawable.bg_item_normal)
        }

        // 그 다음 bind 호출 (클릭 리스너 설정)
        holder.bind(boxInfo)
    }

    override fun getItemCount(): Int = boxList.size
}
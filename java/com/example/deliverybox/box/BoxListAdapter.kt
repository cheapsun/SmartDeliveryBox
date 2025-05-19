package com.example.deliverybox.box

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.databinding.ItemBoxBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.View
import android.util.Log
import android.widget.PopupMenu

class BoxListAdapter(
    private val boxList: MutableList<DeliveryBox>,
    private val onItemClick: (DeliveryBox) -> Unit,
    private val onMainBoxToggle: (DeliveryBox, Boolean) -> Unit,
    private val onUnregisterBox: ((DeliveryBox) -> Unit)? = null
) : RecyclerView.Adapter<BoxListAdapter.BoxViewHolder>() {

    // 메인 박스 ID를 저장할 속성 추가
    private var mainBoxId: String = ""

    // mainBoxId 업데이트 메소드 추가 (첫 번째 구현의 성능 최적화)
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
        fun bind(boxInfo: DeliveryBox) {
            binding.apply {
                // 택배함 이름 설정 (첫 번째 구현)
                tvBoxAlias.text = boxInfo.alias

                // 택배함 정보 설정 (첫 번째 구현 - 시간 표시 포함)
                val packageText = if (boxInfo.packageCount > 0) {
                    "${boxInfo.packageCount}개의 택배"
                } else {
                    "보관 중인 택배 없음"
                }

                // 마지막 업데이트 시간 (첫 번째 구현의 고급 기능)
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

                // 도어락 상태 표시 (두 번째 구현의 깔끔한 방식)
                val lockIcon = if (boxInfo.doorLocked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open
                val lockColor = if (boxInfo.doorLocked) R.color.status_locked else R.color.status_unlocked
                ivStatusIndicator.setImageResource(lockIcon)
                ivStatusIndicator.setColorFilter(ContextCompat.getColor(root.context, lockColor))

                // 메인 박스 여부에 따른 시각적 표시 (첫 번째 구현)
                val isMainBox = boxInfo.boxId == mainBoxId

                // 메인 표시 뱃지 표시/숨김
                tvMainBoxBadge.visibility = if (isMainBox) View.VISIBLE else View.GONE

                // 카드 배경색 설정 (두 번째 구현 추가)
                if (isMainBox) {
                    root.setCardBackgroundColor(ContextCompat.getColor(root.context, R.color.main_box_background))
                } else {
                    root.setCardBackgroundColor(ContextCompat.getColor(root.context, android.R.color.white))
                }

                // 제목 스타일 변경 (첫 번째 구현)
                if (isMainBox) {
                    tvBoxAlias.setTextColor(ContextCompat.getColor(root.context, R.color.primary_blue))
                    tvBoxAlias.setTypeface(null, Typeface.BOLD)
                } else {
                    tvBoxAlias.setTextColor(ContextCompat.getColor(root.context, R.color.text_primary))
                    tvBoxAlias.setTypeface(null, Typeface.NORMAL)
                }

                // 기존 리스너 제거 (첫 번째 구현의 안전한 방식)
                root.setOnClickListener(null)
                root.setOnLongClickListener(null)
                tvMainBoxBadge.setOnClickListener(null)
                tvBoxAlias.setOnClickListener(null)
                tvBoxAlias.setOnLongClickListener(null)

                Log.d("BoxListAdapter", "🎭 ${boxInfo.alias} 바인딩 - 메인 여부: $isMainBox")

                // 아이템 클릭 리스너 (첫 번째 구현)
                root.setOnClickListener {
                    Log.d("BoxListAdapter", "👆 아이템 클릭: ${boxInfo.alias}")
                    onItemClick(boxInfo)
                }

                // 장기 터치 메뉴 (두 번째 구현의 PopupMenu 방식)
                root.setOnLongClickListener { view ->
                    Log.d("BoxListAdapter", "👆🔒 아이템 롱클릭: ${boxInfo.alias}")
                    // 햅틱 피드백 (첫 번째 구현의 고급 기능)
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    showContextMenu(boxInfo)
                    true
                }

                // 메인 박스 뱃지 클릭 리스너 - 메인 박스 해제 (첫 번째 구현의 정교한 방식)
                if (isMainBox) {
                    tvMainBoxBadge.setOnClickListener { view ->
                        Log.d("BoxListAdapter", "🏷️ 메인 뱃지 클릭: ${boxInfo.alias} - 해제 요청")

                        // 햅틱 피드백 추가 (첫 번째 구현)
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

                        // 이벤트 전파 완전 차단 (첫 번째 구현의 고급 기능)
                        view.setOnClickListener(null)  // 임시 제거
                        view.postDelayed({
                            if (view.isAttachedToWindow) {
                                // 리스너 다시 설정
                                view.setOnClickListener { v ->
                                    Log.d("BoxListAdapter", "🏷️ 메인 뱃지 재클릭: ${boxInfo.alias}")
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                    onMainBoxToggle?.invoke(boxInfo, false)
                                }
                            }
                        }, 1000)

                        // 부모 클릭 이벤트 전파 방지 (첫 번째 구현)
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

                // 박스 제목 클릭으로 메인 박스 설정 (두 번째 구현의 간단한 방식)
                if (!isMainBox) {
                    tvBoxAlias.setOnClickListener { view ->
                        Log.d("BoxListAdapter", "👆 제목 클릭: ${boxInfo.alias} - 메인 설정 요청")
                        // 햅틱 피드백 (첫 번째 구현)
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        onMainBoxToggle(boxInfo, true)
                    }
                }

                // 박스 제목 롱클릭으로 메인 박스 토글 (첫 번째 구현 - 백업용)
                tvBoxAlias.setOnLongClickListener { view ->
                    Log.d("BoxListAdapter", "👆🔒 제목 롱클릭: ${boxInfo.alias}, 현재 메인: $isMainBox")

                    // 햅틱 피드백 (첫 번째 구현)
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

                    // 부모 클릭 이벤트 전파 방지 (첫 번째 구현)
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

        // PopupMenu 표시 (두 번째 구현의 기능 추가)
        private fun showContextMenu(boxInfo: DeliveryBox) {
            val popupMenu = PopupMenu(binding.root.context, binding.root)
            popupMenu.menuInflater.inflate(R.menu.menu_box_item, popupMenu.menu)

            // 메인 박스 설정/해제 메뉴 동적 변경 (두 번째 구현)
            val mainBoxItem = popupMenu.menu.findItem(R.id.action_toggle_main_box)
            if (boxInfo.boxId == mainBoxId) {
                mainBoxItem.title = "메인 택배함 해제"
                mainBoxItem.setIcon(R.drawable.ic_star_off)
            } else {
                mainBoxItem.title = "메인 택배함으로 설정"
                mainBoxItem.setIcon(R.drawable.ic_star)
            }

            // 등록 해제 메뉴 표시/숨김 (두 번째 구현)
            val unregisterItem = popupMenu.menu.findItem(R.id.action_unregister_box)
            unregisterItem.isVisible = onUnregisterBox != null

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_toggle_main_box -> {
                        val setAsMain = boxInfo.boxId != mainBoxId
                        Log.d("BoxListAdapter", "🔄 팝업 메뉴: 메인 박스 토글 ($setAsMain)")
                        onMainBoxToggle(boxInfo, setAsMain)
                        true
                    }
                    R.id.action_unregister_box -> {
                        Log.d("BoxListAdapter", "🗑️ 팝업 메뉴: 등록 해제 - ${boxInfo.alias}")
                        onUnregisterBox?.invoke(boxInfo)
                        true
                    }
                    else -> false
                }
            }

            // 팝업 메뉴 표시
            popupMenu.show()
        }

        // 시간 표시 유틸리티 (첫 번째 구현의 고급 기능)
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
        Log.d("BoxListAdapter", "🏗️ onCreateViewHolder 호출됨")
        val binding = ItemBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BoxViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BoxViewHolder, position: Int) {
        Log.d("BoxListAdapter", "📱 onBindViewHolder 시작: position=$position, boxList.size=${boxList.size}")

        if (position >= boxList.size) {
            Log.e("BoxListAdapter", "❌ position($position) >= boxList.size(${boxList.size})")
            return
        }

        val boxInfo = boxList[position]
        val isMainBox = boxInfo.boxId == mainBoxId

        Log.d("BoxListAdapter", "📱 onBindViewHolder: ${boxInfo.alias}, isMain=$isMainBox")

        // 메인 박스 배경 스타일 적용
        if (isMainBox) {
            holder.itemView.setBackgroundResource(R.drawable.bg_item_highlighted)
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_item_normal)
        }

        // 그 다음 bind 호출
        holder.bind(boxInfo)

        Log.d("BoxListAdapter", "📱 onBindViewHolder 완료: ${boxInfo.alias}")
    }

    override fun getItemCount(): Int {
        val count = boxList.size
        Log.d("BoxListAdapter", "📊 getItemCount: $count")
        return count
    }
}
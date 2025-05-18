package com.example.deliverybox.delivery.swipe

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.delivery.adapter.PackageAdapter

class PackageSwipeCallback(
    private val adapter: PackageAdapter
) : ItemTouchHelper.SimpleCallback(
    0, // 드래그 비활성화
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // 좌우 스와이프만
) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false // 드래그 이동 비활성화

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition

        when (direction) {
            ItemTouchHelper.LEFT -> {
                // 왼쪽 스와이프: 삭제
                adapter.deleteItem(position)
            }
            ItemTouchHelper.RIGHT -> {
                // 오른쪽 스와이프: 수령 확인
                adapter.markAsReceived(position)
            }
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            drawSwipeBackground(c, viewHolder, dX)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun drawSwipeBackground(c: Canvas, viewHolder: RecyclerView.ViewHolder, dX: Float) {
        val itemView = viewHolder.itemView
        val paint = Paint()

        if (dX > 0) {
            // 오른쪽 스와이프 - 수령 확인 (초록색)
            paint.color = ContextCompat.getColor(itemView.context, R.color.success)
            c.drawRect(
                itemView.left.toFloat(),
                itemView.top.toFloat(),
                itemView.left + dX,
                itemView.bottom.toFloat(),
                paint
            )

            // 체크 아이콘 그리기
            drawIcon(c, itemView, android.R.drawable.ic_menu_send, dX, true)

        } else if (dX < 0) {
            // 왼쪽 스와이프 - 삭제 (빨간색)
            paint.color = ContextCompat.getColor(itemView.context, R.color.error)
            c.drawRect(
                itemView.right + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat(),
                paint
            )

            // 삭제 아이콘 그리기
            drawIcon(c, itemView, android.R.drawable.ic_menu_delete, dX, false)
        }
    }

    private fun drawIcon(canvas: Canvas, itemView: View, iconRes: Int, dX: Float, isRightSwipe: Boolean) {
        try {
            val icon = ContextCompat.getDrawable(itemView.context, iconRes)
            icon?.let {
                val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + it.intrinsicHeight

                val iconLeft: Int
                val iconRight: Int

                if (isRightSwipe) {
                    // 오른쪽 스와이프 - 왼쪽에 아이콘
                    iconLeft = itemView.left + iconMargin
                    iconRight = iconLeft + it.intrinsicWidth
                } else {
                    // 왼쪽 스와이프 - 오른쪽에 아이콘
                    iconRight = itemView.right - iconMargin
                    iconLeft = iconRight - it.intrinsicWidth
                }

                it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                it.setTint(Color.WHITE)
                it.draw(canvas)
            }
        } catch (e: Exception) {
            // 아이콘을 찾을 수 없는 경우 무시하고 배경색만 표시
        }
    }
}
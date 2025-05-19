package com.example.deliverybox.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.model.Notification
import com.example.deliverybox.model.NotificationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val notifications: List<Notification>,
    private val onNotificationClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconView: ImageView = view.findViewById(R.id.iv_notification_icon)
        val titleView: TextView = view.findViewById(R.id.tv_notification_title)
        val messageView: TextView = view.findViewById(R.id.tv_notification_message)
        val timeView: TextView = view.findViewById(R.id.tv_notification_time)
        val unreadIndicator: View = view.findViewById(R.id.view_unread_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]

        holder.titleView.text = notification.title
        holder.messageView.text = notification.message
        holder.timeView.text = formatTime(notification.timestamp)

        // Unread indicator
        holder.unreadIndicator.visibility = if (notification.read) View.GONE else View.VISIBLE

        // Icon based on notification type
        val iconRes = when (notification.type) {
            NotificationType.PACKAGE_ARRIVED -> R.drawable.ic_package
            NotificationType.DOORLOCK_CONTROL -> R.drawable.ic_doorlock
            NotificationType.SYSTEM -> R.drawable.ic_setting
            else -> R.drawable.ic_notification
        }

        holder.iconView.setImageResource(iconRes)

        // Click listener
        holder.itemView.setOnClickListener {
            onNotificationClick(notification)
        }
    }

    override fun getItemCount() = notifications.size

    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60 * 1000 -> "방금 전"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}분 전"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}시간 전"
            else -> {
                val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }
}
package com.example.deliverybox.delivery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.deliverybox.R

class PackageNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "package_updates"
        private const val CHANNEL_NAME = "택배 상태 업데이트"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "택배 배송 상태 변경 알림"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendStatusUpdateNotification(packageInfo: PackageInfo) {
        val title = when (packageInfo.status) {
            DeliveryStatus.PICKED_UP -> "택배 접수 완료"
            DeliveryStatus.IN_TRANSIT -> "배송 중"
            DeliveryStatus.OUT_FOR_DELIVERY -> "배송 출발"
            DeliveryStatus.IN_BOX -> "택배함 보관 중"
            DeliveryStatus.DELIVERED -> "배송 완료"
            else -> "택배 상태 업데이트"
        }

        val message = "${packageInfo.courierCompany} ${packageInfo.getFormattedTrackingNumber()}"

        // 패키지 상세 화면으로 이동하는 Intent
        val intent = Intent(context, PackageDetailActivity::class.java).apply {
            putExtra("packageId", packageInfo.id)
            putExtra("boxId", packageInfo.boxId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            packageInfo.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_package)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\n${packageInfo.status.toKorean()}")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(
            NOTIFICATION_ID_BASE + packageInfo.id.hashCode(),
            notification
        )
    }

    fun sendAutoDetectionNotification(extractedInfo: ExtractedPackageInfo) {
        // TODO: PackageAddActivity가 생성된 후 주석 해제
        /*
        val intent = Intent(context, PackageAddActivity::class.java).apply {
            putExtra("extracted_info", extractedInfo)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        */

        // 임시로 MainActivity 또는 다른 기본 Activity로 대체
        val intent = Intent().apply {
            // TODO: 실제 메인 액티비티나 패키지 추가 액티비티로 교체
            // setClassName(context, "com.example.deliverybox.ui.MainActivity")
            putExtra("extracted_info", extractedInfo)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            extractedInfo.trackingNumber.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_package)
            .setContentTitle("택배 정보 자동 감지")
            .setContentText("${extractedInfo.courierCompany} 택배가 감지되었습니다.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("운송장번호: ${extractedInfo.trackingNumber}\n택배사: ${extractedInfo.courierCompany}\n\n탭하여 등록하세요.")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(
            NOTIFICATION_ID_BASE + extractedInfo.trackingNumber.hashCode(),
            notification
        )
    }

    fun cancelNotification(packageId: String) {
        notificationManager.cancel(NOTIFICATION_ID_BASE + packageId.hashCode())
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
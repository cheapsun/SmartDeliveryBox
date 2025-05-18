package com.example.deliverybox.delivery.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object NotificationPermissionHelper {

    fun hasNotificationAccess(context: Context): Boolean {
        val contentResolver = context.contentResolver
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = context.packageName
        return enabledListeners?.contains(packageName) == true
    }

    fun requestNotificationAccess(activity: Activity) {
        if (!hasNotificationAccess(activity)) {
            showNotificationPermissionDialog(activity)
        }
    }

    private fun showNotificationPermissionDialog(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("알림 접근 권한 필요")
            .setMessage("""
                자동 택배 등록을 위해 알림 접근 권한이 필요합니다.
                
                • 택배 관련 알림만 분석합니다
                • 개인정보는 필터링됩니다
                • 언제든지 설정에서 해제 가능합니다
            """.trimIndent())
            .setPositiveButton("설정으로 이동") { _, _ ->
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                activity.startActivity(intent)
            }
            .setNegativeButton("나중에") { _, _ -> }
            .show()
    }
}
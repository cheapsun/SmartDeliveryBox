package com.example.deliverybox.delivery

import android.app.Notification
import android.content.Intent
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.parcelize.Parcelize  // ✅ 추가된 import

class PackageNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PackageNotificationListener"

        // 지원 택배사 패턴
        private val COURIER_PATTERNS = mapOf(
            "CJ대한통운" to "\\[CJ대한통운\\].*운송장번호[\\s:]*([0-9]{10,13})".toRegex(),
            "한진택배" to "\\[한진택배\\].*송장번호[\\s:]*([0-9]{10,13})".toRegex(),
            "롯데택배" to "\\[롯데택배\\].*운송장[\\s:]*([0-9]{10,13})".toRegex(),
            "우체국택배" to "\\[우체국\\].*등기번호[\\s:]*([0-9]{13})".toRegex()
        )

        // 모니터링 대상 앱
        private val MONITORED_APPS = setOf(
            "com.kakao.talk",           // 카카오톡
            "com.coupang.mobile",       // 쿠팡
            "com.nhn.android.shopping", // 네이버쇼핑
            "com.lotteon.shopping"      // 롯데온
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isTargetNotification(sbn)) return

        val notification = sbn.notification
        val text = extractNotificationText(notification)

        text?.let { content ->
            extractPackageInfo(content)?.let { packageInfo ->
                // 자동 추출된 정보를 브로드캐스트로 전송
                sendBroadcast(Intent("com.example.deliverybox.PACKAGE_DETECTED").apply {
                    putExtra("package_info", packageInfo)
                    putExtra("source_app", sbn.packageName)
                })
            }
        }
    }

    private fun isTargetNotification(sbn: StatusBarNotification): Boolean {
        // 택배 관련 앱인지 확인
        if (sbn.packageName !in MONITORED_APPS) return false

        // 텍스트에 택배 관련 키워드가 있는지 확인
        val text = extractNotificationText(sbn.notification) ?: return false
        val keywords = listOf("택배", "배송", "운송장", "송장", "배달")

        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun extractNotificationText(notification: Notification): String? {
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""

        return "$title $text $bigText".takeIf { it.isNotBlank() }
    }

    private fun extractPackageInfo(text: String): ExtractedPackageInfo? {
        COURIER_PATTERNS.forEach { (courier, pattern) ->
            val match = pattern.find(text)
            if (match != null) {
                val trackingNumber = match.groupValues[1]

                // 신뢰도 계산
                val confidence = calculateConfidence(text, courier, trackingNumber)

                return ExtractedPackageInfo(
                    trackingNumber = trackingNumber,
                    courierCompany = courier,
                    confidence = confidence,
                    sourceText = filterPersonalInfo(text)
                )
            }
        }
        return null
    }

    private fun calculateConfidence(text: String, courier: String, trackingNumber: String): Float {
        var confidence = 0.8f // 기본 신뢰도

        // 택배사명이 명확히 표시되면 +0.1
        if (text.contains(courier)) confidence += 0.1f

        // 운송장번호 길이가 적절하면 +0.1
        if (trackingNumber.length in 10..13) confidence += 0.1f

        // 개인정보가 포함되어 있으면 -0.2 (위험도 높음)
        if (containsPersonalInfo(text)) confidence -= 0.2f

        return confidence.coerceIn(0.0f, 1.0f)
    }

    private fun containsPersonalInfo(text: String): Boolean {
        val personalPatterns = listOf(
            "\\b[가-힣]{2,4}님\\b".toRegex(),                  // 이름
            "\\b\\d{2,3}-\\d{3,4}-\\d{4}\\b".toRegex(),       // 전화번호
            "\\b[가-힣]+[시군구]\\s[가-힣]+[동읍면]".toRegex()  // 주소
        )

        return personalPatterns.any { it.find(text) != null }
    }

    private fun filterPersonalInfo(text: String): String {
        var filtered = text
        val personalPatterns = mapOf(
            "\\b[가-힣]{2,4}님\\b".toRegex() to "[이름]",
            "\\b\\d{2,3}-\\d{3,4}-\\d{4}\\b".toRegex() to "[전화번호]",
            "\\b[가-힣]+[시군구]\\s[가-힣]+[동읍면]".toRegex() to "[주소]"
        )

        personalPatterns.forEach { (pattern, replacement) ->
            filtered = pattern.replace(filtered, replacement)
        }

        return filtered
    }
}

@Parcelize  // ✅ 애노테이션 추가
data class ExtractedPackageInfo(
    val trackingNumber: String,
    val courierCompany: String,
    val confidence: Float,
    val sourceText: String
) : Parcelable
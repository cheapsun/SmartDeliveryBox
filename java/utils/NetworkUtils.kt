package com.example.deliverybox.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * 네트워크 관련 유틸리티
 */
object NetworkUtils {

    private const val TAG = "NetworkUtils"

    /**
     * 네트워크 연결 상태 확인
     */
    fun isNetworkAvailable(context: Context): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false

            // 다양한 네트워크 타입 확인
            val hasInternet = actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            // 연결 타입 로깅 (디버깅용)
            val isWifi = actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isEthernet = actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

            Log.d(TAG, "네트워크 상태: 인터넷=$hasInternet, 검증=$hasValidated, " +
                    "WiFi=$isWifi, 셀룰러=$isCellular, 이더넷=$isEthernet")

            return hasInternet && hasValidated
        } catch (e: Exception) {
            Log.e(TAG, "네트워크 상태 확인 중 오류: ${e.message}")
            return false
        }
    }

    /**
     * 연결 품질 확인
     */
    fun getConnectionQuality(context: Context): ConnectionQuality {
        try {
            if (!isNetworkAvailable(context)) {
                return ConnectionQuality.NONE
            }

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connectivityManager.activeNetwork ?: return ConnectionQuality.NONE
            val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return ConnectionQuality.NONE

            // Wi-Fi 연결인 경우 좋음으로 간주
            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return ConnectionQuality.GOOD
            }

            // 셀룰러는 중간으로 간주 (모바일 데이터)
            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return ConnectionQuality.MODERATE
            }

            // 이더넷도 좋음으로 간주
            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return ConnectionQuality.GOOD
            }

            // 다른 종류의 네트워크는 중간으로 간주
            return ConnectionQuality.MODERATE
        } catch (e: Exception) {
            Log.e(TAG, "연결 품질 확인 중 오류: ${e.message}")
            return ConnectionQuality.UNKNOWN
        }
    }

    /**
     * 연결 품질 enum
     */
    enum class ConnectionQuality {
        NONE,       // 연결 없음
        POOR,       // 열악한 연결
        MODERATE,   // 보통 연결
        GOOD,       // 좋은 연결
        UNKNOWN     // 알 수 없음
    }
}
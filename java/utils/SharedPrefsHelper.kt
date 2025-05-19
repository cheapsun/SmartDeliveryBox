package com.example.deliverybox.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SharedPrefsHelper {
    private const val TAG = "SharedPrefsHelper"
    private const val PREFS_FILENAME = "deliverybox_prefs"
    private const val SECURE_PREFS_FILENAME = "deliverybox_secure_prefs"

    // 보안 관련 키
    private const val KEY_LAST_LOGIN_TIME = "last_login_time"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_SESSION_TOKEN = "session_token"
    private const val KEY_AUTO_LOGIN = "auto_login"

    // 일반 설정 키
    private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_LAST_APP_VERSION = "last_app_version"

    /**
     * 일반 SharedPreferences 가져오기
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    /**
     * 암호화된 SharedPreferences 가져오기
     */
    private fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            val spec = KeyGenParameterSpec.Builder(
                MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            val masterKey = MasterKey.Builder(context)
                .setKeyGenParameterSpec(spec)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "보안 SharedPreferences 생성 실패: ${e.message}")
            context.getSharedPreferences(SECURE_PREFS_FILENAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * 마지막 로그인 시간 저장
     */
    fun setLastLoginTime(context: Context, timestamp: Long) {
        try {
            val prefs = getSecurePrefs(context)
            prefs.edit().putLong(KEY_LAST_LOGIN_TIME, timestamp).apply()
        } catch (e: Exception) {
            Log.e(TAG, "로그인 시간 저장 실패: ${e.message}")
        }
    }

    /**
     * 마지막 로그인 시간 가져오기
     */
    fun getLastLoginTime(context: Context): Long {
        return try {
            val prefs = getSecurePrefs(context)
            prefs.getLong(KEY_LAST_LOGIN_TIME, 0)
        } catch (e: Exception) {
            Log.e(TAG, "로그인 시간 조회 실패: ${e.message}")
            0
        }
    }

    /**
     * 사용자 세션 저장
     */
    fun saveUserSession(context: Context, userId: String, sessionToken: String) {
        try {
            val prefs = getSecurePrefs(context)
            with(prefs.edit()) {
                putString(KEY_USER_ID, userId)
                putString(KEY_SESSION_TOKEN, sessionToken)
                putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis())
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "사용자 세션 저장 실패: ${e.message}")
        }
    }

    /**
     * 사용자 ID 가져오기
     */
    fun getUserId(context: Context): String? {
        return try {
            getSecurePrefs(context).getString(KEY_USER_ID, null)
        } catch (e: Exception) {
            Log.e(TAG, "사용자 ID 조회 실패: ${e.message}")
            null
        }
    }

    /**
     * 세션 토큰 가져오기
     */
    fun getSessionToken(context: Context): String? {
        return try {
            getSecurePrefs(context).getString(KEY_SESSION_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "세션 토큰 조회 실패: ${e.message}")
            null
        }
    }

    /**
     * 로그인 세션 정보 모두 지우기
     */
    fun clearLoginSession(context: Context) {
        try {
            val prefs = getSecurePrefs(context)
            with(prefs.edit()) {
                remove(KEY_USER_ID)
                remove(KEY_SESSION_TOKEN)
                remove(KEY_LAST_LOGIN_TIME)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "세션 정보 삭제 실패: ${e.message}")
        }
    }

    /**
     * 알림 설정 저장
     */
    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply()
    }

    /**
     * 알림 설정 가져오기 (기본값: 활성화)
     */
    fun isNotificationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATION_ENABLED, true)
    }

    /**
     * 테마 모드 저장
     */
    fun setThemeMode(context: Context, themeMode: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_MODE, themeMode).apply()
    }

    /**
     * 테마 모드 가져오기 (기본값: 시스템 설정)
     */
    fun getThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, 0)  // 0: 시스템 설정, 1: 라이트, 2: 다크
    }

    /**
     * 앱 버전 저장
     */
    fun saveLastAppVersion(context: Context, versionCode: Int) {
        getPrefs(context).edit().putInt(KEY_LAST_APP_VERSION, versionCode).apply()
    }

    /**
     * 마지막 앱 버전 가져오기
     */
    fun getLastAppVersion(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_APP_VERSION, 0)
    }

    /**
     * 자동 로그인 설정 저장
     */
    fun setAutoLogin(context: Context, enabled: Boolean) {
        getSecurePrefs(context).edit().putBoolean(KEY_AUTO_LOGIN, enabled).apply()
    }

    /**
     * 자동 로그인 설정 확인
     */
    fun isAutoLoginEnabled(context: Context): Boolean {
        return getSecurePrefs(context).getBoolean(KEY_AUTO_LOGIN, false)
    }

    /**
     * 모든 설정 초기화 (앱 재설치 등)
     */
    fun clearAllPreferences(context: Context) {
        try {
            getPrefs(context).edit().clear().apply()
            getSecurePrefs(context).edit().clear().apply()
            Log.d(TAG, "모든 설정 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "설정 초기화 중 오류: ${e.message}")
        }
    }
}
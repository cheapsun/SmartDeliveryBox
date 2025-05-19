package com.example.deliverybox.lock

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.util.UUID

/**
 * QR 코드 생성 유틸리티 클래스
 */
object QrCodeGenerator {

    private const val TAG = "QrCodeGenerator"

    /**
     * 도어락 제어용 QR 코드 생성
     *
     * @param boxId 택배함 ID
     * @param userId 사용자 ID
     * @param action 액션 (OPEN 또는 CLOSE)
     * @param expirationSeconds 유효 시간(초)
     * @param size QR 코드 크기(픽셀)
     * @return 생성된 QR 코드 비트맵, 실패 시 null
     */
    fun generateDoorlockQrCode(
        boxId: String,
        userId: String,
        action: String = "OPEN",
        expirationSeconds: Int = 45, // 45초
        size: Int = 512
    ): Bitmap? {
        try {
            // 현재 타임스탬프
            val timestamp = System.currentTimeMillis()
            // 만료 타임스탬프
            val expirationTime = timestamp + (expirationSeconds * 1000)
            // 임의의 토큰 생성 (보안용)
            val token = UUID.randomUUID().toString()

            // QR 코드에 담길 정보 생성 (JSON 형식)
            val qrContent = """
                {
                    "boxId": "$boxId",
                    "userId": "$userId",
                    "action": "$action",
                    "timestamp": $timestamp,
                    "expirationTime": $expirationTime,
                    "token": "$token"
                }
            """.trimIndent()

            // QR 코드 생성
            return generateQrCode(qrContent, size)
        } catch (e: Exception) {
            Log.e(TAG, "QR 코드 생성 실패: ${e.message}")
            return null
        }
    }

    /**
     * 일반 QR 코드 생성
     *
     * @param content QR에 담길 내용
     * @param size QR 코드 크기(픽셀)
     * @return 생성된 QR 코드 비트맵, 실패 시 null
     */
    fun generateQrCode(content: String, size: Int = 512): Bitmap? {
        try {
            val bitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size
            )
            return createBitmap(bitMatrix)
        } catch (e: WriterException) {
            Log.e(TAG, "QR 코드 생성 실패: ${e.message}")
            return null
        }
    }

    /**
     * BitMatrix를 Bitmap으로 변환
     */
    private fun createBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
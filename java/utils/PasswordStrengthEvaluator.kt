package com.example.deliverybox.utils

enum class PasswordStrength(val score: Int, val color: Int, val label: String) {
    WEAK(0, android.graphics.Color.RED, "약함"),
    MEDIUM(1, android.graphics.Color.parseColor("#FFA500"), "중간"),
    STRONG(2, android.graphics.Color.GREEN, "강함")
}

object PasswordStrengthEvaluator {
    fun evaluate(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.WEAK

        var score = 0

        // 길이 점수
        if (password.length >= 8) score += 1
        if (password.length >= 12) score += 1

        // 복잡성 점수
        if (password.any { it.isUpperCase() }) score += 1
        if (password.any { it.isLowerCase() }) score += 1
        if (password.any { it.isDigit() }) score += 1
        if (password.any { !it.isLetterOrDigit() }) score += 1

        // 연속된 문자 감지 (낮은 점수)
        if (hasSequentialChars(password)) score -= 1

        return when {
            score < 3 -> PasswordStrength.WEAK
            score < 5 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
    }

    private fun hasSequentialChars(password: String): Boolean {
        val lowerPassword = password.lowercase()

        // 연속된 숫자 확인
        for (i in 0 until password.length - 2) {
            if (password[i].isDigit() && password[i+1].isDigit() && password[i+2].isDigit()) {
                val digit1 = password[i].digitToInt()
                val digit2 = password[i+1].digitToInt()
                val digit3 = password[i+2].digitToInt()

                if (digit1 + 1 == digit2 && digit2 + 1 == digit3) return true
                if (digit1 - 1 == digit2 && digit2 - 1 == digit3) return true
            }
        }

        // 연속된 알파벳 확인
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        for (i in 0 until alphabet.length - 2) {
            val pattern = alphabet.substring(i, i + 3)
            if (lowerPassword.contains(pattern)) return true
        }

        return false
    }
}
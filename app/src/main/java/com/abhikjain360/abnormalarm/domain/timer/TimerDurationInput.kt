package com.abhikjain360.abnormalarm.domain.timer

private const val MaxDigits = 6
private const val MaxDurationSeconds = 99 * 3600L + 59 * 60L + 59L

/** Helpers for the clock-app-style timer numpad: digits fill HH:MM:SS from right to left. */
object TimerDurationInput {
    fun append(currentDigits: String, token: String): String {
        require(token == "00" || token.length == 1 && token[0].isDigit()) {
            "Unsupported timer input token: $token"
        }
        val normalized = (currentDigits + token)
            .filter(Char::isDigit)
            .trimStart('0')
            .take(MaxDigits)
        return normalized
    }

    fun backspace(currentDigits: String): String = currentDigits.dropLast(1)

    fun secondsFromDigits(digits: String): Long {
        if (digits.isBlank()) return 0L
        val padded = digits.filter(Char::isDigit).take(MaxDigits).padStart(MaxDigits, '0')
        val hours = padded.substring(0, 2).toLong()
        val minutes = padded.substring(2, 4).toLong()
        val seconds = padded.substring(4, 6).toLong()
        return (hours * 3600L + minutes * 60L + seconds).coerceAtMost(MaxDurationSeconds)
    }

    fun digitsFromSeconds(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceIn(0L, MaxDurationSeconds)
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val remainingSeconds = seconds % 60L
        val digits = "%02d%02d%02d".format(hours, minutes, remainingSeconds).trimStart('0')
        return digits
    }

    fun formatSeconds(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0L)
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val remainingSeconds = seconds % 60L
        return "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
    }
}

package com.yishenghuang.keyic.core.crypto

import com.yishenghuang.keyic.core.model.PasswordGeneratorOptions
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpGenerator {
    fun generate(
        secretBase32: String,
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30,
        digits: Int = 6,
    ): String {
        val key = Base32.decode(secretBase32.trim().replace(" ", "").uppercase())
        val counter = timeMillis / 1000L / periodSeconds
        val data = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            data[i] = (value and 0xff).toByte()
            value = value ushr 8
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash.last().toInt() and 0x0f
        val binary =
            ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        val otp = binary % 10.0.pow(digits).toInt()
        return otp.toString().padStart(digits, '0')
    }

    fun nextCode(
        secretBase32: String,
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30,
        digits: Int = 6,
    ): String = generate(secretBase32, timeMillis + periodSeconds * 1000L, periodSeconds, digits)

    fun remainingSeconds(
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30,
    ): Int {
        val elapsed = (timeMillis / 1000L) % periodSeconds
        return (periodSeconds - elapsed).toInt()
    }

    fun progress(
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30,
    ): Float {
        val remaining = remainingSeconds(timeMillis, periodSeconds)
        return remaining / periodSeconds.toFloat()
    }
}

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray {
        val cleaned = input.replace("=", "").uppercase()
        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>((cleaned.length * 5) / 8)
        for (ch in cleaned) {
            val value = ALPHABET.indexOf(ch)
            require(value >= 0) { "Invalid Base32 character: $ch" }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add((buffer shr (bitsLeft - 8) and 0xff).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }
}

object PasswordGenerator {
    private val secureRandom = SecureRandom()

    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}?"
    private const val UPPER_FULL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER_FULL = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS_FULL = "0123456789"

    fun generate(options: PasswordGeneratorOptions): String {
        require(options.length in 4..128) { "Length must be 4..128" }
        val upperPool = if (options.excludeAmbiguous) UPPER else UPPER_FULL
        val lowerPool = if (options.excludeAmbiguous) LOWER else LOWER_FULL
        val digitPool = if (options.excludeAmbiguous) DIGITS else DIGITS_FULL
        val symbolPool = SYMBOLS

        val required = mutableListOf<Char>()
        if (options.uppercase) required += upperPool[secureRandom.nextInt(upperPool.length)]
        if (options.lowercase) required += lowerPool[secureRandom.nextInt(lowerPool.length)]
        if (options.digits) {
            val n = options.minDigits.coerceIn(0, options.length)
            repeat(n) { required += digitPool[secureRandom.nextInt(digitPool.length)] }
        }
        if (options.symbols) {
            val n = options.minSymbols.coerceIn(0, options.length)
            repeat(n) { required += symbolPool[secureRandom.nextInt(symbolPool.length)] }
        }
        while (required.size > options.length) required.removeAt(required.lastIndex)

        val all = buildString {
            if (options.uppercase) append(upperPool)
            if (options.lowercase) append(lowerPool)
            if (options.digits) append(digitPool)
            if (options.symbols) append(symbolPool)
        }
        require(all.isNotEmpty()) { "Select at least one character set" }

        val chars = CharArray(options.length)
        required.forEachIndexed { index, c -> chars[index] = c }
        for (i in required.size until options.length) {
            chars[i] = all[secureRandom.nextInt(all.length)]
        }
        for (i in chars.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }
}

object PasswordStrength {
    fun score(password: String): Int {
        if (password.isEmpty()) return 0
        var s = 0
        s += (password.length * 4).coerceAtMost(40)
        if (password.any { it.isLowerCase() }) s += 10
        if (password.any { it.isUpperCase() }) s += 10
        if (password.any { it.isDigit() }) s += 10
        if (password.any { !it.isLetterOrDigit() }) s += 15
        if (password.length >= 16) s += 15
        if (COMMON_PASSWORDS.contains(password.lowercase())) s = 10
        return s.coerceIn(0, 100)
    }

    fun isWeak(password: String): Boolean = score(password) < 45

    fun scoreLabel(password: String): String = when {
        password.isEmpty() -> "Empty"
        score(password) < 35 -> "Weak"
        score(password) < 55 -> "Fair"
        score(password) < 75 -> "Strong"
        else -> "Very Strong"
    }

    private val COMMON_PASSWORDS = setOf(
        "password", "123456", "123456789", "qwerty", "abc123",
        "password1", "111111", "12345678", "iloveyou", "admin",
    )
}

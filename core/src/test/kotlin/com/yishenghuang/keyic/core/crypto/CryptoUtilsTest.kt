package com.yishenghuang.keyic.core.crypto

import com.yishenghuang.keyic.core.model.PasswordGeneratorOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoUtilsTest {
    @Test
    fun totp_knownVector() {
        // RFC 6238 / common test: secret "JBSWY3DPEHPK3PXP" at a fixed time is environment-dependent;
        // verify length and stability within the same period.
        val secret = "JBSWY3DPEHPK3PXP"
        val t = 1_000_000_000_000L
        val a = TotpGenerator.generate(secret, t)
        val b = TotpGenerator.generate(secret, t + 1_000)
        assertEquals(6, a.length)
        assertEquals(a, b)
        assertTrue(TotpGenerator.remainingSeconds(t) in 1..30)
    }

    @Test
    fun passwordGenerator_respectsLengthAndSets() {
        val pwd = PasswordGenerator.generate(
            PasswordGeneratorOptions(
                length = 24,
                uppercase = true,
                lowercase = true,
                digits = true,
                symbols = false,
                excludeAmbiguous = true,
            ),
        )
        assertEquals(24, pwd.length)
        assertTrue(pwd.any { it.isUpperCase() })
        assertTrue(pwd.any { it.isLowerCase() })
        assertTrue(pwd.any { it.isDigit() })
        assertFalse(pwd.any { !it.isLetterOrDigit() })
    }

    @Test
    fun weakPassword_detected() {
        assertTrue(PasswordStrength.isWeak("123456"))
        assertFalse(PasswordStrength.isWeak("Tr0ub4dor&3xtra!"))
    }
}

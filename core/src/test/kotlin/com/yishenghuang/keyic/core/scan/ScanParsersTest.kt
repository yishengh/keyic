package com.yishenghuang.keyic.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpUriParserTest {
    @Test
    fun parsesIssuerAccountAndSecret() {
        val result = TotpUriParser.parse(
            "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example",
        ).getOrThrow()
        assertEquals("JBSWY3DPEHPK3PXP", result.secret)
        assertEquals("Example", result.issuer)
        assertEquals("alice@example.com", result.account)
    }

    @Test
    fun rejectsMissingSecret() {
        assertTrue(TotpUriParser.parse("otpauth://totp/Example").isFailure)
    }
}

class CardOcrParserTest {
    @Test
    fun parsesNumberExpiryHolder() {
        val text = """
            VISA
            4111 1111 1111 1111
            VALID THRU 12/30
            ALICE EXAMPLE
        """.trimIndent()
        val result = CardOcrParser.parse(text)
        assertEquals("4111111111111111", result.number)
        assertEquals("12/30", result.expiry)
        assertEquals("ALICE EXAMPLE", result.holder)
    }

    @Test
    fun parsesExpiryVariantsAndCvv() {
        val dotted = CardOcrParser.parse(
            """
            4111 1111 1111 1111
            EXP 08.27
            """.trimIndent(),
        )
        assertEquals("08/27", dotted.expiry)

        val labeled = CardOcrParser.parse(
            """
            4111 1111 1111 1111
            有效期至 11/28
            CVV: 123
            """.trimIndent(),
        )
        assertEquals("11/28", labeled.expiry)
        assertEquals("123", labeled.cvv)

        val spaced = CardOcrParser.parse(
            """
            4111 1111 1111 1111
            VALID THRU 1 2 / 3 0
            """.trimIndent(),
        )
        assertEquals("12/30", spaced.expiry)
    }

    @Test
    fun mergeKeepsBestFields() {
        val a = CardOcrResult(number = "4111111111111111")
        val b = CardOcrResult(expiry = "12/30", cvv = "999")
        val merged = a.merge(b)
        assertEquals("4111111111111111", merged.number)
        assertEquals("12/30", merged.expiry)
        assertEquals("999", merged.cvv)
        assertTrue(merged.isCompleteEnough)
    }

    @Test
    fun rejectsInvalidLuhn() {
        assertNull(CardOcrParser.parse("1234 5678 9012 3456").number)
    }
}

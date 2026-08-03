package com.yishenghuang.keyic.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiQrParserTest {
    @Test
    fun parsesStandardWifiQr() {
        val result = WifiQrParser.parse(
            "WIFI:T:WPA;S:MyNetwork;P:secret123;H:false;;",
        ).getOrThrow()
        assertEquals("MyNetwork", result.ssid)
        assertEquals("secret123", result.password)
        assertEquals("WPA", result.security)
        assertFalse(result.hidden)
    }

    @Test
    fun parsesEscapedSpecialChars() {
        val result = WifiQrParser.parse(
            """WIFI:T:WPA;S:Cafe\;Net;P:p\:ass\\word;;""",
        ).getOrThrow()
        assertEquals("Cafe;Net", result.ssid)
        assertEquals("p:ass\\word", result.password)
    }

    @Test
    fun rejectsNonWifi() {
        assertTrue(WifiQrParser.parse("otpauth://totp/x?secret=ABC").isFailure)
    }
}

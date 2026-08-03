package com.yishenghuang.keyic.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdDocumentOcrParserTest {
    @Test
    fun parsesPassportMrz() {
        val text = """
            P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
            L898902C36UTO7408122F1204159ZE184226B<<<<<10
        """.trimIndent()
        val result = IdDocumentOcrParser.parse(text, IdDocumentKind.PASSPORT)
        assertEquals(IdDocumentKind.PASSPORT, result.kind)
        assertEquals("ANNA MARIA ERIKSSON", result.fullName)
        assertEquals("L898902C3", result.documentNumber)
        assertEquals("04/12", result.expiry)
        assertTrue(result.details.orEmpty().contains("DOB: 1974-08-12"))
        assertTrue(result.isCompleteEnough)
    }

    @Test
    fun parsesDriversLicenseLoose() {
        val text = """
            DRIVER LICENSE
            NAME: JANE DOE
            DL NO: D1234567
            EXP 09/28
            DOB 01/15/1990
            CLASS C
        """.trimIndent()
        val result = IdDocumentOcrParser.parse(text, IdDocumentKind.DRIVERS_LICENSE)
        assertEquals(IdDocumentKind.DRIVERS_LICENSE, result.kind)
        assertEquals("JANE DOE", result.fullName)
        assertEquals("D1234567", result.documentNumber)
        assertEquals("09/28", result.expiry)
        assertNotNull(result.details)
    }
}

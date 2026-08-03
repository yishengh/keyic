package com.yishenghuang.keyic.core.csv

import com.yishenghuang.keyic.core.model.VaultEntryDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportParserTest {
    @Test
    fun parsesChromeCsv() {
        val csv = """
            name,url,username,password
            GitHub,https://github.com,octocat,s3cret
        """.trimIndent()
        val drafts = CsvImportParser.parse(csv)
        assertEquals(1, drafts.size)
        assertEquals("GitHub", drafts[0].title)
        assertEquals("octocat", drafts[0].username)
        assertEquals("s3cret", drafts[0].password)
        assertEquals("https://github.com", drafts[0].url)
    }

    @Test
    fun parsesBitwardenStyleTotp() {
        val csv = """
            folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
            Work,,login,Mail,note,,,,user@x.com,pass,JBSWY3DPEHPK3PXP
        """.trimIndent()
        val drafts = CsvImportParser.parse(csv)
        assertEquals(1, drafts.size)
        assertEquals("Mail", drafts[0].title)
        assertEquals("JBSWY3DPEHPK3PXP", drafts[0].totpSecret)
        assertTrue(drafts[0].tags.contains("Work"))
    }

    @Test
    fun parseCsv_handlesQuotes() {
        val rows = CsvImportParser.parseCsv("a,\"b,c\",d\n1,\"2,3\",4\n")
        assertEquals(listOf("a", "b,c", "d"), rows[0])
        assertEquals(listOf("1", "2,3", "4"), rows[1])
    }

    @Test
    fun parsesKeyicTypedExport() {
        val csv = """
            name,type,url,username,password,totp,notes,folder,card_expiry,card_cvv,icon,favorite
            Visa,CARD,,Alice Holder,4111111111111111,,Demo card,finance,12/28,123,visa,true
            Passport,IDENTITY,,Jane Doe,,,DOB 1990-01-01,personal,,,
            Wifi,NOTE,,,,"SSID Home / pass secret",home,,,
        """.trimIndent()
        val drafts = CsvImportParser.parse(csv)
        assertEquals(3, drafts.size)
        assertEquals(com.yishenghuang.keyic.core.model.EntryType.CARD, drafts[0].type)
        assertEquals("12/28", drafts[0].cardExpiry)
        assertEquals("123", drafts[0].cardCvv)
        assertTrue(drafts[0].favorite)
        assertEquals(com.yishenghuang.keyic.core.model.EntryType.IDENTITY, drafts[1].type)
        assertEquals(com.yishenghuang.keyic.core.model.EntryType.NOTE, drafts[2].type)
    }
}

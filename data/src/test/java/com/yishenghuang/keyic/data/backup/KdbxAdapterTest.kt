package com.yishenghuang.keyic.data.backup

import com.yishenghuang.keyic.core.model.CustomField
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.core.port.KdbxBinary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KdbxAdapterTest {
    @Test fun keyicSpecificFieldsSurviveKeePassRoundTrip() {
        val source = VaultEntry(id = "synthetic", title = "Identity", type = com.yishenghuang.keyic.core.model.EntryType.IDENTITY,
            username = "synthetic", password = UUID.randomUUID().toString(), notes = "Notes kept exactly",
            packageHints = listOf("com.example.synthetic"), tags = listOf("one", "two"), favorite = true,
            cardExpiry = "12/30", cardCvv = "000", totpSecret = "JBSWY3DPEHPK3PXP", iconKey = "google",
            customFields = listOf(CustomField("f1", "Duplicate", "one", true), CustomField("f2", "Duplicate", "two", false)),
            createdAt = 1, updatedAt = 2, passwordChangedAt = 3)
        val pass = UUID.randomUUID().toString()
        val bytes = KdbxAdapter.exportKdbx(listOf(source), pass.toCharArray())
        val restored = KdbxAdapter.importKdbx(bytes, pass.toCharArray()).entries.single()
        assertTrue(source == restored.copy(id = source.id))
    }

    @Test
    fun roundTripCustomFieldsAndBinary() = runBlocking {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entry = VaultEntry(
            id = id,
            title = "Bank",
            username = "alice",
            password = "s3cret",
            url = "https://bank.example",
            notes = "personal",
            customFields = listOf(
                CustomField(id = "f1", name = "PIN", value = "1234", masked = true),
                CustomField(id = "f2", name = "Branch", value = "Downtown", masked = false),
            ),
            createdAt = now,
            updatedAt = now,
            passwordChangedAt = now,
        )
        val binaryPayload = "hello-attachment".toByteArray()
        val exported = KdbxAdapter.exportKdbx(
            entries = listOf(entry),
            passphrase = "test-pass".toCharArray(),
            binariesByEntryId = mapOf(id to listOf(KdbxBinary("note.txt", binaryPayload))),
        )
        val imported = KdbxAdapter.importKdbx(exported, "test-pass".toCharArray())
        assertEquals(1, imported.entries.size)
        val got = imported.entries.single()
        assertEquals("Bank", got.title)
        assertEquals("alice", got.username)
        assertEquals("s3cret", got.password)
        assertTrue(got.customFields.any { it.name == "PIN" && it.value == "1234" })
        assertTrue(got.customFields.any { it.name == "Branch" && it.value == "Downtown" })
        assertEquals(1, imported.attachments.size)
        assertEquals("note.txt", imported.attachments.single().fileName)
        assertTrue(imported.attachments.single().data.contentEquals(binaryPayload))
    }

    @Test
    fun importFixtureFromResources() {
        val stream = javaClass.classLoader!!.getResourceAsStream("keyic-custom-fields.kdbx")
            ?: error("Missing checked-in fixture")
        val bytes = stream.readBytes()
        val imported = KdbxAdapter.importKdbx(bytes, "test-pass".toCharArray())
        assertTrue(imported.entries.isNotEmpty())
        assertTrue(imported.entries.any { it.customFields.isNotEmpty() })
    }
}

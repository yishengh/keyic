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
        // Persist fixture for manual inspection / future runs
        val fixtureDir = java.io.File("src/test/resources")
        fixtureDir.mkdirs()
        java.io.File(fixtureDir, "keyic-custom-fields.kdbx").writeBytes(exported)

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
        val stream = javaClass.classLoader.getResourceAsStream("keyic-custom-fields.kdbx")
            ?: return // first run generates fixture via roundTrip test
        val bytes = stream.readBytes()
        val imported = KdbxAdapter.importKdbx(bytes, "test-pass".toCharArray())
        assertTrue(imported.entries.isNotEmpty())
        assertTrue(imported.entries.any { it.customFields.isNotEmpty() })
    }
}

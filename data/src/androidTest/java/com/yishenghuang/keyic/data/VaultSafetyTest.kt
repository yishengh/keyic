package com.yishenghuang.keyic.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yishenghuang.keyic.core.model.*
import com.yishenghuang.keyic.core.port.*
import com.yishenghuang.keyic.data.backup.EncryptedJsonBackupPort
import com.yishenghuang.keyic.data.crypto.VaultKeyManager
import com.yishenghuang.keyic.data.db.VaultDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Runs only in the test APK sandbox. All vaults and secrets are synthetic. */
@RunWith(AndroidJUnit4::class)
class VaultSafetyTest {
    private lateinit var container: AppContainer
    private lateinit var context: Context
    private lateinit var password: String

    @Before fun prepare() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        container = AppContainer(context)
        password = UUID.randomUUID().toString()
        assertTrue(container.session.createAdditionalVault("Synthetic verification", password.toCharArray()))
    }

    @After fun close() = runBlocking { container.session.lock() }

    private suspend fun sample(): VaultEntry = container.vaultRepository.create(VaultEntryDraft(
        title = "Synthetic entry 中文", username = "test@example.invalid", password = UUID.randomUUID().toString(),
        url = "https://example.invalid", notes = "Synthetic note", tags = listOf("test"),
        customFields = listOf(CustomField("field", "PIN", "synthetic", true)),
    ))

    @Test fun editAndFavoritePreserveAttachment() = runBlocking {
        val entry = sample()
        val bytes = "synthetic attachment".toByteArray()
        val attachment = container.attachmentRepository.add(entry.id, "note.txt", "text/plain", bytes.copyOf())
        container.vaultRepository.upsert(entry.copy(title = "Edited", favorite = true))
        assertEquals(1, container.attachmentRepository.listForEntry(entry.id).size)
        assertTrue(bytes.contentEquals(container.attachmentRepository.readDecrypted(attachment.id)))
        container.vaultRepository.delete(entry.id)
        container.vaultRepository.restore(entry.id)
        assertEquals(1, container.attachmentRepository.listForEntry(entry.id).size)
    }

    @Test fun encryptedBackupRestoresEveryFieldAndAttachmentAfterReopen() = runBlocking {
        val entry = sample()
        for (type in listOf(EntryType.CARD, EntryType.NOTE, EntryType.IDENTITY)) {
            container.vaultRepository.upsert(entry.copy(id = UUID.randomUUID().toString(), type = type,
                cardExpiry = "12/30", cardCvv = "000", totpSecret = "JBSWY3DPEHPK3PXP"))
        }
        val original = container.vaultRepository.entries.first().sortedBy { it.id }
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        container.attachmentRepository.add(entry.id, "sample.bin", "application/octet-stream", bytes.copyOf())
        val backupPassword = UUID.randomUUID().toString()
        val backup = container.exportPortable(backupPassword.toCharArray())
        assertFalse(backup.toString(Charsets.ISO_8859_1).contains(entry.username))
        container.vaultRepository.create(VaultEntryDraft("Not in backup"))
        assertEquals(4, container.importPortable(backup, backupPassword.toCharArray()))
        container.session.lock()
        assertTrue(container.session.unlock(password.toCharArray()))
        assertTrue(original == container.vaultRepository.entries.first().sortedBy { it.id })
        val restored = container.attachmentRepository.listForEntry(entry.id).single()
        assertTrue(bytes.contentEquals(container.attachmentRepository.readDecrypted(restored.id)))
    }

    @Test fun incorrectPasswordAndTamperingDoNotModifyVault() = runBlocking {
        val entry = sample()
        val backupPassword = UUID.randomUUID().toString()
        val backup = container.exportPortable(backupPassword.toCharArray())
        assertTrue(runCatching { container.importPortable(backup, UUID.randomUUID().toString().toCharArray()) }.isFailure)
        backup[backup.lastIndex] = (backup.last().toInt() xor 1).toByte()
        assertTrue(runCatching { container.importPortable(backup, backupPassword.toCharArray()) }.isFailure)
        assertTrue(container.vaultRepository.getById(entry.id) == entry)
        container.session.lock()
        assertFalse(container.session.unlock(UUID.randomUUID().toString().toCharArray()))
        assertTrue(container.session.unlock(password.toCharArray()))
        assertTrue(container.vaultRepository.getById(entry.id) == entry)
    }

    @Test fun failedSqlTransactionRollsBackEntriesAndStagedAttachments() = runBlocking {
        val entry = sample()
        val attachment = container.attachmentRepository.add(entry.id, "original", "text/plain", byteArrayOf(1, 2, 3))
        val db = VaultDatabaseFactory.requireOpen()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON attachments BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        val replacement = entry.copy(id = UUID.randomUUID().toString(), title = "Replacement")
        val result = EncryptedJsonImportResult(listOf(replacement), listOf(JsonBackupAttachment(
            replacement.id, "test", "new.bin", "application/octet-stream", 42, byteArrayOf(4, 5, 6))))
        val id = container.session.activeVaultId.first()!!
        assertTrue(runCatching { container.attachmentRepository.importAtomically(result, true, id) }.isFailure)
        assertTrue(container.vaultRepository.getById(entry.id) == entry)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(container.attachmentRepository.readDecrypted(attachment.id)))
        assertNull(container.vaultRepository.getById(replacement.id))
    }

    @Test fun missingAttachmentFailsBackup() = runBlocking {
        val entry = sample()
        val att = container.attachmentRepository.add(entry.id, "lost.bin", "application/octet-stream", byteArrayOf(1))
        val id = container.session.activeVaultId.first()!!
        java.io.File(context.filesDir, "vaults/$id/att/${att.id}.bin").delete()
        assertTrue(runCatching { container.exportPortable(UUID.randomUUID().toString().toCharArray()) }.isFailure)
        assertNotNull(container.vaultRepository.getById(entry.id))
    }

    @Test fun wrongDatabaseKeyNeverDeletesDatabase() = runBlocking {
        val entry = sample()
        val id = container.session.activeVaultId.first()!!
        val correctKey = container.session.peekDbKey()!!
        container.session.lock()
        val file = context.getDatabasePath(VaultDatabaseFactory.dbName(id))
        val original = file.readBytes()
        assertTrue(runCatching { container.session.unlockWithBiometricKey(ByteArray(32) { 7 }) }.isFailure)
        assertFalse(container.session.isUnlocked.first())
        assertTrue(original.contentEquals(file.readBytes()))
        assertTrue(container.session.unlockWithBiometricKey(correctKey))
        correctKey.fill(0)
        assertTrue(container.vaultRepository.getById(entry.id) == entry)
    }

    @Test fun passwordChangeKeepsDataAndRejectsOldPassword() = runBlocking {
        val entry = sample()
        val nextPassword = UUID.randomUUID().toString()
        val key = container.session.peekDbKey()!!
        assertTrue(container.keyManager.changeMasterPassword(key, nextPassword.toCharArray()))
        key.fill(0)
        container.session.lock()
        assertFalse(container.session.unlock(password.toCharArray()))
        assertTrue(container.session.unlock(nextPassword.toCharArray()))
        assertTrue(container.vaultRepository.getById(entry.id) == entry)
    }

    @Test fun switchingVaultRejectsStaleRestoreAndInactiveVaultDeletion() = runBlocking {
        val entry = sample()
        val firstId = container.session.activeVaultId.first()!!
        container.session.createAdditionalVault("Second synthetic vault", UUID.randomUUID().toString().toCharArray())
        assertTrue(runCatching { container.attachmentRepository.importAtomically(EncryptedJsonImportResult(listOf(entry)), true, firstId) }.isFailure)
        assertTrue(container.vaultRepository.entries.first().isEmpty())
        assertFalse(container.session.deleteVault(firstId))
        assertTrue(container.session.deleteVault(container.session.activeVaultId.first()!!))
        assertFalse(container.session.isUnlocked.first())
        assertNull(container.session.peekDbKey())
    }

    @Test fun keepassImportAddsEntriesAndAttachments() = runBlocking {
        val original = sample()
        val bytes = byteArrayOf(9, 8, 7)
        container.attachmentRepository.add(original.id, "sample.bin", "application/octet-stream", bytes.copyOf())
        val pass = UUID.randomUUID().toString()
        val backup = container.exportPortable(pass.toCharArray(), true)
        assertEquals(1, container.importPortable(backup, pass.toCharArray(), true))
        val entries = container.vaultRepository.entries.first()
        assertEquals(2, entries.size)
        val added = entries.single { it.id != original.id }
        assertTrue(added.password == original.password)
        val attachment = container.attachmentRepository.listForEntry(added.id).single()
        assertTrue(bytes.contentEquals(container.attachmentRepository.readDecrypted(attachment.id)))
    }
}

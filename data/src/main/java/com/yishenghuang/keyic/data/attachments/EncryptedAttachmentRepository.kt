package com.yishenghuang.keyic.data.attachments

import android.content.Context
import com.yishenghuang.keyic.core.model.AttachmentMeta
import com.yishenghuang.keyic.core.port.AttachmentRepository
import com.yishenghuang.keyic.data.db.AttachmentEntity
import com.yishenghuang.keyic.data.db.VaultDatabaseFactory
import com.yishenghuang.keyic.data.db.toDomain
import com.yishenghuang.keyic.data.session.VaultSessionImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedAttachmentRepository(
    private val context: Context,
    private val session: VaultSessionImpl,
) : AttachmentRepository {
    companion object {
        const val MAX_FILE_BYTES = 5L * 1024 * 1024
        const val MAX_ENTRY_BYTES = 20L * 1024 * 1024
    }

    override fun observeForEntry(entryId: String): Flow<List<AttachmentMeta>> =
        session.isUnlocked.flatMapLatest { unlocked ->
            if (!unlocked || !VaultDatabaseFactory.isOpen()) {
                flowOf(emptyList())
            } else {
                VaultDatabaseFactory.requireOpen().attachmentDao().observeForEntry(entryId)
                    .map { list -> list.map { it.toDomain() } }
            }
        }

    override suspend fun listForEntry(entryId: String): List<AttachmentMeta> {
        if (!VaultDatabaseFactory.isOpen()) return emptyList()
        return VaultDatabaseFactory.requireOpen().attachmentDao().listForEntry(entryId).map { it.toDomain() }
    }

    override suspend fun add(
        entryId: String,
        fileName: String,
        mimeType: String,
        plainBytes: ByteArray,
    ): AttachmentMeta {
        require(plainBytes.size <= MAX_FILE_BYTES) { "File exceeds 5 MB limit" }
        val dao = VaultDatabaseFactory.requireOpen().attachmentDao()
        val existing = dao.totalSizeForEntry(entryId)
        require(existing + plainBytes.size <= MAX_ENTRY_BYTES) { "Attachments for this entry exceed 20 MB" }
        val dbKey = session.peekDbKey() ?: error("Vault is locked")
        val id = UUID.randomUUID().toString()
        val vaultId = VaultDatabaseFactory.currentVaultId() ?: "default"
        try {
            val encrypted = encrypt(dbKey, id, plainBytes)
            fileFor(vaultId, id).apply {
                parentFile?.mkdirs()
                writeBytes(encrypted)
            }
            val meta = AttachmentEntity(
                id = id,
                entryId = entryId,
                fileName = fileName,
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                sizeBytes = plainBytes.size.toLong(),
                createdAt = System.currentTimeMillis(),
            )
            dao.upsert(meta)
            return meta.toDomain()
        } finally {
            dbKey.fill(0)
            plainBytes.fill(0)
        }
    }

    override suspend fun readDecrypted(attachmentId: String): ByteArray? {
        val dbKey = session.peekDbKey() ?: return null
        val vaultId = VaultDatabaseFactory.currentVaultId() ?: return null
        return try {
            val entity = VaultDatabaseFactory.requireOpen().attachmentDao().getById(attachmentId) ?: return null
            val file = fileFor(vaultId, entity.id)
            if (!file.exists()) return null
            decrypt(dbKey, entity.id, file.readBytes())
        } finally {
            dbKey.fill(0)
        }
    }

    override suspend fun delete(attachmentId: String) {
        val vaultId = VaultDatabaseFactory.currentVaultId() ?: return
        VaultDatabaseFactory.requireOpen().attachmentDao().delete(attachmentId)
        fileFor(vaultId, attachmentId).delete()
    }

    override suspend fun deleteAllForEntry(entryId: String) {
        val vaultId = VaultDatabaseFactory.currentVaultId() ?: return
        val dao = VaultDatabaseFactory.requireOpen().attachmentDao()
        val list = dao.listForEntry(entryId)
        dao.deleteForEntry(entryId)
        list.forEach { fileFor(vaultId, it.id).delete() }
    }

    private fun fileFor(vaultId: String, attachmentId: String): File {
        val dir = File(context.filesDir, "vaults/$vaultId/att")
        return File(dir, "$attachmentId.bin")
    }

    private fun deriveKey(dbKey: ByteArray, attachmentId: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(dbKey)
        md.update(attachmentId.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    private fun encrypt(dbKey: ByteArray, attachmentId: String, plain: ByteArray): ByteArray {
        val key = deriveKey(dbKey, attachmentId)
        return try {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            iv + cipher.doFinal(plain)
        } finally {
            key.fill(0)
        }
    }

    private fun decrypt(dbKey: ByteArray, attachmentId: String, blob: ByteArray): ByteArray {
        require(blob.size > 12) { "Corrupt attachment" }
        val key = deriveKey(dbKey, attachmentId)
        return try {
            val iv = blob.copyOfRange(0, 12)
            val ct = blob.copyOfRange(12, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(ct)
        } finally {
            key.fill(0)
        }
    }
}

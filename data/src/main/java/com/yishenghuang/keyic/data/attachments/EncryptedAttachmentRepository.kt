package com.yishenghuang.keyic.data.attachments

import android.content.Context
import androidx.room.withTransaction
import com.yishenghuang.keyic.core.port.EncryptedJsonImportResult
import com.yishenghuang.keyic.core.port.JsonBackupAttachment
import com.yishenghuang.keyic.data.db.toEntity
import com.yishenghuang.keyic.core.model.AttachmentMeta
import com.yishenghuang.keyic.core.port.AttachmentRepository
import com.yishenghuang.keyic.data.db.AttachmentEntity
import com.yishenghuang.keyic.data.db.VaultDatabaseFactory
import com.yishenghuang.keyic.data.db.toDomain
import com.yishenghuang.keyic.data.session.VaultSessionImpl
import kotlinx.coroutines.ensureActive
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

    /** Files are staged under fresh IDs before the single SQL transaction commits references. */
    suspend fun importAtomically(result: EncryptedJsonImportResult, replace: Boolean, expectedVaultId: String) {
        try {
            val ids = result.entries.map { it.id }.toSet()
            require(ids.size == result.entries.size && ids.none { it.isBlank() }) { "Invalid entry IDs" }
            require(result.attachments.all { it.entryId in ids && it.data.size <= MAX_FILE_BYTES }) {
                "Invalid attachment"
            }
            require(result.attachments.groupBy { it.entryId }.values.all { group ->
                group.sumOf { it.data.size.toLong() } <= MAX_ENTRY_BYTES
            }) { "Attachment limit exceeded" }
            session.withUnlockedVault(expectedVaultId) { db, key, vaultId ->
                val staged = mutableListOf<File>()
                var committed = false
                try {
                    val remapped = result.entries.associate { it.id to if (replace) it.id else UUID.randomUUID().toString() }
                    val metas = result.attachments.map { att ->
                        val id = UUID.randomUUID().toString()
                        val file = fileFor(vaultId, id)
                        staged += file
                        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                        java.io.FileOutputStream(file).use { output ->
                            output.write(encrypt(key, id, att.data))
                            output.fd.sync()
                        }
                        AttachmentEntity(id, remapped.getValue(att.entryId), att.fileName, att.mimeType,
                            att.data.size.toLong(), att.createdAt)
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    // Once commit starts, cancellation cannot delete referenced files.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        db.withTransaction {
                            if (replace) db.vaultDao().deleteAll()
                            db.vaultDao().upsertAll(result.entries.map { it.copy(id = remapped.getValue(it.id)).toEntity() })
                            metas.forEach { db.attachmentDao().upsert(it) }
                        }
                        committed = true
                    }
                } finally {
                    if (!committed) staged.forEach { it.delete() }
                }
            }
        } finally {
            result.attachments.forEach { it.data.fill(0) }
        }
    }

    suspend fun snapshot(expectedVaultId: String? = null): EncryptedJsonImportResult = session.withUnlockedVault(expectedVaultId) { db, key, vaultId ->
        db.withTransaction {
            val attachments = mutableListOf<JsonBackupAttachment>()
            try {
                val entries = db.vaultDao().listActive().map { it.toDomain() }
                for (entry in entries) {
                    for (meta in db.attachmentDao().listForEntry(entry.id)) {
                        // A missing/corrupt file is a failed backup, never a successful partial one.
                        val plain = decrypt(key, meta.id, fileFor(vaultId, meta.id).readBytes())
                        attachments += JsonBackupAttachment(entry.id, meta.id, meta.fileName, meta.mimeType, meta.createdAt, plain)
                    }
                }
                EncryptedJsonImportResult(entries, attachments)
            } catch (failure: Exception) {
                attachments.forEach { it.data.fill(0) }
                throw failure
            }
        }
    }

    override fun observeForEntry(entryId: String): Flow<List<AttachmentMeta>> =
        session.databaseGeneration.flatMapLatest {
            if (!VaultDatabaseFactory.isOpen()) {
                flowOf(emptyList())
            } else {
                VaultDatabaseFactory.requireOpen().attachmentDao().observeForEntry(entryId)
                    .map { list -> list.map { it.toDomain() } }
            }
        }

    override suspend fun listForEntry(entryId: String): List<AttachmentMeta> =
        session.withUnlockedVault { db, _, _ -> db.attachmentDao().listForEntry(entryId).map { it.toDomain() } }

    override suspend fun add(entryId: String, fileName: String, mimeType: String, plainBytes: ByteArray): AttachmentMeta =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                require(plainBytes.size <= MAX_FILE_BYTES) { "File exceeds 5 MB limit" }
                session.withUnlockedVault { db, key, vaultId ->
                    val id = UUID.randomUUID().toString()
                    val file = fileFor(vaultId, id)
                    var committed = false
                    try {
                        var meta: AttachmentEntity? = null
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            db.withTransaction {
                                require(db.attachmentDao().totalSizeForEntry(entryId) + plainBytes.size <= MAX_ENTRY_BYTES) {
                                    "Attachments for this entry exceed 20 MB"
                                }
                                check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                                java.io.FileOutputStream(file).use { output ->
                                    output.write(encrypt(key, id, plainBytes))
                                    output.fd.sync()
                                }
                                meta = AttachmentEntity(id, entryId, fileName, mimeType.ifBlank { "application/octet-stream" },
                                    plainBytes.size.toLong(), System.currentTimeMillis())
                                db.attachmentDao().upsert(meta!!)
                            }
                            committed = true
                        }
                        meta!!.toDomain()
                    } finally { if (!committed) file.delete() }
                }
            } finally { plainBytes.fill(0) }
        }

    override suspend fun readDecrypted(attachmentId: String): ByteArray? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            session.withUnlockedVault { db, key, vaultId ->
                val entity = db.attachmentDao().getById(attachmentId) ?: return@withUnlockedVault null
                val file = fileFor(vaultId, entity.id)
                if (!file.exists()) return@withUnlockedVault null
                decrypt(key, entity.id, file.readBytes())
            }
        }

    override suspend fun delete(attachmentId: String) {
        session.withUnlockedVault { db, _, vaultId ->
            db.attachmentDao().delete(attachmentId)
            fileFor(vaultId, attachmentId).delete()
        }
    }

    override suspend fun deleteAllForEntry(entryId: String) {
        session.withUnlockedVault { db, _, vaultId ->
            val list = db.attachmentDao().listForEntry(entryId)
            db.attachmentDao().deleteForEntry(entryId)
            list.forEach { fileFor(vaultId, it.id).delete() }
        }
    }

    private fun fileFor(vaultId: String, attachmentId: String): File {
        require(vaultId.matches(Regex("[A-Za-z0-9_-]+")) && attachmentId.matches(Regex("[A-Za-z0-9_-]+")))
        return File(context.filesDir, "vaults/$vaultId/att/$attachmentId.bin")
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

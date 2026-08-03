package com.yishenghuang.keyic.core.port

import com.yishenghuang.keyic.core.model.AttachmentMeta
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.core.model.VaultEntryDraft
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    /** Active (non-deleted) entries. */
    val entries: Flow<List<VaultEntry>>

    /** Soft-deleted entries. */
    val deletedEntries: Flow<List<VaultEntry>>

    suspend fun getById(id: String): VaultEntry?
    suspend fun upsert(entry: VaultEntry)
    suspend fun create(draft: VaultEntryDraft): VaultEntry
    /** Soft-delete (moves to recycle bin). */
    suspend fun delete(id: String)
    suspend fun restore(id: String)
    suspend fun purge(id: String)
    suspend fun purgeExpired(beforeEpochMs: Long): Int
    suspend fun search(query: String): List<VaultEntry>
}

interface AttachmentRepository {
    fun observeForEntry(entryId: String): Flow<List<AttachmentMeta>>
    suspend fun listForEntry(entryId: String): List<AttachmentMeta>
    suspend fun add(
        entryId: String,
        fileName: String,
        mimeType: String,
        plainBytes: ByteArray,
    ): AttachmentMeta
    suspend fun readDecrypted(attachmentId: String): ByteArray?
    suspend fun delete(attachmentId: String)
    suspend fun deleteAllForEntry(entryId: String)
}

interface VaultSession {
    val isUnlocked: Flow<Boolean>
    val isVaultConfigured: Flow<Boolean>
    val activeVaultId: Flow<String?>

    suspend fun isConfigured(): Boolean
    suspend fun setup(masterPassword: CharArray, vaultName: String = "Vault 1")
    suspend fun createAdditionalVault(name: String, masterPassword: CharArray): Boolean
    suspend fun switchVault(vaultId: String)
    suspend fun renameVault(vaultId: String, name: String)
    suspend fun deleteVault(vaultId: String): Boolean
    suspend fun unlock(masterPassword: CharArray): Boolean
    suspend fun unlockWithBiometricKey(dbKey: ByteArray): Boolean
    suspend fun lock()
    fun peekDbKey(): ByteArray?
    fun touch()
    fun shouldAutoLock(nowMillis: Long, autoLockSeconds: Int): Boolean
}

interface SettingsRepository {
    val settings: Flow<com.yishenghuang.keyic.core.model.AppSettings>
    suspend fun get(): com.yishenghuang.keyic.core.model.AppSettings
    suspend fun update(transform: (com.yishenghuang.keyic.core.model.AppSettings) -> com.yishenghuang.keyic.core.model.AppSettings)
}

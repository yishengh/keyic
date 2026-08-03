package com.yishenghuang.keyic.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.yishenghuang.keyic.core.port.AttachmentRepository
import com.yishenghuang.keyic.core.port.AutofillMatcher
import com.yishenghuang.keyic.core.port.DefaultAutofillMatcher
import com.yishenghuang.keyic.core.port.ImportExportPort
import com.yishenghuang.keyic.core.port.SettingsRepository
import com.yishenghuang.keyic.core.port.VaultRegistry
import com.yishenghuang.keyic.core.port.VaultRepository
import com.yishenghuang.keyic.core.port.VaultSession
import com.yishenghuang.keyic.data.attachments.EncryptedAttachmentRepository
import com.yishenghuang.keyic.data.backup.EncryptedJsonBackupPort
import com.yishenghuang.keyic.data.backup.SafBackupManager
import com.yishenghuang.keyic.data.crypto.VaultKeyManager
import com.yishenghuang.keyic.data.repo.SqlCipherVaultRepository
import com.yishenghuang.keyic.data.session.VaultSessionImpl
import com.yishenghuang.keyic.data.settings.DataStoreSettingsRepository
import com.yishenghuang.keyic.data.settings.DataStoreVaultRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val vaultRegistry: VaultRegistry = DataStoreVaultRegistry(appContext)
    val session: VaultSessionImpl = VaultSessionImpl(appContext, vaultRegistry)
    val vaultSession: VaultSession = session
    val vaultRepository: VaultRepository = SqlCipherVaultRepository(session.isUnlocked)
    val sqlVaultRepository: SqlCipherVaultRepository =
        vaultRepository as SqlCipherVaultRepository
    val attachmentRepository: AttachmentRepository =
        EncryptedAttachmentRepository(appContext, session)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext)
    val importExportPort: ImportExportPort = EncryptedJsonBackupPort()
    val safBackupManager = SafBackupManager(appContext, importExportPort)
    val autofillMatcher: AutofillMatcher = DefaultAutofillMatcher()

    val keyManager: VaultKeyManager
        get() = session.currentKeyManager

    init {
        runBlocking { session.bootstrap() }
    }

    fun isSafFolderAvailable(): Boolean {
        val uri = runBlocking { settingsRepository.get().safTreeUri } ?: return false
        return runCatching {
            DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.canWrite() == true
        }.getOrDefault(false)
    }

    /** Auto-backup when enabled (also records health). */
    fun requestSafBackup() {
        scope.launch {
            val settings = settingsRepository.get()
            if (!settings.safAutoBackupEnabled) return@launch
            performSafBackup(recordEvenIfDisabled = false)
        }
    }

    /**
     * Used by [com.yishenghuang.keyic.backup.SafBackupWorker].
     * Locked vault → retry later; hard config errors → failure; write ok → success.
     */
    suspend fun runScheduledSafBackup(): ScheduledBackupResult {
        val settings = settingsRepository.get()
        if (!settings.safAutoBackupEnabled) return ScheduledBackupResult.Skipped
        if (settings.safTreeUri == null) return ScheduledBackupResult.PermanentFailure
        if (!safBackupManager.hasPassphrase()) {
            return ScheduledBackupResult.PermanentFailure
        }
        if (!session.isUnlocked.first()) {
            recordBackupHealth(false, "Vault locked — will retry")
            return ScheduledBackupResult.RetryLater
        }
        val (ok, err) = performSafBackup(recordEvenIfDisabled = false)
        return if (ok) {
            ScheduledBackupResult.Success
        } else when (err) {
            "Vault locked" -> ScheduledBackupResult.RetryLater
            "Folder missing or unwritable", "Backup write failed" ->
                ScheduledBackupResult.RetryLater
            else -> ScheduledBackupResult.PermanentFailure
        }
    }

    /** Manual / test backup — writes even if auto-backup is off (folder + passphrase required). */
    fun testSafBackup(onResult: ((Boolean, String?) -> Unit)? = null) {
        scope.launch {
            val (ok, err) = performSafBackup(recordEvenIfDisabled = true)
            onResult?.invoke(ok, err)
        }
    }

    suspend fun collectBackupAttachments(
        entries: List<com.yishenghuang.keyic.core.model.VaultEntry>,
    ): List<com.yishenghuang.keyic.core.port.JsonBackupAttachment> {
        val out = ArrayList<com.yishenghuang.keyic.core.port.JsonBackupAttachment>()
        for (entry in entries.filter { it.deletedAt == null }) {
            val metas = attachmentRepository.listForEntry(entry.id)
            for (meta in metas) {
                val bytes = attachmentRepository.readDecrypted(meta.id) ?: continue
                out += com.yishenghuang.keyic.core.port.JsonBackupAttachment(
                    entryId = entry.id,
                    id = meta.id,
                    fileName = meta.fileName,
                    mimeType = meta.mimeType,
                    createdAt = meta.createdAt,
                    data = bytes,
                )
            }
        }
        return out
    }

    private suspend fun performSafBackup(recordEvenIfDisabled: Boolean): Pair<Boolean, String?> {
        val settings = settingsRepository.get()
        val uri = settings.safTreeUri
        if (uri == null) {
            val err = "No sync folder linked"
            if (recordEvenIfDisabled) recordBackupHealth(false, err)
            return false to err
        }
        if (!session.isUnlocked.first()) {
            val err = "Vault locked"
            if (recordEvenIfDisabled) recordBackupHealth(false, err)
            return false to err
        }
        if (!safBackupManager.hasPassphrase()) {
            val err = "Sync passphrase not set"
            if (recordEvenIfDisabled) recordBackupHealth(false, err)
            return false to err
        }
        val treeOk = runCatching {
            DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.canWrite() == true
        }.getOrDefault(false)
        if (!treeOk) {
            val err = "Folder missing or unwritable"
            recordBackupHealth(false, err)
            return false to err
        }
        val entries = vaultRepository.entries.first()
        val vaultId = session.activeVaultId.first() ?: "default"
        val attachments = collectBackupAttachments(entries)
        val ok = runCatching {
            safBackupManager.writeBackup(uri, entries, vaultId, attachments)
        }.getOrDefault(false)
        val err = if (ok) null else "Backup write failed"
        recordBackupHealth(ok, err)
        return ok to err
    }

    private suspend fun recordBackupHealth(ok: Boolean, error: String?) {
        settingsRepository.update {
            it.copy(
                lastSafBackupAt = System.currentTimeMillis(),
                lastSafBackupOk = ok,
                lastSafBackupError = error,
            )
        }
    }

    fun purgeRecycleBinIfNeeded() {
        scope.launch {
            if (!session.isUnlocked.first()) return@launch
            val days = settingsRepository.get().recycleBinRetentionDays
            if (days <= 0) return@launch
            val before = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
            val deleted = vaultRepository.deletedEntries.first()
            deleted.filter { (it.deletedAt ?: 0L) < before }.forEach { entry ->
                attachmentRepository.deleteAllForEntry(entry.id)
                vaultRepository.purge(entry.id)
            }
        }
    }
}

enum class ScheduledBackupResult {
    Success,
    Skipped,
    RetryLater,
    PermanentFailure,
}

package com.yishenghuang.keyic.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.core.model.VaultEntryDraft
import com.yishenghuang.keyic.core.usecase.HealthAnalyzer
import com.yishenghuang.keyic.data.AppContainer
import com.yishenghuang.keyic.ui.util.UiMessage
import com.yishenghuang.keyic.ui.util.UserError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultUiState(
    val entries: List<VaultEntry> = emptyList(),
    val deletedEntries: List<VaultEntry> = emptyList(),
    val query: String = "",
    val filtered: List<VaultEntry> = emptyList(),
)

class VaultViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    val uiState: StateFlow<VaultUiState> = combine(
        container.vaultRepository.entries,
        container.vaultRepository.deletedEntries,
        query,
    ) { entries, deleted, q ->
        val filtered = if (q.isBlank()) {
            entries
        } else {
            val needle = q.trim().lowercase()
            entries.filter {
                it.title.lowercase().contains(needle) ||
                    it.username.lowercase().contains(needle) ||
                    it.url.lowercase().contains(needle) ||
                    it.tags.any { tag -> tag.lowercase().contains(needle) } ||
                    it.notes.lowercase().contains(needle) ||
                    it.customFields.any { f ->
                        f.name.lowercase().contains(needle) ||
                            f.value.lowercase().contains(needle)
                    }
            }
        }
        VaultUiState(
            entries = entries,
            deletedEntries = deleted,
            query = q,
            filtered = filtered,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    val healthReport = container.vaultRepository.entries
        .map { HealthAnalyzer.analyze(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HealthAnalyzer.analyze(emptyList()),
        )

    fun consumeMessage() {
        _message.value = null
    }

    fun setQuery(value: String) {
        query.value = value
        container.vaultSession.touch()
    }

    fun entry(id: String): StateFlow<VaultEntry?> {
        val flow = MutableStateFlow<VaultEntry?>(null)
        viewModelScope.launch {
            flow.value = container.vaultRepository.getById(id)
        }
        viewModelScope.launch {
            container.vaultRepository.entries.collect { list ->
                flow.value = list.find { it.id == id }
            }
        }
        return flow.asStateFlow()
    }

    fun observeAttachments(entryId: String) =
        container.attachmentRepository.observeForEntry(entryId)

    suspend fun getEntry(id: String): VaultEntry? = container.vaultRepository.getById(id)

    private var saving = false

    fun save(draft: VaultEntryDraft, existingId: String?, previousPassword: String?, onDone: () -> Unit = {}) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val unlocked = container.session.isUnlocked.first()
                if (!unlocked) {
                    _message.value = UserError.vaultLocked(container.appContext)
                    return@launch
                }
                val now = System.currentTimeMillis()
                if (existingId == null) {
                    container.vaultRepository.create(draft)
                } else {
                    val existing = container.vaultRepository.getById(existingId) ?: return@launch
                    val passwordChanged = previousPassword != draft.password
                    container.vaultRepository.upsert(
                        existing.copy(
                            title = draft.title.trim().ifEmpty { "Untitled" },
                            type = draft.type,
                            username = draft.username,
                            password = draft.password,
                            url = draft.url,
                            packageHints = draft.packageHints,
                            totpSecret = draft.totpSecret?.ifBlank { null },
                            notes = draft.notes,
                            tags = draft.tags,
                            favorite = draft.favorite,
                            cardExpiry = draft.cardExpiry,
                            cardCvv = draft.cardCvv,
                            iconKey = draft.iconKey?.ifBlank { null },
                            customFields = draft.customFields,
                            updatedAt = now,
                            passwordChangedAt = if (passwordChanged) now else existing.passwordChangedAt,
                        ),
                    )
                }
                container.vaultSession.touch()
                container.requestSafBackup()
                onDone()
            } catch (e: Exception) {
                _message.value = UserError.generic(container.appContext, e.message)
            } finally {
                saving = false
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                container.vaultRepository.delete(id)
                container.vaultSession.touch()
                container.requestSafBackup()
                _message.value = UserError.movedToRecycleBin(
                    context = container.appContext,
                    onUndo = { restore(id) },
                )
            } catch (e: Exception) {
                _message.value = UserError.generic(container.appContext, e.message)
            }
        }
    }

    fun restore(id: String) {
        viewModelScope.launch {
            try {
                container.vaultRepository.restore(id)
                container.vaultSession.touch()
                container.requestSafBackup()
                _message.value = UiMessage(container.appContext.getString(com.yishenghuang.keyic.R.string.entry_restored))
            } catch (e: Exception) {
                _message.value = UserError.generic(container.appContext, e.message)
            }
        }
    }

    fun purge(id: String) {
        viewModelScope.launch {
            try {
                container.attachmentRepository.deleteAllForEntry(id)
                container.vaultRepository.purge(id)
                container.vaultSession.touch()
                container.requestSafBackup()
                _message.value = UiMessage(container.appContext.getString(com.yishenghuang.keyic.R.string.entry_purged))
            } catch (e: Exception) {
                _message.value = UserError.generic(container.appContext, e.message)
            }
        }
    }

    fun addAttachment(
        entryId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                container.attachmentRepository.add(entryId, fileName, mimeType, bytes)
                container.vaultSession.touch()
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Could not add attachment")
            }
        }
    }

    fun deleteAttachment(attachmentId: String) {
        viewModelScope.launch {
            try {
                container.attachmentRepository.delete(attachmentId)
                container.vaultSession.touch()
            } catch (e: Exception) {
                _message.value = UserError.generic(container.appContext, e.message)
            }
        }
    }

    suspend fun readAttachment(attachmentId: String): ByteArray? =
        container.attachmentRepository.readDecrypted(attachmentId)

    fun toggleFavorite(entry: VaultEntry) {
        viewModelScope.launch {
            container.vaultRepository.upsert(
                entry.copy(favorite = !entry.favorite, updatedAt = System.currentTimeMillis()),
            )
            container.vaultSession.touch()
            container.requestSafBackup()
        }
    }

    fun importCsvDrafts(
        drafts: List<VaultEntryDraft>,
        onDone: (Int) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val unlocked = container.session.isUnlocked.first()
                if (!unlocked) {
                    onError(UserError.vaultLocked(container.appContext).text)
                    return@launch
                }
                container.importCsv(drafts)
                container.vaultSession.touch()
                container.requestSafBackup()
                onDone(drafts.size)
            } catch (e: Exception) {
                onError(UserError.importFailed(container.appContext).text)
            }
        }
    }

    fun replaceAllFromBackup(entries: List<VaultEntry>) {
        viewModelScope.launch {
            container.sqlVaultRepository.replaceAll(entries)
            container.vaultSession.touch()
            container.requestSafBackup()
        }
    }
}

class VaultViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VaultViewModel(container) as T
    }
}

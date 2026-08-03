package com.yishenghuang.keyic.data.repo

import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.core.model.VaultEntryDraft
import com.yishenghuang.keyic.core.port.VaultRepository
import com.yishenghuang.keyic.data.db.VaultDatabaseFactory
import com.yishenghuang.keyic.data.db.toDomain
import com.yishenghuang.keyic.data.db.toEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SqlCipherVaultRepository(
    private val unlockedFlow: Flow<Boolean>,
) : VaultRepository {
    override val entries: Flow<List<VaultEntry>> =
        unlockedFlow.flatMapLatest { unlocked ->
            if (!unlocked || !VaultDatabaseFactory.isOpen()) {
                flowOf(emptyList())
            } else {
                VaultDatabaseFactory.requireOpen().vaultDao().observeActive()
                    .map { list -> list.map { it.toDomain() } }
            }
        }

    override val deletedEntries: Flow<List<VaultEntry>> =
        unlockedFlow.flatMapLatest { unlocked ->
            if (!unlocked || !VaultDatabaseFactory.isOpen()) {
                flowOf(emptyList())
            } else {
                VaultDatabaseFactory.requireOpen().vaultDao().observeDeleted()
                    .map { list -> list.map { it.toDomain() } }
            }
        }

    override suspend fun getById(id: String): VaultEntry? =
        VaultDatabaseFactory.requireOpen().vaultDao().getById(id)?.toDomain()

    override suspend fun upsert(entry: VaultEntry) {
        VaultDatabaseFactory.requireOpen().vaultDao().upsert(entry.toEntity())
    }

    override suspend fun create(draft: VaultEntryDraft): VaultEntry {
        val now = System.currentTimeMillis()
        val entry = VaultEntry(
            id = UUID.randomUUID().toString(),
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
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
            passwordChangedAt = now,
        )
        VaultDatabaseFactory.requireOpen().vaultDao().upsert(entry.toEntity())
        return entry
    }

    override suspend fun delete(id: String) {
        val now = System.currentTimeMillis()
        VaultDatabaseFactory.requireOpen().vaultDao().softDelete(id, now)
    }

    override suspend fun restore(id: String) {
        VaultDatabaseFactory.requireOpen().vaultDao().restore(id, System.currentTimeMillis())
    }

    override suspend fun purge(id: String) {
        VaultDatabaseFactory.requireOpen().vaultDao().purge(id)
    }

    override suspend fun purgeExpired(beforeEpochMs: Long): Int =
        VaultDatabaseFactory.requireOpen().vaultDao().purgeExpired(beforeEpochMs)

    override suspend fun search(query: String): List<VaultEntry> {
        val dao = VaultDatabaseFactory.requireOpen().vaultDao()
        if (query.isBlank()) return emptyList()
        return dao.searchActive(query.trim()).map { it.toDomain() }
    }

    suspend fun replaceAll(entries: List<VaultEntry>) {
        val dao = VaultDatabaseFactory.requireOpen().vaultDao()
        dao.deleteAll()
        dao.upsertAll(entries.map { it.toEntity() })
    }
}

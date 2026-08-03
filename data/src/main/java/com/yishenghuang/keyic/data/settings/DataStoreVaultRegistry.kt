package com.yishenghuang.keyic.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yishenghuang.keyic.core.model.LEGACY_VAULT_ID
import com.yishenghuang.keyic.core.model.VaultMeta
import com.yishenghuang.keyic.core.port.VaultRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.vaultRegistryStore: DataStore<Preferences> by preferencesDataStore("keyic_vault_registry")

class DataStoreVaultRegistry(
    private val context: Context,
) : VaultRegistry {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val vaultsJson = stringPreferencesKey("vaults_json")
        val activeId = stringPreferencesKey("active_vault_id")
    }

    override val vaults: Flow<List<VaultMeta>> =
        context.vaultRegistryStore.data.map { prefs -> prefs.readVaults() }

    override val activeVaultId: Flow<String?> =
        context.vaultRegistryStore.data.map { it[Keys.activeId] }

    override suspend fun list(): List<VaultMeta> = vaults.first()

    override suspend fun getActiveId(): String? =
        context.vaultRegistryStore.data.first()[Keys.activeId]

    override suspend fun getActive(): VaultMeta? {
        val id = getActiveId() ?: return null
        return list().find { it.id == id }
    }

    override suspend fun setActive(vaultId: String) {
        context.vaultRegistryStore.edit { prefs ->
            val all = prefs.readVaults()
            require(all.any { it.id == vaultId }) { "Unknown vault" }
            prefs[Keys.activeId] = vaultId
        }
    }

    override suspend fun add(meta: VaultMeta) {
        context.vaultRegistryStore.edit { prefs ->
            val next = prefs.readVaults().toMutableList()
            if (next.none { it.id == meta.id }) next += meta
            prefs[Keys.vaultsJson] = json.encodeToString(next.map { it.toDto() })
            if (prefs[Keys.activeId].isNullOrBlank()) {
                prefs[Keys.activeId] = meta.id
            }
        }
    }

    override suspend fun rename(vaultId: String, name: String) {
        val trimmed = name.trim().ifEmpty { return }
        context.vaultRegistryStore.edit { prefs ->
            val next = prefs.readVaults().map {
                if (it.id == vaultId) it.copy(name = trimmed) else it
            }
            prefs[Keys.vaultsJson] = json.encodeToString(next.map { it.toDto() })
        }
    }

    override suspend fun remove(vaultId: String) {
        context.vaultRegistryStore.edit { prefs ->
            val next = prefs.readVaults().filterNot { it.id == vaultId }
            prefs[Keys.vaultsJson] = json.encodeToString(next.map { it.toDto() })
            val active = prefs[Keys.activeId]
            if (active == vaultId) {
                if (next.isEmpty()) prefs.remove(Keys.activeId)
                else prefs[Keys.activeId] = next.first().id
            }
        }
    }

    override suspend fun ensureMigratedLegacyIfNeeded(legacyConfigured: Boolean): VaultMeta? {
        val existing = list()
        if (existing.isNotEmpty()) return getActive()
        if (!legacyConfigured) return null
        val meta = VaultMeta(
            id = LEGACY_VAULT_ID,
            name = "Vault 1",
            createdAt = System.currentTimeMillis(),
        )
        add(meta)
        setActive(meta.id)
        return meta
    }

    private fun Preferences.readVaults(): List<VaultMeta> {
        val raw = this[Keys.vaultsJson] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<VaultMetaDto>>(raw).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    @Serializable
    private data class VaultMetaDto(
        val id: String,
        val name: String,
        val createdAt: Long,
    )

    private fun VaultMeta.toDto() = VaultMetaDto(id, name, createdAt)
    private fun VaultMetaDto.toDomain() = VaultMeta(id, name, createdAt)
}

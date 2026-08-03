package com.yishenghuang.keyic.core.port

import com.yishenghuang.keyic.core.model.VaultMeta
import kotlinx.coroutines.flow.Flow

interface VaultRegistry {
    val vaults: Flow<List<VaultMeta>>
    val activeVaultId: Flow<String?>

    suspend fun list(): List<VaultMeta>
    suspend fun getActiveId(): String?
    suspend fun getActive(): VaultMeta?
    suspend fun setActive(vaultId: String)
    suspend fun add(meta: VaultMeta)
    suspend fun rename(vaultId: String, name: String)
    suspend fun remove(vaultId: String)
    suspend fun ensureMigratedLegacyIfNeeded(legacyConfigured: Boolean): VaultMeta?
}

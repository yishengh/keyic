package com.yishenghuang.keyic.data.session

import android.content.Context
import com.yishenghuang.keyic.core.model.LEGACY_VAULT_ID
import com.yishenghuang.keyic.core.model.VaultMeta
import com.yishenghuang.keyic.core.port.VaultRegistry
import com.yishenghuang.keyic.core.port.VaultSession
import com.yishenghuang.keyic.data.crypto.VaultKeyManager
import com.yishenghuang.keyic.data.db.VaultDatabaseFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class VaultSessionImpl(
    private val context: Context,
    private val registry: VaultRegistry,
) : VaultSession {
    private val mutex = Mutex()
    private val unlocked = MutableStateFlow(false)
    private val configured = MutableStateFlow(false)
    private val activeId = MutableStateFlow<String?>(null)
    private var keyManager: VaultKeyManager? = null
    private var dbKey: ByteArray? = null
    private var lastActiveAt: Long = 0L

    override val isUnlocked: Flow<Boolean> = unlocked.asStateFlow()
    override val isVaultConfigured: Flow<Boolean> = configured.asStateFlow()
    override val activeVaultId: Flow<String?> = activeId.asStateFlow()

    val currentKeyManager: VaultKeyManager
        get() = keyManager ?: VaultKeyManager(context, activeId.value ?: LEGACY_VAULT_ID)

    suspend fun bootstrap() {
        mutex.withLock {
            val legacy = VaultKeyManager.isLegacyConfigured(context)
            registry.ensureMigratedLegacyIfNeeded(legacy)
            val list = registry.list()
            configured.value = list.isNotEmpty()
            val id = registry.getActiveId() ?: list.firstOrNull()?.id
            if (id != null) {
                bindVaultLocked(id)
            } else {
                activeId.value = null
                keyManager = null
            }
        }
    }

    override suspend fun isConfigured(): Boolean {
        val list = registry.list()
        configured.value = list.isNotEmpty()
        return configured.value
    }

    override suspend fun setup(masterPassword: CharArray, vaultName: String) = mutex.withLock {
        val id = if (registry.list().isEmpty()) LEGACY_VAULT_ID else UUID.randomUUID().toString()
        val meta = VaultMeta(
            id = id,
            name = vaultName.trim().ifEmpty { "Vault 1" },
            createdAt = System.currentTimeMillis(),
        )
        registry.add(meta)
        registry.setActive(id)
        bindVaultLocked(id)
        VaultDatabaseFactory.deleteDatabase(context, id)
        val key = currentKeyManager.setupWithPassword(masterPassword)
        openLocked(id, key)
        configured.value = true
    }

    override suspend fun createAdditionalVault(name: String, masterPassword: CharArray): Boolean =
        mutex.withLock {
            if (masterPassword.size < 8) {
                masterPassword.fill('\u0000')
                return@withLock false
            }
            lockLocked()
            val id = UUID.randomUUID().toString()
            val meta = VaultMeta(
                id = id,
                name = name.trim().ifEmpty { "Vault" },
                createdAt = System.currentTimeMillis(),
            )
            registry.add(meta)
            registry.setActive(id)
            bindVaultLocked(id)
            VaultDatabaseFactory.deleteDatabase(context, id)
            val key = currentKeyManager.setupWithPassword(masterPassword)
            openLocked(id, key)
            configured.value = true
            true
        }

    override suspend fun switchVault(vaultId: String) = mutex.withLock {
        if (activeId.value == vaultId && !unlocked.value) {
            bindVaultLocked(vaultId)
            return@withLock
        }
        lockLocked()
        registry.setActive(vaultId)
        bindVaultLocked(vaultId)
    }

    override suspend fun renameVault(vaultId: String, name: String) {
        registry.rename(vaultId, name)
    }

    override suspend fun deleteVault(vaultId: String): Boolean = mutex.withLock {
        val all = registry.list()
        if (all.size <= 1) return@withLock false
        if (activeId.value == vaultId) lockLocked()
        VaultKeyManager(context, vaultId).wipe()
        VaultDatabaseFactory.deleteDatabase(context, vaultId)
        registry.remove(vaultId)
        val next = registry.getActiveId() ?: registry.list().firstOrNull()?.id
        if (next != null) {
            bindVaultLocked(next)
            configured.value = true
        } else {
            keyManager = null
            activeId.value = null
            configured.value = false
        }
        true
    }

    override suspend fun unlock(masterPassword: CharArray): Boolean = mutex.withLock {
        ensureBoundLocked()
        val id = activeId.value ?: return@withLock false
        val key = currentKeyManager.unlockWithPassword(masterPassword) ?: return@withLock false
        openLocked(id, key)
        true
    }

    override suspend fun unlockWithBiometricKey(dbKey: ByteArray): Boolean = mutex.withLock {
        ensureBoundLocked()
        val id = activeId.value ?: return@withLock false
        openLocked(id, dbKey.copyOf())
        true
    }

    override suspend fun lock() = mutex.withLock { lockLocked() }

    override fun peekDbKey(): ByteArray? = dbKey?.copyOf()

    override fun touch() {
        lastActiveAt = System.currentTimeMillis()
    }

    override fun shouldAutoLock(nowMillis: Long, autoLockSeconds: Int): Boolean {
        if (!unlocked.value || autoLockSeconds <= 0) return false
        return nowMillis - lastActiveAt >= autoLockSeconds * 1000L
    }

    private fun ensureBoundLocked() {
        if (keyManager != null && activeId.value != null) return
        bindVaultLocked(activeId.value ?: LEGACY_VAULT_ID)
    }

    private fun bindVaultLocked(vaultId: String) {
        keyManager = VaultKeyManager(context, vaultId)
        activeId.value = vaultId
    }

    private fun lockLocked() {
        dbKey?.fill(0)
        dbKey = null
        VaultDatabaseFactory.close()
        unlocked.value = false
    }

    private fun openLocked(vaultId: String, key: ByteArray) {
        dbKey?.fill(0)
        dbKey = key
        try {
            VaultDatabaseFactory.getOrOpen(context, vaultId, key)
        } catch (_: Exception) {
            VaultDatabaseFactory.deleteDatabase(context, vaultId)
            VaultDatabaseFactory.getOrOpen(context, vaultId, key)
        }
        unlocked.value = true
        touch()
    }
}

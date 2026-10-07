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
    private val generation = MutableStateFlow(0L)
    val databaseGeneration: Flow<Long> = generation.asStateFlow()
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
        try {
            check(registry.list().isEmpty()) { "Vault already configured" }
            createLocked(LEGACY_VAULT_ID, vaultName, masterPassword)
        } finally { masterPassword.fill('\u0000') }
    }

    override suspend fun createAdditionalVault(name: String, masterPassword: CharArray): Boolean = mutex.withLock {
        try {
            if (masterPassword.size < 8) return@withLock false
            createLocked(UUID.randomUUID().toString(), name, masterPassword)
            true
        } finally { masterPassword.fill('\u0000') }
    }

    private suspend fun createLocked(id: String, name: String, password: CharArray) {
        require(password.size >= 8) { "Password too short" }
        val manager = VaultKeyManager(context, id)
        check(!manager.isConfigured() && !context.getDatabasePath(VaultDatabaseFactory.dbName(id)).exists()) {
            "Existing vault data requires recovery"
        }
        val previousId = activeId.value
        lockLocked()
        try {
            val key = manager.setupWithPassword(password)
            openLocked(id, key, publish = false)
            registry.add(VaultMeta(id, name.trim().ifEmpty { "Vault" }, System.currentTimeMillis()))
            registry.setActive(id)
            bindVaultLocked(id)
            configured.value = true
            unlocked.value = true
            generation.value++
        } catch (failure: Exception) {
            lockLocked()
            // Only this newly allocated, previously nonexistent vault may be rolled back.
            manager.wipe()
            VaultDatabaseFactory.deleteDatabase(context, id)
            registry.remove(id)
            if (previousId != null) bindVaultLocked(previousId) else {
                activeId.value = null
                keyManager = null
            }
            configured.value = registry.list().isNotEmpty()
            throw failure
        }
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
        if (!unlocked.value || activeId.value != vaultId || all.none { it.id == vaultId }) return@withLock false
        // Binding another vault must never retain the previous vault's open key/database.
        lockLocked()
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

    /** Pin the database/key pair for a short atomic operation, including vault switching. */
    suspend fun <T> withUnlockedVault(
        expectedVaultId: String? = null,
        block: suspend (com.yishenghuang.keyic.data.db.VaultDatabase, ByteArray, String) -> T,
    ): T = mutex.withLock {
        check(unlocked.value) { "Vault is locked" }
        val id = checkNotNull(activeId.value)
        check(expectedVaultId == null || expectedVaultId == id) { "Vault changed" }
        block(VaultDatabaseFactory.requireOpen(), checkNotNull(dbKey), id)
    }

    override fun peekDbKey(): ByteArray? = dbKey?.copyOf()

    override fun touch() {
        lastActiveAt = android.os.SystemClock.elapsedRealtime()
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
        try {
            VaultDatabaseFactory.close()
        } finally {
            unlocked.value = false
            generation.value++
        }
    }

    private fun openLocked(vaultId: String, key: ByteArray, publish: Boolean = true) {
        lockLocked()
        try {
            VaultDatabaseFactory.getOrOpen(context, vaultId, key)
        } catch (failure: Exception) {
            key.fill(0)
            throw failure
        }
        dbKey = key
        unlocked.value = publish
        if (publish) generation.value++
        touch()
    }
}

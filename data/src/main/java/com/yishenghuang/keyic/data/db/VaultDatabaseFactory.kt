package com.yishenghuang.keyic.data.db

import android.content.Context
import androidx.room.Room
import com.yishenghuang.keyic.core.model.LEGACY_VAULT_ID
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object VaultDatabaseFactory {
    @Volatile
    private var instance: VaultDatabase? = null

    @Volatile
    private var openVaultId: String? = null

    /** Kept alive for the open session — SQLCipher factory may open multiple connections. */
    @Volatile
    private var livePassphrase: ByteArray? = null

    fun dbName(vaultId: String): String =
        if (vaultId == LEGACY_VAULT_ID) "keyic_vault.db" else "keyic_vault_$vaultId.db"

    fun currentVaultId(): String? = openVaultId

    fun getOrOpen(context: Context, vaultId: String, dbKey: ByteArray): VaultDatabase {
        instance?.let { existing ->
            if (openVaultId == vaultId) return existing
            close()
        }
        synchronized(this) {
            instance?.let { existing ->
                if (openVaultId == vaultId) return existing
                closeLocked()
            }
            System.loadLibrary("sqlcipher")
            val passphrase = dbKey.copyOf()
            val factory = SupportOpenHelperFactory(passphrase, null, false)
            val name = dbName(vaultId)
            val db = Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                name,
            )
                .openHelperFactory(factory)
                .addMigrations(
                    VaultMigrations.MIGRATION_1_4,
                    VaultMigrations.MIGRATION_2_4,
                    VaultMigrations.MIGRATION_3_4,
                )
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
            db.openHelper.writableDatabase
            livePassphrase = passphrase
            openVaultId = vaultId
            instance = db
            return db
        }
    }

    fun requireOpen(): VaultDatabase =
        checkNotNull(instance) { "Vault is locked" }

    fun isOpen(): Boolean = instance != null

    fun deleteDatabase(context: Context, vaultId: String) {
        if (openVaultId == vaultId) close()
        val name = dbName(vaultId)
        context.applicationContext.deleteDatabase(name)
        val dir = context.applicationContext.getDatabasePath(name).parentFile ?: return
        dir.listFiles()
            ?.filter { it.name.startsWith(name) }
            ?.forEach { it.delete() }
    }

    fun close() {
        synchronized(this) { closeLocked() }
    }

    private fun closeLocked() {
        instance?.close()
        instance = null
        openVaultId = null
        livePassphrase?.fill(0)
        livePassphrase = null
    }
}

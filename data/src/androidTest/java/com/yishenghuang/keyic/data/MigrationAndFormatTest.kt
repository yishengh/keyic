package com.yishenghuang.keyic.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.yishenghuang.keyic.data.backup.EncryptedJsonBackupPort
import com.yishenghuang.keyic.data.db.VaultDatabaseFactory
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class MigrationAndFormatTest {
    @Test fun supportedSchemaMigrationsPreserveRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        System.loadLibrary("sqlcipher")
        for (version in 1..3) {
            VaultDatabaseFactory.close()
            val vaultId = UUID.randomUUID().toString()
            val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val helper = SupportOpenHelperFactory(key.copyOf(), null, false).create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(VaultDatabaseFactory.dbName(vaultId))
                    .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            val base = "CREATE TABLE vault_entries (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, url TEXT NOT NULL, packageHints TEXT NOT NULL, totpSecret TEXT, notes TEXT NOT NULL, tags TEXT NOT NULL, favorite INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, passwordChangedAt INTEGER NOT NULL"
                            val additions = if (version == 3) ", entryType TEXT NOT NULL, cardExpiry TEXT NOT NULL, cardCvv TEXT NOT NULL, iconKey TEXT" else ""
                            db.execSQL(base + additions + ")")
                            val cols = if (version == 3) ",entryType,cardExpiry,cardCvv,iconKey" else ""
                            val values = if (version == 3) ",'LOGIN','','',NULL" else ""
                            db.execSQL("INSERT INTO vault_entries (id,title,username,password,url,packageHints,totpSecret,notes,tags,favorite,createdAt,updatedAt,passwordChangedAt$cols) VALUES ('fixture','Synthetic migration','','','','[]',NULL,'','[]',0,1,2,3$values)")
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    }).build())
            helper.writableDatabase
            helper.close()
            val migrated = VaultDatabaseFactory.getOrOpen(context, vaultId, key)
            assertEquals("Synthetic migration", migrated.vaultDao().getById("fixture")?.title)
            assertTrue(migrated.attachmentDao().listForEntry("fixture").isEmpty())
            VaultDatabaseFactory.close()
            key.fill(0)
        }
    }

    @Test fun downgradeFailsWithoutDeletingData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val db = VaultDatabaseFactory.getOrOpen(context, id, key)
        db.openHelper.writableDatabase.version = 5
        VaultDatabaseFactory.close()
        val file = context.getDatabasePath(VaultDatabaseFactory.dbName(id))
        val before = file.readBytes()
        assertTrue(runCatching { VaultDatabaseFactory.getOrOpen(context, id, key) }.isFailure)
        assertTrue(before.contentEquals(file.readBytes()))
        key.fill(0)
    }

    @Test fun legacyBackupSchemasAcceptedUnknownSchemaRejectedAndPasswordsCleared() = runBlocking {
        val port = EncryptedJsonBackupPort()
        val password = UUID.randomUUID().toString()
        for (schema in listOf(1, 2, 3, 99)) {
            val bytes = encryptedPayload("""{"schema":$schema,"exportedAt":1,"entries":[]}""", password)
            val chars = password.toCharArray()
            val result = runCatching { port.importEncryptedJson(bytes, chars) }
            assertEquals(schema != 99, result.isSuccess)
            assertTrue(chars.all { it == '\u0000' })
        }
        val chars = password.toCharArray()
        assertTrue(runCatching { port.importEncryptedJson(byteArrayOf(1), chars) }.isFailure)
        assertTrue(chars.all { it == '\u0000' })
    }

    private fun encryptedPayload(payload: String, password: String): ByteArray {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val key = Argon2Kt().hash(Argon2Mode.ARGON2_ID, password.toByteArray(), salt,
            tCostInIterations = 3, mCostInKibibyte = 32 * 1024, parallelism = 2, hashLengthInBytes = 32).rawHashAsByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return try { "KEYIC1".toByteArray() + salt + cipher.iv + cipher.doFinal(payload.toByteArray()) }
        finally { key.fill(0) }
    }
}

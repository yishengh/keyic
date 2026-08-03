package com.yishenghuang.keyic.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object VaultMigrations {
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "vault_entries", "deletedAt")) {
                db.execSQL("ALTER TABLE vault_entries ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            }
            if (!columnExists(db, "vault_entries", "customFieldsJson")) {
                db.execSQL("ALTER TABLE vault_entries ADD COLUMN customFieldsJson TEXT NOT NULL DEFAULT '[]'")
            }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `attachments` (
                    `id` TEXT NOT NULL,
                    `entryId` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`entryId`) REFERENCES `vault_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_attachments_entryId` ON `attachments` (`entryId`)",
            )
        }
    }

    /** Catch-up for installs that skipped intermediate versions via destructive resets. */
    val MIGRATION_1_4 = object : Migration(1, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureBaseColumns(db)
            MIGRATION_3_4.migrate(db)
        }
    }

    val MIGRATION_2_4 = object : Migration(2, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureBaseColumns(db)
            MIGRATION_3_4.migrate(db)
        }
    }

    private fun ensureBaseColumns(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "vault_entries", "entryType")) {
            db.execSQL("ALTER TABLE vault_entries ADD COLUMN entryType TEXT NOT NULL DEFAULT 'LOGIN'")
        }
        if (!columnExists(db, "vault_entries", "cardExpiry")) {
            db.execSQL("ALTER TABLE vault_entries ADD COLUMN cardExpiry TEXT NOT NULL DEFAULT ''")
        }
        if (!columnExists(db, "vault_entries", "cardCvv")) {
            db.execSQL("ALTER TABLE vault_entries ADD COLUMN cardCvv TEXT NOT NULL DEFAULT ''")
        }
        if (!columnExists(db, "vault_entries", "iconKey")) {
            db.execSQL("ALTER TABLE vault_entries ADD COLUMN iconKey TEXT DEFAULT NULL")
        }
    }

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == column) return true
            }
        }
        return false
    }
}

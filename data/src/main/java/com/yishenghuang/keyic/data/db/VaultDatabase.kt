package com.yishenghuang.keyic.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VaultEntryEntity::class, AttachmentEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun attachmentDao(): AttachmentDao
}

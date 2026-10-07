package com.yishenghuang.keyic.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val entryType: String = "LOGIN",
    val username: String,
    val password: String,
    val url: String,
    val packageHints: String,
    val totpSecret: String?,
    val notes: String,
    val tags: String,
    val favorite: Boolean,
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val iconKey: String? = null,
    val customFieldsJson: String = "[]",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val passwordChangedAt: Long,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
)

@Dao
interface VaultDao {
    @Query(
        """
        SELECT * FROM vault_entries
        WHERE deletedAt IS NULL
        ORDER BY favorite DESC, title COLLATE NOCASE ASC
        """,
    )
    fun observeActive(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries WHERE deletedAt IS NULL")
    suspend fun listActive(): List<VaultEntryEntity>

    @Query(
        """
        SELECT * FROM vault_entries
        WHERE deletedAt IS NOT NULL
        ORDER BY deletedAt DESC
        """,
    )
    fun observeDeleted(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultEntryEntity?

    @Query(
        """
        SELECT * FROM vault_entries
        WHERE deletedAt IS NULL
          AND (
            title LIKE '%' || :q || '%'
            OR username LIKE '%' || :q || '%'
            OR url LIKE '%' || :q || '%'
            OR notes LIKE '%' || :q || '%'
            OR tags LIKE '%' || :q || '%'
          )
        ORDER BY favorite DESC, title COLLATE NOCASE ASC
        """,
    )
    suspend fun searchActive(q: String): List<VaultEntryEntity>

    @Upsert
    suspend fun upsert(entity: VaultEntryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<VaultEntryEntity>)

    @Update
    suspend fun update(entity: VaultEntryEntity)

    @Query("UPDATE vault_entries SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE vault_entries SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun purge(id: String)

    @Query("DELETE FROM vault_entries WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeExpired(before: Long): Int

    @Query("DELETE FROM vault_entries")
    suspend fun deleteAll()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE entryId = :entryId ORDER BY createdAt ASC")
    fun observeForEntry(entryId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE entryId = :entryId ORDER BY createdAt ASC")
    suspend fun listForEntry(entryId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AttachmentEntity?

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM attachments WHERE entryId = :entryId")
    suspend fun totalSizeForEntry(entryId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM attachments WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: String)
}

package com.yishenghuang.keyic.core.port

import com.yishenghuang.keyic.core.model.VaultEntry

/**
 * Portable vault interchange. JSON v3 is the primary format (attachments optional);
 * KeePass .kdbx is supported via KeePassJava2.
 */
interface ImportExportPort {
    suspend fun exportEncryptedJson(
        entries: List<VaultEntry>,
        passphrase: CharArray,
        attachments: List<JsonBackupAttachment> = emptyList(),
    ): ByteArray

    suspend fun importEncryptedJson(
        bytes: ByteArray,
        passphrase: CharArray,
    ): EncryptedJsonImportResult

    suspend fun exportKdbx(
        entries: List<VaultEntry>,
        passphrase: CharArray,
        binariesByEntryId: Map<String, List<KdbxBinary>> = emptyMap(),
    ): ByteArray

    suspend fun importKdbx(
        bytes: ByteArray,
        passphrase: CharArray,
    ): KdbxImportResult
}

data class JsonBackupAttachment(
    val entryId: String,
    val id: String,
    val fileName: String,
    val mimeType: String,
    val createdAt: Long,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JsonBackupAttachment) return false
        return entryId == other.entryId &&
            id == other.id &&
            fileName == other.fileName &&
            mimeType == other.mimeType &&
            createdAt == other.createdAt &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var r = entryId.hashCode()
        r = 31 * r + id.hashCode()
        r = 31 * r + fileName.hashCode()
        r = 31 * r + mimeType.hashCode()
        r = 31 * r + createdAt.hashCode()
        r = 31 * r + data.contentHashCode()
        return r
    }
}

data class EncryptedJsonImportResult(
    val entries: List<VaultEntry>,
    val attachments: List<JsonBackupAttachment> = emptyList(),
)

data class KdbxBinary(
    val fileName: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KdbxBinary) return false
        return fileName == other.fileName && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * fileName.hashCode() + data.contentHashCode()
}

data class KdbxImportResult(
    val entries: List<VaultEntry>,
    val attachments: List<KdbxImportedAttachment> = emptyList(),
)

data class KdbxImportedAttachment(
    val entryId: String,
    val fileName: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KdbxImportedAttachment) return false
        return entryId == other.entryId &&
            fileName == other.fileName &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int =
        31 * (31 * entryId.hashCode() + fileName.hashCode()) + data.contentHashCode()
}

class KdbxNotImplementedException :
    UnsupportedOperationException("KeePass .kdbx import/export failed or is unavailable")

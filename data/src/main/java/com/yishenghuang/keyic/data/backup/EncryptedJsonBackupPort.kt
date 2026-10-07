package com.yishenghuang.keyic.data.backup

import com.yishenghuang.keyic.core.model.CustomField
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.core.port.EncryptedJsonImportResult
import com.yishenghuang.keyic.core.port.ImportExportPort
import com.yishenghuang.keyic.core.port.JsonBackupAttachment
import com.yishenghuang.keyic.core.port.KdbxBinary
import com.yishenghuang.keyic.core.port.KdbxImportResult
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keyic encrypted JSON backup schema v3 (reader accepts v1/v2; attachments since v3).
 *
 * File layout (binary):
 * magic(6) "KEYIC1" | salt(16) | iv(12) | ciphertext(AES-256-GCM)
 * Ciphertext decrypts to UTF-8 JSON [BackupPayload].
 */
class EncryptedJsonBackupPort : ImportExportPort {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    private val argon2 = Argon2Kt()
    private val random = SecureRandom()

    override suspend fun exportEncryptedJson(
        entries: List<VaultEntry>,
        passphrase: CharArray,
        attachments: List<JsonBackupAttachment>,
    ): ByteArray {
        val active = entries.filter { it.deletedAt == null }
        val payload = BackupPayload(
            schema = 3,
            exportedAt = System.currentTimeMillis(),
            entries = active.map { it.toBackup() },
            attachments = attachments.map { it.toDto() },
        )
        val plain = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also { random.nextBytes(it) }
        var key: ByteArray? = null
        return try {
            key = derive(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val iv = cipher.iv
            require(plain.size <= com.yishenghuang.keyic.core.backup.BoundedInput.MAX_BYTES - 50) { "Backup exceeds size limit" }
            val ct = cipher.doFinal(plain)
            MAGIC + salt + iv + ct
        } finally {
            key?.fill(0)
            plain.fill(0)
            passphrase.fill('\u0000')
        }
    }

    override suspend fun importEncryptedJson(
        bytes: ByteArray,
        passphrase: CharArray,
    ): EncryptedJsonImportResult {
        var key: ByteArray? = null
        var plain: ByteArray? = null
        return try {
            require(bytes.size >= MAGIC.size + 16 + 12 + 16 &&
                bytes.size <= com.yishenghuang.keyic.core.backup.BoundedInput.MAX_BYTES) { "Invalid backup size" }
            require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not a Keyic backup" }
            val salt = bytes.copyOfRange(MAGIC.size, MAGIC.size + 16)
            val iv = bytes.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
            key = derive(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            plain = cipher.doFinal(bytes, MAGIC.size + 28, bytes.size - MAGIC.size - 28)
            val payload = json.decodeFromString<BackupPayload>(plain.toString(Charsets.UTF_8))
            require(payload.schema in 1..3) { "Unsupported backup version" }
            require(payload.entries.map { it.id }.toSet().size == payload.entries.size) { "Duplicate entry IDs" }
            require(payload.entries.all { it.id.isNotBlank() }) { "Invalid entry ID" }
            val ids = payload.entries.map { it.id }.toSet()
            require(payload.attachments.all { it.entryId in ids }) { "Orphan attachment" }
            EncryptedJsonImportResult(payload.entries.map { it.toDomain() }, payload.attachments.map { it.toDomain() })
        } finally {
            plain?.fill(0)
            key?.fill(0)
            passphrase.fill('\u0000')
        }
    }

    override suspend fun exportKdbx(
        entries: List<VaultEntry>,
        passphrase: CharArray,
        binariesByEntryId: Map<String, List<KdbxBinary>>,
    ): ByteArray = KdbxAdapter.exportKdbx(
        entries = entries.filter { it.deletedAt == null },
        passphrase = passphrase,
        binariesByEntryId = binariesByEntryId,
    )

    override suspend fun importKdbx(
        bytes: ByteArray,
        passphrase: CharArray,
    ): KdbxImportResult = KdbxAdapter.importKdbx(bytes, passphrase)

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val pw = passphrase.concatToString().toByteArray(Charsets.UTF_8)
        return try {
            argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = pw,
                salt = salt,
                tCostInIterations = 3,
                mCostInKibibyte = 32 * 1024,
                parallelism = 2,
                hashLengthInBytes = 32,
            ).rawHashAsByteArray()
        } finally {
            pw.fill(0)
        }
    }

    companion object {
        private val MAGIC = "KEYIC1".toByteArray(Charsets.US_ASCII)
    }
}

@Serializable
data class BackupPayload(
    val schema: Int,
    val exportedAt: Long,
    val entries: List<BackupEntryDto>,
    val attachments: List<BackupAttachmentDto> = emptyList(),
)

@Serializable
data class BackupAttachmentDto(
    val entryId: String,
    val id: String = "",
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val createdAt: Long = 0L,
    val dataBase64: String,
)

@Serializable
data class BackupEntryDto(
    val id: String,
    val title: String,
    val type: String = "LOGIN",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val packageHints: List<String> = emptyList(),
    val totpSecret: String? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val iconKey: String? = null,
    val customFields: List<CustomField> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val passwordChangedAt: Long,
)

private fun VaultEntry.toBackup() = BackupEntryDto(
    id = id,
    title = title,
    type = type.name,
    username = username,
    password = password,
    url = url,
    packageHints = packageHints,
    totpSecret = totpSecret,
    notes = notes,
    tags = tags,
    favorite = favorite,
    cardExpiry = cardExpiry,
    cardCvv = cardCvv,
    iconKey = iconKey,
    customFields = customFields,
    createdAt = createdAt,
    updatedAt = updatedAt,
    passwordChangedAt = passwordChangedAt,
)

private fun BackupEntryDto.toDomain() = VaultEntry(
    id = id,
    title = title,
    type = com.yishenghuang.keyic.core.model.EntryType.valueOf(type),
    username = username,
    password = password,
    url = url,
    packageHints = packageHints,
    totpSecret = totpSecret,
    notes = notes,
    tags = tags,
    favorite = favorite,
    cardExpiry = cardExpiry,
    cardCvv = cardCvv,
    iconKey = iconKey?.ifBlank { null },
    customFields = customFields,
    deletedAt = null,
    createdAt = createdAt,
    updatedAt = updatedAt,
    passwordChangedAt = passwordChangedAt,
)

private fun JsonBackupAttachment.toDto() = BackupAttachmentDto(
    entryId = entryId,
    id = id,
    fileName = fileName,
    mimeType = mimeType,
    createdAt = createdAt,
    dataBase64 = Base64.getEncoder().encodeToString(data),
)

private fun BackupAttachmentDto.toDomain() = JsonBackupAttachment(
    entryId = entryId,
    id = id.ifBlank { UUID.randomUUID().toString() },
    fileName = fileName,
    mimeType = mimeType,
    createdAt = createdAt,
    data = Base64.getDecoder().decode(dataBase64),
)

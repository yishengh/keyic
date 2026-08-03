package com.yishenghuang.keyic.data.db

import com.yishenghuang.keyic.core.model.AttachmentMeta
import com.yishenghuang.keyic.core.model.CustomField
import com.yishenghuang.keyic.core.model.EntryType
import com.yishenghuang.keyic.core.model.VaultEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun VaultEntryEntity.toDomain(): VaultEntry = VaultEntry(
    id = id,
    title = title,
    type = runCatching { EntryType.valueOf(entryType) }.getOrDefault(EntryType.LOGIN),
    username = username,
    password = password,
    url = url,
    packageHints = packageHints.split('|').filter { it.isNotBlank() },
    totpSecret = totpSecret,
    notes = notes,
    tags = tags.split('|').filter { it.isNotBlank() },
    favorite = favorite,
    cardExpiry = cardExpiry,
    cardCvv = cardCvv,
    iconKey = iconKey?.ifBlank { null },
    customFields = decodeCustomFields(customFieldsJson),
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    passwordChangedAt = passwordChangedAt,
)

fun VaultEntry.toEntity(): VaultEntryEntity = VaultEntryEntity(
    id = id,
    title = title,
    entryType = type.name,
    username = username,
    password = password,
    url = url,
    packageHints = packageHints.joinToString("|"),
    totpSecret = totpSecret,
    notes = notes,
    tags = tags.joinToString("|"),
    favorite = favorite,
    cardExpiry = cardExpiry,
    cardCvv = cardCvv,
    iconKey = iconKey?.ifBlank { null },
    customFieldsJson = encodeCustomFields(customFields),
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    passwordChangedAt = passwordChangedAt,
)

fun AttachmentEntity.toDomain(): AttachmentMeta = AttachmentMeta(
    id = id,
    entryId = entryId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    createdAt = createdAt,
)

fun encodeCustomFields(fields: List<CustomField>): String =
    json.encodeToString(fields)

fun decodeCustomFields(raw: String): List<CustomField> =
    runCatching { json.decodeFromString<List<CustomField>>(raw) }.getOrDefault(emptyList())

package com.yishenghuang.keyic.core.model

enum class EntryType {
    LOGIN,
    NOTE,
    CARD,
    IDENTITY,
}

@kotlinx.serialization.Serializable
data class CustomField(
    val id: String,
    val name: String,
    val value: String = "",
    val masked: Boolean = false,
)

/**
 * Domain vault entry. Sensitive fields stay as String at the domain boundary;
 * UI should prefer short-lived copies and clear clipboard on a timer.
 */
data class VaultEntry(
    val id: String,
    val title: String,
    val type: EntryType = EntryType.LOGIN,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    /** Package names / host hints for Autofill matching. */
    val packageHints: List<String> = emptyList(),
    val totpSecret: String? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    /** Card expiry MM/YY when type == CARD */
    val cardExpiry: String = "",
    /** Card CVV when type == CARD */
    val cardCvv: String = "",
    /**
     * Manual brand icon id from BrandCatalog (e.g. "google").
     * Null/blank = auto-detect from title/url.
     */
    val iconKey: String? = null,
    val customFields: List<CustomField> = emptyList(),
    /** Soft-delete timestamp; null = active. */
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val passwordChangedAt: Long,
)

data class VaultEntryDraft(
    val title: String,
    val type: EntryType = EntryType.LOGIN,
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
)

data class AttachmentMeta(
    val id: String,
    val entryId: String,
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long,
    val createdAt: Long,
)

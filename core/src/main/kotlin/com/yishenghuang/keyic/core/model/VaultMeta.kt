package com.yishenghuang.keyic.core.model

/** Metadata for an independent encrypted vault (separate DB + master password). */
data class VaultMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
)

/** First-installed / migrated vault — keeps legacy DB & prefs filenames. */
const val LEGACY_VAULT_ID = "default"

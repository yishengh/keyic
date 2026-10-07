package com.yishenghuang.keyic.core.port

import com.yishenghuang.keyic.core.model.VaultEntry

interface AutofillMatcher {
    fun match(
        packageName: String?,
        webDomain: String?,
        entries: List<VaultEntry>,
        preferCards: Boolean = false,
    ): List<VaultEntry>
}

class DefaultAutofillMatcher : AutofillMatcher {
    override fun match(
        packageName: String?,
        webDomain: String?,
        entries: List<VaultEntry>,
        preferCards: Boolean,
    ): List<VaultEntry> {
        val type = if (preferCards) com.yishenghuang.keyic.core.model.EntryType.CARD
            else com.yishenghuang.keyic.core.model.EntryType.LOGIN
        val domain = normalizeDomain(webDomain)
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() }
        return entries.asSequence()
            .filter { it.deletedAt == null && it.type == type }
            .filter { entry ->
                if (preferCards) true // Released only after explicit destination confirmation.
                else if (webDomain != null) domain != null && (
                    normalizeDomain(entry.url) == domain ||
                        entry.packageHints.any { normalizeDomain(it) == domain })
                else pkg != null && entry.packageHints.any { it.trim() == pkg }
            }
            .sortedWith(compareByDescending<VaultEntry> { it.favorite }
                .thenByDescending { it.updatedAt }.thenBy { it.title.lowercase() })
            .take(8).toList()
    }

    companion object {
        fun normalizeDomain(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val text = raw.trim()
                val uri = java.net.URI(if ("://" in text) text else "https://$text")
                if (uri.scheme.lowercase(java.util.Locale.ROOT) !in setOf("https", "http") || uri.userInfo != null) return null
                val host = uri.host ?: return null
                java.net.IDN.toASCII(host).lowercase(java.util.Locale.ROOT)
                    .removeSuffix(".").removePrefix("www.").takeIf { it.contains('.') }
            }.getOrNull()
        }
    }
}

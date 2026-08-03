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
        val active = entries.filter { it.deletedAt == null }.let { list ->
            if (preferCards) {
                val cards = list.filter {
                    it.type == com.yishenghuang.keyic.core.model.EntryType.CARD
                }
                cards.ifEmpty { list }
            } else {
                list
            }
        }
        if (active.isEmpty()) return emptyList()
        val pkg = packageName?.lowercase()?.takeIf { it.isNotBlank() }
        val domain = normalizeDomain(webDomain)

        val scored = active.map { entry ->
            entry to score(entry, pkg, domain, preferCards)
        }.sortedWith(
            compareByDescending<Pair<VaultEntry, Int>> { it.second }
                .thenByDescending { it.first.updatedAt }
                .thenByDescending { it.first.favorite }
                .thenBy { it.first.title.lowercase() },
        )

        val matched = scored.filter { it.second > 0 }.map { it.first }
        if (matched.isNotEmpty()) return matched.take(8)

        return active
            .sortedWith(
                compareByDescending<VaultEntry> { it.favorite }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.title.lowercase() },
            )
            .take(6)
    }

    private fun score(
        entry: VaultEntry,
        pkg: String?,
        domain: String?,
        preferCards: Boolean,
    ): Int {
        var score = 0
        val hints = entry.packageHints.map { normalizeHint(it) }
        val urlDomain = normalizeDomain(entry.url)
        val url = entry.url.lowercase()
        val title = entry.title.lowercase()

        if (preferCards && entry.type == com.yishenghuang.keyic.core.model.EntryType.CARD) {
            score += 40
        }

        if (pkg != null) {
            if (hints.any { it == pkg || pkg.contains(it) || it.contains(pkg) }) score += 100
            if (url.contains(pkg)) score += 40
            if (title.contains(pkg.substringAfterLast('.'))) score += 10
            val reversePkg = pkg.split('.').asReversed().joinToString(".")
            if (domain != null && (reversePkg.contains(domain) || domain.contains(pkg.substringAfterLast('.')))) {
                score += 25
            }
        }
        if (domain != null) {
            if (urlDomain == domain || urlDomain?.endsWith(".$domain") == true) score += 110
            else if (url.contains(domain)) score += 100
            if (hints.any {
                    val h = normalizeDomain(it) ?: it
                    h == domain || h.contains(domain) || domain.contains(h)
                }
            ) {
                score += 80
            }
            if (title.contains(domain.substringBefore('.'))) score += 15
        }
        if (entry.favorite) score += 5
        val ageDays = ((System.currentTimeMillis() - entry.updatedAt).coerceAtLeast(0L) / 86_400_000L)
        score += (20 - ageDays.toInt().coerceIn(0, 20))
        return score
    }

    companion object {
        fun normalizeDomain(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var s = raw.trim().lowercase()
            s = s.removePrefix("https://").removePrefix("http://")
            s = s.substringBefore('/').substringBefore(':')
            s = s.removePrefix("www.")
            // Ignore android-app / package-looking hosts
            if (s.startsWith("android.app") || s.count { it == '.' } == 0 && !s.contains('.')) {
                return s.takeIf { it.isNotBlank() }
            }
            return s.takeIf { it.isNotBlank() }
        }

        private fun normalizeHint(hint: String): String =
            hint.trim().lowercase().removePrefix("www.")
    }
}

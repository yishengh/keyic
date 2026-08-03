package com.yishenghuang.keyic.core.usecase

import com.yishenghuang.keyic.core.crypto.PasswordStrength
import com.yishenghuang.keyic.core.model.HealthIssue
import com.yishenghuang.keyic.core.model.HealthIssueKind
import com.yishenghuang.keyic.core.model.HealthReport
import com.yishenghuang.keyic.core.model.VaultEntry

object HealthAnalyzer {
    private const val STALE_DAYS = 180L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun analyze(entries: List<VaultEntry>, nowMillis: Long = System.currentTimeMillis()): HealthReport {
        val issues = mutableListOf<HealthIssue>()
        val byPassword = entries
            .filter { it.password.isNotEmpty() }
            .groupBy { it.password }

        entries.forEach { entry ->
            if (entry.password.isNotEmpty() && PasswordStrength.isWeak(entry.password)) {
                issues += HealthIssue(
                    entryId = entry.id,
                    title = entry.title,
                    kind = HealthIssueKind.WEAK,
                    detail = "Password is short or too simple",
                )
            }
            val age = nowMillis - entry.passwordChangedAt
            if (entry.password.isNotEmpty() && age > STALE_DAYS * DAY_MS) {
                issues += HealthIssue(
                    entryId = entry.id,
                    title = entry.title,
                    kind = HealthIssueKind.STALE,
                    detail = "Password not changed in ${age / DAY_MS} days",
                )
            }
        }

        byPassword.values.filter { it.size > 1 }.forEach { group ->
            group.forEach { entry ->
                issues += HealthIssue(
                    entryId = entry.id,
                    title = entry.title,
                    kind = HealthIssueKind.REUSED,
                    detail = "Same password used on ${group.size} entries",
                )
            }
        }

        return HealthReport(
            issues = issues.distinctBy { it.entryId to it.kind },
            totalEntries = entries.size,
            analyzedAt = nowMillis,
        )
    }
}

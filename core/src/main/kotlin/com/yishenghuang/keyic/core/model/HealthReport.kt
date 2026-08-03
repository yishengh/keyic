package com.yishenghuang.keyic.core.model

data class HealthIssue(
    val entryId: String,
    val title: String,
    val kind: HealthIssueKind,
    val detail: String,
)

enum class HealthIssueKind {
    WEAK,
    REUSED,
    STALE,
}

data class HealthReport(
    val issues: List<HealthIssue>,
    val totalEntries: Int,
    val analyzedAt: Long,
) {
    val weakCount: Int get() = issues.count { it.kind == HealthIssueKind.WEAK }
    val reusedCount: Int get() = issues.count { it.kind == HealthIssueKind.REUSED }
    val staleCount: Int get() = issues.count { it.kind == HealthIssueKind.STALE }
    val score: Int
        get() {
            if (totalEntries == 0) return 100
            val penalty = (weakCount * 12 + reusedCount * 15 + staleCount * 8)
                .coerceAtMost(100)
            return (100 - penalty).coerceIn(0, 100)
        }
}

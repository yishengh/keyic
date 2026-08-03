package com.yishenghuang.keyic.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.model.HealthIssueKind
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.theme.Danger
import com.yishenghuang.keyic.ui.theme.Success
import com.yishenghuang.keyic.ui.theme.Warning
import com.yishenghuang.keyic.ui.vault.VaultViewModel

@Composable
fun HealthScreen(
    viewModel: VaultViewModel,
    onOpenEntry: (String) -> Unit,
) {
    val report by viewModel.healthReport.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            strong = true,
            contentPadding = PaddingValues(20.dp),
        ) {
            Column {
                Text(stringResource(R.string.health_vault_score), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${report.score}",
                    style = MaterialTheme.typography.displayMedium,
                    color = when {
                        report.score >= 80 -> Success
                        report.score >= 50 -> Warning
                        else -> Danger
                    },
                )
                Text(
                    text = stringResource(
                        R.string.health_entries_issues,
                        report.totalEntries,
                        report.issues.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(stringResource(R.string.health_weak), report.weakCount)
                    Stat(stringResource(R.string.health_reused), report.reusedCount)
                    Stat(stringResource(R.string.health_stale), report.staleCount)
                }
            }
        }

        if (report.issues.isEmpty()) {
            Text(
                text = stringResource(R.string.health_looking_good),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(report.issues, key = { "${it.entryId}-${it.kind}" }) { issue ->
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenEntry(issue.entryId) },
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Column {
                            Text(issue.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = issue.kind.label(),
                                style = MaterialTheme.typography.labelMedium,
                                color = when (issue.kind) {
                                    HealthIssueKind.WEAK -> Danger
                                    HealthIssueKind.REUSED -> Warning
                                    HealthIssueKind.STALE -> MaterialTheme.colorScheme.primary
                                },
                            )
                            Text(
                                text = issue.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column {
        Text("$value", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HealthIssueKind.label(): String = when (this) {
    HealthIssueKind.WEAK -> stringResource(R.string.health_issue_weak)
    HealthIssueKind.REUSED -> stringResource(R.string.health_issue_reused)
    HealthIssueKind.STALE -> stringResource(R.string.health_issue_stale)
}

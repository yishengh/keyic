package com.yishenghuang.keyic.ui.auth

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.crypto.TotpGenerator
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.ui.components.EmptyState
import com.yishenghuang.keyic.ui.components.GlassSearchField
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.util.SecureClipboard
import com.yishenghuang.keyic.ui.vault.VaultViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AuthScreen(
    viewModel: VaultViewModel,
    clipboard: SecureClipboard,
    clipboardClearSeconds: Int,
    onOpenEntry: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    val totpEntries = remember(state.entries, query) {
        state.entries
            .filter { !it.totpSecret.isNullOrBlank() }
            .filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.username.contains(query, ignoreCase = true)
            }
            .sortedBy { it.title.lowercase() }
    }
    val grouped = remember(totpEntries) {
        totpEntries.groupBy { it.title.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
            .toSortedMap()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassSearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = stringResource(R.string.search_accounts),
            leadingIcon = Icons.Outlined.Search,
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassSurface(
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                elevation = 4.dp,
            ) {
                Text(
                    text = stringResource(R.string.accounts_count, totpEntries.size),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            GlassSurface(
                shape = MaterialTheme.shapes.large,
                strong = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                elevation = 4.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.end_to_end_encrypted),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (totpEntries.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_auth_title),
                subtitle = stringResource(R.string.empty_auth_subtitle),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                grouped.forEach { (letter, items) ->
                    item(key = "h-$letter") {
                        Text(
                            text = letter,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(items, key = { it.id }) { entry ->
                        TotpCard(
                            entry = entry,
                            now = now,
                            onCopy = {
                                val code = runCatching {
                                    TotpGenerator.generate(entry.totpSecret!!, now)
                                }.getOrNull() ?: return@TotpCard
                                clipboard.copy("totp", code, clipboardClearSeconds)
                            },
                            onOpen = { onOpenEntry(entry.id) },
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
private fun TotpCard(
    entry: VaultEntry,
    now: Long,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    val secret = entry.totpSecret ?: return
    val code = remember(now, secret) {
        runCatching { TotpGenerator.generate(secret, now) }.getOrDefault("------")
    }
    val next = remember(now, secret) {
        runCatching { TotpGenerator.nextCode(secret, now) }.getOrDefault("------")
    }
    val progress by animateFloatAsState(
        targetValue = TotpGenerator.progress(now),
        label = "authProgress",
    )
    GlassSurface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.username.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = code.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${TotpGenerator.remainingSeconds(now)}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Next ${next.chunked(3).joinToString(" ")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )
        }
    }
}

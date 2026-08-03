package com.yishenghuang.keyic.ui.vault

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.model.EntryType
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.data.AppContainer
import com.yishenghuang.keyic.ui.adaptive.AdaptiveContentWidth
import com.yishenghuang.keyic.ui.adaptive.rememberWindowWidthSize
import com.yishenghuang.keyic.ui.adaptive.useListDetail
import com.yishenghuang.keyic.ui.adaptive.useNavigationRail
import com.yishenghuang.keyic.ui.auth.AuthScreen
import com.yishenghuang.keyic.ui.brand.BrandAvatar
import com.yishenghuang.keyic.ui.components.EmptyState
import com.yishenghuang.keyic.ui.components.GlassFab
import com.yishenghuang.keyic.ui.components.GlassNavItem
import com.yishenghuang.keyic.ui.components.GlassNavigationBar
import com.yishenghuang.keyic.ui.components.GlassNavigationRail
import com.yishenghuang.keyic.ui.components.GlassSearchField
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.components.KeyicBackdrop
import com.yishenghuang.keyic.ui.generator.GeneratorScreen
import com.yishenghuang.keyic.ui.health.HealthScreen
import com.yishenghuang.keyic.ui.settings.SettingsScreen
import com.yishenghuang.keyic.ui.theme.GlassVariant
import com.yishenghuang.keyic.ui.theme.glass
import com.yishenghuang.keyic.ui.util.SecureClipboard
import kotlinx.coroutines.launch

private enum class VaultFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    FAVORITES(R.string.filter_favorites),
    LOGIN(R.string.filter_login),
    NOTE(R.string.filter_note),
    CARD(R.string.filter_card),
    IDENTITY(R.string.filter_identity),
    TOTP(R.string.filter_totp),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(
    vaultViewModel: VaultViewModel,
    onOpenEntry: (String) -> Unit,
    onEditEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    onOpenGenerator: () -> Unit,
    clipboard: SecureClipboard,
    clipboardClearSeconds: Int,
    container: AppContainer,
) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf(
        stringResource(R.string.tab_vault),
        stringResource(R.string.tab_auth),
        stringResource(R.string.tab_generator),
        stringResource(R.string.tab_security),
        stringResource(R.string.tab_settings),
    )
    val scope = rememberCoroutineScope()
    var showVaultSwitcher by remember { mutableStateOf(false) }
    val vaults by container.vaultRegistry.vaults.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by container.vaultRegistry.activeVaultId.collectAsStateWithLifecycle(initialValue = null)
    val activeName = vaults.find { it.id == activeId }?.name
        ?: stringResource(R.string.vault_default_name)
    val widthSize = rememberWindowWidthSize()
    val useRail = widthSize.useNavigationRail
    val useListDetail = widthSize.useListDetail
    var selectedEntryId by remember { mutableStateOf<String?>(null) }
    val vaultState by vaultViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(tab) {
        if (tab != 0) selectedEntryId = null
    }
    LaunchedEffect(vaultState.entries, selectedEntryId) {
        val id = selectedEntryId ?: return@LaunchedEffect
        if (vaultState.entries.none { it.id == id }) {
            selectedEntryId = null
        }
    }

    val navItems = listOf(
        GlassNavItem(
            stringResource(R.string.tab_vault),
            Icons.Outlined.Shield,
            tab == 0,
        ) { tab = 0 },
        GlassNavItem(
            stringResource(R.string.tab_auth),
            Icons.Outlined.Timer,
            tab == 1,
        ) { tab = 1 },
        GlassNavItem(
            stringResource(R.string.tab_generator),
            Icons.Outlined.Password,
            tab == 2,
        ) { tab = 2 },
        GlassNavItem(
            stringResource(R.string.tab_security),
            Icons.Outlined.HealthAndSafety,
            tab == 3,
        ) { tab = 3 },
        GlassNavItem(
            stringResource(R.string.tab_settings),
            Icons.Outlined.Settings,
            tab == 4,
        ) { tab = 4 },
    )

    KeyicBackdrop(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                GlassNavigationRail(items = navItems)
            }
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (tab == 0) {
                                        Modifier.clickable { showVaultSwitcher = true }
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Text(
                                text = titles[tab],
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            if (tab == 0) {
                                Text(
                                    "$activeName  ▾",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        IconButton(onClick = {
                            scope.launch { container.vaultSession.lock() }
                        }) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = stringResource(R.string.cd_lock_now),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        GlassNavigationBar(items = navItems)
                    }
                },
                floatingActionButton = {
                    if (tab == 0 || tab == 1) {
                        GlassFab(
                            onClick = onAddEntry,
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.fab_add_item)) },
                        )
                    }
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                ) {
                    when (tab) {
                        0 -> {
                            if (useListDetail) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    VaultListScreen(
                                        viewModel = vaultViewModel,
                                        onOpenEntry = { selectedEntryId = it },
                                        onAddEntry = onAddEntry,
                                        selectedEntryId = selectedEntryId,
                                        modifier = Modifier
                                            .weight(0.4f)
                                            .fillMaxHeight(),
                                    )
                                    VerticalDivider(
                                        modifier = Modifier.fillMaxHeight(),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    ) {
                                        val detailId = selectedEntryId
                                        if (detailId != null) {
                                            EntryDetailScreen(
                                                entryId = detailId,
                                                viewModel = vaultViewModel,
                                                clipboard = clipboard,
                                                clipboardClearSeconds = clipboardClearSeconds,
                                                onBack = { selectedEntryId = null },
                                                onEdit = { onEditEntry(detailId) },
                                                embedded = true,
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.pad_select_entry),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(32.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                VaultListScreen(
                                    viewModel = vaultViewModel,
                                    onOpenEntry = onOpenEntry,
                                    onAddEntry = onAddEntry,
                                )
                            }
                        }
                        1 -> AdaptiveContentWidth {
                            AuthScreen(
                                viewModel = vaultViewModel,
                                clipboard = clipboard,
                                clipboardClearSeconds = clipboardClearSeconds,
                                onOpenEntry = onOpenEntry,
                            )
                        }
                        2 -> AdaptiveContentWidth {
                            GeneratorScreen(
                                clipboard = clipboard,
                                clipboardClearSeconds = clipboardClearSeconds,
                            )
                        }
                        3 -> AdaptiveContentWidth {
                            HealthScreen(
                                viewModel = vaultViewModel,
                                onOpenEntry = onOpenEntry,
                            )
                        }
                        else -> AdaptiveContentWidth {
                            SettingsScreen(container = container)
                        }
                    }
                }
            }
        }
    }

    if (showVaultSwitcher) {
        VaultSwitcherSheet(
            container = container,
            onDismiss = { showVaultSwitcher = false },
        )
    }
}

@Composable
fun VaultListScreen(
    viewModel: VaultViewModel,
    onOpenEntry: (String) -> Unit,
    onAddEntry: () -> Unit = {},
    selectedEntryId: String? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(VaultFilter.ALL) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    val glass = MaterialTheme.glass

    val availableTags = remember(state.entries) {
        state.entries
            .flatMap { it.tags }
            .map { PresetTags.normalize(it) }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    val filtered = remember(state.filtered, filter, tagFilter) {
        val byType = when (filter) {
            VaultFilter.ALL -> state.filtered
            VaultFilter.FAVORITES -> state.filtered.filter { it.favorite }
            VaultFilter.LOGIN -> state.filtered.filter { it.type == EntryType.LOGIN }
            VaultFilter.NOTE -> state.filtered.filter { it.type == EntryType.NOTE }
            VaultFilter.CARD -> state.filtered.filter { it.type == EntryType.CARD }
            VaultFilter.IDENTITY -> state.filtered.filter { it.type == EntryType.IDENTITY }
            VaultFilter.TOTP -> state.filtered.filter { !it.totpSecret.isNullOrBlank() }
        }
        if (tagFilter.isNullOrBlank()) {
            byType
        } else {
            byType.filter { entry ->
                entry.tags.any { it.equals(tagFilter, ignoreCase = true) }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassSearchField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = stringResource(R.string.search_vault),
            leadingIcon = Icons.Outlined.Search,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VaultFilter.entries.forEach { f ->
                val selected = filter == f
                FilterChip(
                    selected = selected,
                    onClick = { filter = f },
                    label = {
                        Text(
                            stringResource(f.labelRes),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (glass.isDark) glass.chromeFill else Color.White.copy(alpha = 0.7f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = glass.sage,
                        selectedLabelColor = glass.sageOn,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = Color.Transparent,
                        selectedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        TagFilterRow(
            tags = availableTags,
            selectedTag = tagFilter,
            onSelect = { tagFilter = it },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.entries.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.empty_vault_title),
                        subtitle = stringResource(R.string.empty_vault_subtitle),
                        primaryLabel = stringResource(R.string.fab_add_item),
                        onPrimary = onAddEntry,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.no_matches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { entry ->
                    VaultRow(
                        entry = entry,
                        selected = entry.id == selectedEntryId,
                        onClick = { onOpenEntry(entry.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(entry) },
                    )
                }
                item { Spacer(modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
private fun VaultRow(
    entry: VaultEntry,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    selected: Boolean = false,
) {
    val glass = MaterialTheme.glass
    val secureNote = stringResource(R.string.secure_note)
    val noUsername = stringResource(R.string.no_username)
    val identityLabel = stringResource(R.string.type_identity)
    val typeLabel = when (entry.type) {
        EntryType.LOGIN -> stringResource(R.string.filter_login)
        EntryType.NOTE -> stringResource(R.string.filter_note)
        EntryType.CARD -> stringResource(R.string.filter_card)
        EntryType.IDENTITY -> stringResource(R.string.filter_identity)
    }
    val shape = MaterialTheme.shapes.extraLarge
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            ),
        variant = GlassVariant.Tile,
        strong = selected,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandAvatar(entry = entry, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (entry.type) {
                        EntryType.NOTE -> entry.notes.take(40).ifBlank { secureNote }
                        EntryType.CARD -> entry.password.takeLast(4).let { "•••• $it" }
                        EntryType.IDENTITY -> entry.username.ifBlank { identityLabel }
                        EntryType.LOGIN -> entry.username.ifBlank { entry.url.ifBlank { noUsername } }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.tags.firstOrNull()?.let { presetTagLabel(it) } ?: typeLabel.lowercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.tag,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!entry.totpSecret.isNullOrBlank()) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = stringResource(R.string.cd_has_2fa),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (entry.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (entry.favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

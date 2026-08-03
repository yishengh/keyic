package com.yishenghuang.keyic.ui.vault

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yishenghuang.keyic.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }

    val customSelected = selected.filterNot { PresetTags.isPreset(it) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.label_tags), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(R.string.tag_picker_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetTags.all.forEach { def ->
                val selectedChip = selected.any { it.equals(def.key, ignoreCase = true) }
                FilterChip(
                    selected = selectedChip,
                    onClick = {
                        val next = selected.toMutableSet()
                        val existing = next.firstOrNull { it.equals(def.key, ignoreCase = true) }
                        if (existing != null) next.remove(existing) else next.add(def.key)
                        onSelectedChange(next)
                    },
                    label = { Text(stringResource(def.labelRes)) },
                )
            }
            customSelected.forEach { tag ->
                FilterChip(
                    selected = true,
                    onClick = { onSelectedChange(selected - tag) },
                    label = { Text(tag) },
                )
            }
            FilterChip(
                selected = false,
                onClick = {
                    customInput = ""
                    showCustomDialog = true
                },
                label = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text(stringResource(R.string.tag_add_custom))
                    }
                },
            )
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(stringResource(R.string.tag_add_custom)) },
            text = {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tag_custom_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = PresetTags.normalize(customInput)
                        if (normalized.isNotBlank()) {
                            onSelectedChange(selected + normalized)
                        }
                        showCustomDialog = false
                    },
                ) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
fun TagFilterRow(
    tags: List<String>,
    selectedTag: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedTag == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_all_tags)) },
        )
        tags.forEach { tag ->
            FilterChip(
                selected = selectedTag.equals(tag, ignoreCase = true),
                onClick = {
                    onSelect(if (selectedTag.equals(tag, ignoreCase = true)) null else tag)
                },
                label = { Text(presetTagLabel(tag)) },
            )
        }
    }
}

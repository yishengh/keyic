package com.yishenghuang.keyic.ui.vault

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.crypto.TotpGenerator
import com.yishenghuang.keyic.core.model.EntryType
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.components.KeyicBackdrop
import com.yishenghuang.keyic.ui.util.SecureClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    viewModel: VaultViewModel,
    clipboard: SecureClipboard,
    clipboardClearSeconds: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    embedded: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entry = state.entries.find { it.id == entryId }
    val attachments by viewModel.observeAttachments(entryId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showPassword by remember { mutableStateOf(false) }
    var revealedFields by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val app = context.applicationContext as KeyicApp
    val scope = rememberCoroutineScope()

    fun endPicker() = app.endExternalUi()
    fun startPicker() = app.beginExternalUi()

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        endPicker()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val (name, mime, bytes) = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    var fileName = "attachment.bin"
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cursor.moveToFirst()) {
                            fileName = cursor.getString(idx) ?: fileName
                        }
                    }
                    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                    val data = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read file")
                    Triple(fileName, mimeType, data)
                }
                viewModel.addAttachment(
                    entryId = entryId,
                    fileName = name,
                    mimeType = mime,
                    bytes = bytes,
                    onDone = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.attachment_added),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onError = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    },
                )
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Attach failed", Toast.LENGTH_LONG).show()
            }
        }
    }
    var pendingExportAttachmentId by remember { mutableStateOf<String?>(null) }
    val createExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        endPicker()
        val pendingId = pendingExportAttachmentId
        pendingExportAttachmentId = null
        if (uri == null || pendingId == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = viewModel.readAttachment(pendingId) ?: error("Decrypt failed")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.attachment_exported),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Export failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(entry?.totpSecret) {
        if (entry?.totpSecret.isNullOrBlank()) return@LaunchedEffect
        while (isActive) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    val detailBody: @Composable () -> Unit = {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(entry?.title ?: "Entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        if (entry == null) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.entry_not_found))
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = when (entry.type) {
                        EntryType.LOGIN -> stringResource(R.string.type_login)
                        EntryType.NOTE -> stringResource(R.string.type_note)
                        EntryType.CARD -> stringResource(R.string.type_card)
                        EntryType.IDENTITY -> stringResource(R.string.type_identity)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                when (entry.type) {
                    EntryType.NOTE -> {
                        Text(stringResource(R.string.label_notes), style = MaterialTheme.typography.labelLarge)
                        Text(entry.notes.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                    }
                    EntryType.CARD -> {
                        CopyField(
                            label = stringResource(R.string.label_cardholder),
                            value = entry.username.ifBlank { "—" },
                            onCopy = {
                                if (entry.username.isNotBlank()) {
                                    clipboard.copy("cardholder", entry.username, clipboardClearSeconds)
                                }
                            },
                        )
                        CopyField(
                            label = stringResource(R.string.label_card_number),
                            value = if (showPassword) {
                                entry.password
                            } else {
                                "•".repeat(entry.password.length.coerceAtLeast(12))
                            },
                            trailing = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = null,
                                    )
                                }
                            },
                            onCopy = {
                                clipboard.copy("card", entry.password, clipboardClearSeconds)
                            },
                        )
                        if (entry.cardExpiry.isNotBlank()) {
                            CopyField(
                                label = stringResource(R.string.label_expiry),
                                value = entry.cardExpiry,
                                onCopy = { clipboard.copy("expiry", entry.cardExpiry, clipboardClearSeconds) },
                            )
                        }
                        if (entry.cardCvv.isNotBlank()) {
                            CopyField(
                                label = stringResource(R.string.label_cvv),
                                value = if (showPassword) entry.cardCvv else "•••",
                                onCopy = { clipboard.copy("cvv", entry.cardCvv, clipboardClearSeconds) },
                            )
                        }
                        if (entry.notes.isNotBlank()) {
                            Text(stringResource(R.string.label_notes), style = MaterialTheme.typography.labelLarge)
                            Text(entry.notes, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    EntryType.IDENTITY -> {
                        CopyField(
                            label = stringResource(R.string.label_full_name),
                            value = entry.username.ifBlank { "—" },
                            onCopy = {
                                if (entry.username.isNotBlank()) {
                                    clipboard.copy("name", entry.username, clipboardClearSeconds)
                                }
                            },
                        )
                        CopyField(
                            label = stringResource(R.string.label_id_number),
                            value = if (showPassword) {
                                entry.password
                            } else {
                                "•".repeat(entry.password.length.coerceAtLeast(8))
                            },
                            trailing = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = null,
                                    )
                                }
                            },
                            onCopy = {
                                clipboard.copy("id", entry.password, clipboardClearSeconds)
                            },
                        )
                        if (entry.cardExpiry.isNotBlank()) {
                            CopyField(
                                label = stringResource(R.string.label_doc_expiry),
                                value = entry.cardExpiry,
                                onCopy = {
                                    clipboard.copy("expiry", entry.cardExpiry, clipboardClearSeconds)
                                },
                            )
                        }
                        if (entry.notes.isNotBlank()) {
                            Text(stringResource(R.string.label_details), style = MaterialTheme.typography.labelLarge)
                            Text(entry.notes, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    EntryType.LOGIN -> {
                        CopyField(
                            label = stringResource(R.string.label_username_email),
                            value = entry.username.ifBlank { "—" },
                            onCopy = {
                                if (entry.username.isNotBlank()) {
                                    clipboard.copy("username", entry.username, clipboardClearSeconds)
                                }
                            },
                        )
                        CopyField(
                            label = stringResource(R.string.label_password),
                            value = if (showPassword) {
                                entry.password
                            } else {
                                "•".repeat(entry.password.length.coerceAtLeast(8))
                            },
                            trailing = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = null,
                                    )
                                }
                            },
                            onCopy = {
                                clipboard.copy("password", entry.password, clipboardClearSeconds)
                            },
                        )
                        val secret = entry.totpSecret
                        if (!secret.isNullOrBlank()) {
                            TotpCard(
                                secret = secret,
                                now = now,
                                onCopy = { code -> clipboard.copy("totp", code, clipboardClearSeconds) },
                            )
                        }
                        if (entry.url.isNotBlank()) {
                            CopyField(
                                label = stringResource(R.string.label_url),
                                value = entry.url,
                                onCopy = { clipboard.copy("url", entry.url, clipboardClearSeconds) },
                            )
                        }
                        if (entry.notes.isNotBlank()) {
                            Text(stringResource(R.string.label_notes), style = MaterialTheme.typography.labelLarge)
                            Text(entry.notes, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                if (entry.customFields.isNotEmpty()) {
                    Text(stringResource(R.string.label_custom_fields), style = MaterialTheme.typography.labelLarge)
                    entry.customFields.forEach { field ->
                        val revealed = field.id in revealedFields
                        val display = when {
                            !field.masked -> field.value.ifBlank { "—" }
                            revealed -> field.value.ifBlank { "—" }
                            else -> "•".repeat(field.value.length.coerceAtLeast(6))
                        }
                        CopyField(
                            label = field.name,
                            value = display,
                            trailing = if (field.masked) {
                                {
                                    IconButton(onClick = {
                                        revealedFields = if (revealed) {
                                            revealedFields - field.id
                                        } else {
                                            revealedFields + field.id
                                        }
                                    }) {
                                        Icon(
                                            if (revealed) {
                                                Icons.Outlined.VisibilityOff
                                            } else {
                                                Icons.Outlined.Visibility
                                            },
                                            contentDescription = null,
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            onCopy = {
                                if (field.value.isNotBlank()) {
                                    clipboard.copy(field.name, field.value, clipboardClearSeconds)
                                }
                            },
                        )
                    }
                }

                Text(stringResource(R.string.label_attachments), style = MaterialTheme.typography.labelLarge)
                Text(
                    "Max 5 MB per file, 20 MB per entry. Encrypted beside the vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                attachments.forEach { att ->
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AttachFile, contentDescription = null)
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(att.fileName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    formatSize(att.sizeBytes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                pendingExportAttachmentId = att.id
                                startPicker()
                                createExport.launch(att.fileName)
                            }) { Text(stringResource(R.string.action_export)) }
                            IconButton(onClick = { viewModel.deleteAttachment(att.id) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.cd_delete_attachment),
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    startPicker()
                    pickFile.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.add_file)) }

                if (entry.tags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tags_prefix, presetTagLabels(entry.tags)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    }

    if (embedded) {
        detailBody()
    } else {
        KeyicBackdrop(modifier = Modifier.fillMaxSize()) {
            detailBody()
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.move_to_bin_title)) },
            text = {
                Text(stringResource(R.string.move_to_bin_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entryId)
                    confirmDelete = false
                    onBack()
                }) { Text(stringResource(R.string.move_to_bin)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun formatSize(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

@Composable
private fun TotpCard(
    secret: String,
    now: Long,
    onCopy: (String) -> Unit,
) {
    val code = remember(now, secret) {
        runCatching { TotpGenerator.generate(secret, now) }.getOrDefault("------")
    }
    val progress by animateFloatAsState(
        targetValue = TotpGenerator.progress(now),
        label = "totp",
    )
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        strong = true,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column {
            Text(stringResource(R.string.label_2fa_code), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = code.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onCopy(code) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy code")
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Text(
                text = "${TotpGenerator.remainingSeconds(now)}s remaining",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CopyField(
    label: String,
    value: String,
    onCopy: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            trailing?.invoke()
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
            }
        }
    }
}

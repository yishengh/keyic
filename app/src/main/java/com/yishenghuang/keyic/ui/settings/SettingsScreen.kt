package com.yishenghuang.keyic.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.backup.SafBackupWorker
import com.yishenghuang.keyic.core.csv.CsvImportParser
import com.yishenghuang.keyic.core.model.AppLanguage
import com.yishenghuang.keyic.core.model.BackupFrequency
import com.yishenghuang.keyic.core.model.ThemeMode
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.core.model.VaultEntryDraft
import com.yishenghuang.keyic.core.port.KdbxBinary
import com.yishenghuang.keyic.data.AppContainer
import com.yishenghuang.keyic.ui.biometric.BiometricUnlock
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.locale.AppLocaleController
import com.yishenghuang.keyic.ui.theme.GlassVariant
import com.yishenghuang.keyic.ui.util.UserError
import com.yishenghuang.keyic.ui.util.findFragmentActivity
import com.yishenghuang.keyic.ui.vault.VaultViewModel
import com.yishenghuang.keyic.ui.vault.VaultViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = com.yishenghuang.keyic.core.model.AppSettings(),
    )
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val app = context.applicationContext as KeyicApp
    val scope = rememberCoroutineScope()
    val vaultVm: VaultViewModel = viewModel(factory = VaultViewModelFactory(container))
    var backupPassphrase by remember { mutableStateOf("") }
    var showBackupDialog by remember { mutableStateOf<BackupMode?>(null) }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingKdbxUri by remember { mutableStateOf<Uri?>(null) }
    var showSafPassDialog by remember { mutableStateOf(false) }
    var safPassphrase by remember { mutableStateOf("") }
    var pendingTreeUri by remember { mutableStateOf<Uri?>(null) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showCsvExportConfirm by remember { mutableStateOf(false) }
    var showAboutHelp by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val activeVaultId by container.session.activeVaultId.collectAsStateWithLifecycle(initialValue = null)
    var biometricEnabled by remember(activeVaultId) { mutableStateOf(container.keyManager.isBiometricEnabled()) }
    val vaultState by vaultVm.uiState.collectAsStateWithLifecycle()
    val folderOk = remember(settings.safTreeUri) { container.isSafFolderAvailable() }

    fun endPicker() = app.endExternalUi()
    fun startPicker() = app.beginExternalUi()

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        endPicker()
        if (uri != null) {
            pendingExportUri = uri
            showBackupDialog = BackupMode.Export
        }
    }
    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        endPicker()
        if (uri != null) {
            pendingImportUri = uri
            showBackupDialog = BackupMode.Import
        }
    }
    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        endPicker()
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.error_saf_permission, Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            pendingTreeUri = uri
            showSafPassDialog = true
        }
    }
    val openCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        endPicker()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.use { com.yishenghuang.keyic.core.backup.BoundedInput.read(it, 8 * 1024 * 1024).toString(Charsets.UTF_8) }
                        ?: error("Input unavailable")
                }
                val drafts = CsvImportParser.parse(text)
                if (drafts.isEmpty()) {
                    Toast.makeText(context, resources.getString(R.string.toast_csv_empty), Toast.LENGTH_SHORT).show()
                } else {
                    vaultVm.importCsvDrafts(
                        drafts = drafts,
                        onDone = { count ->
                            Toast.makeText(
                                context,
                                resources.getString(R.string.toast_csv_imported, count),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onError = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        },
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, resources.getString(R.string.operation_failed_safe), Toast.LENGTH_LONG).show()
            }
        }
    }
    val openKdbx = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        endPicker()
        if (uri != null) {
            pendingKdbxUri = uri
            showBackupDialog = BackupMode.ImportKdbx
        }
    }
    val createKdbx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-keepass"),
    ) { uri ->
        endPicker()
        if (uri != null) {
            pendingExportUri = uri
            showBackupDialog = BackupMode.ExportKdbx
        }
    }
    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        endPicker()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val entries = container.vaultRepository.entries.first()
                val csv = CsvImportParser.export(entries)
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri, "wt") ?: error("Output unavailable")).use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(
                    context,
                    resources.getString(R.string.toast_csv_exported, entries.size),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, resources.getString(R.string.operation_failed_safe), Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showAboutHelp) {
        androidx.activity.compose.BackHandler { showAboutHelp = false }
        AboutHelpScreen(onBack = { showAboutHelp = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleLarge)
        SettingRow(
            title = stringResource(R.string.settings_biometric),
            subtitle = stringResource(R.string.settings_biometric_sub),
            checked = biometricEnabled,
            onCheckedChange = { enabled ->
                val activity = context.findFragmentActivity()
                if (enabled) {
                    if (activity == null || !BiometricUnlock.canAuthenticate(activity)) {
                        Toast.makeText(
                            context,
                            resources.getString(R.string.biometrics_unavailable),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@SettingRow
                    }
                    val dbKey = container.vaultSession.peekDbKey()
                    if (dbKey == null) {
                        Toast.makeText(
                            context,
                            resources.getString(R.string.unlock_vault_first),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@SettingRow
                    }
                    (context.applicationContext as KeyicApp).beginExternalUi()
                    BiometricUnlock.enroll(
                        activity = activity,
                        keyManager = container.keyManager,
                        dbKey = dbKey,
                        onSuccess = {
                            biometricEnabled = true
                            dbKey.fill(0)
                            (context.applicationContext as KeyicApp).endExternalUi()
                            scope.launch {
                                container.settingsRepository.update {
                                    it.copy(biometricEnabled = true)
                                }
                            }
                            Toast.makeText(
                                context,
                                resources.getString(R.string.biometric_enabled),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onError = { msg ->
                            dbKey.fill(0)
                            (context.applicationContext as KeyicApp).endExternalUi()
                            if (msg != resources.getString(R.string.error_cancelled)) {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    return@SettingRow
                } else {
                    container.keyManager.disableBiometric()
                    biometricEnabled = false
                    scope.launch {
                        container.settingsRepository.update { it.copy(biometricEnabled = false) }
                    }
                }
            },
        )
        SettingRow(
            title = stringResource(R.string.settings_lock_background),
            subtitle = stringResource(R.string.settings_lock_background_sub),
            checked = settings.lockOnBackground,
            onCheckedChange = { enabled ->
                scope.launch {
                    container.settingsRepository.update { it.copy(lockOnBackground = enabled) }
                }
            },
        )
        Text(
            stringResource(R.string.settings_auto_lock, settings.autoLockSeconds),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = settings.autoLockSeconds.toFloat(),
            onValueChange = { value ->
                scope.launch {
                    container.settingsRepository.update {
                        it.copy(autoLockSeconds = value.toInt().coerceIn(15, 600))
                    }
                }
            },
            valueRange = 15f..600f,
        )
        Text(
            stringResource(R.string.settings_clipboard_clear, settings.clipboardClearSeconds),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = settings.clipboardClearSeconds.toFloat(),
            onValueChange = { value ->
                scope.launch {
                    container.settingsRepository.update {
                        it.copy(clipboardClearSeconds = value.toInt().coerceIn(5, 120))
                    }
                }
            },
            valueRange = 5f..120f,
        )
        SettingRow(
            title = stringResource(R.string.settings_screenshots),
            subtitle = if (settings.allowScreenshots) {
                stringResource(R.string.settings_screenshots_on)
            } else {
                stringResource(R.string.settings_screenshots_off)
            },
            checked = settings.allowScreenshots,
            onCheckedChange = { enabled ->
                scope.launch {
                    container.settingsRepository.update { it.copy(allowScreenshots = enabled) }
                }
            },
        )
        FilledTonalButton(
            onClick = { showChangePassword = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.settings_change_master)) }

        HorizontalDivider()
        Text(stringResource(R.string.settings_autofill), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_autofill_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                val afm = context.getSystemService(AutofillManager::class.java)
                if (afm != null && afm.isAutofillSupported) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                } else {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.settings_autofill_unsupported),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.settings_autofill_open)) }

        HorizontalDivider()
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleLarge)
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.Tile,
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = {
                                scope.launch {
                                    container.settingsRepository.update { it.copy(themeMode = mode) }
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.language_system),
                        AppLanguage.ENGLISH to stringResource(R.string.language_english),
                        AppLanguage.CHINESE_SIMPLIFIED to stringResource(R.string.language_chinese_simplified),
                        AppLanguage.CHINESE_TRADITIONAL to stringResource(R.string.language_chinese_traditional),
                        AppLanguage.JAPANESE to stringResource(R.string.language_japanese),
                        AppLanguage.KOREAN to stringResource(R.string.language_korean),
                        AppLanguage.SPANISH to stringResource(R.string.language_spanish),
                        AppLanguage.FRENCH to stringResource(R.string.language_french),
                    ).forEach { (lang, label) ->
                        FilterChip(
                            selected = settings.appLanguage == lang,
                            onClick = {
                                scope.launch {
                                    container.settingsRepository.update { it.copy(appLanguage = lang) }
                                    // Persist settings first — applying locales may recreate the Activity.
                                    AppLocaleController.apply(lang)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        SettingRow(
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = stringResource(R.string.settings_dynamic_color_sub),
            checked = settings.dynamicColor,
            onCheckedChange = { enabled ->
                scope.launch {
                    container.settingsRepository.update { it.copy(dynamicColor = enabled) }
                }
            },
        )
        SettingRow(
            title = stringResource(R.string.settings_amoled),
            subtitle = stringResource(R.string.settings_amoled_sub),
            checked = settings.amoledBlack,
            onCheckedChange = { enabled ->
                scope.launch {
                    container.settingsRepository.update { it.copy(amoledBlack = enabled) }
                }
            },
        )
        Text(
            stringResource(R.string.settings_brand_credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_backup_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val backupStatus = when {
            settings.safTreeUri == null -> stringResource(R.string.backup_never_linked)
            !folderOk -> stringResource(R.string.backup_folder_missing)
            settings.lastSafBackupAt == null -> stringResource(R.string.backup_never)
            settings.lastSafBackupOk -> stringResource(
                R.string.backup_ok_ago,
                relativeTime(context, settings.lastSafBackupAt!!),
            )
            else -> stringResource(
                R.string.backup_failed_detail,
                stringResource(R.string.error_backup_failed),
            )
        }
        val statusColor = when {
            settings.safTreeUri == null || settings.lastSafBackupAt == null ->
                MaterialTheme.colorScheme.onSurfaceVariant
            !folderOk || !settings.lastSafBackupOk -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
        Text(
            stringResource(R.string.settings_last_backup, backupStatus),
            style = MaterialTheme.typography.labelLarge,
            color = statusColor,
        )
        Text(
            text = if (settings.safTreeUri != null) {
                if (settings.safAutoBackupEnabled) {
                    stringResource(R.string.backup_folder_linked_on)
                } else {
                    stringResource(R.string.backup_folder_linked_off)
                }
            } else {
                stringResource(R.string.backup_no_folder)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Button(
            onClick = {
                startPicker()
                openTree.launch(null)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.backup_choose_folder)) }
        if (settings.safTreeUri != null) {
            SettingRow(
                title = stringResource(R.string.backup_auto),
                subtitle = stringResource(R.string.backup_auto_sub),
                checked = settings.safAutoBackupEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !container.safBackupManager.hasPassphrase()) {
                        Toast.makeText(
                            context,
                            resources.getString(R.string.set_sync_passphrase_first),
                            Toast.LENGTH_SHORT,
                        ).show()
                        showSafPassDialog = true
                        return@SettingRow
                    }
                    scope.launch {
                        container.settingsRepository.update { it.copy(safAutoBackupEnabled = enabled) }
                        SafBackupWorker.rescheduleFromSettings(
                            context,
                            enabled,
                            settings.safBackupFrequency,
                        )
                        if (enabled) container.requestSafBackup()
                    }
                },
            )
            if (settings.safAutoBackupEnabled) {
                Text(stringResource(R.string.backup_frequency), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        BackupFrequency.HOURS_6 to stringResource(R.string.backup_freq_6h),
                        BackupFrequency.HOURS_12 to stringResource(R.string.backup_freq_12h),
                        BackupFrequency.DAILY to stringResource(R.string.backup_freq_daily),
                        BackupFrequency.DAYS_3 to stringResource(R.string.backup_freq_3d),
                        BackupFrequency.WEEKLY to stringResource(R.string.backup_freq_weekly),
                    ).forEach { (freq, label) ->
                        FilterChip(
                            selected = settings.safBackupFrequency == freq,
                            onClick = {
                                scope.launch {
                                    container.settingsRepository.update {
                                        it.copy(safBackupFrequency = freq)
                                    }
                                    SafBackupWorker.rescheduleFromSettings(context, true, freq)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = {
                    container.testSafBackup { ok, err ->
                        scope.launch {
                            Toast.makeText(
                                context,
                                resources.getString(if (ok) R.string.operation_succeeded else R.string.operation_failed_safe),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) { Text(stringResource(R.string.settings_test_backup)) }
            TextButton(
                onClick = {
                    scope.launch {
                        container.safBackupManager.clearPassphrase()
                        container.settingsRepository.update {
                            it.copy(safTreeUri = null, safAutoBackupEnabled = false)
                        }
                        SafBackupWorker.rescheduleFromSettings(
                            context,
                            false,
                            settings.safBackupFrequency,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_unlink_folder)) }
        }
        Button(
            onClick = {
                startPicker()
                createDoc.launch("keyic-backup.keyic")
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.settings_export_backup)) }
        FilledTonalButton(
            onClick = {
                startPicker()
                openDoc.launch(arrayOf("application/octet-stream", "*/*"))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.settings_restore_backup)) }

        HorizontalDivider()
        Text(stringResource(R.string.settings_import_export), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_import_export_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                startPicker()
                openCsv.launch(arrayOf("text/*", "text/csv", "*/*"))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.settings_import_csv)) }
        FilledTonalButton(
            onClick = { showCsvExportConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.settings_export_csv)) }
        Button(
            onClick = {
                startPicker()
                openKdbx.launch(arrayOf("application/octet-stream", "*/*"))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.settings_import_kdbx)) }
        FilledTonalButton(
            onClick = {
                startPicker()
                createKdbx.launch("keyic-export.kdbx")
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text(stringResource(R.string.settings_export_kdbx)) }

        HorizontalDivider()
        Text(stringResource(R.string.settings_recycle_bin), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_recycle_bin_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.settings_recycle_purge, settings.recycleBinRetentionDays),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = settings.recycleBinRetentionDays.toFloat(),
            onValueChange = { value ->
                scope.launch {
                    container.settingsRepository.update {
                        it.copy(recycleBinRetentionDays = value.toInt().coerceIn(0, 365))
                    }
                }
            },
            valueRange = 0f..90f,
        )
        if (vaultState.deletedEntries.isEmpty()) {
            Text(
                stringResource(R.string.settings_recycle_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            vaultState.deletedEntries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            entry.username.ifBlank { entry.type.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vaultVm.restore(entry.id) }) {
                        Text(stringResource(R.string.action_restore))
                    }
                    TextButton(onClick = { vaultVm.purge(entry.id) }) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_help), style = MaterialTheme.typography.titleLarge)
        TextButton(
            onClick = { showAboutHelp = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_about_help)) }
        TextButton(
            onClick = {
                scope.launch {
                    container.settingsRepository.update { it.copy(onboardingDone = false) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_replay_onboarding)) }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = { scope.launch { container.vaultSession.lock() } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_lock_vault_now)) }
    }

    if (showSafPassDialog) {
        AlertDialog(
            onDismissRequest = {
                showSafPassDialog = false
                safPassphrase = ""
                pendingTreeUri = null
            },
            title = { Text(stringResource(R.string.sync_passphrase_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.sync_passphrase_body))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = safPassphrase,
                        onValueChange = { safPassphrase = it },
                        label = { Text(stringResource(R.string.label_passphrase)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingTreeUri
                    val pass = safPassphrase
                    showSafPassDialog = false
                    safPassphrase = ""
                    pendingTreeUri = null
                    if (uri == null || pass.length < 6) {
                        Toast.makeText(
                            context,
                            resources.getString(R.string.passphrase_min_6),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@TextButton
                    }
                    container.safBackupManager.savePassphrase(pass.toCharArray())
                    scope.launch {
                        container.settingsRepository.update {
                            it.copy(safTreeUri = uri.toString(), safAutoBackupEnabled = true)
                        }
                        SafBackupWorker.rescheduleFromSettings(
                            context,
                            true,
                            settings.safBackupFrequency,
                        )
                        container.requestSafBackup()
                        Toast.makeText(
                            context,
                            resources.getString(R.string.sync_folder_linked),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSafPassDialog = false
                    safPassphrase = ""
                    pendingTreeUri = null
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showCsvExportConfirm) {
        AlertDialog(
            onDismissRequest = { showCsvExportConfirm = false },
            title = { Text(stringResource(R.string.csv_export_confirm_title)) },
            text = { Text(stringResource(R.string.csv_export_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showCsvExportConfirm = false
                    startPicker()
                    createCsv.launch("keyic-export.csv")
                }) { Text(stringResource(R.string.csv_export_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showCsvExportConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showChangePassword) {
        AlertDialog(
            onDismissRequest = {
                showChangePassword = false
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
            },
            title = { Text(stringResource(R.string.settings_change_master)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_change_master_body))
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text(stringResource(R.string.settings_current_password)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.settings_new_password)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.settings_confirm_new_password)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val current = currentPassword
                    val next = newPassword
                    val confirm = confirmPassword
                    showChangePassword = false
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                    scope.launch {
                        if (next.length < 8) {
                            Toast.makeText(
                                context,
                                resources.getString(R.string.error_new_password_short),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@launch
                        }
                        if (next != confirm) {
                            Toast.makeText(
                                context,
                                resources.getString(R.string.error_passwords_mismatch),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@launch
                        }
                        val ok = try {
                            withContext(Dispatchers.IO) {
                                container.changeMasterPassword(current.toCharArray(), next.toCharArray())
                            }
                        } catch (_: Exception) { false }
                        Toast.makeText(
                            context,
                            resources.getString(if (ok) R.string.password_updated else R.string.operation_failed_safe),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text(stringResource(R.string.settings_update_password)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChangePassword = false
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    val mode = showBackupDialog
    if (mode != null) {
        AlertDialog(
            onDismissRequest = {
                showBackupDialog = null
                backupPassphrase = ""
            },
            title = {
                Text(
                    when (mode) {
                        BackupMode.Export -> stringResource(R.string.backup_passphrase_title)
                        BackupMode.Import -> stringResource(R.string.restore_passphrase_title)
                        BackupMode.ImportKdbx -> stringResource(R.string.keepass_passphrase_title)
                        BackupMode.ExportKdbx -> stringResource(R.string.keepass_passphrase_title)
                    },
                )
            },
            text = {
                Column {
                    Text(
                        when (mode) {
                            BackupMode.Export -> stringResource(R.string.backup_passphrase_help)
                            BackupMode.Import -> stringResource(R.string.restore_replace_warning)
                            BackupMode.ImportKdbx, BackupMode.ExportKdbx ->
                                stringResource(R.string.keepass_passphrase_help)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text(stringResource(R.string.label_passphrase)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = backupPassphrase.isNotEmpty() &&
                    (mode == BackupMode.Import || mode == BackupMode.ImportKdbx || backupPassphrase.length >= 8), onClick = {
                    val passphrase = backupPassphrase
                    showBackupDialog = null
                    backupPassphrase = ""
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                when (mode) {
                                    BackupMode.Export, BackupMode.ExportKdbx -> {
                                        val uri = checkNotNull(pendingExportUri)
                                        val bytes = container.exportPortable(passphrase.toCharArray(), mode == BackupMode.ExportKdbx)
                                        try {
                                            val output = context.contentResolver.openOutputStream(uri, "wt")
                                                ?: error("Output unavailable")
                                            output.use { it.write(bytes); it.flush() }
                                        } finally { bytes.fill(0) }
                                    }
                                    BackupMode.Import, BackupMode.ImportKdbx -> {
                                        val uri = checkNotNull(if (mode == BackupMode.Import) pendingImportUri else pendingKdbxUri)
                                        val bytes = (context.contentResolver.openInputStream(uri)
                                            ?: error("Input unavailable")).use {
                                            com.yishenghuang.keyic.core.backup.BoundedInput.read(it)
                                        }
                                        try {
                                            container.importPortable(bytes, passphrase.toCharArray(), mode == BackupMode.ImportKdbx)
                                        } finally { bytes.fill(0) }
                                    }
                                }
                            }
                            Toast.makeText(context, resources.getString(R.string.operation_succeeded), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                resources.getString(R.string.operation_failed_safe),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackupDialog = null
                    backupPassphrase = ""
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private enum class BackupMode { Export, Import, ImportKdbx, ExportKdbx }

private fun relativeTime(context: android.content.Context, epochMs: Long): String {
    val resources = context.resources
    val delta = System.currentTimeMillis() - epochMs
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    return when {
        minutes < 1 -> resources.getString(R.string.relative_just_now)
        minutes < 60 -> resources.getString(R.string.relative_minutes, minutes)
        minutes < 60 * 24 -> resources.getString(R.string.relative_hours, minutes / 60)
        else -> resources.getString(R.string.relative_days, minutes / (60 * 24))
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        variant = GlassVariant.Tile,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = title })
        }
    }
}

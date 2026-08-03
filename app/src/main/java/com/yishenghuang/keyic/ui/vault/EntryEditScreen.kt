package com.yishenghuang.keyic.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.crypto.PasswordGenerator
import com.yishenghuang.keyic.core.crypto.PasswordStrength
import com.yishenghuang.keyic.core.model.CustomField
import com.yishenghuang.keyic.core.model.EntryType
import com.yishenghuang.keyic.core.model.PasswordGeneratorOptions
import com.yishenghuang.keyic.core.model.VaultEntryDraft
import java.util.UUID
import com.yishenghuang.keyic.scan.ScanCaptureActivity
import com.yishenghuang.keyic.ui.adaptive.AdaptiveContentWidth
import com.yishenghuang.keyic.ui.brand.BrandIconPickerSheet
import com.yishenghuang.keyic.ui.brand.BrandIconResolver
import com.yishenghuang.keyic.ui.brand.BrandStyleAvatar
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.components.KeyicBackdrop
import com.yishenghuang.keyic.ui.theme.Success
import com.yishenghuang.keyic.ui.theme.GlassVariant
import com.yishenghuang.keyic.ui.util.passwordStrengthLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    entryId: String?,
    viewModel: VaultViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    var type by remember { mutableStateOf(EntryType.LOGIN) }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favorite by remember { mutableStateOf(false) }
    var packageHints by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvv by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf<String?>(null) }
    var customFields by remember { mutableStateOf<List<CustomField>>(emptyList()) }
    var showIconPicker by remember { mutableStateOf(false) }
    var previousPassword by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as KeyicApp

    val totpScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        app.endExternalUi()
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val secret = data.getStringExtra(ScanCaptureActivity.EXTRA_TOTP_SECRET).orEmpty()
        if (secret.isBlank()) return@rememberLauncherForActivityResult
        totpSecret = secret
        val issuer = data.getStringExtra(ScanCaptureActivity.EXTRA_TOTP_ISSUER)
        val account = data.getStringExtra(ScanCaptureActivity.EXTRA_TOTP_ACCOUNT)
        if (title.isBlank() && !issuer.isNullOrBlank()) title = issuer
        if (username.isBlank() && !account.isNullOrBlank()) username = account
        Toast.makeText(context, context.getString(R.string.scan_totp_filled), Toast.LENGTH_SHORT).show()
    }
    val cardScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        app.endExternalUi()
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val number = data.getStringExtra(ScanCaptureActivity.EXTRA_CARD_NUMBER).orEmpty()
        if (number.isNotBlank()) password = number
        data.getStringExtra(ScanCaptureActivity.EXTRA_CARD_EXPIRY)?.takeIf { it.isNotBlank() }?.let {
            cardExpiry = it
        }
        data.getStringExtra(ScanCaptureActivity.EXTRA_CARD_HOLDER)?.takeIf { it.isNotBlank() }?.let {
            username = it
        }
        data.getStringExtra(ScanCaptureActivity.EXTRA_CARD_CVV)?.takeIf { it.isNotBlank() }?.let {
            cardCvv = it
        }
        Toast.makeText(context, context.getString(R.string.scan_card_filled), Toast.LENGTH_SHORT).show()
    }
    val wifiScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        app.endExternalUi()
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val ssid = data.getStringExtra(ScanCaptureActivity.EXTRA_WIFI_SSID).orEmpty()
        if (ssid.isBlank()) return@rememberLauncherForActivityResult
        val wifiPassword = data.getStringExtra(ScanCaptureActivity.EXTRA_WIFI_PASSWORD).orEmpty()
        val security = data.getStringExtra(ScanCaptureActivity.EXTRA_WIFI_SECURITY).orEmpty()
        val hidden = data.getBooleanExtra(ScanCaptureActivity.EXTRA_WIFI_HIDDEN, false)
        if (title.isBlank()) title = ssid
        if (type == EntryType.NOTE) {
            val block = buildList {
                add(context.getString(R.string.wifi_ssid_line, ssid))
                if (wifiPassword.isNotBlank()) {
                    add(context.getString(R.string.wifi_password_line, wifiPassword))
                }
                if (security.isNotBlank()) add(context.getString(R.string.wifi_security_line, security))
                if (hidden) add(context.getString(R.string.wifi_hidden_line))
            }.joinToString("\n")
            notes = if (notes.isBlank()) block else "$notes\n$block"
        } else {
            if (username.isBlank()) username = ssid
            if (wifiPassword.isNotBlank()) password = wifiPassword
            val detailLines = buildList {
                if (security.isNotBlank()) add(context.getString(R.string.wifi_security_line, security))
                if (hidden) add(context.getString(R.string.wifi_hidden_line))
            }
            if (detailLines.isNotEmpty()) {
                val block = detailLines.joinToString("\n")
                notes = if (notes.isBlank()) block else "$notes\n$block"
            }
        }
        tags = tags + "wifi"
        Toast.makeText(context, context.getString(R.string.scan_wifi_filled), Toast.LENGTH_SHORT).show()
    }
    val idScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        app.endExternalUi()
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        data.getStringExtra(ScanCaptureActivity.EXTRA_ID_NAME)?.takeIf { it.isNotBlank() }?.let {
            username = it
        }
        data.getStringExtra(ScanCaptureActivity.EXTRA_ID_NUMBER)?.takeIf { it.isNotBlank() }?.let {
            password = it
        }
        data.getStringExtra(ScanCaptureActivity.EXTRA_ID_EXPIRY)?.takeIf { it.isNotBlank() }?.let {
            cardExpiry = it
        }
        data.getStringExtra(ScanCaptureActivity.EXTRA_ID_DETAILS)?.takeIf { it.isNotBlank() }?.let { detail ->
            notes = if (notes.isBlank()) detail else "$notes\n$detail"
        }
        data.getStringExtra(ScanCaptureActivity.EXTRA_ID_TITLE)?.takeIf { it.isNotBlank() }?.let { suggested ->
            if (title.isBlank()) title = suggested
        }
        Toast.makeText(context, context.getString(R.string.scan_id_filled), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(entryId) {
        if (entryId != null) {
            val existing = viewModel.getEntry(entryId)
            if (existing != null) {
                type = existing.type
                title = existing.title
                username = existing.username
                password = existing.password
                previousPassword = existing.password
                url = existing.url
                totpSecret = existing.totpSecret.orEmpty()
                notes = existing.notes
                tags = existing.tags.map { PresetTags.normalize(it) }.toSet()
                favorite = existing.favorite
                packageHints = existing.packageHints.joinToString(", ")
                cardExpiry = existing.cardExpiry
                cardCvv = existing.cardCvv
                iconKey = existing.iconKey
                customFields = existing.customFields
            }
        }
        loaded = true
    }

    fun draft() = VaultEntryDraft(
        title = title,
        type = type,
        username = username,
        password = password,
        url = url,
        packageHints = packageHints.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        totpSecret = totpSecret.ifBlank { null },
        notes = notes,
        tags = tags.toList(),
        favorite = favorite,
        cardExpiry = cardExpiry,
        cardCvv = cardCvv,
        iconKey = iconKey,
        customFields = customFields.filter { it.name.isNotBlank() },
    )

    val previewStyle = remember(title, url, type, iconKey) {
        BrandIconResolver.preview(title = title, url = url, type = type, iconKey = iconKey)
    }

    KeyicBackdrop(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (entryId == null) {
                                stringResource(R.string.entry_add)
                            } else {
                                stringResource(R.string.entry_edit)
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            viewModel.save(draft(), entryId, previousPassword)
                            onDone()
                        }) { Text(stringResource(R.string.action_save)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            },
        ) { padding ->
            if (!loaded) return@Scaffold
            AdaptiveContentWidth(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.Tile,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.label_type), style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EntryType.entries.forEach { t ->
                                FilterChip(
                                    selected = type == t,
                                    onClick = { type = t },
                                    label = {
                                        Text(
                                            when (t) {
                                                EntryType.LOGIN -> stringResource(R.string.type_login)
                                                EntryType.NOTE -> stringResource(R.string.type_note)
                                                EntryType.CARD -> stringResource(R.string.type_card)
                                                EntryType.IDENTITY -> stringResource(R.string.type_identity)
                                            },
                                        )
                                    },
                                )
                            }
                        }

                        Text(stringResource(R.string.label_icon), style = MaterialTheme.typography.labelLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showIconPicker = true }
                                .padding(vertical = 4.dp),
                        ) {
                            BrandStyleAvatar(
                                style = previewStyle,
                                size = 56.dp,
                                onClick = { showIconPicker = true },
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = iconKey?.replace('-', ' ') ?: stringResource(R.string.icon_auto),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = if (iconKey == null) {
                                        stringResource(R.string.icon_auto_hint)
                                    } else {
                                        stringResource(R.string.icon_custom_hint)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { showIconPicker = true }) {
                                Text(stringResource(R.string.action_change))
                            }
                        }
                    }
                }

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.Tile,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = {
                                Text(
                                    if (type == EntryType.LOGIN) {
                                        stringResource(R.string.label_nickname)
                                    } else {
                                        stringResource(R.string.label_title)
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )

                        when (type) {
                            EntryType.LOGIN -> {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text(stringResource(R.string.label_username_email)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                PasswordField(password = password, onChange = { password = it })
                                OutlinedTextField(
                                    value = url,
                                    onValueChange = { url = it },
                                    label = { Text(stringResource(R.string.label_url)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = packageHints,
                                    onValueChange = { packageHints = it },
                                    label = { Text(stringResource(R.string.label_package_hints)) },
                                    supportingText = { Text(stringResource(R.string.label_package_hints_support)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = totpSecret,
                                    onValueChange = { totpSecret = it },
                                    label = { Text(stringResource(R.string.label_totp)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                FilledTonalButton(
                                    onClick = {
                                        app.beginExternalUi()
                                        totpScanLauncher.launch(
                                            ScanCaptureActivity.intent(
                                                context,
                                                ScanCaptureActivity.MODE_TOTP_QR,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.scan_totp_qr))
                                }
                                FilledTonalButton(
                                    onClick = {
                                        app.beginExternalUi()
                                        wifiScanLauncher.launch(
                                            ScanCaptureActivity.intent(
                                                context,
                                                ScanCaptureActivity.MODE_WIFI_QR,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Icon(Icons.Outlined.Wifi, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.scan_wifi_qr))
                                }
                            }
                            EntryType.NOTE -> {
                                FilledTonalButton(
                                    onClick = {
                                        app.beginExternalUi()
                                        wifiScanLauncher.launch(
                                            ScanCaptureActivity.intent(
                                                context,
                                                ScanCaptureActivity.MODE_WIFI_QR,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Icon(Icons.Outlined.Wifi, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.scan_wifi_qr))
                                }
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text(stringResource(R.string.label_secure_note)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                            EntryType.CARD -> {
                                FilledTonalButton(
                                    onClick = {
                                        app.beginExternalUi()
                                        cardScanLauncher.launch(
                                            ScanCaptureActivity.intent(
                                                context,
                                                ScanCaptureActivity.MODE_CARD_OCR,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Icon(Icons.Outlined.CreditCard, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.scan_card))
                                }
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text(stringResource(R.string.label_cardholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text(stringResource(R.string.label_card_number)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = cardExpiry,
                                    onValueChange = { cardExpiry = it },
                                    label = { Text(stringResource(R.string.label_expiry)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = cardCvv,
                                    onValueChange = { cardCvv = it },
                                    label = { Text(stringResource(R.string.label_cvv)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                            EntryType.IDENTITY -> {
                                FilledTonalButton(
                                    onClick = {
                                        app.beginExternalUi()
                                        idScanLauncher.launch(
                                            ScanCaptureActivity.intent(
                                                context,
                                                ScanCaptureActivity.MODE_PASSPORT_OCR,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Icon(Icons.Outlined.TravelExplore, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.scan_passport))
                                }
                                FilledTonalButton(
                                    onClick = {
                                        app.beginExternalUi()
                                        idScanLauncher.launch(
                                            ScanCaptureActivity.intent(
                                                context,
                                                ScanCaptureActivity.MODE_LICENSE_OCR,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Icon(Icons.Outlined.Badge, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.scan_license))
                                }
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text(stringResource(R.string.label_full_name)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text(stringResource(R.string.label_id_number)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = cardExpiry,
                                    onValueChange = { cardExpiry = it },
                                    label = { Text(stringResource(R.string.label_doc_expiry)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text(stringResource(R.string.label_details)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                        }

                        TagPicker(
                            selected = tags,
                            onSelectedChange = { tags = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (type == EntryType.LOGIN || type == EntryType.CARD) {
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text(stringResource(R.string.label_notes)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = favorite, onCheckedChange = { favorite = it })
                            Text(stringResource(R.string.label_favorite))
                        }
                    }
                }

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.Tile,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.label_custom_fields), style = MaterialTheme.typography.labelLarge)
                        customFields.forEachIndexed { index, field ->
                            OutlinedTextField(
                                value = field.name,
                                onValueChange = { name ->
                                    customFields = customFields.toMutableList().also {
                                        it[index] = field.copy(name = name)
                                    }
                                },
                                label = { Text(stringResource(R.string.label_field_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )
                            OutlinedTextField(
                                value = field.value,
                                onValueChange = { value ->
                                    customFields = customFields.toMutableList().also {
                                        it[index] = field.copy(value = value)
                                    }
                                },
                                label = { Text(stringResource(R.string.label_value)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = field.masked,
                                    onCheckedChange = { masked ->
                                        customFields = customFields.toMutableList().also {
                                            it[index] = field.copy(masked = masked)
                                        }
                                    },
                                )
                                Text(stringResource(R.string.label_masked), modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    customFields = customFields.toMutableList().also { it.removeAt(index) }
                                }) { Text(stringResource(R.string.action_remove)) }
                            }
                        }
                        TextButton(onClick = {
                            customFields = customFields + CustomField(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                value = "",
                                masked = false,
                            )
                        }) { Text(stringResource(R.string.add_field)) }
                    }
                }

                Button(
                    onClick = {
                        viewModel.save(draft(), entryId, previousPassword)
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) { Text(stringResource(R.string.save_entry)) }
            }
            }
        }
    }

    if (showIconPicker) {
        BrandIconPickerSheet(
            selectedIconKey = iconKey,
            onSelect = { iconKey = it },
            onDismiss = { showIconPicker = false },
        )
    }
}

@Composable
private fun PasswordField(password: String, onChange: (String) -> Unit) {
    val score = PasswordStrength.score(password)
    OutlinedTextField(
        value = password,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.label_password)) },
        supportingText = {
            Text(
                stringResource(
                    R.string.password_strength,
                    passwordStrengthLabel(password),
                ),
            )
        },
        trailingIcon = {
            IconButton(onClick = {
                onChange(PasswordGenerator.generate(PasswordGeneratorOptions()))
            }) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.cd_generate),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
    LinearProgressIndicator(
        progress = { score / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = if (score >= 55) Success else MaterialTheme.colorScheme.primary,
    )
    TextButton(onClick = {
        onChange(PasswordGenerator.generate(PasswordGeneratorOptions()))
    }) { Text(stringResource(R.string.generate_password)) }
}

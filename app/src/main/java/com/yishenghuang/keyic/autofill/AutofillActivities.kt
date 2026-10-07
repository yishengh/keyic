package com.yishenghuang.keyic.autofill

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yishenghuang.keyic.data.db.toDomain
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.crypto.TotpGenerator
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.components.KeyicBackdrop
import com.yishenghuang.keyic.ui.lock.LockScreen
import com.yishenghuang.keyic.ui.lock.LockViewModel
import com.yishenghuang.keyic.ui.lock.LockViewModelFactory
import com.yishenghuang.keyic.ui.theme.KeyicTheme
import com.yishenghuang.keyic.ui.util.SecureClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AutofillUnlockActivity : SecureAutofillActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val parsed = intent.readParsedFields()
        val app = application as KeyicApp

        setContent {
            KeyicTheme {
                val vm: LockViewModel = viewModel(factory = LockViewModelFactory(application, app.container))
                val unlocked by app.container.session.isUnlocked.collectAsStateWithLifecycle(
                    initialValue = false,
                )
                LaunchedEffect(unlocked) {
                    if (unlocked) {
                        finishWithDatasets(app, parsed)
                    }
                }
                if (!unlocked) {
                    LockScreen(viewModel = vm)
                }
            }
        }
    }

    private fun finishWithDatasets(app: KeyicApp, parsed: ParsedAutofillFields) {
        try {
            val entries = kotlinx.coroutines.runBlocking {
                val all = app.container.vaultRepository.entries.first()
                app.container.autofillMatcher.match(
                    packageName = parsed.packageName,
                    webDomain = parsed.webDomain,
                    entries = all,
                    preferCards = parsed.hasCardFields && !parsed.hasLoginFields,
                )
            }
            if (entries.isEmpty()) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return
            }
            val builder = FillResponse.Builder()
            entries.forEach { entry ->
                builder.addDataset(authDatasetForEntry(entry, parsed))
            }
            val reply = Intent().putExtra(
                AutofillManager.EXTRA_AUTHENTICATION_RESULT,
                builder.build(),
            )
            setResult(Activity.RESULT_OK, reply)
            finish()
        } catch (_: Exception) {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun authDatasetForEntry(
        entry: VaultEntry,
        parsed: ParsedAutofillFields,
    ): Dataset {
        val label = buildString {
            append(entry.title)
            if (entry.username.isNotBlank()) append(" — ").append(entry.username)
            if (!entry.totpSecret.isNullOrBlank()) append(" · 2FA")
        }
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, label)
        }
        val intent = Intent(this, AutofillFillActivity::class.java).apply {
            putExtra(KeyicAutofillService.EXTRA_ENTRY_ID, entry.id)
            putExtra(KeyicAutofillService.EXTRA_VAULT_ID, com.yishenghuang.keyic.data.db.VaultDatabaseFactory.currentVaultId())
            putParsedExtras(parsed)
        }
        val pending = PendingIntent.getActivity(
            this,
            entry.id.hashCode(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0,
        )
        return Dataset.Builder(presentation)
            .setAuthentication(pending.intentSender)
            .apply {
                parsed.usernameId?.let { setValue(it, null) }
                parsed.passwordId?.let { setValue(it, null) }
                parsed.cardNumberId?.let { setValue(it, null) }
                parsed.cardExpiryId?.let { setValue(it, null) }
                parsed.cardCvvId?.let { setValue(it, null) }
                parsed.cardHolderId?.let { setValue(it, null) }
            }
            .build()
    }
}

class AutofillFillActivity : SecureAutofillActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryId = intent.getStringExtra(KeyicAutofillService.EXTRA_ENTRY_ID)
        val vaultId = intent.getStringExtra(KeyicAutofillService.EXTRA_VAULT_ID)
        val parsed = intent.readParsedFields()
        val app = application as KeyicApp
        if (entryId == null || vaultId == null) { finish(); return }
        setContent {
            KeyicTheme {
                val scope = rememberCoroutineScope()
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(stringResource(R.string.autofill_confirm_target)) },
                    text = { Text(stringResource(R.string.autofill_target_warning,
                        getString(R.string.app_name), parsed.packageName.orEmpty(), parsed.webDomain ?: "—")) },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    val settings = app.container.settingsRepository.get()
                                    check(!app.container.session.shouldAutoLock(android.os.SystemClock.elapsedRealtime(), settings.autoLockSeconds))
                                    app.container.session.withUnlockedVault(vaultId) { db, _, _ ->
                                        val entry = db.vaultDao().getById(entryId)?.let { it.toDomain() }
                                            ?: error("Entry unavailable")
                                        check(app.container.autofillMatcher.match(parsed.packageName, parsed.webDomain,
                                            listOf(entry), parsed.hasCardFields && !parsed.hasLoginFields).isNotEmpty())
                                        val dataset = KeyicAutofillService.filledDataset(packageName, entry, parsed)
                                        if (!entry.totpSecret.isNullOrBlank()) {
                                            runCatching {
                                                val code = TotpGenerator.generate(entry.totpSecret!!)
                                                app.secureClipboard.copy("totp", code, settings.clipboardClearSeconds)
                                            }
                                        }
                                        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
                                    }
                                } catch (_: Exception) {
                                    setResult(Activity.RESULT_CANCELED)
                                    Toast.makeText(this@AutofillFillActivity, R.string.autofill_entry_unavailable, Toast.LENGTH_LONG).show()
                                }
                                finish()
                            }
                        }) { Text(stringResource(R.string.action_continue)) }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.action_cancel)) } },
                )
            }
        }
    }
}

/** Pick another vault, then unlock and rematch for autofill. */
class AutofillVaultPickActivity : SecureAutofillActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val parsed = intent.readParsedFields()
        val app = application as KeyicApp
        setContent {
            KeyicTheme {
                val vaults by app.container.vaultRegistry.vaults.collectAsStateWithLifecycle(
                    initialValue = emptyList(),
                )
                val scope = rememberCoroutineScope()
                KeyicBackdrop(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.autofill_pick_vault_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            stringResource(R.string.autofill_pick_vault_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(vaults, key = { it.id }) { vault ->
                                GlassSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        scope.launch {
                                            app.container.session.switchVault(vault.id)
                                            val unlock = Intent(
                                                this@AutofillVaultPickActivity,
                                                AutofillUnlockActivity::class.java,
                                            ).apply {
                                                putParsedExtras(parsed)
                                                addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                                            }
                                            startActivity(unlock)
                                            finish()
                                        }
                                    },
                                    contentPadding = PaddingValues(16.dp),
                                ) {
                                    Text(vault.name, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableAutofillId(key: String): AutofillId? {
    return if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, AutofillId::class.java)
    } else {
        getParcelableExtra(key)
    }
}

private fun Intent.readParsedFields(): ParsedAutofillFields = ParsedAutofillFields(
    usernameId = parcelableAutofillId(KeyicAutofillService.EXTRA_USERNAME_ID),
    passwordId = parcelableAutofillId(KeyicAutofillService.EXTRA_PASSWORD_ID),
    cardNumberId = parcelableAutofillId(KeyicAutofillService.EXTRA_CARD_NUMBER_ID),
    cardExpiryId = parcelableAutofillId(KeyicAutofillService.EXTRA_CARD_EXPIRY_ID),
    cardCvvId = parcelableAutofillId(KeyicAutofillService.EXTRA_CARD_CVV_ID),
    cardHolderId = parcelableAutofillId(KeyicAutofillService.EXTRA_CARD_HOLDER_ID),
    packageName = getStringExtra(KeyicAutofillService.EXTRA_PACKAGE),
    webDomain = getStringExtra(KeyicAutofillService.EXTRA_DOMAIN),
)

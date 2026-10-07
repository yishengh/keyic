package com.yishenghuang.keyic.autofill

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.model.VaultEntryDraft
import com.yishenghuang.keyic.ui.lock.LockScreen
import com.yishenghuang.keyic.ui.lock.LockViewModel
import com.yishenghuang.keyic.ui.lock.LockViewModelFactory
import com.yishenghuang.keyic.ui.theme.KeyicTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class AutofillSaveActivity : SecureAutofillActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val username = intent.getStringExtra(KeyicAutofillService.EXTRA_SAVE_USERNAME).orEmpty()
        val password = intent.getStringExtra(KeyicAutofillService.EXTRA_SAVE_PASSWORD).orEmpty()
        val pkg = intent.getStringExtra(KeyicAutofillService.EXTRA_PACKAGE)
        val domain = intent.getStringExtra(KeyicAutofillService.EXTRA_DOMAIN)
        val app = application as KeyicApp
        val defaultTitle = domain?.removePrefix("www.")
            ?: pkg?.substringAfterLast('.')
            ?: getString(R.string.autofill_saved_title)

        setContent {
            KeyicTheme {
                val unlocked by app.container.session.isUnlocked.collectAsStateWithLifecycle(
                    initialValue = false,
                )
                if (!unlocked) {
                    val vm: LockViewModel = viewModel(
                        factory = LockViewModelFactory(application, app.container),
                    )
                    LockScreen(viewModel = vm)
                } else {
                    var title by remember { mutableStateOf(defaultTitle) }
                    var user by remember { mutableStateOf(username) }
                    var pass by remember { mutableStateOf(password) }
                    val scope = rememberCoroutineScope()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = getString(R.string.autofill_save_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = getString(R.string.autofill_save_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(getString(R.string.label_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text(getString(R.string.label_username_email)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                        OutlinedTextField(
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text(getString(R.string.label_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val hints = linkedSetOf<String>()
                                    pkg?.takeIf { it.isNotBlank() }?.let { hints += it }
                                    domain?.removePrefix("www.")?.takeIf { it.isNotBlank() }
                                        ?.let { hints += it }
                                    val url = domain?.let { d ->
                                        val host = d.removePrefix("www.")
                                        if (host.startsWith("http")) host else "https://$host"
                                    }.orEmpty()
                                    try {
                                        app.container.session.withUnlockedVault { _, _, _ ->
                                            app.container.vaultRepository.create(VaultEntryDraft(
                                                title = title.ifBlank { defaultTitle }, username = user,
                                                password = pass, url = url, packageHints = hints.toList()))
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(this@AutofillSaveActivity, R.string.operation_failed_safe, Toast.LENGTH_LONG).show()
                                        return@launch
                                    }
                                    app.container.requestSafBackup()
                                    Toast.makeText(
                                        this@AutofillSaveActivity,
                                        getString(R.string.autofill_saved),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    finish()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.large,
                        ) { Text(getString(R.string.autofill_save_confirm)) }
                        TextButton(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(getString(R.string.autofill_save_cancel)) }
                    }
                }
            }
        }
    }
}

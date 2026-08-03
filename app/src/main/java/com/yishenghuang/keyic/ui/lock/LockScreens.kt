package com.yishenghuang.keyic.ui.lock

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.ui.biometric.BiometricUnlock
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.components.KeyicBackdrop
import com.yishenghuang.keyic.ui.theme.GlassVariant
import com.yishenghuang.keyic.ui.util.findFragmentActivity
import com.yishenghuang.keyic.ui.util.passwordStrengthLabel

@Composable
fun SetupScreen(viewModel: LockViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    val strength = passwordStrengthLabel(state.password)

    AuthScaffold(
        title = stringResource(R.string.setup_title),
        subtitle = stringResource(R.string.setup_subtitle),
    ) {
        OutlinedTextField(
            value = state.vaultName,
            onValueChange = viewModel::onVaultNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_vault_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_master_password)) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Text(
            text = stringResource(R.string.password_strength, strength),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_confirm_password)) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.setup() }),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        LockErrorText(errorRes = state.errorRes, errorDetail = state.errorDetail)
        Button(
            onClick = viewModel::setup,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.create_vault))
            }
        }
    }
}

@Composable
fun LockScreen(viewModel: LockViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    var showVaultSwitcher by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as KeyicApp
    val settings by app.container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = com.yishenghuang.keyic.core.model.AppSettings())
    val vaults by app.container.vaultRegistry.vaults.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by app.container.vaultRegistry.activeVaultId.collectAsStateWithLifecycle(initialValue = null)
    val activeName = vaults.find { it.id == activeId }?.name
        ?: stringResource(R.string.vault_default_name)
    val bioEnabled = settings.biometricEnabled && app.container.keyManager.isBiometricEnabled()
    val activity = context.findFragmentActivity()

    LaunchedEffect(bioEnabled, activeId) {
        if (bioEnabled && activity != null) {
            app.beginExternalUi()
            BiometricUnlock.prompt(
                activity = activity,
                keyManager = app.container.keyManager,
                onSuccess = {
                    app.endExternalUi()
                    viewModel.unlockWithDbKey(it)
                },
                onError = { app.endExternalUi() },
            )
        }
    }

    AuthScaffold(
        title = stringResource(R.string.welcome_back),
        subtitle = stringResource(R.string.unlock_vault_subtitle, activeName),
    ) {
        if (vaults.size > 1) {
            FilledTonalButton(
                onClick = { showVaultSwitcher = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) { Text(stringResource(R.string.switch_vault, activeName)) }
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_master_password)) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.unlock() }),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        LockErrorText(errorRes = state.errorRes, errorDetail = state.errorDetail)
        Button(
            onClick = viewModel::unlock,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.action_unlock))
            }
        }
        if (bioEnabled && activity != null) {
            FilledTonalButton(
                onClick = {
                    app.beginExternalUi()
                    BiometricUnlock.prompt(
                        activity = activity,
                        keyManager = app.container.keyManager,
                        onSuccess = {
                            app.endExternalUi()
                            viewModel.unlockWithDbKey(it)
                        },
                        onError = { app.endExternalUi() },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.use_biometrics))
            }
        }
    }

    if (showVaultSwitcher) {
        com.yishenghuang.keyic.ui.vault.VaultSwitcherSheet(
            container = app.container,
            onDismiss = { showVaultSwitcher = false },
        )
    }
}

@Composable
private fun LockErrorText(errorRes: Int?, errorDetail: String?) {
    if (errorDetail == null && errorRes == null) return
    val fromRes = if (errorRes != null) stringResource(errorRes) else null
    Text(
        errorDetail ?: fromRes.orEmpty(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.96f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lockScale",
    )
    val scroll = rememberScrollState()

    KeyicBackdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .scale(scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                variant = GlassVariant.Tile,
                strong = true,
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    content()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

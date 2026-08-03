package com.yishenghuang.keyic.ui.generator

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.crypto.PasswordGenerator
import com.yishenghuang.keyic.core.crypto.PasswordStrength
import com.yishenghuang.keyic.core.model.PasswordGeneratorOptions
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.theme.Danger
import com.yishenghuang.keyic.ui.theme.Success
import com.yishenghuang.keyic.ui.theme.Warning
import com.yishenghuang.keyic.ui.util.SecureClipboard
import com.yishenghuang.keyic.ui.util.passwordStrengthLabel

@Composable
fun GeneratorScreen(
    clipboard: SecureClipboard,
    clipboardClearSeconds: Int,
) {
    var length by remember { mutableFloatStateOf(20f) }
    var minDigits by remember { mutableFloatStateOf(2f) }
    var minSymbols by remember { mutableFloatStateOf(3f) }
    var uppercase by remember { mutableStateOf(true) }
    var lowercase by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var excludeAmbiguous by remember { mutableStateOf(true) }
    var checkInput by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }

    fun options() = PasswordGeneratorOptions(
        length = length.toInt(),
        uppercase = uppercase,
        lowercase = lowercase,
        digits = digits,
        symbols = symbols,
        excludeAmbiguous = excludeAmbiguous,
        minDigits = minDigits.toInt(),
        minSymbols = minSymbols.toInt(),
    )

    var password by remember {
        mutableStateOf(PasswordGenerator.generate(PasswordGeneratorOptions()))
    }

    fun regenerate() {
        password = PasswordGenerator.generate(options())
    }

    val analyzed = if (tab == 0) password else checkInput
    val score = PasswordStrength.score(analyzed)
    val label = passwordStrengthLabel(analyzed)
    val barColor = when {
        score < 35 -> Danger
        score < 55 -> Warning
        else -> Success
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == 0,
                onClick = { tab = 0 },
                label = { Text(stringResource(R.string.generator_tab)) },
            )
            FilterChip(
                selected = tab == 1,
                onClick = { tab = 1 },
                label = { Text(stringResource(R.string.generator_strength_tab)) },
            )
        }

        if (tab == 0) {
            GlassSurface(
                shape = MaterialTheme.shapes.extraLarge,
                strong = true,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
            ) {
                Column {
                    Text(stringResource(R.string.generator_generated), style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = password,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.generator_strength), style = MaterialTheme.typography.labelMedium)
                        Text(label, color = barColor, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            clipboard.copy("password", password, clipboardClearSeconds)
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.generator_copy))
                        }
                        FilledTonalButton(onClick = { regenerate() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.generator_refresh))
                        }
                    }
                }
            }

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.generator_options), style = MaterialTheme.typography.titleMedium)
                    OptionSlider(
                        stringResource(R.string.generator_length),
                        length,
                        8f..64f,
                        stringResource(R.string.generator_length_value, length.toInt()),
                    ) {
                        length = it
                        regenerate()
                    }
                    if (digits) {
                        OptionSlider(
                            stringResource(R.string.generator_min_numbers),
                            minDigits,
                            0f..8f,
                            "${minDigits.toInt()}",
                        ) {
                            minDigits = it
                            regenerate()
                        }
                    }
                    if (symbols) {
                        OptionSlider(
                            stringResource(R.string.generator_min_symbols),
                            minSymbols,
                            0f..8f,
                            "${minSymbols.toInt()}",
                        ) {
                            minSymbols = it
                            regenerate()
                        }
                    }
                    ToggleRow(stringResource(R.string.generator_uppercase), uppercase) { uppercase = it; regenerate() }
                    ToggleRow(stringResource(R.string.generator_lowercase), lowercase) { lowercase = it; regenerate() }
                    ToggleRow(stringResource(R.string.generator_digits), digits) { digits = it; regenerate() }
                    ToggleRow(stringResource(R.string.generator_symbols), symbols) { symbols = it; regenerate() }
                    ToggleRow(stringResource(R.string.generator_exclude_ambiguous), excludeAmbiguous) {
                        excludeAmbiguous = it; regenerate()
                    }
                }
            }

            Button(
                onClick = { regenerate() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) { Text(stringResource(R.string.generator_generate)) }
        } else {
            GlassSurface(
                shape = MaterialTheme.shapes.extraLarge,
                strong = true,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
            ) {
                Column {
                    Text(stringResource(R.string.generator_check_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = checkInput,
                        onValueChange = { checkInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.generator_check_label)) },
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.generator_strength), style = MaterialTheme.typography.labelMedium)
                        Text(label, color = barColor, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = barColor,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.generator_score, score),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    badge: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            GlassSurface(
                shape = MaterialTheme.shapes.large,
                strong = true,
                elevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(badge, style = MaterialTheme.typography.labelMedium)
            }
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

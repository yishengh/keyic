package com.yishenghuang.keyic.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.crypto.PasswordStrength

@Composable
fun passwordStrengthLabel(password: String): String {
    val score = PasswordStrength.score(password)
    return when {
        password.isEmpty() -> stringResource(R.string.strength_empty)
        score < 35 -> stringResource(R.string.strength_weak)
        score < 55 -> stringResource(R.string.strength_fair)
        score < 75 -> stringResource(R.string.strength_strong)
        else -> stringResource(R.string.strength_very_strong)
    }
}

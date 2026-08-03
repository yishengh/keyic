package com.yishenghuang.keyic.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthSize {
    Compact,
    Medium,
    Expanded,
}

@Composable
fun rememberWindowWidthSize(): WindowWidthSize {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        when {
            widthDp >= 840 -> WindowWidthSize.Expanded
            widthDp >= 600 -> WindowWidthSize.Medium
            else -> WindowWidthSize.Compact
        }
    }
}

val WindowWidthSize.useNavigationRail: Boolean
    get() = this != WindowWidthSize.Compact

val WindowWidthSize.useListDetail: Boolean
    get() = this == WindowWidthSize.Expanded

/** Comfortable reading width for forms / settings on tablets. */
fun WindowWidthSize.contentMaxWidthOrNull(): Dp? = when (this) {
    WindowWidthSize.Compact -> null
    WindowWidthSize.Medium -> 720.dp
    WindowWidthSize.Expanded -> 840.dp
}

@Composable
fun AdaptiveContentWidth(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val maxWidth = rememberWindowWidthSize().contentMaxWidthOrNull()
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = if (maxWidth != null) {
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            },
        ) {
            content()
        }
    }
}

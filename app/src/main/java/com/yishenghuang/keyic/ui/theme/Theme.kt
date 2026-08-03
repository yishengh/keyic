package com.yishenghuang.keyic.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = SageFill,
    onPrimaryContainer = SageOnFill,
    secondary = TealPrimary,
    onSecondary = TealOnPrimary,
    secondaryContainer = TealContainer,
    onSecondaryContainer = TealOnContainer,
    tertiary = Color(0xFF3F5A9A),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOutline,
    outline = LightOutline,
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealOnContainerDark,
    secondary = TealPrimaryDark,
    onSecondary = TealOnPrimaryDark,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = TealOnContainerDark,
    tertiary = Color(0xFFA8C0FF),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOutline,
    outline = DarkOutline,
    error = Color(0xFFFFB4AB),
)

private val AmoledColors = DarkColors.copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceVariant = Color(0xFF121212),
)

val KeyicShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

enum class GlassVariant {
    /** Near-opaque white tile — vault rows, content cards */
    Tile,
    /** More translucent chrome — nav bar, sheets */
    Chrome,
    /** Recessed search / sunk fields */
    Recessed,
}

@Immutable
data class GlassTokens(
    val fill: Color,
    val fillStrong: Color,
    val tileFill: Color,
    val recessedFill: Color,
    val chromeFill: Color,
    val stroke: Color,
    val strokeHighlight: Color,
    val shadow: Color,
    val elevation: Dp,
    val tileElevation: Dp,
    val sage: Color,
    val sageOn: Color,
    val tag: Color,
    val backdrop: Brush,
    val glowTeal: Color,
    val glowCool: Color,
    val isDark: Boolean,
    val amoled: Boolean,
)

val LocalGlassTokens = staticCompositionLocalOf {
    GlassTokens(
        fill = Color.White.copy(alpha = 0.88f),
        fillStrong = Color.White,
        tileFill = LightTile,
        recessedFill = LightRecessed.copy(alpha = 0.85f),
        chromeFill = Color.White.copy(alpha = 0.72f),
        stroke = Color.White.copy(alpha = 0.65f),
        strokeHighlight = Color.White.copy(alpha = 0.8f),
        shadow = LightShadow.copy(alpha = 0.08f),
        elevation = 6.dp,
        tileElevation = 8.dp,
        sage = SageFill,
        sageOn = SageOnFill,
        tag = SageTag,
        backdrop = Brush.verticalGradient(listOf(LightBackground, LightGradientEnd)),
        glowTeal = Color.Transparent,
        glowCool = Color.Transparent,
        isDark = false,
        amoled = false,
    )
}

fun glassTokensFor(
    scheme: ColorScheme,
    darkTheme: Boolean,
    amoledBlack: Boolean,
): GlassTokens {
    return if (darkTheme) {
        val base = if (amoledBlack) AmoledBlack else scheme.background
        // Higher opacity tiles so body text stays readable over glow / grain
        val tileBase = if (amoledBlack) {
            Color(0xFF141414).copy(alpha = 0.92f)
        } else {
            DarkTile.copy(alpha = 0.88f)
        }
        GlassTokens(
            fill = tileBase.copy(alpha = 0.72f),
            fillStrong = tileBase.copy(alpha = 0.94f),
            tileFill = tileBase,
            recessedFill = DarkRecessed.copy(alpha = 0.90f),
            chromeFill = tileBase.copy(alpha = 0.82f),
            stroke = Color.White.copy(alpha = 0.16f),
            strokeHighlight = Color.White.copy(alpha = 0.28f),
            shadow = DarkShadow.copy(alpha = 0.55f),
            elevation = 12.dp,
            tileElevation = 14.dp,
            sage = DarkSage,
            sageOn = DarkSageOn,
            tag = DarkTag,
            backdrop = Brush.verticalGradient(
                listOf(base, DarkGradientEnd.copy(alpha = 0.9f).compositeOver(base)),
            ),
            glowTeal = if (amoledBlack) Color.Transparent else DarkGlowTeal.copy(alpha = 0.42f),
            glowCool = if (amoledBlack) Color.Transparent else DarkGlowCool.copy(alpha = 0.32f),
            isDark = true,
            amoled = amoledBlack,
        )
    } else {
        val tile = Color.White.copy(alpha = 0.96f)
        GlassTokens(
            fill = Color.White.copy(alpha = 0.78f),
            fillStrong = Color.White.copy(alpha = 0.94f),
            tileFill = tile,
            recessedFill = LightRecessed.copy(alpha = 0.92f),
            chromeFill = Color.White.copy(alpha = 0.78f),
            stroke = Color.White.copy(alpha = 0.7f),
            strokeHighlight = Color.White,
            shadow = LightShadow.copy(alpha = 0.09f),
            elevation = 5.dp,
            tileElevation = 8.dp,
            sage = SageFill,
            sageOn = SageOnFill,
            tag = SageTag,
            backdrop = Brush.verticalGradient(
                listOf(
                    scheme.background,
                    LightGradientEnd.copy(alpha = 0.85f).compositeOver(scheme.background),
                ),
            ),
            glowTeal = Color.Transparent,
            glowCool = Color.Transparent,
            isDark = false,
            amoled = false,
        )
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    val r = red * a + background.red * (1f - a)
    val g = green * a + background.green * (1f - a)
    val b = blue * a + background.blue * (1f - a)
    return Color(r, g, b, 1f)
}

val MaterialTheme.glass: GlassTokens
    @Composable
    get() = LocalGlassTokens.current

@Composable
fun KeyicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            val dynamic = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            when {
                darkTheme && amoledBlack ->
                    dynamic.copy(background = AmoledBlack, surface = AmoledBlack)
                !darkTheme ->
                    dynamic.copy(
                        background = LightBackground,
                        surface = LightSurface,
                        primaryContainer = SageFill,
                        onPrimaryContainer = SageOnFill,
                    )
                else -> dynamic
            }
        }
        darkTheme && amoledBlack -> AmoledColors
        darkTheme -> DarkColors
        else -> LightColors
    }

    val glass = glassTokensFor(colorScheme, darkTheme, amoledBlack)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalGlassTokens provides glass) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = KeyicShapes,
            content = content,
        )
    }
}

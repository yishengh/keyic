package com.yishenghuang.keyic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yishenghuang.keyic.ui.theme.GlassVariant
import com.yishenghuang.keyic.ui.theme.glass

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Tile,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    strong: Boolean = false,
    elevation: Dp? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val glass = MaterialTheme.glass
    val fill = when (variant) {
        GlassVariant.Tile -> if (strong) glass.fillStrong else glass.tileFill
        GlassVariant.Chrome -> glass.chromeFill
        GlassVariant.Recessed -> glass.recessedFill
    }
    val elev = elevation ?: when (variant) {
        GlassVariant.Tile -> glass.tileElevation
        GlassVariant.Chrome -> glass.elevation
        GlassVariant.Recessed -> 2.dp
    }
    val stroke = when (variant) {
        GlassVariant.Chrome -> if (glass.isDark) glass.strokeHighlight else glass.stroke
        GlassVariant.Recessed -> glass.stroke.copy(alpha = glass.stroke.alpha * 0.5f)
        GlassVariant.Tile -> glass.stroke
    }
    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = elev,
                shape = shape,
                ambientColor = glass.shadow,
                spotColor = glass.shadow,
                clip = false,
            )
            .clip(shape)
            .background(fill, shape)
            .border(width = 1.dp, color = stroke, shape = shape)
            .then(clickMod)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

@Composable
fun BentoTile(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    strong: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        variant = GlassVariant.Tile,
        strong = strong,
        onClick = onClick,
        contentPadding = contentPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leading != null || title != null || trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    leading?.invoke()
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    trailing?.invoke()
                }
            }
            content?.invoke(this)
        }
    }
}

@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
    leadingIcon: ImageVector? = null,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        variant = GlassVariant.Recessed,
        shape = RoundedCornerShape(28.dp),
        elevation = 2.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(10.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.merge(
                        TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

data class GlassNavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun GlassNavigationBar(
    items: List<GlassNavItem>,
    modifier: Modifier = Modifier,
) {
    val glass = MaterialTheme.glass
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding(),
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.Chrome,
            shape = RoundedCornerShape(32.dp),
            elevation = glass.elevation + 2.dp,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = item.selected,
                        onClick = item.onClick,
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (glass.isDark) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                glass.sageOn
                            },
                            selectedTextColor = if (glass.isDark) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                glass.sageOn
                            },
                            indicatorColor = glass.sage,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun GlassNavigationRail(
    items: List<GlassNavItem>,
    modifier: Modifier = Modifier,
) {
    val glass = MaterialTheme.glass
    val itemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = if (glass.isDark) {
            MaterialTheme.colorScheme.primary
        } else {
            glass.sageOn
        },
        selectedTextColor = if (glass.isDark) {
            MaterialTheme.colorScheme.primary
        } else {
            glass.sageOn
        },
        indicatorColor = glass.sage,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 8.dp, bottom = 12.dp),
    ) {
        GlassSurface(
            variant = GlassVariant.Chrome,
            shape = RoundedCornerShape(28.dp),
            elevation = glass.elevation + 2.dp,
            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEach { item ->
                    NavigationRailItem(
                        selected = item.selected,
                        onClick = item.onClick,
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1) },
                        colors = itemColors,
                    )
                }
            }
        }
    }
}

@Composable
fun GlassFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    text: @Composable (() -> Unit)? = null,
) {
    val glass = MaterialTheme.glass
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        containerColor = if (glass.isDark) glass.fillStrong else Color.White,
        contentColor = MaterialTheme.colorScheme.onSurface,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = glass.tileElevation,
            pressedElevation = glass.tileElevation + 2.dp,
        ),
    ) {
        if (text != null) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon()
                text()
            }
        } else {
            icon()
        }
    }
}

@Composable
fun KeyicBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glass = MaterialTheme.glass
    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glass.backdrop)
                .drawBehind {
                    if (glass.glowTeal.alpha > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glass.glowTeal, Color.Transparent),
                                center = Offset(w * 0.18f, h * 0.22f),
                                radius = w * 0.75f,
                            ),
                            radius = w * 0.75f,
                            center = Offset(w * 0.18f, h * 0.22f),
                        )
                    }
                    if (glass.glowCool.alpha > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glass.glowCool, Color.Transparent),
                                center = Offset(w * 0.88f, h * 0.62f),
                                radius = w * 0.7f,
                            ),
                            radius = w * 0.7f,
                            center = Offset(w * 0.88f, h * 0.62f),
                        )
                    }
                },
        ) {
            content()
        }
    }
}

@Composable
fun GlassSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.Tile,
            contentPadding = PaddingValues(16.dp),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            },
        )
    }
}

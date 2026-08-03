package com.yishenghuang.keyic.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yishenghuang.keyic.ui.theme.glass

enum class OnboardingVisual {
    OfflineVault,
    SmartCapture,
    AutofillAuth,
    BackupSync,
}

@Composable
fun OnboardingIllustration(
    visual: OnboardingVisual,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (visual) {
            OnboardingVisual.OfflineVault -> OfflineVaultVisual()
            OnboardingVisual.SmartCapture -> SmartCaptureVisual()
            OnboardingVisual.AutofillAuth -> AutofillAuthVisual()
            OnboardingVisual.BackupSync -> BackupSyncVisual()
        }
    }
}

@Composable
private fun OfflineVaultVisual() {
    val glass = MaterialTheme.glass
    val infinite = rememberInfiniteTransition(label = "offline")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val ring by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )
    val primary = MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val r = size.minDimension / 2f
            drawCircle(
                color = primary.copy(alpha = (1f - ring) * 0.28f),
                radius = r * ring,
            )
            drawCircle(
                color = primary.copy(alpha = (1f - ((ring + 0.35f) % 1f)) * 0.18f),
                radius = r * ((ring + 0.35f) % 1f).coerceIn(0.35f, 1f),
            )
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(glass.sage.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = glass.sageOn,
                modifier = Modifier.size(42.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = 18.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, glass.stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 12.dp, y = (-14).dp)
                .size(40.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, glass.stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SmartCaptureVisual() {
    val infinite = rememberInfiniteTransition(label = "capture")
    val scanY by infinite.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan",
    )
    val cardShift by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "card",
    )
    val primary = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.size(220.dp, 180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val corner = 22.dp.toPx()
            val len = 28.dp.toPx()
            val strokeW = 3.5.dp.toPx()
            val color = primary
            fun cornerPath(ox: Float, oy: Float, dx: Float, dy: Float) {
                drawLine(color, Offset(ox, oy + dy * len), Offset(ox, oy), strokeW, StrokeCap.Round)
                drawLine(color, Offset(ox, oy), Offset(ox + dx * len, oy), strokeW, StrokeCap.Round)
            }
            cornerPath(0f, 0f, 1f, 1f)
            cornerPath(size.width, 0f, -1f, 1f)
            cornerPath(0f, size.height, 1f, -1f)
            cornerPath(size.width, size.height, -1f, -1f)
            val y = size.height * scanY
            drawLine(
                color = primary.copy(alpha = 0.85f),
                start = Offset(12.dp.toPx(), y),
                end = Offset(size.width - 12.dp.toPx(), y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawRoundRect(
                color = primary.copy(alpha = 0.08f),
                topLeft = Offset(18.dp.toPx(), 18.dp.toPx()),
                size = Size(size.width - 36.dp.toPx(), size.height - 36.dp.toPx()),
                cornerRadius = CornerRadius(corner, corner),
            )
            val cell = 10.dp.toPx()
            val origin = Offset(42.dp.toPx(), 42.dp.toPx())
            for (r in 0 until 5) {
                for (c in 0 until 5) {
                    if ((r + c) % 2 == 0 || (r == 0 || c == 0 || r == 4 || c == 4)) {
                        drawRoundRect(
                            color = primary.copy(alpha = 0.55f),
                            topLeft = origin + Offset(c * cell, r * cell),
                            size = Size(cell * 0.72f, cell * 0.72f),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                    }
                }
            }
        }
        Icon(
            Icons.Outlined.QrCodeScanner,
            contentDescription = null,
            tint = primary.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
                .offset(y = 36.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 4.dp)
                .offset(x = cardShift.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiniChip(Icons.Outlined.CreditCard)
            MiniChip(Icons.Outlined.Password)
            MiniChip(Icons.Outlined.QrCodeScanner)
        }
    }
}

@Composable
private fun MiniChip(icon: ImageVector) {
    val glass = MaterialTheme.glass
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(glass.sage.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = glass.sageOn, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AutofillAuthVisual() {
    val glass = MaterialTheme.glass
    val infinite = rememberInfiniteTransition(label = "autofill")
    val fillProgress by infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fill",
    )
    val codePulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "code",
    )
    val tick by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tick",
    )
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Column(
                modifier = Modifier
                    .width(128.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .border(1.dp, glass.stroke, RoundedCornerShape(20.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FakeField(fillProgress.coerceIn(0f, 1f), wide = true)
                FakeField(((fillProgress - 0.35f) / 0.65f).coerceIn(0f, 1f), wide = true)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(primary.copy(alpha = if (fillProgress > 0.85f) 0.9f else 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Password,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary.copy(
                            alpha = if (fillProgress > 0.85f) 1f else 0.4f,
                        ),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(codePulse)
                    .clip(RoundedCornerShape(24.dp))
                    .background(glass.sage.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = glass.sageOn,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "482 019",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = glass.sageOn,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Canvas(modifier = Modifier.width(72.dp).height(6.dp)) {
                drawRoundRect(
                    color = primary.copy(alpha = 0.2f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                drawRoundRect(
                    color = primary,
                    size = Size(size.width * (1f - tick), size.height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun FakeField(progress: Float, wide: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp),
    ) {
        drawRoundRect(
            color = primary.copy(alpha = 0.1f),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
        if (progress > 0f) {
            drawRoundRect(
                color = primary.copy(alpha = 0.45f),
                size = Size(size.width * progress * if (wide) 0.85f else 0.55f, size.height * 0.45f),
                topLeft = Offset(8.dp.toPx(), size.height * 0.28f),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun BackupSyncVisual() {
    val glass = MaterialTheme.glass
    val infinite = rememberInfiniteTransition(label = "backup")
    val floatY by infinite.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float",
    )
    val arrowAlpha by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrow",
    )
    val packetX by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "packet",
    )
    val primary = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.size(240.dp, 160.dp), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SyncNode(
                icon = Icons.Outlined.Shield,
                modifier = Modifier.offset(y = floatY.dp),
            )
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                    drawLine(
                        color = primary.copy(alpha = 0.35f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .offset(x = ((packetX - 0.5f) * 56).dp)
                        .size(14.dp)
                        .alpha(0.35f + arrowAlpha * 0.65f)
                        .clip(CircleShape)
                        .background(primary),
                )
            }
            SyncNode(
                icon = Icons.Outlined.Folder,
                modifier = Modifier.offset(y = (-floatY).dp),
            )
            Icon(
                Icons.Outlined.CloudDone,
                contentDescription = null,
                tint = primary.copy(alpha = 0.35f + arrowAlpha * 0.65f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(28.dp)
                    .rotate(packetX * 8f - 4f),
            )
        }
    }
}

@Composable
private fun SyncNode(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val glass = MaterialTheme.glass
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(glass.sage.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = glass.sageOn, modifier = Modifier.size(26.dp))
    }
}

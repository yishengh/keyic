package com.yishenghuang.keyic.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val SplashTeal = Color(0xFF1A6B5C)
private val SplashTealDeep = Color(0xFF0F3D34)
private val SplashTealGlow = Color(0xFF2A9A82)
private val SplashMist = Color(0xFFF4F5F0)
private val SplashSage = Color(0xFFD1E6D9)

@Composable
fun BrandLaunchSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = remember { Animatable(0f) }
    val markScale = remember { Animatable(0.82f) }
    val exitAlpha = remember { Animatable(1f) }
    val exitScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            contentAlpha.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        }
        launch {
            markScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        delay(1650)
        launch {
            exitScale.animateTo(1.08f, tween(420, easing = FastOutSlowInEasing))
        }
        exitAlpha.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(exitAlpha.value)
            .scale(exitScale.value)
            .background(SplashTealDeep),
        contentAlignment = Alignment.Center,
    ) {
        SplashAnimatedBackground(modifier = Modifier.fillMaxSize())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(contentAlpha.value)
                .scale(markScale.value),
        ) {
            BrandShieldLockMark(modifier = Modifier.size(128.dp))
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Keyic",
                color = SplashMist.copy(alpha = 0.95f),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

@Composable
private fun SplashAnimatedBackground(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "splashBg")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val ring by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )

    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(SplashTeal, SplashTealDeep),
                ),
            )
            val angle = (drift * Math.PI * 2).toFloat()
            val c1 = Offset(w * (0.22f + 0.06f * sin(angle)), h * 0.28f)
            val c2 = Offset(w * (0.82f - 0.05f * cos(angle)), h * 0.68f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SplashTealGlow.copy(alpha = 0.45f), Color.Transparent),
                    center = c1,
                    radius = w * 0.55f * pulse,
                ),
                radius = w * 0.55f * pulse,
                center = c1,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SplashSage.copy(alpha = 0.22f), Color.Transparent),
                    center = c2,
                    radius = w * 0.5f,
                ),
                radius = w * 0.5f,
                center = c2,
            )
            val mid = Offset(w * 0.5f, h * 0.42f)
            for (i in 0..2) {
                val t = ((ring + i * 0.33f) % 1f)
                drawCircle(
                    color = SplashMist.copy(alpha = (1f - t) * 0.14f),
                    radius = (w * 0.12f) + t * w * 0.38f,
                    center = mid,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
fun BrandShieldLockMark(
    modifier: Modifier = Modifier,
    color: Color = SplashMist,
    letterColor: Color = SplashTeal,
) {
    Canvas(modifier = modifier) {
        val min = size.minDimension
        val stroke = min * 0.065f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Lock shackle above the shield (scheme E)
        val shackleR = min * 0.145f
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - shackleR, cy - min * 0.42f),
            size = Size(shackleR * 2f, shackleR * 1.9f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Solid shield body
        val shield = Path().apply {
            val top = cy - min * 0.18f
            val bottom = cy + min * 0.42f
            val left = cx - min * 0.32f
            val right = cx + min * 0.32f
            moveTo(cx, top)
            cubicTo(
                cx + min * 0.16f, top + min * 0.05f,
                right, top + min * 0.08f,
                right, top + min * 0.1f,
            )
            lineTo(right, cy + min * 0.02f)
            cubicTo(
                right, cy + min * 0.26f,
                cx + min * 0.16f, bottom - min * 0.05f,
                cx, bottom,
            )
            cubicTo(
                cx - min * 0.16f, bottom - min * 0.05f,
                left, cy + min * 0.26f,
                left, cy + min * 0.02f,
            )
            lineTo(left, top + min * 0.1f)
            cubicTo(
                left, top + min * 0.08f,
                cx - min * 0.16f, top + min * 0.05f,
                cx, top,
            )
            close()
        }
        drawPath(path = shield, color = color)

        // Geometric K monogram
        val k = Path().apply {
            val left = cx - min * 0.11f
            val right = cx + min * 0.14f
            val top = cy - min * 0.02f
            val bottom = cy + min * 0.26f
            val midY = (top + bottom) / 2f
            val stem = min * 0.065f
            moveTo(left, top)
            lineTo(left + stem, top)
            lineTo(left + stem, midY - min * 0.01f)
            lineTo(right - min * 0.02f, top)
            lineTo(right, top)
            lineTo(cx + min * 0.02f, midY)
            lineTo(right, bottom)
            lineTo(right - min * 0.02f, bottom)
            lineTo(left + stem, midY + min * 0.01f)
            lineTo(left + stem, bottom)
            lineTo(left, bottom)
            close()
        }
        drawPath(path = k, color = letterColor)
    }
}

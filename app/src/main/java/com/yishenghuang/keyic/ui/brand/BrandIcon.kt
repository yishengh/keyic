package com.yishenghuang.keyic.ui.brand

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yishenghuang.keyic.core.model.EntryType
import com.yishenghuang.keyic.core.model.VaultEntry
import java.net.URI
import kotlin.math.abs

data class BrandStyle(
    val color: Color,
    val letter: String,
    val icon: ImageVector? = null,
    @param:DrawableRes val drawableRes: Int? = null,
    val preferIcon: Boolean = false,
)

/**
 * Offline brand styling using Streamline Logos solid (CC BY 4.0).
 * Catalog is auto-imported from logos/solid (deduped variants).
 * https://streamlinehq.com — see app/NOTICE_STREAMLINE.txt
 */
object BrandIconResolver {
    fun resolve(entry: VaultEntry): BrandStyle {
        when (entry.type) {
            EntryType.NOTE -> {
                if (entry.iconKey.isNullOrBlank()) {
                    return BrandStyle(
                        Color(0xFF5C6BC0), "N", Icons.AutoMirrored.Outlined.Note, preferIcon = true,
                    )
                }
            }
            EntryType.CARD -> {
                if (entry.iconKey.isNullOrBlank()) {
                    return BrandStyle(
                        Color(0xFF00897B), "C", Icons.Outlined.CreditCard, preferIcon = true,
                    )
                }
            }
            EntryType.IDENTITY -> {
                if (entry.iconKey.isNullOrBlank()) {
                    return BrandStyle(
                        Color(0xFF6D4C41), "ID", Icons.Outlined.Person, preferIcon = true,
                    )
                }
            }
            EntryType.LOGIN -> Unit
        }

        entry.iconKey?.takeIf { it.isNotBlank() }?.let { key ->
            BrandCatalog.byId(key)?.let { brand ->
                val letter = entry.title.trim().firstOrNull()?.uppercaseChar()?.toString()
                    ?: brand.id.firstOrNull()?.uppercaseChar()?.toString()
                    ?: "?"
                return BrandStyle(
                    color = brand.color,
                    letter = letter,
                    drawableRes = brand.drawableRes,
                )
            }
        }

        val haystack = buildString {
            append(entry.title.lowercase())
            append(' ')
            append(entry.url.lowercase())
            append(' ')
            append(hostOf(entry.url).orEmpty())
            entry.packageHints.forEach { append(' '); append(it.lowercase()) }
        }
        var best: BrandCatalog.Entry? = null
        var bestLen = 0
        for (brand in BrandCatalog.entries) {
            for (key in brand.keys) {
                if (key.length > bestLen && haystack.contains(key)) {
                    best = brand
                    bestLen = key.length
                }
            }
        }
        if (best != null) {
            val letter = entry.title.trim().firstOrNull()?.uppercaseChar()?.toString()
                ?: best.id.firstOrNull()?.uppercaseChar()?.toString()
                ?: "?"
            return BrandStyle(
                color = best.color,
                letter = letter,
                drawableRes = best.drawableRes,
            )
        }
        return fallback(entry.title)
    }

    /** Preview for edit screen before the entry is saved. */
    fun preview(
        title: String,
        url: String,
        type: EntryType,
        iconKey: String?,
    ): BrandStyle = resolve(
        VaultEntry(
            id = "_preview",
            title = title,
            type = type,
            url = url,
            iconKey = iconKey,
            createdAt = 0,
            updatedAt = 0,
            passwordChangedAt = 0,
        ),
    )

    fun fallback(title: String): BrandStyle {
        val letter = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val hue = abs(title.hashCode() % 360).toFloat()
        val color = Color.hsl(hue, 0.48f, 0.42f)
        return BrandStyle(color, letter, Icons.Outlined.Language)
    }

    private fun hostOf(url: String): String? {
        if (url.isBlank()) return null
        val normalized = when {
            url.startsWith("http://", true) || url.startsWith("https://", true) -> url
            else -> "https://$url"
        }
        return runCatching { URI(normalized).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    }
}

@Composable
fun BrandAvatar(
    entry: VaultEntry,
    size: Dp = 44.dp,
) {
    val style = remember(entry.id, entry.title, entry.url, entry.type, entry.packageHints) {
        BrandIconResolver.resolve(entry)
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(style.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            style.drawableRes != null -> {
                Image(
                    painter = painterResource(style.drawableRes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(size * 0.52f),
                )
            }
            style.preferIcon && style.icon != null -> {
                Icon(
                    style.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.48f),
                )
            }
            else -> {
                Text(
                    text = style.letter.take(2),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.36f).sp,
                )
            }
        }
    }
}

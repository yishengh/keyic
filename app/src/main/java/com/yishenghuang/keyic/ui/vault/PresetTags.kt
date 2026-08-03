package com.yishenghuang.keyic.ui.vault

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yishenghuang.keyic.R

object PresetTags {
    data class Def(val key: String, @StringRes val labelRes: Int)

    val all: List<Def> = listOf(
        Def("work", R.string.tag_work),
        Def("personal", R.string.tag_personal),
        Def("finance", R.string.tag_finance),
        Def("banking", R.string.tag_banking),
        Def("social", R.string.tag_social),
        Def("shopping", R.string.tag_shopping),
        Def("email", R.string.tag_email),
        Def("travel", R.string.tag_travel),
        Def("wifi", R.string.tag_wifi),
        Def("important", R.string.tag_important),
    )

    fun isPreset(key: String): Boolean = all.any { it.key.equals(key, ignoreCase = true) }

    fun normalize(key: String): String {
        val trimmed = key.trim()
        return all.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }?.key ?: trimmed
    }
}

@Composable
fun presetTagLabel(key: String): String {
    val preset = PresetTags.all.firstOrNull { it.key.equals(key, ignoreCase = true) }
    return if (preset != null) stringResource(preset.labelRes) else key
}

@Composable
fun presetTagLabels(keys: List<String>): String {
    if (keys.isEmpty()) return ""
    val labels = keys.map { presetTagLabel(it) }
    return labels.joinToString(", ")
}

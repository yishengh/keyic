package com.yishenghuang.keyic.ui.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.yishenghuang.keyic.core.model.AppLanguage
import java.util.Locale

object AppLocaleController {
    fun languageTag(language: AppLanguage): String? = when (language) {
        AppLanguage.SYSTEM -> null
        AppLanguage.ENGLISH -> "en"
        AppLanguage.CHINESE_SIMPLIFIED -> "zh-CN"
        AppLanguage.CHINESE_TRADITIONAL -> "zh-TW"
        AppLanguage.JAPANESE -> "ja"
        AppLanguage.KOREAN -> "ko"
        AppLanguage.SPANISH -> "es"
        AppLanguage.FRENCH -> "fr"
    }

    fun apply(language: AppLanguage) {
        val tag = languageTag(language)
        val desired = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        if (isSameLocales(AppCompatDelegate.getApplicationLocales(), desired, tag)) {
            return
        }
        AppCompatDelegate.setApplicationLocales(desired)
    }

    /**
     * Context whose resources resolve against [language], while keeping [base]
     * (usually the Activity) in the ContextWrapper chain for findActivity().
     */
    fun wrap(base: Context, language: AppLanguage): Context {
        val tag = languageTag(language) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        val localized = base.createConfigurationContext(config)
        return object : ContextWrapper(base) {
            override fun getResources(): Resources = localized.resources
            override fun getAssets() = localized.assets
        }
    }

    fun configuration(base: Context, language: AppLanguage): Configuration {
        val tag = languageTag(language)
        val config = Configuration(base.resources.configuration)
        if (tag != null) {
            config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        }
        return config
    }

    private fun isSameLocales(
        current: LocaleListCompat,
        desired: LocaleListCompat,
        desiredTag: String?,
    ): Boolean {
        if (desiredTag == null) return current.isEmpty
        if (current.isEmpty) return false
        if (current.toLanguageTags().equals(desired.toLanguageTags(), ignoreCase = true)) {
            return true
        }
        val want = Locale.forLanguageTag(desiredTag)
        val have = current[0] ?: return false
        return have.language.equals(want.language, ignoreCase = true) &&
            (want.country.isEmpty() ||
                have.country.equals(want.country, ignoreCase = true))
    }
}

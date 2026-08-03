package com.yishenghuang.keyic.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yishenghuang.keyic.core.model.AppLanguage
import com.yishenghuang.keyic.core.model.AppSettings
import com.yishenghuang.keyic.core.model.BackupFrequency
import com.yishenghuang.keyic.core.model.ThemeMode
import com.yishenghuang.keyic.core.port.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("keyic_settings")

class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {
    private object Keys {
        val biometric = booleanPreferencesKey("biometric")
        val lockOnBackground = booleanPreferencesKey("lock_on_background")
        val autoLockSeconds = intPreferencesKey("auto_lock_seconds")
        val amoledBlack = booleanPreferencesKey("amoled_black")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val clipboardClearSeconds = intPreferencesKey("clipboard_clear_seconds")
        val safTreeUri = stringPreferencesKey("saf_tree_uri")
        val safAutoBackup = booleanPreferencesKey("saf_auto_backup")
        val safFrequency = stringPreferencesKey("saf_frequency")
        val allowScreenshots = booleanPreferencesKey("allow_screenshots")
        val themeMode = stringPreferencesKey("theme_mode")
        val appLanguage = stringPreferencesKey("app_language")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val lastSafBackupAt = longPreferencesKey("last_saf_backup_at")
        val lastSafBackupOk = booleanPreferencesKey("last_saf_backup_ok")
        val lastSafBackupError = stringPreferencesKey("last_saf_backup_error")
        val recycleBinRetentionDays = intPreferencesKey("recycle_bin_retention_days")
    }

    override val settings: Flow<AppSettings> =
        context.settingsDataStore.data.map { prefs -> prefs.toSettings() }

    override suspend fun get(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.biometric] = next.biometricEnabled
            prefs[Keys.lockOnBackground] = next.lockOnBackground
            prefs[Keys.autoLockSeconds] = next.autoLockSeconds
            prefs[Keys.amoledBlack] = next.amoledBlack
            prefs[Keys.dynamicColor] = next.dynamicColor
            prefs[Keys.clipboardClearSeconds] = next.clipboardClearSeconds
            val treeUri = next.safTreeUri
            if (treeUri == null) prefs.remove(Keys.safTreeUri)
            else prefs[Keys.safTreeUri] = treeUri
            prefs[Keys.safAutoBackup] = next.safAutoBackupEnabled
            prefs[Keys.safFrequency] = next.safBackupFrequency.name
            prefs[Keys.allowScreenshots] = next.allowScreenshots
            prefs[Keys.themeMode] = next.themeMode.name
            prefs[Keys.appLanguage] = next.appLanguage.name
            prefs[Keys.onboardingDone] = next.onboardingDone
            val lastAt = next.lastSafBackupAt
            if (lastAt == null) prefs.remove(Keys.lastSafBackupAt)
            else prefs[Keys.lastSafBackupAt] = lastAt
            prefs[Keys.lastSafBackupOk] = next.lastSafBackupOk
            val err = next.lastSafBackupError
            if (err.isNullOrBlank()) prefs.remove(Keys.lastSafBackupError)
            else prefs[Keys.lastSafBackupError] = err
            prefs[Keys.recycleBinRetentionDays] = next.recycleBinRetentionDays
        }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        biometricEnabled = this[Keys.biometric] ?: false,
        lockOnBackground = this[Keys.lockOnBackground] ?: true,
        autoLockSeconds = this[Keys.autoLockSeconds] ?: 60,
        amoledBlack = this[Keys.amoledBlack] ?: false,
        dynamicColor = this[Keys.dynamicColor] ?: true,
        clipboardClearSeconds = this[Keys.clipboardClearSeconds] ?: 30,
        safTreeUri = this[Keys.safTreeUri],
        safAutoBackupEnabled = this[Keys.safAutoBackup] ?: false,
        safBackupFrequency = runCatching {
            BackupFrequency.valueOf(this[Keys.safFrequency] ?: BackupFrequency.DAILY.name)
        }.getOrDefault(BackupFrequency.DAILY),
        allowScreenshots = this[Keys.allowScreenshots] ?: false,
        themeMode = runCatching {
            ThemeMode.valueOf(this[Keys.themeMode] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM),
        appLanguage = runCatching {
            AppLanguage.valueOf(this[Keys.appLanguage] ?: AppLanguage.SYSTEM.name)
        }.getOrDefault(AppLanguage.SYSTEM),
        onboardingDone = this[Keys.onboardingDone] ?: false,
        lastSafBackupAt = this[Keys.lastSafBackupAt],
        lastSafBackupOk = this[Keys.lastSafBackupOk] ?: false,
        lastSafBackupError = this[Keys.lastSafBackupError],
        recycleBinRetentionDays = this[Keys.recycleBinRetentionDays] ?: 30,
    )
}

package com.yishenghuang.keyic.core.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    CHINESE_SIMPLIFIED,
    CHINESE_TRADITIONAL,
    JAPANESE,
    KOREAN,
    SPANISH,
    FRENCH,
}

enum class BackupFrequency(val hours: Long) {
    HOURS_6(6),
    HOURS_12(12),
    DAILY(24),
    DAYS_3(72),
    WEEKLY(168),
}

data class PasswordGeneratorOptions(
    val length: Int = 20,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = true,
    val minDigits: Int = 2,
    val minSymbols: Int = 2,
)

data class AppSettings(
    val biometricEnabled: Boolean = false,
    val lockOnBackground: Boolean = true,
    val autoLockSeconds: Int = 60,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val clipboardClearSeconds: Int = 30,
    val safTreeUri: String? = null,
    val safAutoBackupEnabled: Boolean = false,
    val safBackupFrequency: BackupFrequency = BackupFrequency.DAILY,
    val allowScreenshots: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val onboardingDone: Boolean = false,
    val lastSafBackupAt: Long? = null,
    val lastSafBackupOk: Boolean = false,
    val lastSafBackupError: String? = null,
    /** Auto-purge recycle bin entries older than this many days; 0 = never. */
    val recycleBinRetentionDays: Int = 30,
)

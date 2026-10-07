package com.yishenghuang.keyic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yishenghuang.keyic.core.model.AppLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val password = UUID.randomUUID().toString()
    private val app get() = ui.activity.application as KeyicApp

    @Before fun prepare() {
        runBlocking {
            app.container.settingsRepository.update { it.copy(onboardingDone = true, appLanguage = AppLanguage.ENGLISH, autoLockSeconds = 60, lockOnBackground = true) }
            app.container.session.createAdditionalVault("Synthetic UI vault", password.toCharArray())
        }
        ui.waitUntil(15_000) { ui.onAllNodesWithText("No entries yet").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun createLockWrongPasswordUnlockKeepsEntry() {
        ui.onNodeWithText("No entries yet").assertIsDisplayed()
        ui.onAllNodesWithText("Add Item").onFirst().performClick()
        ui.onNodeWithText("Nickname").performTextInput("Synthetic UI login")
        ui.onNodeWithText("Username / Email").performScrollTo().performTextInput("test@example.invalid")
        ui.onAllNodesWithText("Save").onFirst().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Synthetic UI login").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Lock now").performClick()
        ui.onNodeWithText("Master password").performTextInput("intentionally incorrect")
        ui.onNodeWithText("Unlock").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(app.getString(R.string.error_wrong_password)).fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        ui.onNodeWithText("Master password").performTextReplacement(password)
        ui.onNodeWithText("Unlock").performClick()
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Synthetic UI login").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    @Test fun settingsAboutAndChineseHelpAreReachable() {
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("About & help").performScrollTo().performClick()
        ui.onAllNodesWithText("Keyic").onFirst().assertIsDisplayed()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("About & help").performScrollTo().assertIsDisplayed()
        runBlocking { app.container.settingsRepository.update { it.copy(appLanguage = AppLanguage.CHINESE_SIMPLIFIED) } }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty() }
        val localized = app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply {
            setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
        })
        ui.onNodeWithText(localized.getString(R.string.settings_about_help)).performScrollTo().performClick()
        ui.onNodeWithText(localized.getString(R.string.about_offline_promise)).assertIsDisplayed()
    }

    @Test fun clipboardExpiresButDoesNotDeleteReplacement() {
        val clipboard = ui.runOnIdle { app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
        ui.runOnIdle { app.secureClipboard.copy("synthetic", UUID.randomUUID().toString(), 1) }
        Thread.sleep(1200)
        ui.runOnIdle {
            app.secureClipboard.clearExpired()
            if (android.os.Build.VERSION.SDK_INT >= 28) assertFalse(clipboard.hasPrimaryClip())
            else assertTrue(clipboard.primaryClip?.getItemAt(0)?.text.isNullOrEmpty()) // API 26–27 clear via empty replacement.
            app.secureClipboard.copy("synthetic", UUID.randomUUID().toString(), 1)
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "unrelated synthetic text"))
        }
        Thread.sleep(1200)
        ui.runOnIdle {
            app.secureClipboard.clearExpired()
            assertEquals("external", clipboard.primaryClipDescription?.label)
        }
    }

    @Test fun packagedAppCannotAccessNetworkOrAndroidBackup() {
        val info = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        assertTrue(app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP == 0)
    }

    @Test fun foregroundIdleAndBackgroundBothLock() {
        runBlocking { app.container.settingsRepository.update { it.copy(autoLockSeconds = 2) } }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Master password").fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            app.container.settingsRepository.update { it.copy(autoLockSeconds = 60) }
            assertTrue(app.container.session.unlock(password.toCharArray()))
        }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("No entries yet").fetchSemanticsNodes().isNotEmpty() }
        val session = app.container.session
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        Thread.sleep(1800)
        assertNull(session.peekDbKey())
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        ui.onNodeWithText("Master password").assertIsDisplayed()
    }
}

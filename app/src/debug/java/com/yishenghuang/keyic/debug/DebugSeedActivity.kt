package com.yishenghuang.keyic.debug

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.core.csv.CsvImportParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only: create/unlock vault with a known password and import sample CSV from assets.
 *
 * adb shell am start -n com.yishenghuang.keyic/.debug.DebugSeedActivity \
 *   -e password testdata1
 */
class DebugSeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val password = intent.getStringExtra(EXTRA_PASSWORD)?.takeIf { it.length >= 8 } ?: DEFAULT_PASSWORD
        val clearFirst = intent.getBooleanExtra(EXTRA_CLEAR, false)
        lifecycleScope.launch {
            val result = runCatching { seed(password, clearFirst) }
            val message = result.getOrElse { e ->
                Log.e(TAG, "Seed failed", e)
                "Seed failed: ${e.message}"
            }
            Log.i(TAG, message)
            runCatching {
                openFileOutput("seed-result.txt", MODE_PRIVATE).bufferedWriter().use {
                    it.write(message)
                }
            }
            Toast.makeText(this@DebugSeedActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private suspend fun seed(password: String, clearFirst: Boolean): String = withContext(Dispatchers.Default) {
        val app = application as KeyicApp
        val container = app.container
        if (clearFirst && container.session.isUnlocked.first()) {
            // Soft clear of entries only when already unlocked.
            container.sqlVaultRepository.replaceAll(emptyList())
        }
        val configured = container.session.isVaultConfigured.first()
        if (!configured) {
            container.vaultSession.setup(password.toCharArray(), "Demo vault")
        } else if (!container.session.isUnlocked.first()) {
            val ok = container.vaultSession.unlock(password.toCharArray())
            require(ok) {
                "Vault already exists but password is not '$DEFAULT_PASSWORD'. " +
                    "Clear app data first: adb shell pm clear com.yishenghuang.keyic"
            }
        }
        container.settingsRepository.update { it.copy(onboardingDone = true) }
        val csv = assets.open(ASSET_CSV).bufferedReader().use { it.readText() }
        val drafts = CsvImportParser.parse(csv)
        require(drafts.isNotEmpty()) { "Sample CSV parsed empty" }
        // Reset active vault contents so re-seeding is idempotent.
        container.sqlVaultRepository.replaceAll(emptyList())
        drafts.forEach { container.vaultRepository.create(it) }
        container.vaultSession.touch()
        "Imported ${drafts.size} sample entries (unlock with $DEFAULT_PASSWORD)"
    }

    companion object {
        private const val TAG = "DebugSeed"
        private const val ASSET_CSV = "keyic-sample-import.csv"
        const val DEFAULT_PASSWORD = "testdata1"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_CLEAR = "clear"
    }
}

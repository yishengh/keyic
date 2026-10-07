package com.yishenghuang.keyic.autofill

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.yishenghuang.keyic.KeyicApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Autofill is another vault UI, with the same screenshot and idle-lock protection. */
abstract class SecureAutofillActivity : FragmentActivity() {
    private var idleJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        idleJob = lifecycleScope.launch {
            val container = (application as KeyicApp).container
            while (isActive) {
                val timeout = container.settingsRepository.get().autoLockSeconds
                if (container.session.shouldAutoLock(SystemClock.elapsedRealtime(), timeout)) container.session.lock()
                delay(1_000)
            }
        }
    }

    override fun onPause() {
        idleJob?.cancel()
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as KeyicApp).onVaultInteraction()
    }
}

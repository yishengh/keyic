package com.yishenghuang.keyic

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.yishenghuang.keyic.backup.SafBackupWorker
import com.yishenghuang.keyic.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KeyicApp : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val secureClipboard by lazy { com.yishenghuang.keyic.ui.util.SecureClipboard(this, scope) }
    private var startedActivities = 0
    private var lockJob: Job? = null

    /** While > 0, skip lock-on-background (system file pickers / SAF). */
    @Volatile
    private var suppressBackgroundLock = 0

    fun beginExternalUi() {
        suppressBackgroundLock++
        lockJob?.cancel()
        lockJob = scope.launch {
            val seconds = container.settingsRepository.get().autoLockSeconds
            delay((if (seconds > 0) seconds else 60) * 1000L)
            if (startedActivities == 0) container.vaultSession.lock()
        }
    }

    fun endExternalUi() {
        suppressBackgroundLock = (suppressBackgroundLock - 1).coerceAtLeast(0)
    }

    fun onVaultInteraction() {
        scope.launch {
            val timeout = container.settingsRepository.get().autoLockSeconds
            if (container.session.shouldAutoLock(android.os.SystemClock.elapsedRealtime(), timeout)) {
                container.session.lock()
            } else container.session.touch()
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch(Dispatchers.IO) {
            val settings = container.settingsRepository.get()
            // Apply once after DataStore is ready — never from Compose with a default SYSTEM value.
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                com.yishenghuang.keyic.ui.locale.AppLocaleController.apply(settings.appLanguage)
            }
            SafBackupWorker.rescheduleFromSettings(
                this@KeyicApp,
                settings.safAutoBackupEnabled && settings.safTreeUri != null,
                settings.safBackupFrequency,
            )
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                lockJob?.cancel()
                lockJob = null
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    startedActivities = 0
                    lockJob?.cancel()
                    lockJob = scope.launch {
                        val settings = container.settingsRepository.get()
                        val timeout = when {
                            suppressBackgroundLock > 0 -> (if (settings.autoLockSeconds > 0) settings.autoLockSeconds else 60) * 1000L
                            settings.lockOnBackground -> 1_200L
                            settings.autoLockSeconds > 0 -> settings.autoLockSeconds * 1000L
                            else -> return@launch
                        }
                        delay(timeout)
                        if (startedActivities == 0) {
                            container.vaultSession.lock()
                        }
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) { secureClipboard.clearExpired() }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

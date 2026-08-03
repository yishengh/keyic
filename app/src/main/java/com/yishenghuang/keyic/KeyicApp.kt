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
    private var startedActivities = 0
    private var lockJob: Job? = null

    /** While > 0, skip lock-on-background (system file pickers / SAF). */
    @Volatile
    private var suppressBackgroundLock = 0

    fun beginExternalUi() {
        suppressBackgroundLock++
        lockJob?.cancel()
        lockJob = null
    }

    fun endExternalUi() {
        suppressBackgroundLock = (suppressBackgroundLock - 1).coerceAtLeast(0)
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
                        delay(1_200)
                        val settings = container.settingsRepository.get()
                        if (settings.lockOnBackground &&
                            startedActivities == 0 &&
                            suppressBackgroundLock == 0
                        ) {
                            container.vaultSession.lock()
                        }
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

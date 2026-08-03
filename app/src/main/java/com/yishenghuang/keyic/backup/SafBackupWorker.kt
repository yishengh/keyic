package com.yishenghuang.keyic.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.core.model.BackupFrequency
import com.yishenghuang.keyic.data.ScheduledBackupResult
import java.util.concurrent.TimeUnit

class SafBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? KeyicApp ?: return Result.success()
        return when (app.container.runScheduledSafBackup()) {
            ScheduledBackupResult.Success,
            ScheduledBackupResult.Skipped,
            -> Result.success()
            ScheduledBackupResult.RetryLater -> Result.retry()
            ScheduledBackupResult.PermanentFailure -> Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "keyic_saf_periodic_backup"

        fun schedule(context: Context, enabled: Boolean, frequency: BackupFrequency) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val hours = frequency.hours.coerceAtLeast(6)
            val request = PeriodicWorkRequestBuilder<SafBackupWorker>(hours, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun rescheduleFromSettings(context: Context, enabled: Boolean, frequency: BackupFrequency) {
            schedule(context, enabled, frequency)
        }
    }
}

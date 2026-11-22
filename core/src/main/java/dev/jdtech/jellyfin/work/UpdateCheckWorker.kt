package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.update.AppUpdateManager
import java.util.concurrent.TimeUnit
import timber.log.Timber

@HiltWorker
class UpdateCheckWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val appUpdateManager: AppUpdateManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
                appUpdateManager.checkForUpdates(force = false)
                Result.success()
            }
            .getOrElse { error ->
                Timber.w(error, "24h update check failed")
                Result.retry()
            }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "jellyx_auto_update_24h"

        fun schedulePeriodicCheck(workManager: WorkManager) {
            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request =
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}


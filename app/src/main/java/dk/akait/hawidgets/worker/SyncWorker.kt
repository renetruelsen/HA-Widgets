package dk.akait.hawidgets.worker

import android.content.Context
import dk.akait.hawidgets.data.reconcileWidgets
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dk.akait.hawidgets.data.EntityRepository
import java.util.concurrent.TimeUnit

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // Ryd forældreløse config-rækker (fjernede widgets) FØR pull, så vi ikke synker for dem.
        // Må ikke vælte sync ved fejl.
        runCatching { reconcileWidgets(applicationContext) }
        // Repository ejer pull + fan-out til widgets.
        val allOk = EntityRepository.refreshAll(applicationContext)
        return if (allOk) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "ha_entity_sync"
        private const val NOW_WORK_NAME = "ha_entity_sync_now"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Kør én gang nu — expedited, ingen net-constraint. Bruges efter config for at
         *  hente state og fan-out til widgets uden at blokere config-activity. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

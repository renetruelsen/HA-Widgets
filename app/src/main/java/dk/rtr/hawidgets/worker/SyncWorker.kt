package dk.rtr.hawidgets.worker

import android.content.Context
import dk.rtr.hawidgets.data.reconcileWidgets
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
import dk.rtr.hawidgets.data.EntityRepository
import dk.rtr.hawidgets.data.SecureStore
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

        /**
         * (Gen)planlægger den periodiske sync ud fra brugerens valgte interval
         * ([SecureStore.syncIntervalMinutes]). Idempotent: kaldes både ved app-start og hver gang
         * brugeren skifter interval i indstillingerne.
         *
         * - [SecureStore.SYNC_MANUAL] (0) → aflys det periodiske arbejde helt (kun tryk-på-widget
         *   opdaterer derefter; [runNow] er upåvirket).
         * - Ellers enqueue med [ExistingPeriodicWorkPolicy.UPDATE], så et NYT interval træder i kraft
         *   uden at nulstille den eksisterende tidsplan når intervallet er uændret (modsat KEEP, som
         *   ville ignorere et skift, og REPLACE, som ville nulstille timeren ved hver app-start).
         */
        fun schedule(context: Context) {
            val minutes = SecureStore.get(context).syncIntervalMinutes
            val workManager = WorkManager.getInstance(context)
            if (minutes <= SecureStore.SYNC_MANUAL) {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
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

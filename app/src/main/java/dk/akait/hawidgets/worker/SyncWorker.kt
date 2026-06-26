package dk.akait.hawidgets.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.data.db.AppDatabase
import androidx.glance.appwidget.GlanceAppWidgetManager
import dk.akait.hawidgets.widget.light.LightWidget
import java.util.concurrent.TimeUnit

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val store = SecureStore.get(applicationContext)
        val baseUrl = store.baseUrl ?: return Result.success()
        val token = store.token ?: return Result.success()

        val db = AppDatabase.get(applicationContext)
        val entityIds = db.entityWidgetDao().allEntityIds()
        if (entityIds.isEmpty()) return Result.success()

        val client = HaApiClient(baseUrl, token)
        var anyFailed = false

        for (entityId in entityIds) {
            val state = client.getState(entityId)
            if (state != null) db.entityStateDao().upsert(state)
            else anyFailed = true
        }

        GlanceAppWidgetManager(applicationContext)
            .getGlanceIds(LightWidget::class.java)
            .forEach { LightWidget().update(applicationContext, it) }

        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "ha_entity_sync"

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

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

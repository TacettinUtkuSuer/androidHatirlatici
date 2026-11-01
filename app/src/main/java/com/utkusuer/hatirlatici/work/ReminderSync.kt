package com.utkusuer.hatirlatici.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.utkusuer.hatirlatici.HatirlaticiApp
import java.util.concurrent.TimeUnit

/** WorkManager tetikleyicileri: anlık senkron ve güvenlik ağı olarak periyodik senkron. */
class ReminderSync(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun refreshNow() {
        workManager.enqueueUniqueWork(
            WORK_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderSyncWorker>().build(),
        )
    }

    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderSyncWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    private companion object {
        const val WORK_NOW = "reminder_refresh_now"
        const val WORK_PERIODIC = "reminder_refresh_periodic"
    }
}

class ReminderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as HatirlaticiApp).container.reminderEngine.refreshAll()
        return Result.success()
    }
}

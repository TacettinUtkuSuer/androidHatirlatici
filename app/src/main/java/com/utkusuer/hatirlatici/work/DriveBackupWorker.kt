package com.utkusuer.hatirlatici.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.utkusuer.hatirlatici.HatirlaticiApp
import com.utkusuer.hatirlatici.data.backup.DriveAuth
import com.utkusuer.hatirlatici.data.backup.DriveClient
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Haftalık otomatik Drive yedeği. Pil dostu: yalnızca internet varken ve pil düşük değilken,
 * tercihen şarjdayken çalışır. Hassas kart bilgileri otomatik yedeğe dahil edilmez (parola gerekir).
 */
class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as HatirlaticiApp).container
        val settings = container.settingsRepository
        if (!settings.driveState.first().autoBackup) return Result.success()
        val token = DriveAuth.silentToken(applicationContext) ?: return Result.success()
        return try {
            val json = container.backupRepository.export(password = null)
            val client = DriveClient(token)
            client.upload("hatirlatici-otomatik-${LocalDate.now()}.json", json)
            client.prune()
            settings.setDriveLastBackup(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "drive_auto_backup"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            wm.enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DriveBackupWorker>(7, TimeUnit.DAYS).setConstraints(constraints).build(),
            )
        }
    }
}

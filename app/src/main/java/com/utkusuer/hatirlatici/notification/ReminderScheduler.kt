package com.utkusuer.hatirlatici.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Her ödeme için "bir sonraki hatırlatma" alarmını kurar. Alarm çalınca
 * [ReminderAlarmReceiver] bildirimi gösterir ve bir sonrakini planlar (zincir).
 * WorkManager dakika hassasiyeti vermediği için saatli uyarılarda AlarmManager kullanılır.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(paymentId: Long, triggerAtMillis: Long) {
        val pi = pendingIntent(paymentId, create = true)!!
        if (canExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            // İzin yoksa birkaç dakikalık sapmayla çalışır.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancel(paymentId: Long) {
        pendingIntent(paymentId, create = false)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /** Gün dönümü tetikleyicisi; dakika hassasiyeti gerekmez. */
    fun scheduleDayTick(triggerAtMillis: Long) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_DAY_TICK)
        val pi = PendingIntent.getBroadcast(
            context, DAY_TICK_REQUEST, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    fun canExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(paymentId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(PaymentNotifier.EXTRA_PAYMENT_ID, paymentId)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, paymentId.toInt(), intent, flags)
    }

    companion object {
        const val ACTION_REMIND = "com.utkusuer.hatirlatici.action.REMIND"
        const val ACTION_DAY_TICK = "com.utkusuer.hatirlatici.action.DAY_TICK"
        private const val DAY_TICK_REQUEST = Int.MAX_VALUE
    }
}

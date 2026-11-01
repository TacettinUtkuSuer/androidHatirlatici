package com.utkusuer.hatirlatici.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Pil dostu zamanlama: tüm kayıtlar için TEK bir "sonraki hatırlatma" alarmı ve gece yarısı için
 * TEK bir esnek (inexact) alarm kurulur. Aynı saatteki hatırlatmalar tek uyanmada gönderilir.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Bir sonraki gün içi hatırlatma. [minuteOfDay] alarm çaldığında hangi saatin kayıtlarına bakılacağını söyler. */
    fun scheduleReminder(triggerAtMillis: Long, minuteOfDay: Int) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_MINUTE, minuteOfDay)
        val pi = PendingIntent.getBroadcast(
            context, REMINDER_REQUEST, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (canExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancelReminder() {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_REMIND)
        PendingIntent.getBroadcast(
            context, REMINDER_REQUEST, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /** Gün dönümü tetikleyicisi; esnek (inexact) — cihazı ayrıca uyandırmaz, sistem diğer işlerle birleştirir. */
    fun scheduleDayTick(triggerAtMillis: Long) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_DAY_TICK)
        val pi = PendingIntent.getBroadcast(
            context, DAY_TICK_REQUEST, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.set(AlarmManager.RTC, triggerAtMillis, pi)
    }

    fun canExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    companion object {
        const val ACTION_REMIND = "com.utkusuer.hatirlatici.action.REMIND"
        const val ACTION_DAY_TICK = "com.utkusuer.hatirlatici.action.DAY_TICK"
        const val EXTRA_MINUTE = "minute"
        private const val REMINDER_REQUEST = Int.MAX_VALUE - 1
        private const val DAY_TICK_REQUEST = Int.MAX_VALUE
    }
}

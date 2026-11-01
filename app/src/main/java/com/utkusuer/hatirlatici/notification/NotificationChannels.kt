package com.utkusuer.hatirlatici.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.utkusuer.hatirlatici.R

object NotificationChannels {
    /** Kalıcı, sessiz bildirim (bildirim çubuğunda zarif şekilde durur). */
    const val ONGOING = "ongoing_due"
    /** Gün içi saatli uyarılar (ses + heads-up). */
    const val REMINDER = "reminders"

    fun register(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(ONGOING, context.getString(R.string.channel_ongoing_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_ongoing_desc)
                    setShowBadge(true)
                },
                NotificationChannel(REMINDER, context.getString(R.string.channel_reminder_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_reminder_desc)
                    enableVibration(true)
                },
            ),
        )
    }
}

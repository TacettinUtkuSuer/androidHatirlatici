package com.utkusuer.hatirlatici.di

import android.content.Context
import com.utkusuer.hatirlatici.data.local.AppDatabase
import com.utkusuer.hatirlatici.data.repository.PaymentRepository
import com.utkusuer.hatirlatici.data.security.PinCipher
import com.utkusuer.hatirlatici.data.settings.SettingsRepository
import com.utkusuer.hatirlatici.notification.PaymentNotifier
import com.utkusuer.hatirlatici.notification.ReminderScheduler
import com.utkusuer.hatirlatici.work.ReminderEngine
import com.utkusuer.hatirlatici.work.ReminderSync

/** Basit manuel bağımlılık konteyneri (tek kullanıcılı uygulama için Hilt'e gerek yok). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val pinCipher by lazy { PinCipher() }
    val notifier by lazy { PaymentNotifier(appContext) }
    val scheduler by lazy { ReminderScheduler(appContext) }
    val reminderSync by lazy { ReminderSync(appContext) }

    val reminderEngine by lazy {
        ReminderEngine(database.paymentDao(), database.cycleDao(), settingsRepository, notifier, scheduler)
    }

    val paymentRepository by lazy {
        PaymentRepository(database.paymentDao(), database.cycleDao(), pinCipher, reminderSync)
    }
}

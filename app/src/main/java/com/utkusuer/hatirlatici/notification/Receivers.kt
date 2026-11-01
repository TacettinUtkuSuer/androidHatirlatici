package com.utkusuer.hatirlatici.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.utkusuer.hatirlatici.HatirlaticiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** goAsync ile kısa süreli coroutine çalıştırır (10 sn sınırı içinde). */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

private val Context.engine get() = (applicationContext as HatirlaticiApp).container.reminderEngine

/** Saatli hatırlatma alarmı ve gün dönümü tetikleyicisi. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderScheduler.ACTION_DAY_TICK) {
            runAsync { context.engine.refreshAll() }
            return
        }
        val paymentId = intent.getLongExtra(PaymentNotifier.EXTRA_PAYMENT_ID, -1L)
        if (paymentId < 0) return
        runAsync { context.engine.onReminderAlarm(paymentId) }
    }
}

/** Kullanıcı kalıcı bildirimi kaydırdıysa (Android 14+) ve ödeme hâlâ bekliyorsa geri getirir. */
class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val paymentId = intent.getLongExtra(PaymentNotifier.EXTRA_PAYMENT_ID, -1L)
        if (paymentId < 0) return
        runAsync { context.engine.refreshPayment(paymentId) }
    }
}

/** Yeniden başlatma, saat/saat dilimi değişikliği vb. sonrası alarmları yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as HatirlaticiApp).container.reminderSync.refreshNow()
    }
}

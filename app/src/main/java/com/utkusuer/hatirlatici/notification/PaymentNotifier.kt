package com.utkusuer.hatirlatici.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.utkusuer.hatirlatici.R
import com.utkusuer.hatirlatici.data.local.entity.EntryKind
import com.utkusuer.hatirlatici.data.local.entity.PaymentCycleEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.domain.Formatters
import com.utkusuer.hatirlatici.ui.MainActivity
import com.utkusuer.hatirlatici.ui.confirm.ConfirmPaidActivity
import java.time.LocalDate

/** Bildirimlerin oluşturulması ve kaldırılması. Tüm iş mantığı [com.utkusuer.hatirlatici.work.ReminderEngine]'dedir. */
class PaymentNotifier(private val context: Context) {

    private val nm = NotificationManagerCompat.from(context)

    fun showOngoing(payment: PaymentEntity, cycle: PaymentCycleEntity, today: LocalDate) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, NotificationChannels.ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titleOf(payment))
            .setContentText(bodyOf(payment, cycle, today))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent())
            .addAction(0, actionLabel(payment), confirmIntent(payment, cycle))
            // Android 14+ "ongoing" bildirimleri kaydırılabilir yaptı; silinirse anında yeniden gösterilir.
            .setDeleteIntent(dismissedIntent(payment.id))
            .build()
        nm.notify(ongoingId(payment.id), notification)
    }

    fun showReminder(payment: PaymentEntity, cycle: PaymentCycleEntity, today: LocalDate) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    if (payment.kind == EntryKind.INCOME) R.string.income_reminder_title else R.string.reminder_title,
                    titleOf(payment),
                ),
            )
            .setContentText(bodyOf(payment, cycle, today))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(0, actionLabel(payment), confirmIntent(payment, cycle))
            .build()
        nm.notify(reminderId(payment.id), notification)
    }

    fun cancelOngoing(paymentId: Long) = nm.cancel(ongoingId(paymentId))

    fun cancelAll(paymentId: Long) {
        nm.cancel(ongoingId(paymentId))
        nm.cancel(reminderId(paymentId))
    }

    private fun canPost(): Boolean =
        nm.areNotificationsEnabled() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun actionLabel(p: PaymentEntity) = context.getString(
        if (p.kind == EntryKind.INCOME) R.string.action_mark_received else R.string.action_mark_paid,
    )

    private fun titleOf(p: PaymentEntity) =
        listOfNotNull(p.title, p.institution?.takeIf { it.isNotBlank() }).joinToString(" · ")

    private fun bodyOf(p: PaymentEntity, c: PaymentCycleEntity, today: LocalDate): String {
        val end = LocalDate.ofEpochDay(c.windowEndEpochDay)
        val due = when {
            today.isAfter(end) && p.kind == EntryKind.INCOME ->
                context.getString(R.string.income_late_days, (today.toEpochDay() - end.toEpochDay()).toInt())
            today.isAfter(end) -> context.getString(R.string.overdue_days, (today.toEpochDay() - end.toEpochDay()).toInt())
            today == end -> context.getString(R.string.due_today)
            else -> context.getString(R.string.due_on, Formatters.shortDate(end))
        }
        val amount = p.amountMinor?.let { (if (p.kind == EntryKind.INCOME) "+" else "") + Formatters.money(it, p.currency) }
            ?: context.getString(R.string.amount_variable)
        return "$due · $amount"
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Bildirim aksiyonu doğrudan onay penceresini açar (Android 12+ trampoline kısıtına uygun). */
    private fun confirmIntent(p: PaymentEntity, c: PaymentCycleEntity): PendingIntent = PendingIntent.getActivity(
        context, c.id.toInt(),
        ConfirmPaidActivity.intent(context, c.id, p.title, p.kind == EntryKind.INCOME),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun dismissedIntent(paymentId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, paymentId.toInt(),
        Intent(context, NotificationDismissedReceiver::class.java).putExtra(EXTRA_PAYMENT_ID, paymentId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val EXTRA_PAYMENT_ID = "payment_id"
        fun ongoingId(paymentId: Long) = 100_000 + paymentId.toInt()
        fun reminderId(paymentId: Long) = 200_000 + paymentId.toInt()
    }
}

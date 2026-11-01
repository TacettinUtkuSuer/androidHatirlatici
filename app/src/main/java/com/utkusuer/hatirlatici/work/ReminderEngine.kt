package com.utkusuer.hatirlatici.work

import com.utkusuer.hatirlatici.data.local.dao.CycleDao
import com.utkusuer.hatirlatici.data.local.dao.PaymentDao
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentWithReminders
import com.utkusuer.hatirlatici.data.settings.AppSettings
import com.utkusuer.hatirlatici.data.settings.SettingsRepository
import com.utkusuer.hatirlatici.domain.CycleCalculator
import com.utkusuer.hatirlatici.notification.PaymentNotifier
import com.utkusuer.hatirlatici.notification.ReminderScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Bildirim sisteminin beyni. Tüm tetikleyiciler (Worker, alarm, boot, UI) buraya gelir;
 * durum her seferinde veritabanından yeniden hesaplanır (idempotent).
 *
 *  1. Dönemleri (önceki/bu/sonraki ay) üretir.
 *  2. Bildirim zamanı gelmiş & ödenmemiş dönem varsa kalıcı bildirimi gösterir, yoksa kaldırır.
 *  3. Bir sonraki gün içi hatırlatma saatine alarm kurar.
 */
class ReminderEngine(
    private val paymentDao: PaymentDao,
    private val cycleDao: CycleDao,
    private val settingsRepository: SettingsRepository,
    private val notifier: PaymentNotifier,
    private val scheduler: ReminderScheduler,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val mutex = Mutex()

    suspend fun refreshAll() = mutex.withLock {
        val settings = settingsRepository.current()
        paymentDao.getAllWithReminders().forEach { refreshLocked(it, settings) }
        scheduler.scheduleDayTick(nextDayTick())
    }

    suspend fun refreshPayment(paymentId: Long) = mutex.withLock {
        val pw = paymentDao.getWithReminders(paymentId)
        if (pw == null) {
            notifier.cancelAll(paymentId)
            scheduler.cancel(paymentId)
        } else {
            refreshLocked(pw, settingsRepository.current())
        }
    }

    /** Gün içi hatırlatma alarmı çaldığında. */
    suspend fun onReminderAlarm(paymentId: Long) = mutex.withLock {
        val pw = paymentDao.getWithReminders(paymentId) ?: run {
            scheduler.cancel(paymentId); return@withLock
        }
        val settings = settingsRepository.current()
        val today = LocalDate.now(zone())
        if (pw.payment.isActive && settings.remindersEnabled) {
            ensureCycles(pw.payment, today)
            cycleDao.getDueForPayment(paymentId, today.toEpochDay()).firstOrNull()?.let {
                notifier.showReminder(pw.payment, it, today)
            }
        }
        refreshLocked(pw, settings)
    }

    /** Çift onaydan sonra çağrılır: dönemi kapatır, bildirimleri kaldırır. */
    suspend fun markPaid(cycleId: Long, amountMinor: Long? = null) = mutex.withLock {
        val cycle = cycleDao.getById(cycleId) ?: return@withLock
        cycleDao.markPaid(cycleId, System.currentTimeMillis(), amountMinor)
        notifier.cancelAll(cycle.paymentId)
        paymentDao.getWithReminders(cycle.paymentId)?.let { refreshLocked(it, settingsRepository.current()) }
    }

    private suspend fun refreshLocked(pw: PaymentWithReminders, settings: AppSettings) {
        val payment = pw.payment
        if (!payment.isActive) {
            notifier.cancelAll(payment.id)
            scheduler.cancel(payment.id)
            return
        }

        val now = ZonedDateTime.now(zone())
        val today = now.toLocalDate()
        ensureCycles(payment, today)

        val pending = cycleDao.getPendingForPayment(payment.id)
        val due = pending.filter { it.notifyFromEpochDay <= today.toEpochDay() }

        val current = due.minByOrNull { it.windowEndEpochDay }
        if (current != null && settings.persistentNotificationsEnabled && payment.persistentNotification) {
            notifier.showOngoing(payment, current, today)
        } else {
            notifier.cancelOngoing(payment.id)
        }

        val times = (if (payment.useDefaultReminderTimes) settings.defaultReminderMinutes else pw.reminderTimes.map { it.minuteOfDay })
            .filter { it in 0 until MINUTES_PER_DAY }
            .distinct()
            .sorted()
        if (!settings.remindersEnabled || times.isEmpty() || pending.isEmpty()) {
            scheduler.cancel(payment.id)
            return
        }

        val firstDay = if (due.isNotEmpty()) today
        else maxOf(today, LocalDate.ofEpochDay(pending.minOf { it.notifyFromEpochDay }))
        scheduler.schedule(payment.id, nextTrigger(firstDay, times, now).toInstant().toEpochMilli())
    }

    private suspend fun ensureCycles(payment: PaymentEntity, today: LocalDate) {
        val month = YearMonth.from(today)
        for (m in listOf(month.minusMonths(1), month, month.plusMonths(1))) {
            CycleCalculator.cycleFor(payment, m)?.let { cycleDao.insertIgnore(it) }
        }
    }

    private fun nextTrigger(firstDay: LocalDate, sortedMinutes: List<Int>, now: ZonedDateTime): ZonedDateTime {
        var day = firstDay
        while (true) {
            for (m in sortedMinutes) {
                val candidate = day.atTime(m / 60, m % 60).atZone(zone())
                if (candidate.isAfter(now)) return candidate
            }
            day = day.plusDays(1)
        }
    }

    /** Gün dönümünde (00:01) yeni dönemlerin bildirimleri hemen görünsün. */
    private fun nextDayTick(): Long =
        LocalDate.now(zone()).plusDays(1).atTime(0, 1).atZone(zone()).toInstant().toEpochMilli()

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

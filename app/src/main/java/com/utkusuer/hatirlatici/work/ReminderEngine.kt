package com.utkusuer.hatirlatici.work

import com.utkusuer.hatirlatici.data.local.dao.CycleDao
import com.utkusuer.hatirlatici.data.local.dao.LogDao
import com.utkusuer.hatirlatici.data.local.dao.PaymentDao
import com.utkusuer.hatirlatici.data.local.entity.ActivityLogEntity
import com.utkusuer.hatirlatici.data.local.entity.CycleStatus
import com.utkusuer.hatirlatici.data.local.entity.EntryKind
import com.utkusuer.hatirlatici.data.local.entity.LogType
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentWithReminders
import com.utkusuer.hatirlatici.data.settings.AppSettings
import com.utkusuer.hatirlatici.data.settings.SettingsRepository
import com.utkusuer.hatirlatici.domain.CycleCalculator
import com.utkusuer.hatirlatici.domain.Formatters
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
 *  3. TÜM kayıtlar için en yakın hatırlatma saatine TEK alarm kurar (pil dostu).
 */
class ReminderEngine(
    private val paymentDao: PaymentDao,
    private val cycleDao: CycleDao,
    private val settingsRepository: SettingsRepository,
    private val notifier: PaymentNotifier,
    private val scheduler: ReminderScheduler,
    private val logDao: LogDao,
    /** Veri değiştiğinde (widget güncellemesi vb.). */
    private val onChanged: suspend () -> Unit = {},
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val mutex = Mutex()

    suspend fun refreshAll() {
        mutex.withLock { refreshAllLocked() }
        onChanged()
    }

    /** Kalıcı bildirim kaydırılınca vb. — tümünü yeniden hesaplamak birkaç kayıt için ucuzdur. */
    suspend fun refreshPayment(@Suppress("UNUSED_PARAMETER") paymentId: Long) = refreshAll()

    /** Ortak hatırlatma alarmı çaldığında: bu saate kurulu ve zamanı gelmiş tüm kayıtlar için uyarı. */
    suspend fun onReminderAlarm(minuteOfDay: Int?) {
        mutex.withLock {
            val settings = settingsRepository.current()
            if (settings.remindersEnabled && minuteOfDay != null) {
                val today = LocalDate.now(zone())
                for (pw in paymentDao.getAllWithReminders()) {
                    val p = pw.payment
                    if (!p.isActive || minuteOfDay !in timesOf(pw, settings)) continue
                    ensureCycles(p, today)
                    cycleDao.getDueForPayment(p.id, today.toEpochDay()).firstOrNull()?.let {
                        notifier.showReminder(p, it, today)
                    }
                }
            }
            refreshAllLocked()
        }
    }

    /** Çift onaydan sonra çağrılır: dönemi kapatır, loglar, bildirimleri kaldırır. */
    suspend fun markPaid(cycleId: Long, amountMinor: Long? = null) {
        mutex.withLock {
            val cycle = cycleDao.getById(cycleId) ?: return@withLock
            if (cycle.status == CycleStatus.PAID) return@withLock
            cycleDao.markPaid(cycleId, System.currentTimeMillis(), amountMinor)
            notifier.cancelAll(cycle.paymentId)
            paymentDao.getWithReminders(cycle.paymentId)?.payment?.let { p ->
                val period = YearMonth.parse(cycle.periodKey)
                logDao.insert(
                    ActivityLogEntity(
                        type = if (p.kind == EntryKind.INCOME) LogType.RECEIVED else LogType.PAID,
                        paymentId = p.id,
                        title = p.title,
                        detail = "${Formatters.monthYear(period)} dönemi · son gün ${Formatters.shortDate(LocalDate.ofEpochDay(cycle.windowEndEpochDay))}",
                        amountMinor = amountMinor ?: p.amountMinor,
                    ),
                )
            }
            refreshAllLocked()
        }
        onChanged()
    }

    private suspend fun refreshAllLocked() {
        val settings = settingsRepository.current()
        val now = ZonedDateTime.now(zone())
        var next: Pair<ZonedDateTime, Int>? = null
        for (pw in paymentDao.getAllWithReminders()) {
            val candidate = refreshOne(pw, settings, now) ?: continue
            if (next == null || candidate.first.isBefore(next.first)) next = candidate
        }
        if (next != null) {
            scheduler.scheduleReminder(next.first.toInstant().toEpochMilli(), next.second)
        } else {
            scheduler.cancelReminder()
        }
        scheduler.scheduleDayTick(nextDayTick())
    }

    /** Kalıcı bildirimi günceller ve bu kaydın bir sonraki hatırlatma zamanını döndürür. */
    private suspend fun refreshOne(pw: PaymentWithReminders, settings: AppSettings, now: ZonedDateTime): Pair<ZonedDateTime, Int>? {
        val payment = pw.payment
        if (!payment.isActive) {
            notifier.cancelAll(payment.id)
            return null
        }
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

        val times = timesOf(pw, settings)
        if (!settings.remindersEnabled || times.isEmpty() || pending.isEmpty()) return null
        val firstDay = if (due.isNotEmpty()) today
        else maxOf(today, LocalDate.ofEpochDay(pending.minOf { it.notifyFromEpochDay }))
        return nextTrigger(firstDay, times, now)
    }

    private fun timesOf(pw: PaymentWithReminders, settings: AppSettings): List<Int> =
        (if (pw.payment.useDefaultReminderTimes) settings.defaultReminderMinutes else pw.reminderTimes.map { it.minuteOfDay })
            .filter { it in 0 until MINUTES_PER_DAY }
            .distinct()
            .sorted()

    private suspend fun ensureCycles(payment: PaymentEntity, today: LocalDate) {
        val month = YearMonth.from(today)
        for (m in listOf(month.minusMonths(1), month, month.plusMonths(1))) {
            CycleCalculator.cycleFor(payment, m)?.let { cycleDao.insertIgnore(it) }
        }
    }

    private fun nextTrigger(firstDay: LocalDate, sortedMinutes: List<Int>, now: ZonedDateTime): Pair<ZonedDateTime, Int> {
        var day = firstDay
        while (true) {
            for (m in sortedMinutes) {
                val candidate = day.atTime(m / 60, m % 60).atZone(zone())
                if (candidate.isAfter(now)) return candidate to m
            }
            day = day.plusDays(1)
        }
    }

    /** Gün dönümünde (00:01) yeni dönemlerin kalıcı bildirimleri görünsün. */
    private fun nextDayTick(): Long =
        LocalDate.now(zone()).plusDays(1).atTime(0, 1).atZone(zone()).toInstant().toEpochMilli()

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

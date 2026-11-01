package com.utkusuer.hatirlatici.domain

import com.utkusuer.hatirlatici.data.local.entity.PaymentCycleEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.Recurrence
import com.utkusuer.hatirlatici.data.local.entity.ScheduleType
import com.utkusuer.hatirlatici.data.local.entity.WeekendRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Bir ödeme tanımından, verilen ay için dönem (pencere) hesaplar. Saf Kotlin; birim testlenebilir.
 */
object CycleCalculator {

    fun periodKey(month: YearMonth): String = month.toString() // "2026-11"

    fun cycleFor(payment: PaymentEntity, month: YearMonth): PaymentCycleEntity? {
        val window = windowFor(payment, month)
        val firstMonth = firstCycleMonth(payment)
        if (month.isBefore(firstMonth)) return null

        when (payment.recurrence) {
            Recurrence.ONE_TIME -> if (month != firstMonth) return null
            Recurrence.MONTHLY -> {
                payment.totalInstallments?.let { total ->
                    if (ChronoUnit.MONTHS.between(firstMonth, month) >= total) return null
                }
            }
        }
        payment.endDateEpochDay?.let { end ->
            if (window.first.toEpochDay() > end) return null
        }

        val notifyFrom = when (payment.scheduleType) {
            ScheduleType.SINGLE_DAY -> window.first.minusDays(payment.leadDays.coerceAtLeast(0).toLong())
            ScheduleType.DATE_RANGE -> minOf(
                window.first.plusDays(payment.reminderDelayDays.coerceAtLeast(0).toLong()),
                window.second,
            )
        }

        return PaymentCycleEntity(
            paymentId = payment.id,
            periodKey = periodKey(month),
            windowStartEpochDay = window.first.toEpochDay(),
            windowEndEpochDay = window.second.toEpochDay(),
            notifyFromEpochDay = notifyFrom.toEpochDay(),
        )
    }

    /**
     * (başlangıç, bitiş) — tek günlük ödemelerde ikisi aynıdır.
     * Hafta sonu kuralı son güne uygulanır; aralıkta başlangıç son günü geçemez.
     */
    fun windowFor(payment: PaymentEntity, month: YearMonth): Pair<LocalDate, LocalDate> =
        when (payment.scheduleType) {
            ScheduleType.SINGLE_DAY -> adjustForWeekend(month.clampDay(payment.dayStart), payment.weekendRule).let { it to it }
            ScheduleType.DATE_RANGE -> {
                val startDay = payment.dayStart
                val endDay = payment.dayEnd ?: payment.dayStart
                val start = month.clampDay(startDay)
                val rawEnd = if (endDay >= startDay) month.clampDay(endDay) else month.plusMonths(1).clampDay(endDay)
                val end = adjustForWeekend(rawEnd, payment.weekendRule)
                minOf(start, end) to end
            }
        }

    /**
     * Hafta sonu kuralı yüzünden son gün kaydırıldıysa asıl (kaydırılmamış) tarihi döndürür, değilse null.
     */
    fun originalDueIfShifted(payment: PaymentEntity, cycle: PaymentCycleEntity): LocalDate? {
        if (payment.weekendRule == WeekendRule.NONE) return null
        val month = runCatching { YearMonth.parse(cycle.periodKey) }.getOrNull() ?: return null
        val raw = windowFor(payment.copy(weekendRule = WeekendRule.NONE), month).second
        return raw.takeIf { it.toEpochDay() != cycle.windowEndEpochDay }
    }

    /** Cumartesi/Pazar'a denk gelen günü kurala göre iş gününe kaydırır. */
    fun adjustForWeekend(date: LocalDate, rule: WeekendRule): LocalDate {
        val dow = date.dayOfWeek
        if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) return date
        return when (rule) {
            WeekendRule.NONE -> date
            WeekendRule.BEFORE -> date.minusDays(if (dow == DayOfWeek.SATURDAY) 1 else 2)
            WeekendRule.AFTER -> date.plusDays(if (dow == DayOfWeek.SATURDAY) 2 else 1)
            WeekendRule.NEAREST -> if (dow == DayOfWeek.SATURDAY) date.minusDays(1) else date.plusDays(1)
        }
    }

    /** Penceresi başlangıç tarihinden sonra biten ilk ay. */
    fun firstCycleMonth(payment: PaymentEntity): YearMonth {
        val start = LocalDate.ofEpochDay(payment.startDateEpochDay)
        val month = YearMonth.from(start)
        return if (windowFor(payment, month).second.isBefore(start)) month.plusMonths(1) else month
    }

    private fun YearMonth.clampDay(day: Int): LocalDate = atDay(day.coerceIn(1, lengthOfMonth()))
}

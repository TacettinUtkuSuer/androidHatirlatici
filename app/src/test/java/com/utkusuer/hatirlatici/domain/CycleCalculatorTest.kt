package com.utkusuer.hatirlatici.domain

import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.Recurrence
import com.utkusuer.hatirlatici.data.local.entity.ScheduleType
import com.utkusuer.hatirlatici.data.local.entity.WeekendRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CycleCalculatorTest {

    private fun payment(
        type: ScheduleType,
        start: Int,
        end: Int? = null,
        delay: Int = 0,
        lead: Int = 0,
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        recurrence: Recurrence = Recurrence.MONTHLY,
        installments: Int? = null,
        weekend: WeekendRule = WeekendRule.NONE,
    ) = PaymentEntity(
        id = 1, title = "Test", scheduleType = type, dayStart = start, dayEnd = end,
        reminderDelayDays = delay, leadDays = lead, startDateEpochDay = startDate.toEpochDay(),
        recurrence = recurrence, totalInstallments = installments, weekendRule = weekend,
    )

    @Test fun singleDayClampsToMonthEnd() {
        val c = CycleCalculator.cycleFor(payment(ScheduleType.SINGLE_DAY, 31), YearMonth.of(2026, 2))!!
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), c.windowEndEpochDay)
        assertEquals("2026-02", c.periodKey)
    }

    @Test fun singleDayLeadDays() {
        val c = CycleCalculator.cycleFor(payment(ScheduleType.SINGLE_DAY, 10, lead = 3), YearMonth.of(2026, 3))!!
        assertEquals(LocalDate.of(2026, 3, 7).toEpochDay(), c.notifyFromEpochDay)
    }

    @Test fun rangeWithDelay() {
        val c = CycleCalculator.cycleFor(payment(ScheduleType.DATE_RANGE, 1, 15, delay = 10), YearMonth.of(2026, 4))!!
        assertEquals(LocalDate.of(2026, 4, 1).toEpochDay(), c.windowStartEpochDay)
        assertEquals(LocalDate.of(2026, 4, 15).toEpochDay(), c.windowEndEpochDay)
        assertEquals(LocalDate.of(2026, 4, 11).toEpochDay(), c.notifyFromEpochDay)
    }

    @Test fun delayNeverPassesWindowEnd() {
        val c = CycleCalculator.cycleFor(payment(ScheduleType.DATE_RANGE, 1, 5, delay = 30), YearMonth.of(2026, 4))!!
        assertEquals(c.windowEndEpochDay, c.notifyFromEpochDay)
    }

    @Test fun rangeWrapsIntoNextMonth() {
        val c = CycleCalculator.cycleFor(payment(ScheduleType.DATE_RANGE, 25, 10), YearMonth.of(2026, 12))!!
        assertEquals(LocalDate.of(2026, 12, 25).toEpochDay(), c.windowStartEpochDay)
        assertEquals(LocalDate.of(2027, 1, 10).toEpochDay(), c.windowEndEpochDay)
    }

    @Test fun noCycleBeforeStartDate() {
        val p = payment(ScheduleType.SINGLE_DAY, 5, startDate = LocalDate.of(2026, 6, 20))
        assertNull(CycleCalculator.cycleFor(p, YearMonth.of(2026, 6)))
        assertEquals(YearMonth.of(2026, 7), CycleCalculator.firstCycleMonth(p))
    }

    @Test fun installmentsStopAfterCount() {
        val p = payment(ScheduleType.SINGLE_DAY, 5, installments = 3)
        assertEquals("2026-03", CycleCalculator.cycleFor(p, YearMonth.of(2026, 3))!!.periodKey)
        assertNull(CycleCalculator.cycleFor(p, YearMonth.of(2026, 4)))
    }

    @Test fun oneTimeOnlyFirstMonth() {
        val p = payment(ScheduleType.SINGLE_DAY, 5, recurrence = Recurrence.ONE_TIME)
        assertNull(CycleCalculator.cycleFor(p, YearMonth.of(2026, 2)))
    }

    // 2026-11-07 Cumartesi, 2026-11-08 Pazar
    @Test fun weekendRules() {
        val sat = LocalDate.of(2026, 11, 7)
        val sun = LocalDate.of(2026, 11, 8)
        assertEquals(sat, CycleCalculator.adjustForWeekend(sat, WeekendRule.NONE))
        assertEquals(LocalDate.of(2026, 11, 6), CycleCalculator.adjustForWeekend(sat, WeekendRule.BEFORE))
        assertEquals(LocalDate.of(2026, 11, 6), CycleCalculator.adjustForWeekend(sun, WeekendRule.BEFORE))
        assertEquals(LocalDate.of(2026, 11, 9), CycleCalculator.adjustForWeekend(sat, WeekendRule.AFTER))
        assertEquals(LocalDate.of(2026, 11, 9), CycleCalculator.adjustForWeekend(sun, WeekendRule.AFTER))
        assertEquals(LocalDate.of(2026, 11, 6), CycleCalculator.adjustForWeekend(sat, WeekendRule.NEAREST))
        assertEquals(LocalDate.of(2026, 11, 9), CycleCalculator.adjustForWeekend(sun, WeekendRule.NEAREST))
        val weekday = LocalDate.of(2026, 11, 4)
        assertEquals(weekday, CycleCalculator.adjustForWeekend(weekday, WeekendRule.AFTER))
    }

    @Test fun weekendRuleAppliesToCycle() {
        val p = payment(ScheduleType.SINGLE_DAY, 8, lead = 2, weekend = WeekendRule.AFTER)
        val c = CycleCalculator.cycleFor(p, YearMonth.of(2026, 11))!!
        assertEquals(LocalDate.of(2026, 11, 9).toEpochDay(), c.windowEndEpochDay)
        assertEquals(LocalDate.of(2026, 11, 7).toEpochDay(), c.notifyFromEpochDay)
    }

    @Test fun rangeEndShiftedBefore() {
        val p = payment(ScheduleType.DATE_RANGE, 1, 8, weekend = WeekendRule.BEFORE)
        val c = CycleCalculator.cycleFor(p, YearMonth.of(2026, 11))!!
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), c.windowStartEpochDay)
        assertEquals(LocalDate.of(2026, 11, 6).toEpochDay(), c.windowEndEpochDay)
    }
}

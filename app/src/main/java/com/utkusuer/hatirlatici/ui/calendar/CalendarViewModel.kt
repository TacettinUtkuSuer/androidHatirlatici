package com.utkusuer.hatirlatici.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkusuer.hatirlatici.data.local.entity.CycleStatus
import com.utkusuer.hatirlatici.data.local.entity.PaymentCycleEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.repository.PaymentRepository
import com.utkusuer.hatirlatici.domain.CycleCalculator
import com.utkusuer.hatirlatici.work.ReminderEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Takvimdeki bir dönem. [stored] = veritabanında var (ödendi bilgisi gerçek);
 * değilse ileriki aylar için hesaplanmış bir projeksiyondur.
 */
data class CalendarEntry(
    val payment: PaymentEntity,
    val cycle: PaymentCycleEntity,
    val stored: Boolean,
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(cycle.windowStartEpochDay)
    val end: LocalDate get() = LocalDate.ofEpochDay(cycle.windowEndEpochDay)
    val isPaid get() = cycle.status == CycleStatus.PAID
    fun covers(day: LocalDate) = !day.isBefore(start) && !day.isAfter(end)
}

data class CalendarState(
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val entries: List<CalendarEntry> = emptyList(),
) {
    fun entriesOn(day: LocalDate) = entries.filter { it.covers(day) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: PaymentRepository,
    private val engine: ReminderEngine,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())

    private val storedCycles = month.flatMapLatest { m ->
        repository.observeCycles(m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay())
    }

    val state: StateFlow<CalendarState> =
        combine(month, selected, repository.observePayments(), storedCycles) { m, sel, payments, stored ->
            val first = m.atDay(1).toEpochDay()
            val last = m.atEndOfMonth().toEpochDay()
            val storedEntries = stored.map { CalendarEntry(it.payment, it.cycle, stored = true) }
            val storedKeys = stored.map { it.cycle.paymentId to it.cycle.periodKey }.toSet()
            val projected = payments.asSequence()
                .map { it.payment }
                .filter { it.isActive }
                .flatMap { p -> listOf(m.minusMonths(1), m).mapNotNull { mm -> CycleCalculator.cycleFor(p, mm)?.let { p to it } } }
                .filter { (p, c) -> (p.id to c.periodKey) !in storedKeys }
                .filter { (_, c) -> c.windowStartEpochDay <= last && c.windowEndEpochDay >= first }
                .map { (p, c) -> CalendarEntry(p, c, stored = false) }
                .toList()
            // Pasif kayıtların yalnızca ödenmiş geçmişi gösterilir.
            val visibleStored = storedEntries.filter { it.payment.isActive || it.isPaid }
            CalendarState(m, sel, (visibleStored + projected).sortedBy { it.cycle.windowEndEpochDay })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun previousMonth() = changeMonth(month.value.minusMonths(1))
    fun nextMonth() = changeMonth(month.value.plusMonths(1))
    fun goToday() {
        month.value = YearMonth.now()
        selected.value = LocalDate.now()
    }

    fun select(day: LocalDate) {
        selected.value = day
        if (YearMonth.from(day) != month.value) month.value = YearMonth.from(day)
    }

    private fun changeMonth(m: YearMonth) {
        month.value = m
        selected.value = if (m == YearMonth.now()) LocalDate.now() else m.atDay(1)
    }

    fun markPaid(cycleId: Long) {
        viewModelScope.launch { engine.markPaid(cycleId) }
    }

    fun markUnpaid(cycleId: Long) {
        viewModelScope.launch { repository.markUnpaid(cycleId) }
    }
}

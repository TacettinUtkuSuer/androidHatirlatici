package com.utkusuer.hatirlatici.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkusuer.hatirlatici.data.local.entity.CycleWithPayment
import com.utkusuer.hatirlatici.data.local.entity.EntryKind
import com.utkusuer.hatirlatici.data.repository.PaymentRepository
import com.utkusuer.hatirlatici.data.settings.SettingsRepository
import com.utkusuer.hatirlatici.work.ReminderEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardState(
    val today: LocalDate = LocalDate.now(),
    val overdue: List<CycleWithPayment> = emptyList(),
    val current: List<CycleWithPayment> = emptyList(),
    val upcoming: List<CycleWithPayment> = emptyList(),
    val incomes: List<CycleWithPayment> = emptyList(),
    val upcomingDays: Int = 14,
    val loading: Boolean = true,
) {
    val isEmpty get() = overdue.isEmpty() && current.isEmpty() && upcoming.isEmpty() && incomes.isEmpty()
    /** Şu an ödenmesi gerekenlerin bilinen tutar toplamı (kuruş) ve tutarı değişken olan sayısı. */
    val dueNow get() = overdue + current
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    repository: PaymentRepository,
    settingsRepository: SettingsRepository,
    private val engine: ReminderEngine,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    val state: StateFlow<DashboardState> =
        combine(settingsRepository.settings, today) { s, t -> s.upcomingWindowDays to t }
            .flatMapLatest { (days, t) ->
                repository.observeOpenCycles(t.plusDays(days.toLong()).toEpochDay()).map { all ->
                    val todayEpoch = t.toEpochDay()
                    val (incomes, list) = all.partition { it.payment.kind == EntryKind.INCOME }
                    val (overdue, rest) = list.partition { it.cycle.windowEndEpochDay < todayEpoch }
                    val (current, upcoming) = rest.partition { it.cycle.windowStartEpochDay <= todayEpoch }
                    DashboardState(t, overdue, current, upcoming, incomes, days, loading = false)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun onResume() {
        today.value = LocalDate.now()
    }

    fun markPaid(cycleId: Long) {
        viewModelScope.launch { engine.markPaid(cycleId) }
    }
}

package com.utkusuer.hatirlatici.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkusuer.hatirlatici.data.local.entity.PaymentCategory
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.Recurrence
import com.utkusuer.hatirlatici.data.local.entity.ScheduleType
import com.utkusuer.hatirlatici.data.repository.PaymentRepository
import com.utkusuer.hatirlatici.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/** Form alanları metin olarak tutulur; doğrulama kaydederken yapılır. */
data class EditForm(
    val title: String = "",
    val institution: String = "",
    val category: PaymentCategory = PaymentCategory.CREDIT_CARD,
    val scheduleType: ScheduleType = ScheduleType.DATE_RANGE,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val dayStart: String = "",
    val dayEnd: String = "",
    val delayDays: String = "0",
    val leadDays: String = "0",
    val amount: String = "",
    val installments: String = "",
    val isActive: Boolean = true,
    val persistent: Boolean = true,
    val useDefaultTimes: Boolean = true,
    val customTimes: List<Int> = emptyList(),
    val cardLast4: String = "",
    val note: String = "",
    val hasStoredPin: Boolean = false,
    val newPin: String = "",
    val removePin: Boolean = false,
)

data class EditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: EditForm = EditForm(),
    val errors: Map<String, String> = emptyMap(),
    val defaultTimes: List<Int> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
)

class EditPaymentViewModel(
    private val paymentId: Long,
    private val repository: PaymentRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state
    private var original: PaymentEntity? = null

    init {
        viewModelScope.launch {
            val defaults = settingsRepository.current().defaultReminderMinutes
            val existing = if (paymentId > 0) repository.observePayment(paymentId).first() else null
            original = existing?.payment
            val form = existing?.let { pw ->
                val p = pw.payment
                EditForm(
                    title = p.title,
                    institution = p.institution.orEmpty(),
                    category = p.category,
                    scheduleType = p.scheduleType,
                    recurrence = p.recurrence,
                    dayStart = p.dayStart.toString(),
                    dayEnd = p.dayEnd?.toString().orEmpty(),
                    delayDays = p.reminderDelayDays.toString(),
                    leadDays = p.leadDays.toString(),
                    amount = p.amountMinor?.let(::formatAmountInput).orEmpty(),
                    installments = p.totalInstallments?.toString().orEmpty(),
                    isActive = p.isActive,
                    persistent = p.persistentNotification,
                    useDefaultTimes = p.useDefaultReminderTimes,
                    customTimes = pw.reminderTimes.map { it.minuteOfDay }.sorted().ifEmpty { defaults },
                    cardLast4 = p.cardLast4.orEmpty(),
                    note = p.note.orEmpty(),
                    hasStoredPin = p.encryptedPin != null,
                )
            } ?: EditForm(customTimes = defaults)
            _state.value = EditUiState(loading = false, isNew = existing == null, form = form, defaultTimes = defaults)
        }
    }

    fun update(transform: (EditForm) -> EditForm) {
        _state.update { it.copy(form = transform(it.form), errors = emptyMap()) }
    }

    fun addTime(minute: Int) = update { it.copy(customTimes = (it.customTimes + minute).distinct().sorted()) }
    fun removeTime(minute: Int) = update { it.copy(customTimes = it.customTimes - minute) }

    fun save() {
        val f = _state.value.form
        val errors = mutableMapOf<String, String>()
        if (f.title.isBlank()) errors["title"] = "Bir ad girin"
        val dayStart = f.dayStart.toIntOrNull()?.takeIf { it in 1..31 }
        if (dayStart == null) errors["dayStart"] = "1–31 arası"
        val dayEnd = if (f.scheduleType == ScheduleType.DATE_RANGE) {
            f.dayEnd.toIntOrNull()?.takeIf { it in 1..31 }.also { if (it == null) errors["dayEnd"] = "1–31 arası" }
        } else null
        val delay = f.delayDays.ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..60 }
        if (delay == null) errors["delayDays"] = "0–60 arası"
        val lead = f.leadDays.ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..30 }
        if (lead == null) errors["leadDays"] = "0–30 arası"
        val amount = if (f.amount.isBlank()) null else parseAmount(f.amount).also { if (it == null) errors["amount"] = "Geçersiz tutar" }
        val installments = if (f.installments.isBlank()) null else f.installments.toIntOrNull()?.takeIf { it in 1..600 }
            .also { if (it == null) errors["installments"] = "Geçersiz sayı" }
        if (f.cardLast4.isNotEmpty() && !(f.cardLast4.length == 4 && f.cardLast4.all(Char::isDigit))) {
            errors["cardLast4"] = "4 rakam girin"
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        val now = System.currentTimeMillis()
        val base = original ?: PaymentEntity(
            title = "", scheduleType = f.scheduleType, dayStart = 1,
            startDateEpochDay = LocalDate.now().toEpochDay(), createdAt = now,
        )
        val entity = base.copy(
            title = f.title.trim(),
            institution = f.institution.trim().ifBlank { null },
            category = f.category,
            scheduleType = f.scheduleType,
            recurrence = f.recurrence,
            dayStart = dayStart!!,
            dayEnd = dayEnd,
            reminderDelayDays = if (f.scheduleType == ScheduleType.DATE_RANGE) delay!! else 0,
            leadDays = if (f.scheduleType == ScheduleType.SINGLE_DAY) lead!! else 0,
            amountMinor = amount,
            totalInstallments = if (f.recurrence == Recurrence.MONTHLY) installments else null,
            isActive = f.isActive,
            persistentNotification = f.persistent,
            useDefaultReminderTimes = f.useDefaultTimes,
            cardLast4 = f.cardLast4.ifBlank { null },
            note = f.note.trim().ifBlank { null },
        )
        val pinArg = when {
            f.newPin.isNotEmpty() -> f.newPin
            f.removePin -> ""
            else -> null
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.save(entity, if (f.useDefaultTimes) emptyList() else f.customTimes, pinArg)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    fun delete() {
        val p = original ?: return
        viewModelScope.launch {
            repository.delete(p)
            _state.update { it.copy(finished = true) }
        }
    }

    companion object {
        /** "1.250,50", "1250,5", "1250.50", "1.250", "1250" → kuruş. */
        fun parseAmount(raw: String): Long? {
            val s = raw.trim().replace(" ", "").replace("₺", "")
            val normalized = when {
                s.contains(',') -> s.replace(".", "").replace(',', '.')
                // "1.250" / "12.500.000": nokta binlik ayırıcı
                Regex("""\d{1,3}(\.\d{3})+""").matches(s) -> s.replace(".", "")
                else -> s
            }
            return normalized.toBigDecimalOrNull()
                ?.takeIf { it.signum() > 0 }
                ?.movePointRight(2)
                ?.setScale(0, java.math.RoundingMode.HALF_UP)
                ?.toLong()
        }

        fun formatAmountInput(minor: Long): String =
            BigDecimal.valueOf(minor, 2).toPlainString().replace('.', ',').removeSuffix(",00")
    }
}

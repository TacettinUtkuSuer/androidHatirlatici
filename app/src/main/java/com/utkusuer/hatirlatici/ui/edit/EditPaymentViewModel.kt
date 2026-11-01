package com.utkusuer.hatirlatici.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkusuer.hatirlatici.data.local.entity.EntryKind
import com.utkusuer.hatirlatici.data.local.entity.PaymentCategory
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.Recurrence
import com.utkusuer.hatirlatici.data.local.entity.ScheduleType
import com.utkusuer.hatirlatici.data.local.entity.WeekendRule
import com.utkusuer.hatirlatici.data.repository.PaymentRepository
import com.utkusuer.hatirlatici.data.security.CardSecrets
import com.utkusuer.hatirlatici.data.settings.SettingsRepository
import com.utkusuer.hatirlatici.domain.Banks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/** Form alanları metin olarak tutulur; doğrulama kaydederken yapılır. */
data class EditForm(
    val kind: EntryKind = EntryKind.EXPENSE,
    val title: String = "",
    val institution: String = "",
    val bankCode: String? = null,
    val category: PaymentCategory = PaymentCategory.CREDIT_CARD,
    val weekendRule: WeekendRule = WeekendRule.NONE,
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
    /** Kayıtlı hassas bilgi var ve henüz kimlik doğrulamasıyla açılmadı. */
    val secretsLocked: Boolean = false,
    /** Kullanıcı bu ekranda hassas alanlara dokundu mu (dokunmadıysa kayıtta değişmez). */
    val secretsTouched: Boolean = false,
    val secrets: CardSecrets = CardSecrets(),
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
                    kind = p.kind,
                    title = p.title,
                    institution = p.institution.orEmpty(),
                    bankCode = p.bankCode,
                    category = p.category,
                    weekendRule = p.weekendRule,
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
                    secretsLocked = p.encryptedSecrets != null || p.encryptedPin != null,
                )
            } ?: EditForm(customTimes = defaults)
            _state.value = EditUiState(loading = false, isNew = existing == null, form = form, defaultTimes = defaults)
        }
    }

    fun update(transform: (EditForm) -> EditForm) {
        _state.update { it.copy(form = transform(it.form), errors = emptyMap()) }
    }

    fun setKind(kind: EntryKind) = update {
        if (it.kind == kind) it
        else it.copy(
            kind = kind,
            category = if (kind == EntryKind.INCOME) PaymentCategory.SALARY else PaymentCategory.CREDIT_CARD,
            scheduleType = if (kind == EntryKind.INCOME) ScheduleType.SINGLE_DAY else it.scheduleType,
        )
    }

    fun selectBank(code: String?) = update { f ->
        val oldName = Banks.find(f.bankCode)?.name
        val newName = Banks.find(code)?.name
        // Kurum alanı boşsa veya önceki bankanın adıysa yeni bankayla doldur.
        val institution = if (f.institution.isBlank() || f.institution == oldName) newName.orEmpty() else f.institution
        f.copy(bankCode = code, institution = institution)
    }

    fun updateSecrets(transform: (CardSecrets) -> CardSecrets) = update {
        it.copy(secrets = transform(it.secrets), secretsTouched = true)
    }

    /** Kimlik doğrulaması başarılı olduktan sonra çağrılır. */
    fun unlockSecrets() {
        val p = original ?: return
        val revealed = runCatching { repository.revealSecrets(p) }.getOrElse { CardSecrets() }
        update { it.copy(secrets = revealed, secretsLocked = false) }
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
        val cardDigits = f.secrets.cardNumber.filter(Char::isDigit)
        if (cardDigits.isNotEmpty() && cardDigits.length !in 15..19) errors["cardNumber"] = "15–19 hane olmalı"
        if (f.secrets.expiry.isNotEmpty() && !Regex("""(0[1-9]|1[0-2])/\d{2}""").matches(f.secrets.expiry)) {
            errors["expiry"] = "AA/YY biçiminde"
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
            kind = f.kind,
            title = f.title.trim(),
            institution = f.institution.trim().ifBlank { null },
            bankCode = f.bankCode,
            category = f.category,
            weekendRule = f.weekendRule,
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
        val secretsArg = if (f.secretsTouched && !f.secretsLocked) {
            f.secrets.copy(cardNumber = cardDigits)
        } else null
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.save(entity, if (f.useDefaultTimes) emptyList() else f.customTimes, secretsArg)
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

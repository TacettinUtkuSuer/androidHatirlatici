package com.utkusuer.hatirlatici.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.utkusuer.hatirlatici.HatirlaticiApp
import com.utkusuer.hatirlatici.R
import com.utkusuer.hatirlatici.data.local.entity.PaymentCategory
import com.utkusuer.hatirlatici.data.local.entity.PaymentCycleEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.Recurrence
import com.utkusuer.hatirlatici.data.local.entity.ScheduleType
import com.utkusuer.hatirlatici.di.AppContainer
import com.utkusuer.hatirlatici.domain.Formatters
import java.time.LocalDate

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as HatirlaticiApp).container

/** Geniş ekranlarda (tablet, katlanabilir, yatay) içeriği okunabilir genişlikte ortalar. */
@Composable
fun ContentWidth(maxWidth: Dp = 720.dp, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Formatters.TR),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

/** "Ödemeyi Yaptım" çift onayı — hem uygulama içinde hem bildirimden kullanılır. */
@Composable
fun ConfirmPaidDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_paid_title)) },
        text = { Text(stringResource(R.string.confirm_paid_body, title)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm_yes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm_no)) } },
    )
}

fun PaymentCategory.label(): String = when (this) {
    PaymentCategory.CREDIT_CARD -> "Kredi kartı"
    PaymentCategory.BILL -> "Fatura"
    PaymentCategory.LOAN -> "Kredi"
    PaymentCategory.INSTALLMENT -> "Taksit"
    PaymentCategory.SUBSCRIPTION -> "Abonelik"
    PaymentCategory.RENT -> "Kira"
    PaymentCategory.OTHER -> "Diğer"
}

fun PaymentEntity.amountText(): String =
    amountMinor?.let { Formatters.money(it, currency) } ?: "Tutar değişken"

fun PaymentEntity.scheduleText(): String {
    val base = when (scheduleType) {
        ScheduleType.SINGLE_DAY -> "Ayın ${dayStart}. günü"
        ScheduleType.DATE_RANGE -> "Ayın ${dayStart}–${dayEnd ?: dayStart} arası"
    }
    return when (recurrence) {
        Recurrence.MONTHLY -> "Her ay · $base" + (totalInstallments?.let { " · $it taksit" } ?: "")
        Recurrence.ONE_TIME -> "Tek sefer · $base"
    }
}

fun PaymentEntity.displayTitle(): String =
    listOfNotNull(title, institution?.takeIf { it.isNotBlank() }).joinToString(" · ")

/** Durum metni ve gecikmiş olup olmadığı. */
fun PaymentCycleEntity.dueText(today: LocalDate): Pair<String, Boolean> {
    val start = LocalDate.ofEpochDay(windowStartEpochDay)
    val end = LocalDate.ofEpochDay(windowEndEpochDay)
    val left = end.toEpochDay() - today.toEpochDay()
    return when {
        left < 0 -> "${-left} gün gecikti · son gün ${Formatters.shortDate(end)}" to true
        left == 0L -> "Son gün bugün" to false
        !today.isBefore(start) -> "Son gün ${Formatters.shortDate(end)} · $left gün kaldı" to false
        start == end -> "${Formatters.shortDate(end)} · $left gün sonra" to false
        else -> "${Formatters.shortDate(start)} – ${Formatters.shortDate(end)}" to false
    }
}

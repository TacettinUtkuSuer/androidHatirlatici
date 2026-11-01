package com.utkusuer.hatirlatici.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.utkusuer.hatirlatici.domain.Formatters
import com.utkusuer.hatirlatici.ui.components.BankBadge
import com.utkusuer.hatirlatici.ui.components.Banks
import com.utkusuer.hatirlatici.ui.components.ConfirmPaidDialog
import com.utkusuer.hatirlatici.ui.components.isIncome
import com.utkusuer.hatirlatici.ui.components.SectionHeader
import com.utkusuer.hatirlatici.ui.components.amountText
import com.utkusuer.hatirlatici.ui.components.appContainer
import com.utkusuer.hatirlatici.ui.components.dueText
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val WEEK_DAYS = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(widthSizeClass: WindowWidthSizeClass, onOpenPayment: (Long) -> Unit) {
    val container = appContainer()
    val vm: CalendarViewModel = viewModel { CalendarViewModel(container.paymentRepository, container.reminderEngine) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<CalendarEntry?>(null) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Takvim") },
                actions = { TextButton(onClick = vm::goToday) { Text("Bugün") } },
            )
        },
    ) { padding ->
        val grid: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                MonthHeader(state.month, vm::previousMonth, vm::nextMonth)
                MonthGrid(state, today, vm::select)
                Legend()
            }
        }
        val details: @Composable (Modifier) -> Unit = { modifier ->
            DayDetails(
                state = state,
                today = today,
                modifier = modifier,
                onOpen = onOpenPayment,
                onPaid = { confirming = it },
                onUnpaid = { vm.markUnpaid(it.cycle.id) },
            )
        }

        if (widthSizeClass == WindowWidthSizeClass.Expanded) {
            Row(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                grid(Modifier.weight(1f).widthIn(max = 560.dp).verticalScroll(rememberScrollState()))
                details(Modifier.weight(1f).verticalScroll(rememberScrollState()))
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                ) {
                    grid(Modifier)
                    details(Modifier)
                }
            }
        }
    }

    confirming?.let { entry ->
        ConfirmPaidDialog(
            title = entry.payment.title,
            isIncome = entry.payment.isIncome,
            onConfirm = { vm.markPaid(entry.cycle.id); confirming = null },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Önceki ay") }
        Text(
            DateTimeFormatter.ofPattern("LLLL yyyy", Formatters.TR).format(month)
                .replaceFirstChar { it.titlecase(Formatters.TR) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sonraki ay") }
    }
}

@Composable
private fun MonthGrid(state: CalendarState, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val month = state.month
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells = leading + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Row(Modifier.fillMaxWidth()) {
        WEEK_DAYS.forEach {
            Text(
                it, Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    for (r in 0 until rows) {
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                val dayNum = r * 7 + c - leading + 1
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (dayNum in 1..month.lengthOfMonth()) {
                        val day = month.atDay(dayNum)
                        DayCell(
                            day = day,
                            entries = state.entriesOn(day),
                            isToday = day == today,
                            isSelected = day == state.selected,
                            onClick = { onSelect(day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    entries: List<CalendarEntry>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val unpaid = entries.filterNot { it.isPaid }
    val dueHere = entries.filter { it.end == day }
    val inRange = unpaid.any { it.start != it.end }
    val overdue = dueHere.any { !it.isPaid && !it.payment.isIncome && day.isBefore(LocalDate.now()) }

    Column(
        Modifier
            .padding(2.dp)
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) colors.primary else if (inRange) colors.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, colors.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || dueHere.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) colors.onPrimary else colors.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(5.dp)) {
            dueHere.take(3).forEach { e ->
                val dotColor = when {
                    isSelected -> colors.onPrimary
                    e.isPaid -> colors.outline
                    overdue -> colors.error
                    else -> colors.secondary
                }
                if (e.payment.isIncome) {
                    Box(Modifier.size(5.dp).border(1.dp, dotColor, CircleShape))
                } else {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor))
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(colors.secondary, "Son gün")
        LegendItem(colors.outline, "Ödendi")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).border(1.dp, colors.secondary, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("Gelir", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 14.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceVariant))
            Spacer(Modifier.width(6.dp))
            Text("Ödeme aralığı", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayDetails(
    state: CalendarState,
    today: LocalDate,
    modifier: Modifier,
    onOpen: (Long) -> Unit,
    onPaid: (CalendarEntry) -> Unit,
    onUnpaid: (CalendarEntry) -> Unit,
) {
    val entries = state.entriesOn(state.selected)
    Column(modifier) {
        SectionHeader(DateTimeFormatter.ofPattern("d MMMM EEEE", Formatters.TR).format(state.selected))
        if (entries.isEmpty()) {
            Text("Bu güne ait ödeme yok.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entries.forEach { e ->
            val income = e.payment.isIncome
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(e.payment.id) }.heightIn(min = 56.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BankBadge(Banks.find(e.payment.bankCode), e.payment.title, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.payment.title, style = MaterialTheme.typography.titleMedium)
                    val (due, overdue) = e.cycle.dueText(today, income)
                    Text(
                        if (e.isPaid) (if (income) "Geldi" else "Ödendi") else due,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            e.isPaid -> MaterialTheme.colorScheme.onSurfaceVariant
                            overdue && !income -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.secondary
                        },
                    )
                    Text(e.payment.amountText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    !e.stored -> Unit // ileriki ay projeksiyonu: henüz işaretlenemez
                    e.isPaid -> TextButton(onClick = { onUnpaid(e) }) { Text("Geri al") }
                    else -> TextButton(onClick = { onPaid(e) }) { Text(if (income) "Geldi" else "Ödedim") }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

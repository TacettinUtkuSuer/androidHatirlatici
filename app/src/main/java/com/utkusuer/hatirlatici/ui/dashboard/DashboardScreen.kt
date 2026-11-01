package com.utkusuer.hatirlatici.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.utkusuer.hatirlatici.data.local.entity.CycleWithPayment
import com.utkusuer.hatirlatici.domain.CycleCalculator
import com.utkusuer.hatirlatici.domain.Formatters
import com.utkusuer.hatirlatici.ui.components.BankBadge
import com.utkusuer.hatirlatici.domain.Banks
import com.utkusuer.hatirlatici.ui.components.ConfirmPaidDialog
import com.utkusuer.hatirlatici.ui.components.isIncome
import com.utkusuer.hatirlatici.ui.components.ContentWidth
import com.utkusuer.hatirlatici.ui.components.SectionHeader
import com.utkusuer.hatirlatici.ui.components.amountText
import com.utkusuer.hatirlatici.ui.components.appContainer
import com.utkusuer.hatirlatici.ui.components.dueText
import com.utkusuer.hatirlatici.ui.components.label
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onAdd: () -> Unit, onOpenPayment: (Long) -> Unit) {
    val container = appContainer()
    val vm: DashboardViewModel = viewModel {
        DashboardViewModel(container.paymentRepository, container.settingsRepository, container.reminderEngine)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    var confirming by remember { mutableStateOf<CycleWithPayment?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Ödemeler")
                        Text(
                            DateTimeFormatter.ofPattern("d MMMM, EEEE", Formatters.TR).format(state.today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Yeni kayıt") }
        },
    ) { padding ->
        ContentWidth {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
            ) {
                if (!state.loading) item { Summary(state) }
                if (!state.loading && state.isEmpty) {
                    item {
                        Text(
                            "Şu an bekleyen veya yaklaşan ödeme yok.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 32.dp),
                        )
                    }
                }
                section("Gecikmiş", state.overdue, state.today, onOpenPayment) { confirming = it }
                section("Ödeme zamanı", state.current, state.today, onOpenPayment) { confirming = it }
                section("Yaklaşan · ${state.upcomingDays} gün", state.upcoming, state.today, onOpenPayment) { confirming = it }
                section("Beklenen gelirler", state.incomes, state.today, onOpenPayment) { confirming = it }
            }
        }
    }

    confirming?.let { item ->
        ConfirmPaidDialog(
            title = item.payment.title,
            isIncome = item.payment.isIncome,
            onConfirm = {
                vm.markPaid(item.cycle.id)
                confirming = null
            },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun Summary(state: DashboardState) {
    val due = state.dueNow
    val known = due.mapNotNull { it.payment.amountMinor }.sum()
    val variable = due.count { it.payment.amountMinor == null }
    val text = when {
        due.isEmpty() -> "Şu an ödenmesi gereken bir şey yok."
        else -> buildString {
            append("${due.size} ödeme bekliyor")
            if (known > 0) append(" · ${Formatters.money(known, "TRY")}")
            if (variable > 0) append(" + $variable değişken tutar")
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (state.overdue.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun LazyListScope.section(
    title: String,
    list: List<CycleWithPayment>,
    today: LocalDate,
    onOpen: (Long) -> Unit,
    onPaid: (CycleWithPayment) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h_$title") { SectionHeader(title) }
    items(list, key = { it.cycle.id }) { item ->
        PaymentRow(item, today, onClick = { onOpen(item.payment.id) }, onPaid = { onPaid(item) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun PaymentRow(item: CycleWithPayment, today: LocalDate, onClick: () -> Unit, onPaid: () -> Unit) {
    val income = item.payment.isIncome
    val (due, overdue) = item.cycle.dueText(today, income)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BankBadge(Banks.find(item.payment.bankCode), item.payment.title)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.payment.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.payment.institution?.takeIf { it.isNotBlank() }, item.payment.category.label())
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                due,
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue && !income) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            )
            CycleCalculator.originalDueIfShifted(item.payment, item.cycle)?.let { original ->
                Text(
                    Formatters.shiftNote(original),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.payment.amountText(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.payment.amountMinor == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = onPaid, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                Text(if (income) "Geldi" else "Ödedim", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

package com.utkusuer.hatirlatici.ui.payments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentWithReminders
import com.utkusuer.hatirlatici.data.repository.PaymentRepository
import com.utkusuer.hatirlatici.ui.components.BankBadge
import com.utkusuer.hatirlatici.ui.components.Banks
import com.utkusuer.hatirlatici.ui.components.ContentWidth
import com.utkusuer.hatirlatici.ui.components.isIncome
import com.utkusuer.hatirlatici.ui.components.SectionHeader
import com.utkusuer.hatirlatici.ui.components.amountText
import com.utkusuer.hatirlatici.ui.components.appContainer
import com.utkusuer.hatirlatici.ui.components.label
import com.utkusuer.hatirlatici.ui.components.scheduleText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PaymentsViewModel(private val repository: PaymentRepository) : ViewModel() {
    val payments: StateFlow<List<PaymentWithReminders>?> = repository.observePayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setActive(id: Long, active: Boolean) {
        viewModelScope.launch { repository.setActive(id, active) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(onAdd: () -> Unit, onOpenPayment: (Long) -> Unit) {
    val container = appContainer()
    val vm: PaymentsViewModel = viewModel { PaymentsViewModel(container.paymentRepository) }
    val payments by vm.payments.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Kayıtlar") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Yeni kayıt") }
        },
    ) { padding ->
        val list = payments ?: return@Scaffold
        val (activeAll, passive) = list.map { it.payment }.partition { it.isActive }
        val (incomes, active) = activeAll.partition { it.isIncome }
        ContentWidth {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
            ) {
                if (list.isEmpty()) {
                    item {
                        Text(
                            "Henüz kayıt yok. Sağ alttaki + ile kredi kartı, fatura, kredi veya maaş gibi bir gelir ekleyin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                if (active.isNotEmpty()) item { SectionHeader("Ödemeler · ${active.size}") }
                items(active, key = { it.id }) { p ->
                    PaymentListRow(p, onClick = { onOpenPayment(p.id) }, onToggle = { vm.setActive(p.id, it) })
                }
                if (incomes.isNotEmpty()) item { SectionHeader("Gelirler · ${incomes.size}") }
                items(incomes, key = { it.id }) { p ->
                    PaymentListRow(p, onClick = { onOpenPayment(p.id) }, onToggle = { vm.setActive(p.id, it) })
                }
                if (passive.isNotEmpty()) item { SectionHeader("Pasif · bildirim yok") }
                items(passive, key = { it.id }) { p ->
                    PaymentListRow(p, onClick = { onOpenPayment(p.id) }, onToggle = { vm.setActive(p.id, it) })
                }
            }
        }
    }
}

@Composable
private fun PaymentListRow(p: PaymentEntity, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val dim = if (p.isActive) 1f else 0.55f
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BankBadge(Banks.find(p.bankCode), p.title, Modifier.alpha(dim))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(p.title, p.cardLast4?.let { "•••• $it" }).joinToString("  "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(p.institution?.takeIf { it.isNotBlank() }, p.category.label(), p.amountText()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                p.scheduleText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = p.isActive, onCheckedChange = onToggle)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.utkusuer.hatirlatici.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.utkusuer.hatirlatici.data.local.entity.ActivityLogEntity
import com.utkusuer.hatirlatici.data.local.entity.LogType
import com.utkusuer.hatirlatici.data.repository.PaymentRepository
import com.utkusuer.hatirlatici.domain.Formatters
import com.utkusuer.hatirlatici.ui.components.ContentWidth
import com.utkusuer.hatirlatici.ui.components.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogViewModel(private val repository: PaymentRepository) : ViewModel() {
    val entries: StateFlow<List<ActivityLogEntity>?> = repository.observeLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clear() {
        viewModelScope.launch { repository.clearLog() }
    }
}

private fun LogType.label(): String = when (this) {
    LogType.PAID -> "Ödendi"
    LogType.RECEIVED -> "Para geldi"
    LogType.UNPAID -> "Geri alındı"
    LogType.CREATED -> "Kayıt eklendi"
    LogType.UPDATED -> "Kayıt düzenlendi"
    LogType.DELETED -> "Kayıt silindi"
    LogType.ACTIVATED -> "Aktif edildi"
    LogType.DEACTIVATED -> "Pasife alındı"
}

/**
 * İşlem geçmişi. İki ayrı sekme: ödeme/gelir hareketleri ve kayıt (ekleme/düzenleme) işlemleri.
 */
@Composable
fun LogScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: LogViewModel = viewModel { LogViewModel(container.paymentRepository) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("İşlem geçmişi") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = { TextButton(onClick = { confirmClear = true }) { Text("Temizle") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ödeme tarihleri") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Kayıt işlemleri") })
            }
            val list = entries.orEmpty().filter { it.type.isPaymentEvent == (tab == 0) }
            ContentWidth {
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    if (entries != null && list.isEmpty()) {
                        item {
                            Text(
                                if (tab == 0) "Henüz ödeme kaydı yok." else "Henüz kayıt işlemi yok.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { LogRow(it) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Geçmiş temizlensin mi?") },
            text = { Text("Tüm log kayıtları silinecek. Ödeme durumları etkilenmez.") },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Temizle") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun LogRow(e: ActivityLogEntity) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium)
            Text(
                e.type.label() + (e.detail?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = when (e.type) {
                    LogType.PAID, LogType.RECEIVED -> MaterialTheme.colorScheme.secondary
                    LogType.DELETED, LogType.UNPAID -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(Formatters.dateTime(e.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        e.amountMinor?.let {
            Text(Formatters.money(it, "TRY"), style = MaterialTheme.typography.bodyMedium)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

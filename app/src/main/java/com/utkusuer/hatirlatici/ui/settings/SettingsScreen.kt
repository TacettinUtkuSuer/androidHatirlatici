@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.utkusuer.hatirlatici.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.utkusuer.hatirlatici.BuildConfig
import com.utkusuer.hatirlatici.data.settings.AppSettings
import com.utkusuer.hatirlatici.data.settings.SettingsRepository
import com.utkusuer.hatirlatici.domain.Formatters
import com.utkusuer.hatirlatici.notification.ReminderScheduler
import com.utkusuer.hatirlatici.ui.components.SectionHeader
import com.utkusuer.hatirlatici.ui.components.appContainer
import com.utkusuer.hatirlatici.work.ReminderSync
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val sync: ReminderSync,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun edit(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch {
            repo.block()
            sync.refreshNow()
        }
    }

    fun setPersistent(v: Boolean) = edit { setPersistentEnabled(v) }
    fun setReminders(v: Boolean) = edit { setRemindersEnabled(v) }
    fun setUpcomingDays(v: Int) = edit { setUpcomingWindowDays(v) }
    fun addTime(minute: Int) = edit { setDefaultReminderMinutes((current().defaultReminderMinutes + minute)) }
    fun removeTime(minute: Int) = edit { setDefaultReminderMinutes(current().defaultReminderMinutes - minute) }
}

private data class SystemStatus(val notifications: Boolean, val exactAlarms: Boolean, val batteryUnrestricted: Boolean)

private fun readStatus(context: Context, scheduler: ReminderScheduler) = SystemStatus(
    notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    exactAlarms = scheduler.canExact(),
    batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
)

@Composable
fun SettingsScreen(onOpenLog: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container.settingsRepository, container.reminderSync) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf(readStatus(context, container.scheduler)) }
    LifecycleResumeEffect(Unit) {
        status = readStatus(context, container.scheduler)
        onPauseOrDispose { }
    }
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Ayarlar") }) }) { padding ->
        val s = settings ?: return@Scaffold
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader("Bildirimler")
                ToggleRow("Kalıcı bildirimler", "Ödeme / gelir zamanı geldiğinde bildirim çubuğunda kalır", s.persistentNotificationsEnabled, vm::setPersistent)
                ToggleRow("Saatli hatırlatmalar", "Aşağıdaki saatlerde sesli uyarı", s.remindersEnabled, vm::setReminders)

                Text("Varsayılan hatırlatma saatleri", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.defaultReminderMinutes.forEach { m ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removeTime(m) },
                            label = { Text(Formatters.minuteOfDay(m)) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Kaldır") },
                        )
                    }
                    AssistChip(onClick = { showTimePicker = true }, label = { Text("Saat ekle") }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                }
                Text(
                    "Kayıtlarda \"Varsayılan hatırlatma saatleri\" açıksa bu saatler kullanılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionHeader("Ana ekran")
                var days by remember(s.upcomingWindowDays) { mutableIntStateOf(s.upcomingWindowDays) }
                Text("Yaklaşan ödemeler: önümüzdeki $days gün", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = days.toFloat(),
                    onValueChange = { days = it.toInt() },
                    onValueChangeFinished = { vm.setUpcomingDays(days) },
                    valueRange = 3f..45f,
                )

                SectionHeader("Sistem izinleri")
                StatusRow("Bildirim izni", status.notifications) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
                StatusRow("Tam zamanlı alarm", status.exactAlarms) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
                StatusRow("Pil kısıtlaması yok", status.batteryUnrestricted) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
                Text(
                    "Samsung cihazlarda: Uygulama bilgisi → Pil → \"Kısıtlanmamış\" seçin. Bu, pil tüketimini artırmaz; " +
                        "yalnızca One UI'ın uygulamayı uyutup hatırlatmaları geciktirmesini engeller. Uygulama arka planda " +
                        "sürekli çalışmaz: günde yalnızca hatırlatma saatlerinizde kısa süreliğine uyanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionHeader("Akıllı saat")
                Text(
                    "Huawei saat için: Huawei Health → Cihazlar → saatiniz → Bildirimler → \"Hatırlatıcı\"yı açın. " +
                        "Kalıcı (sessiz) bildirimler saatlere genellikle iletilmez; saatli hatırlatmalar iletilir. " +
                        "Telefon ekranı açıkken saat bildirim göstermeyebilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { container.notifier.showTest() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Test bildirimi gönder")
                }

                SectionHeader("Kayıtlar")
                NavRow("İşlem geçmişi (log)", "Ödeme tarihleri ve kayıt ekleme/düzenleme günleri", onOpenLog)

                BackupSection()

                SectionHeader("Hakkında")
                Text("Hatırlatıcı ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Hatırlatma saati") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.addTime(pickerState.hour * 60 + pickerState.minute)
                    showTimePicker = false
                }) { Text("Ekle") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (ok) "Açık" else "Kapalı — hatırlatmalar etkilenebilir",
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        if (!ok) TextButton(onClick = { runCatching(onFix) }) { Text("Düzelt") }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

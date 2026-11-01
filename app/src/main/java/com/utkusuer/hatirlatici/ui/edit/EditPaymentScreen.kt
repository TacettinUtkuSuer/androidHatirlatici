@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.utkusuer.hatirlatici.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.utkusuer.hatirlatici.data.local.entity.PaymentCategory
import com.utkusuer.hatirlatici.data.local.entity.Recurrence
import com.utkusuer.hatirlatici.data.local.entity.ScheduleType
import com.utkusuer.hatirlatici.domain.Formatters
import com.utkusuer.hatirlatici.ui.components.SectionHeader
import com.utkusuer.hatirlatici.ui.components.appContainer
import com.utkusuer.hatirlatici.ui.components.label

@Composable
fun EditPaymentScreen(paymentId: Long, onDone: () -> Unit) {
    val container = appContainer()
    val vm: EditPaymentViewModel = viewModel(key = "edit_$paymentId") {
        EditPaymentViewModel(paymentId, container.paymentRepository, container.settingsRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) { if (state.finished) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Yeni kayıt" else "Kaydı düzenle") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                },
                actions = {
                    TextButton(onClick = vm::save, enabled = !state.loading && !state.saving) { Text("Kaydet") }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        val f = state.form
        val e = state.errors
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // --- Genel ---
                SectionHeader("Genel")
                Field("Ad *", f.title, e["title"]) { v -> vm.update { it.copy(title = v) } }
                Field("Kurum / banka", f.institution, null) { v -> vm.update { it.copy(institution = v) } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentCategory.entries.forEach { c ->
                        FilterChip(
                            selected = f.category == c,
                            onClick = { vm.update { it.copy(category = c) } },
                            label = { Text(c.label()) },
                        )
                    }
                }

                // --- Zamanlama ---
                SectionHeader("Zamanlama")
                Segmented(
                    options = listOf(ScheduleType.SINGLE_DAY to "Tek gün", ScheduleType.DATE_RANGE to "Tarih aralığı"),
                    selected = f.scheduleType,
                ) { v -> vm.update { it.copy(scheduleType = v) } }

                if (f.scheduleType == ScheduleType.SINGLE_DAY) {
                    NumberField("Son ödeme günü (ayın kaçı) *", f.dayStart, e["dayStart"]) { v -> vm.update { it.copy(dayStart = v) } }
                    NumberField("Kaç gün önceden hatırlatmaya başlansın", f.leadDays, e["leadDays"]) { v -> vm.update { it.copy(leadDays = v) } }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField("Başlangıç günü *", f.dayStart, e["dayStart"], Modifier.weight(1f)) { v -> vm.update { it.copy(dayStart = v) } }
                        NumberField("Son gün *", f.dayEnd, e["dayEnd"], Modifier.weight(1f)) { v -> vm.update { it.copy(dayEnd = v) } }
                    }
                    Hint("Son gün başlangıçtan küçükse sonraki aya taşar (ör. 25 → 10).")
                    NumberField("İlk kaç gün hatırlatma yapılmasın", f.delayDays, e["delayDays"]) { v -> vm.update { it.copy(delayDays = v) } }
                    Hint("Ör. 1–15 aralığında 10 girerseniz bildirimler ayın 11'inde başlar.")
                }

                Segmented(
                    options = listOf(Recurrence.MONTHLY to "Her ay", Recurrence.ONE_TIME to "Tek seferlik"),
                    selected = f.recurrence,
                ) { v -> vm.update { it.copy(recurrence = v) } }
                if (f.recurrence == Recurrence.MONTHLY) {
                    NumberField("Toplam taksit sayısı (opsiyonel)", f.installments, e["installments"]) { v -> vm.update { it.copy(installments = v) } }
                }

                // --- Tutar ---
                SectionHeader("Tutar")
                OutlinedTextField(
                    value = f.amount,
                    onValueChange = { v -> vm.update { it.copy(amount = v) } },
                    label = { Text("Tutar (₺)") },
                    placeholder = { Text("Boş bırakın: tutar her ay değişiyor") },
                    isError = e["amount"] != null,
                    supportingText = e["amount"]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // --- Bildirimler ---
                SectionHeader("Bildirimler")
                SwitchRow("Aktif", "Pasif kayıtlar hiçbir bildirim üretmez", f.isActive) { v -> vm.update { it.copy(isActive = v) } }
                SwitchRow("Kalıcı bildirim", "Ödeme yapılana kadar bildirim çubuğunda kalır", f.persistent) { v -> vm.update { it.copy(persistent = v) } }
                SwitchRow(
                    "Varsayılan hatırlatma saatleri",
                    state.defaultTimes.joinToString(", ") { Formatters.minuteOfDay(it) }.ifEmpty { "Tanımlı değil" },
                    f.useDefaultTimes,
                ) { v -> vm.update { it.copy(useDefaultTimes = v) } }
                if (!f.useDefaultTimes) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        f.customTimes.forEach { m ->
                            InputChip(
                                selected = false,
                                onClick = { vm.removeTime(m) },
                                label = { Text(Formatters.minuteOfDay(m)) },
                                trailingIcon = { Icon(Icons.Filled.Close, "Kaldır", Modifier.padding(start = 2.dp)) },
                            )
                        }
                        AssistChip(
                            onClick = { showTimePicker = true },
                            label = { Text("Saat ekle") },
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                        )
                    }
                    if (f.customTimes.isEmpty()) Hint("Saat eklenmezse yalnızca kalıcı bildirim gösterilir.")
                }

                // --- Kart bilgisi ---
                SectionHeader("Kart bilgisi")
                NumberField("Kartın son 4 hanesi", f.cardLast4, e["cardLast4"], maxLength = 4) { v ->
                    vm.update { it.copy(cardLast4 = v) }
                }
                if (f.hasStoredPin && !f.removePin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "PIN şifreli olarak kayıtlı",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.update { it.copy(removePin = true, newPin = "") } }) { Text("Sil") }
                    }
                }
                OutlinedTextField(
                    value = f.newPin,
                    onValueChange = { v -> vm.update { it.copy(newPin = v.filter(Char::isDigit).take(12)) } },
                    label = { Text(if (f.hasStoredPin) "Yeni PIN (değiştirmek için)" else "PIN (opsiyonel)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint("PIN, Android Keystore ile cihaza bağlı bir anahtarla şifrelenir. Saklamak kendi sorumluluğunuzdadır.")

                // --- Not ---
                SectionHeader("Not")
                OutlinedTextField(
                    value = f.note,
                    onValueChange = { v -> vm.update { it.copy(note = v) } },
                    label = { Text("Not") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!state.isNew) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Kaydı sil", color = MaterialTheme.colorScheme.error)
                    }
                }
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Kayıt silinsin mi?") },
            text = { Text("Bu kayıt ve tüm geçmiş ödeme bilgileri kalıcı olarak silinecek. Yalnızca bildirimleri durdurmak için \"Aktif\" anahtarını kapatabilirsiniz.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Sil", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun Field(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    maxLength: Int = 3,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(maxLength)) },
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, text) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(text) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

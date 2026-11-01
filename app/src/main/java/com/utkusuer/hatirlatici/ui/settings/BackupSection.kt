package com.utkusuer.hatirlatici.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.Identity
import com.utkusuer.hatirlatici.data.backup.DriveAuth
import com.utkusuer.hatirlatici.domain.Formatters
import com.utkusuer.hatirlatici.ui.components.SectionHeader
import com.utkusuer.hatirlatici.ui.components.authenticate
import java.time.Instant

private enum class ExportTarget { FILE, DRIVE }

/** Ayarlar → Yedekleme: yerel dosya ve Google Drive. */
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val vm: BackupViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val drive by vm.drive.collectAsStateWithLifecycle()

    var exportTarget by remember { mutableStateOf<ExportTarget?>(null) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var pendingDriveAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportToFile(uri, exportPassword)
        exportPassword = null
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadFromFile(uri)
    }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val action = pendingDriveAction
        pendingDriveAction = null
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data).accessToken }
            .onSuccess { token -> if (token != null) { vm.onDriveLinked(); action?.invoke(token) } }
            .onFailure(vm::showError)
    }

    /** Drive erişim anahtarı al (gerekirse Google hesap seçimi / izin ekranı açılır), sonra [action]. */
    fun withDriveToken(action: (String) -> Unit) {
        Identity.getAuthorizationClient(context).authorize(DriveAuth.request())
            .addOnSuccessListener { r ->
                if (r.hasResolution()) {
                    pendingDriveAction = action
                    authLauncher.launch(IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build())
                } else {
                    r.accessToken?.let { vm.onDriveLinked(); action(it) }
                }
            }
            .addOnFailureListener(vm::showError)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Yedekleme")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { vm.clearMessage() },
            )
        }

        Text("Cihaz hafızası", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { exportTarget = ExportTarget.FILE }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("Dışa aktar")
            }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("İçe aktar")
            }
        }

        Text("Google Drive", style = MaterialTheme.typography.bodyLarge)
        Text(
            when {
                !drive.linked -> "Bağlı değil. İlk yedeklemede Google hesabınızı seçmeniz istenecek."
                drive.lastBackupAt != null -> "Bağlı · son yedek ${Formatters.dateTime(drive.lastBackupAt!!)}"
                else -> "Bağlı · henüz yedek yok"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { exportTarget = ExportTarget.DRIVE }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("Drive'a yedekle")
            }
            OutlinedButton(onClick = { withDriveToken(vm::driveListForRestore) }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("Geri yükle")
            }
        }
        if (drive.linked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Haftalık otomatik yedek", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "İnternet varken ve pil düşük değilken. Kart bilgileri dahil edilmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = drive.autoBackup, onCheckedChange = vm::setAutoBackup)
            }
            TextButton(onClick = vm::unlinkDrive) { Text("Drive bağlantısını kaldır") }
        }
    }

    exportTarget?.let { target ->
        ExportDialog(
            onDismiss = { exportTarget = null },
            onConfirm = { includeSecrets, password ->
                exportTarget = null
                val proceed = {
                    val pw = if (includeSecrets) password else null
                    when (target) {
                        ExportTarget.FILE -> { exportPassword = pw; createDoc.launch(vm.suggestedFileName()) }
                        ExportTarget.DRIVE -> withDriveToken { token -> vm.driveBackup(token, pw) }
                    }
                }
                if (includeSecrets) authenticate(context, "Kart bilgilerini yedekle", onSuccess = proceed) else proceed()
            },
        )
    }

    state.driveFiles?.let { files ->
        AlertDialog(
            onDismissRequest = vm::closeDriveFiles,
            title = { Text("Drive'daki yedekler") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(files, key = { it.id }) { f ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { withDriveToken { token -> vm.driveDownload(token, f) } }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                runCatching { Formatters.dateTime(Instant.parse(f.modifiedTime).toEpochMilli()) }.getOrDefault(f.modifiedTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::closeDriveFiles) { Text("Kapat") } },
        )
    }

    state.pendingImport?.let { pending ->
        ImportDialog(pending, onConfirm = vm::confirmImport, onDismiss = vm::cancelImport)
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onConfirm: (includeSecrets: Boolean, password: String) -> Unit) {
    var includeSecrets by rememberSaveable { mutableStateOf(false) }
    var pw by rememberSaveable { mutableStateOf("") }
    var pw2 by rememberSaveable { mutableStateOf("") }
    val pwOk = !includeSecrets || (pw.length >= 6 && pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yedekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tüm kayıtlar, ödeme geçmişi, log ve ayarlar yedeklenir.", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kart ve hesap bilgilerini dahil et", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = includeSecrets, onCheckedChange = { includeSecrets = it })
                }
                if (includeSecrets) {
                    PasswordField("Yedek parolası (en az 6 karakter)", pw) { pw = it }
                    PasswordField("Parola tekrar", pw2) { pw2 = it }
                    Text(
                        "Kart bilgileri bu parolayla şifrelenir. Parolayı unutursanız bu bilgiler geri yüklenemez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(includeSecrets, pw) }, enabled = pwOk) { Text("Devam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun ImportDialog(pending: PendingImport, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var pw by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yedek geri yüklensin mi?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${pending.source} · ${Formatters.dateTime(pending.info.createdAt)}\n${pending.info.paymentCount} kayıt" +
                        if (pending.info.hasSecrets) " · kart bilgileri (parolalı)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (pending.info.hasSecrets) {
                    PasswordField("Yedek parolası", pw) { pw = it }
                    Text(
                        "Boş bırakırsanız kart bilgileri olmadan yüklenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Dikkat: Bu cihazdaki mevcut tüm kayıtlar silinip yedektekilerle değiştirilecek.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }) { Text("Geri yükle") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

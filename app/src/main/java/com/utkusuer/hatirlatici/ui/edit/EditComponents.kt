package com.utkusuer.hatirlatici.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.utkusuer.hatirlatici.data.security.CardSecrets
import com.utkusuer.hatirlatici.ui.components.BankBadge
import com.utkusuer.hatirlatici.domain.Banks

@Composable
fun BankPickerRow(bankCode: String?, title: String, onClick: () -> Unit) {
    val bank = Banks.find(bankCode)
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BankBadge(bank, title.ifBlank { "?" })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Banka", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bank?.name ?: "Seçilmedi", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Değiştir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun BankPickerDialog(selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Banka seç") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Ara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    item {
                        BankOption(null, "Banka yok", selected == null) { onSelect(null) }
                    }
                    items(Banks.search(query), key = { it.code }) { bank ->
                        BankOption(bank.code, bank.name, selected == bank.code) { onSelect(bank.code) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun BankOption(code: String?, name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BankBadge(Banks.find(code), "–", size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (isSelected) Icon(Icons.Filled.Check, contentDescription = "Seçili")
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Kart no, SKT, CVV, PIN, internet bankacılığı şifresi, hesap no / IBAN. */
@Composable
fun SecretFields(
    secrets: CardSecrets,
    income: Boolean,
    errors: Map<String, String>,
    onChange: ((CardSecrets) -> CardSecrets) -> Unit,
) {
    var reveal by rememberSaveable { mutableStateOf(false) }
    val hidden: VisualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Şifreler ${if (reveal) "görünür" else "gizli"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Gizle" else "Göster") }
        }
        if (!income) {
            SecretField(
                label = "Kart numarası",
                value = secrets.cardNumber,
                error = errors["cardNumber"],
                keyboardType = KeyboardType.Number,
                visual = CardNumberTransformation,
            ) { v -> onChange { it.copy(cardNumber = v.filter(Char::isDigit).take(19)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecretField(
                    label = "Son kullanma (AA/YY)",
                    value = secrets.expiry,
                    error = errors["expiry"],
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                ) { v -> onChange { it.copy(expiry = formatExpiry(v)) } }
                SecretField(
                    label = "CVV (arkadaki kod)",
                    value = secrets.cvv,
                    keyboardType = KeyboardType.NumberPassword,
                    visual = hidden,
                    modifier = Modifier.weight(1f),
                ) { v -> onChange { it.copy(cvv = v.filter(Char::isDigit).take(4)) } }
            }
            SecretField(
                label = "Kart şifresi (PIN)",
                value = secrets.pin,
                keyboardType = KeyboardType.NumberPassword,
                visual = hidden,
            ) { v -> onChange { it.copy(pin = v.filter(Char::isDigit).take(12)) } }
        }
        SecretField(
            label = "İnternet bankacılığı şifresi",
            value = secrets.bankingPassword,
            keyboardType = KeyboardType.Password,
            visual = hidden,
        ) { v -> onChange { it.copy(bankingPassword = v.take(64)) } }
        SecretField(
            label = "Hesap numarası / IBAN",
            value = secrets.accountNumber,
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
        ) { v -> onChange { it.copy(accountNumber = v.uppercase().take(40)) } }
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    error: String? = null,
    keyboardType: KeyboardType,
    visual: VisualTransformation = VisualTransformation.None,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization, autoCorrectEnabled = false),
        singleLine = true,
        modifier = modifier,
    )
}

/** "1225" → "12/25" */
internal fun formatExpiry(raw: String): String {
    val d = raw.filter(Char::isDigit).take(4)
    return if (d.length <= 2) d else d.substring(0, 2) + "/" + d.substring(2)
}

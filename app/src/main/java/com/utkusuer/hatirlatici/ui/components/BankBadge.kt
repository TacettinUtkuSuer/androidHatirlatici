package com.utkusuer.hatirlatici.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.utkusuer.hatirlatici.domain.Bank
import com.utkusuer.hatirlatici.domain.Formatters

/** Banka rozeti. Banka yoksa kaydın baş harfiyle nötr bir rozet çizilir. */
@Composable
fun BankBadge(bank: Bank?, fallback: String, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val bg = bank?.let { Color(it.color) } ?: MaterialTheme.colorScheme.surfaceVariant
    val fg = when {
        bank == null -> MaterialTheme.colorScheme.onSurfaceVariant
        bank.darkText -> Color(0xFF15171A)
        else -> Color.White
    }
    val text = bank?.short ?: fallback.trim().take(1).uppercase(Formatters.TR)
    val fontSize = when {
        text.length >= 4 -> (size.value * 0.26f).sp
        text.length == 3 -> (size.value * 0.3f).sp
        else -> (size.value * 0.36f).sp
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = fg, fontSize = fontSize, fontWeight = FontWeight.Bold, letterSpacing = 0.sp))
    }
}

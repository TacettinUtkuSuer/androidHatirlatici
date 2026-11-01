package com.utkusuer.hatirlatici.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.utkusuer.hatirlatici.HatirlaticiApp
import com.utkusuer.hatirlatici.data.local.entity.EntryKind
import com.utkusuer.hatirlatici.domain.Formatters
import com.utkusuer.hatirlatici.ui.MainActivity
import java.time.LocalDate

private data class WidgetRow(
    val id: Long,
    val title: String,
    val due: String,
    val amount: String,
    val overdue: Boolean,
    val income: Boolean,
)

/**
 * Ana ekran widget'ı: gecikmiş + önümüzdeki 14 gün. Renkler GlanceTheme ile telefonun
 * (Material You) temasından gelir; içerik aşırı sade tutulur.
 */
class PaymentsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = load(context)
        provideContent {
            GlanceTheme { Content(rows) }
        }
    }

    private suspend fun load(context: Context): List<WidgetRow> {
        val container = (context.applicationContext as HatirlaticiApp).container
        val today = LocalDate.now()
        return container.database.cycleDao().getOpen(today.plusDays(14).toEpochDay()).map { item ->
            val p = item.payment
            val end = LocalDate.ofEpochDay(item.cycle.windowEndEpochDay)
            val left = end.toEpochDay() - today.toEpochDay()
            val income = p.kind == EntryKind.INCOME
            WidgetRow(
                id = item.cycle.id,
                title = p.title,
                due = when {
                    left < 0 -> if (income) "Bekleniyor" else "${-left} gün gecikti"
                    left == 0L -> "Bugün"
                    left == 1L -> "Yarın"
                    else -> Formatters.shortDate(end)
                },
                amount = p.amountMinor?.let { (if (income) "+" else "") + Formatters.money(it, p.currency) } ?: "",
                overdue = left < 0,
                income = income,
            )
        }
    }

    @Composable
    private fun Content(rows: List<WidgetRow>) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                "Ödemeler",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(6.dp))
            if (rows.isEmpty()) {
                Text(
                    "Yaklaşan ödeme yok",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            } else {
                LazyColumn {
                    items(rows, itemId = { it.id }) { row ->
                        Row(
                            GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable(actionStartActivity<MainActivity>()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(GlanceModifier.defaultWeight()) {
                                Text(
                                    row.title,
                                    maxLines = 1,
                                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                                )
                                if (row.amount.isNotEmpty()) {
                                    Text(
                                        row.amount,
                                        maxLines = 1,
                                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                                    )
                                }
                            }
                            Text(
                                row.due,
                                style = TextStyle(
                                    color = if (row.overdue && !row.income) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            runCatching { PaymentsWidget().updateAll(context) }
        }
    }
}

class PaymentsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaymentsWidget()
}

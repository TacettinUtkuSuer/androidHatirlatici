package com.utkusuer.hatirlatici.domain

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object Formatters {
    val TR: Locale = Locale.forLanguageTag("tr-TR")
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", TR)

    fun shortDate(date: LocalDate): String = shortDate.format(date)

    fun money(amountMinor: Long, currency: String): String {
        val f = NumberFormat.getCurrencyInstance(TR)
        runCatching { f.currency = Currency.getInstance(currency) }
        return f.format(amountMinor / 100.0)
    }

    fun minuteOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}
